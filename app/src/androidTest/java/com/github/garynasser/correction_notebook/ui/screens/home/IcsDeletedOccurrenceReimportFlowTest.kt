package com.github.garynasser.correction_notebook.ui.screens.home

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.model.home.ImportDecision
import com.github.garynasser.correction_notebook.data.repository.IcsImportRepository
import com.github.garynasser.correction_notebook.ui.components.LocalAiEnabled
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import java.io.File
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class IcsDeletedOccurrenceReimportFlowTest {
    @get:Rule val compose = createComposeRule()
    private val monday = LocalDate.of(2026, 10, 5)

    @Test fun aFailedMergeCanRetryWithoutRestoringTheDeletedMovedCourse() = checkReimport(ImportDecision.MERGE, failOnce = true)
    @Test fun aMergeProtectsDeletionEvenWhenTheOriginalIcsAlreadyExcludedThatDate() = checkReimport(ImportDecision.MERGE, originallyExcluded = true)
    @Test fun anExplicitOverwriteRestoresTheReviewedVersionWithoutChangingAnotherCalendar() = checkReimport(ImportDecision.OVERWRITE)

    private fun checkReimport(decision: ImportDecision, originallyExcluded: Boolean = false, failOnce: Boolean = false): Unit = runBlocking {
        val f = HomeFormSaveFailureTest.Fixture()
        val file = File(File(f.context.filesDir, "knowledge_base").apply { mkdirs() }, "qa-deleted-course-${UUID.randomUUID()}.ics")
        try {
            withContext(Dispatchers.Main) { f.create() }
            f.await { f.home.uiState.value.scheduleSections.size == 7 }
            val lines = mutableListOf(
                "BEGIN:VCALENDAR", "VERSION:2.0", "X-WR-CALNAME:${file.name}",
                "BEGIN:VEVENT", "UID:course", "DTSTART:20261005T080000", "DTEND:20261005T093500",
                "RRULE:FREQ=WEEKLY;COUNT=3", "SUMMARY:计算理论与算法分析设计", "LOCATION:文萃楼 M134"
            )
            if (originallyExcluded) lines += "EXDATE:20261019T080000"
            lines += listOf("END:VEVENT", "BEGIN:VEVENT", "UID:course", "RECURRENCE-ID:20261019T080000",
                "DTSTART:20261006T132000", "DTEND:20261006T145500", "SUMMARY:调课后的计算理论与算法分析设计",
                "LOCATION:综教 A303\\n良乡校区", "END:VEVENT", "END:VCALENDAR")
            file.writeText(lines.joinToString("\r\n"))
            val uri = FileProvider.getUriForFile(f.context, "${f.context.packageName}.fileprovider", file)
            val initial = IcsImportRepository(f.context, f.schedules).buildPreview(uri)
            f.schedules.applyImportPreview(initial, ImportDecision.MERGE)
            val moved = initial.incomingEvents.single { it.recurrenceId != null }
            val master = initial.incomingEvents.single { it.recurrenceId == null }
            val other = master.copy(id = "other-calendar", title = "独立日历的计算理论", sourceCalendarId = "other-calendar")
            f.schedules.addEvent(other)
            withContext(Dispatchers.Main) { f.home.setSelectedWeek(monday) }
            f.await { f.home.uiState.value.selectedDate == monday &&
                f.home.uiState.value.scheduleSections.flatMap { it.items }.size == 3 }
            val dark = InstrumentationRegistry.getArguments().getString("qaDark") == "true"
            compose.setContent {
                CompositionLocalProvider(LocalAiEnabled provides false) {
                    CorrectionNotebookTheme(darkTheme = dark, dynamicColor = false) { HomeScreen(f.home, f.statistics) }
                }
            }

            compose.onNodeWithText(moved.title, substring = true).performClick()
            compose.onNodeWithText("删除").performClick()
            compose.onNodeWithText("仅这一次").performScrollTo().assertIsSelected()
            compose.onNodeWithText("删除").performClick()
            f.await { !f.home.uiState.value.isEditingSchedule &&
                f.home.uiState.value.scheduleSections.flatMap { it.items }.none { it.eventId == moved.id } }
            val deleted = f.schedules.scheduleEvents.first()
            val localMaster = deleted.single { it.id == master.id }
            assertEquals(listOf(moved.recurrenceId!!), localMaster.exDateList)
            assertTrue(localMaster.updatedAt > localMaster.lastImportedAt!!)
            assertEquals(other, deleted.single { it.id == other.id })

            withContext(Dispatchers.Main) { f.home.importIcs(uri) }
            f.await { f.home.uiState.value.pendingIcsPreview != null && !f.home.uiState.value.isImportingSchedule }
            val reviewed = f.home.uiState.value.pendingIcsPreview!!
            assertTrue(reviewed.added.isEmpty())
            assertEquals(if (originallyExcluded) 1 else 2, reviewed.conflicts.size)
            assertEquals(WARNING, reviewed.conflicts.single { it.title == moved.title }.detail)
            val list = compose.onNode(hasScrollAction() and hasAnyAncestor(isDialog()))
            list.performScrollToNode(hasText(moved.title))
            assertTextFits(moved.title).forEach { layout ->
                if (layout.lineCount > 1) {
                    val last = layout.lineCount - 1
                    assertTrue("A course title must not leave a single-character last line",
                        layout.getLineEnd(last) - layout.getLineStart(last) > 1)
                }
            }
            list.performScrollToNode(hasText(WARNING))
            assertTextFits(WARNING)
            capture("${if (dark) "dark" else "light"}-${decision.name.lowercase()}-${if (originallyExcluded) "excluded" else "plain"}")
            assertTrue(file.delete()) // Retry must apply the reviewed content, not reopen its source file.
            val label = if (decision == ImportDecision.MERGE) "合并" else "覆盖"
            if (failOnce) {
                val writes = f.scheduleWrites.attempts.get()
                f.scheduleWrites.fail = true
                compose.onNodeWithText(label).performClick()
                f.await { f.scheduleWrites.attempts.get() == writes + 1 && !f.home.uiState.value.isImportingSchedule }
                assertSame(reviewed, f.home.uiState.value.pendingIcsPreview)
                assertEquals(deleted, f.schedules.scheduleEvents.first())
                compose.onNodeWithText("日程保存失败，请稍后再试").assertIsDisplayed()
                f.scheduleWrites.fail = false
            }
            compose.onNodeWithText(label).performClick()
            f.await { f.home.uiState.value.pendingIcsPreview == null && !f.home.uiState.value.isImportingSchedule }
            compose.onNodeWithText("课表导入完成").assertIsDisplayed()
            val saved = f.schedules.scheduleEvents.first()
            if (decision == ImportDecision.MERGE) {
                assertEquals(deleted, saved)
                compose.onNodeWithText("合并导入完成").assertIsDisplayed()
            } else {
                assertEquals((reviewed.incomingEvents + other).toSet(), saved.toSet())
                compose.onNodeWithText("已覆盖导入 2 个日程").assertIsDisplayed()
            }
            compose.onNodeWithText("知道了").performClick()
            f.await { f.home.uiState.value.scheduleSections.flatMap { it.items }.size ==
                if (decision == ImportDecision.MERGE) 2 else 3 }
            if (decision == ImportDecision.MERGE) {
                compose.onNodeWithText(moved.title, substring = true).assertDoesNotExist()
            } else {
                compose.onNodeWithText(moved.title, substring = true).assertIsDisplayed()
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

    private fun assertTextFits(text: String): List<TextLayoutResult> {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
        assertTrue(layouts.isNotEmpty())
        layouts.forEach { layout ->
            assertFalse(layout.didOverflowHeight)
            assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1))
            assertEquals(InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.fontScale,
                layout.layoutInput.density.fontScale, 0.01f)
            repeat(layout.lineCount) { line ->
                assertFalse(layout.isLineEllipsized(line))
                assertTrue(layout.getLineRight(line) <= layout.size.width + 1f)
            }
        }
        return layouts
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        Thread.sleep(300)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "qa-ics-deleted-$name.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    companion object {
        private const val WARNING = "该次调课的原日期已在本地排除；合并时保留本地安排，覆盖时恢复导入版本"
    }
}
