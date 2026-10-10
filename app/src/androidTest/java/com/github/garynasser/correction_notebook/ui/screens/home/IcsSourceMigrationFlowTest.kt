package com.github.garynasser.correction_notebook.ui.screens.home

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.model.home.ImportDecision
import com.github.garynasser.correction_notebook.data.repository.IcsImportRepository
import com.github.garynasser.correction_notebook.data.repository.ScheduleRepository
import com.github.garynasser.correction_notebook.data.repository.buildLegacyIcsCalendarId
import com.github.garynasser.correction_notebook.data.repository.buildPreviousIcsCalendarId
import com.github.garynasser.correction_notebook.ui.components.LocalAiEnabled
import com.github.garynasser.correction_notebook.ui.components.BottomBarTab
import com.github.garynasser.correction_notebook.ui.navigation.AITutor
import com.github.garynasser.correction_notebook.ui.navigation.Home
import com.github.garynasser.correction_notebook.ui.navigation.bottomNavList
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class IcsSourceMigrationFlowTest {
    @get:Rule val compose = createComposeRule()
    private val monday = LocalDate.of(2026, 10, 5)

    @Test fun anEditedPreviousVersionMasterRecognizesItsNewMovedCourseAndSingleDeletion() = checkMigration(false)
    @Test fun anEditedLegacyMovedCourseRecognizesItsUpdatedMasterAndSeriesDeletion() = checkMigration(true)

    private fun checkMigration(editedOverride: Boolean): Unit = runBlocking {
        val f = HomeFormSaveFailureTest.Fixture()
        val file = File(File(f.context.filesDir, "knowledge_base").apply { mkdirs() }, "qa-migration-${UUID.randomUUID()}.ics")
        try {
            withContext(Dispatchers.Main) { f.create() }
            f.await { f.home.uiState.value.scheduleSections.size == 7 }
            val masterLines = listOf("BEGIN:VEVENT", "UID:course", "DTSTART:20261005T080000", "DTEND:20261005T093500",
                "RRULE:FREQ=WEEKLY;COUNT=3", "SUMMARY:计算理论与算法分析设计", "LOCATION:文萃楼 M134", "END:VEVENT")
            val movedLines = listOf("BEGIN:VEVENT", "UID:course", "RECURRENCE-ID:20261019T080000",
                "DTSTART:20261006T132000", "DTEND:20261006T145500", "SUMMARY:调课后的计算理论与算法分析设计",
                "LOCATION:综教 A303\\n良乡校区", "END:VEVENT")
            val header = listOf("BEGIN:VCALENDAR", "VERSION:2.0", "X-WR-CALNAME:${file.name}")
            val originalLines = header + masterLines + (if (editedOverride) movedLines else emptyList()) + "END:VCALENDAR"
            val raw = originalLines.joinToString("\r\n")
            file.writeText(raw)
            val uri = FileProvider.getUriForFile(f.context, "${f.context.packageName}.fileprovider", file)
            val importer = IcsImportRepository(f.context, f.schedules)
            val initial = importer.buildPreview(uri)
            val oldCalendar = if (editedOverride) buildLegacyIcsCalendarId(file.name, raw)
                else buildPreviousIcsCalendarId(file.name, originalLines)
            val oldEvents = initial.incomingEvents.map { it.copy(sourceCalendarId = oldCalendar) }
            oldEvents.forEach { f.schedules.addEvent(it) }
            val edited = oldEvents.single { (it.recurrenceId != null) == editedOverride }.copy(
                title = if (editedOverride) "本地调整后的计算理论与算法分析设计" else "本地修改的计算理论与算法分析设计",
                location = "文萃楼 F702\n良乡校区", description = "本地备注：带上课堂笔记"
            )
            f.schedules.updateEvent(edited)
            val local = f.schedules.getEventById(edited.id)!!
            assertTrue(local.updatedAt > local.lastImportedAt!!)
            val other = oldEvents.single { it.recurrenceId == null }.copy(id = "other-calendar",
                title = "另一份日历的课程", sourceCalendarId = "ics_v3_other",
                startAt = monday.atTime(15, 15), endAt = monday.atTime(16, 50))
            f.schedules.addEvent(other)
            withContext(Dispatchers.Main) { f.home.setSelectedWeek(monday) }
            f.await { f.home.uiState.value.selectedDate == monday &&
                f.home.uiState.value.scheduleSections.flatMap { it.items }.size == if (editedOverride) 3 else 2 }
            val dark = InstrumentationRegistry.getArguments().getString("qaDark") == "true"
            compose.setContent {
                CompositionLocalProvider(LocalAiEnabled provides false) {
                    CorrectionNotebookTheme(darkTheme = dark, dynamicColor = false) {
                        Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0), bottomBar = {
                            Surface(Modifier.fillMaxWidth().navigationBarsPadding()) {
                                Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    bottomNavList.filter { it.route !is AITutor }.forEach { item ->
                                        BottomBarTab(Modifier.weight(1f), item.title, item.icon,
                                            isSelected = item.route is Home, onClick = {})
                                    }
                                }
                            }
                        }) { padding ->
                            Box(Modifier.fillMaxSize().padding(padding)) { HomeScreen(f.home, f.statistics) }
                        }
                    }
                }
            }
            val incomingMaster = if (editedOverride) masterLines.map {
                if (it.startsWith("SUMMARY:")) "SUMMARY:更新后的计算理论与算法分析设计" else it
            } else masterLines
            file.writeText((header + incomingMaster + movedLines + "END:VCALENDAR").joinToString("\r\n"))
            withContext(Dispatchers.Main) { f.home.importIcs(uri) }
            f.await { f.home.uiState.value.pendingIcsPreview != null && !f.home.uiState.value.isImportingSchedule }
            val reviewed = f.home.uiState.value.pendingIcsPreview!!
            assertTrue(oldCalendar in reviewed.replacedCalendarIds)
            assertEquals(1, reviewed.conflicts.size)
            assertEquals(if (editedOverride) 0 else 1, reviewed.added.size)
            assertEquals(if (editedOverride) 1 else 0, reviewed.updated.size)
            compose.onNodeWithText("合并").performClick()
            f.await { f.home.uiState.value.pendingIcsPreview == null && !f.home.uiState.value.isImportingSchedule }
            compose.onNodeWithText("合并导入完成").assertIsDisplayed()
            compose.onNodeWithText("知道了").performClick()
            f.await { f.home.uiState.value.scheduleSections.flatMap { it.items }.size == 3 }
            val saved = ScheduleRepository(f.scheduleWrites).scheduleEvents.first()
            assertEquals(local.copy(sourceCalendarId = reviewed.sourceCalendarId), saved.single { it.id == local.id })
            assertEquals(other, saved.single { it.id == other.id })
            assertEquals(setOf(reviewed.sourceCalendarId), saved.filterNot { it.id == other.id }.map { it.sourceCalendarId }.toSet())
            compose.onNodeWithText(local.title, substring = true).assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(local.title, substring = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                assertTrue(it(layouts))
            }
            assertTrue(layouts.isNotEmpty())
            layouts.forEach { layout ->
                assertFalse("The complete course and room must fit: size=${layout.size}, font=${layout.layoutInput.style.fontSize}",
                    layout.hasVisualOverflow)
                assertEquals(TextAlign.Center, layout.layoutInput.style.textAlign)
                assertTrue(layout.layoutInput.text.text.endsWith("F702"))
                assertEquals(layout.layoutInput.text.length, layout.getLineEnd(layout.lineCount - 1))
                repeat(layout.lineCount) { line ->
                    assertFalse(layout.isLineEllipsized(line))
                    assertTrue(layout.getLineBottom(line) <= layout.size.height + 1f)
                }
            }
            val navigation = compose.onNode(hasText("首页") and hasClickAction()).fetchSemanticsNode().boundsInRoot
            val lastPeriod = compose.onNodeWithText("13\n20:10").assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("The last period must remain above the bottom navigation", lastPeriod.bottom <= navigation.top)
            assertTrue(compose.onNodeWithContentDescription("课表操作").fetchSemanticsNode().boundsInRoot.bottom <= navigation.top)
            capture("${if (dark) "dark" else "light"}-${if (editedOverride) "override" else "master"}")

            repeat(2) { index ->
                compose.onNodeWithContentDescription("下一周").performClick()
                f.await { f.home.uiState.value.selectedDate == monday.plusWeeks(index + 1L) }
            }
            f.await { f.home.uiState.value.scheduleSections.flatMap { it.items }.map { it.eventId } == listOf(other.id) }
            saved.filterNot { it.id == other.id }.forEach {
                compose.onNodeWithText(it.title, substring = true).assertDoesNotExist()
            }
            compose.onNodeWithText(other.title, substring = true).assertIsDisplayed()
            repeat(2) { index ->
                compose.onNodeWithContentDescription("上一周").performClick()
                f.await { f.home.uiState.value.selectedDate == monday.plusWeeks(1L - index) }
            }
            f.await { f.home.uiState.value.scheduleSections.flatMap { it.items }.size == 3 }
            val moved = saved.single { it.id != other.id && it.recurrenceId != null }
            compose.onNodeWithText(moved.title, substring = true).performClick()
            compose.onNodeWithText(moved.location).performScrollTo().assertIsDisplayed()
            compose.onNodeWithText("删除").performClick()
            compose.onNodeWithText(if (editedOverride) "整组日程" else "仅这一次").performScrollTo().performClick()
            compose.onNodeWithText("删除").performClick()
            f.await { !f.home.uiState.value.isEditingSchedule &&
                f.home.uiState.value.scheduleSections.flatMap { it.items }.size == if (editedOverride) 1 else 2 }
            val deleted = f.schedules.scheduleEvents.first()
            if (editedOverride) {
                assertEquals(listOf(other), deleted)
            } else {
                val retained = deleted.single { it.id == local.id }
                assertEquals(listOf(moved.recurrenceId!!), retained.exDateList)
                assertEquals(reviewed.sourceCalendarId, retained.sourceCalendarId)
                assertEquals(other, deleted.single { it.id == other.id })
                withContext(Dispatchers.Main) { f.home.importIcs(uri) }
                f.await { f.home.uiState.value.pendingIcsPreview != null && !f.home.uiState.value.isImportingSchedule }
                assertEquals(2, f.home.uiState.value.pendingIcsPreview!!.conflicts.size)
                compose.onNodeWithText("合并").performClick()
                f.await { f.home.uiState.value.pendingIcsPreview == null && !f.home.uiState.value.isImportingSchedule }
                compose.onNodeWithText("知道了").performClick()
                assertEquals(deleted, f.schedules.scheduleEvents.first())
                compose.onNodeWithText(moved.title, substring = true).assertDoesNotExist()
            }
        } finally {
            withContext(NonCancellable) {
                withContext(Dispatchers.Main) { f.store.clear() }
                f.scope.cancel()
                f.scope.coroutineContext[Job]?.join()
                file.delete()
                f.files.forEach { it.delete() }
                f.database.close()
                f.knowledge.close()
                f.network.dispatcher.executorService.shutdownNow()
                f.network.connectionPool.evictAll()
            }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        Thread.sleep(300)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "qa-ics-migration-$name.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }
}
