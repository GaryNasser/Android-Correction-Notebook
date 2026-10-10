package com.github.garynasser.correction_notebook.ui.screens.home

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.model.home.ScheduleEvent
import com.github.garynasser.correction_notebook.data.model.home.ScheduleSourceType
import com.github.garynasser.correction_notebook.ui.components.LocalAiEnabled
import com.github.garynasser.correction_notebook.ui.components.BottomBarTab
import com.github.garynasser.correction_notebook.ui.navigation.AITutor
import com.github.garynasser.correction_notebook.ui.navigation.Home
import com.github.garynasser.correction_notebook.ui.navigation.bottomNavList
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CourseWeekPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightWeekKeepsCourseTextCompleteAndOpensTheOriginalAddress() = checkWeek(false)

    @Test fun darkWeekKeepsCourseTextCompleteAndOpensTheOriginalAddress() = checkWeek(true)

    @Test fun anAllDayScheduleOpensItsCompleteDetailsFromTheWeeklyList() = checkWeek(false, allDay = true)

    @Test fun aCrossDayTimedScheduleStaysOffGridAndShowsBothDates() = checkWeek(false, crossDay = true)

    private fun checkWeek(dark: Boolean, allDay: Boolean = false, crossDay: Boolean = false) = runBlocking {
        val f = HomeFormSaveFailureTest.Fixture()
        val monday = LocalDate.of(2026, 10, 5)
        val offGrid = allDay || crossDay
        val detailKind = when { allDay -> "all-day"; crossDay -> "cross-day"; else -> "course" }
        val events = listOf(
            ScheduleEvent(id = "english", title = "Signals and Systems", location = "文萃楼 F702\n良乡校区",
                startAt = monday.atTime(15, 15), endAt = monday.atTime(16, 50), sourceType = ScheduleSourceType.ICS_IMPORT),
            ScheduleEvent(id = "long",
                title = if (offGrid) "计算理论与算法分析设计：形式语言、自动机与可计算性理论专题研讨"
                    else "毛泽东思想和中国特色社会主义理论体系概论",
                location = "文萃楼 M134\n良乡校区备用教室",
                description = (1..15).joinToString("\n") { "第${it}次专题研讨：带上课堂笔记，核对案例分析与课程实践的准备内容。" },
                startAt = when {
                    allDay -> monday.plusDays(1).atStartOfDay()
                    crossDay -> monday.plusDays(1).atTime(8, 0)
                    else -> monday.plusDays(1).atTime(9, 55)
                },
                endAt = when {
                    allDay -> monday.plusDays(3).atStartOfDay()
                    crossDay -> monday.plusDays(2).atTime(9, 35)
                    else -> monday.plusDays(1).atTime(12, 20)
                },
                allDay = allDay, sourceType = if (offGrid) ScheduleSourceType.ICS_IMPORT else ScheduleSourceType.SCHOOL_IMPORT),
            ScheduleEvent(id = "short", title = "导入链路验证", location = "文萃楼 M134\n本地验证第二行",
                startAt = monday.plusDays(2).atTime(9, 55), endAt = monday.plusDays(2).atTime(11, 30), sourceType = ScheduleSourceType.ICS_IMPORT),
            ScheduleEvent(id = "single", title = "数据结构与算法", location = "综教 A101\n良乡校区备用教室",
                startAt = monday.plusDays(3).atTime(8, 0), endAt = monday.plusDays(3).atTime(8, 45)),
            ScheduleEvent(id = "matrix", title = "矩阵分析", location = "综教 A101\n良乡校区",
                startAt = monday.plusDays(4).atTime(13, 20), endAt = monday.plusDays(4).atTime(14, 55), sourceType = ScheduleSourceType.SCHOOL_IMPORT)
        )
        try {
            withContext(Dispatchers.Main) { f.create() }
            f.await { f.home.uiState.value.scheduleSections.size == 7 }
            events.forEach { f.schedules.addEvent(it) }
            withContext(Dispatchers.Main) { f.home.setSelectedWeek(monday) }
            f.await { f.home.uiState.value.scheduleSections.flatMap { it.items }.distinctBy { it.occurrenceId }.size == events.size }
            compose.setContent {
                CompositionLocalProvider(LocalAiEnabled provides false) {
                    CorrectionNotebookTheme(darkTheme = dark, dynamicColor = false) {
                        // Reserve the same bottom-bar and system-inset space as MainContainer.
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
            assertWeek(monday, events)
            capture(if (dark) "dark" else "light")

            val selected = events[1]
            openDetails(selected)
            capture("${if (dark) "dark" else "light"}-detail-$detailKind")
            compose.onNodeWithText(selected.title).assertIsDisplayed()
                .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            assertDetailTextComplete(selected.title)
            val time = when {
                allDay -> "10月06日 - 10月07日 · 全天"
                crossDay -> "10月06日 08:00 - 10月07日 09:35"
                else -> "10月06日 09:55 - 12:20"
            }
            compose.onNodeWithText(time).performScrollTo().assertIsDisplayed()
            assertDetailTextComplete(time)
            compose.onNodeWithText(selected.location).performScrollTo().assertIsDisplayed()
            assertDetailTextComplete(selected.location)
            assertDetailTextComplete(selected.description)
            val scroll = compose.onNode(hasScrollAction() and hasAnyAncestor(isDialog()))
            scroll.performSemanticsAction(SemanticsActions.ScrollBy) { assertTrue(it(0f, 100_000f)) }
            compose.waitForIdle()
            val notes = compose.onNodeWithText(selected.description).fetchSemanticsNode()
            val viewport = scroll.fetchSemanticsNode().boundsInRoot
            val notesLayout = assertDetailTextComplete(selected.description).single()
            val lastLine = notesLayout.lineCount - 1
            assertTrue(notes.positionInRoot.y + notesLayout.getLineTop(lastLine) >= viewport.top - 2f)
            assertTrue(notes.positionInRoot.y + notesLayout.getLineBottom(lastLine) <= viewport.bottom + 2f)
            listOf("关闭", "删除").forEach {
                compose.onNodeWithText(it).assertIsDisplayed().assertIsEnabled().assertHeightIsAtLeast(48.dp)
            }
            capture("${if (dark) "dark" else "light"}-detail-end-$detailKind")
            compose.onNodeWithText("关闭").performClick()
            compose.onNodeWithContentDescription("下一周").performClick()
            f.await { f.home.uiState.value.selectedDate == monday.plusWeeks(1) &&
                f.home.uiState.value.scheduleSections.flatMap { it.items }.isEmpty() }
            compose.onNodeWithText(gridText(selected)).assertDoesNotExist()
            compose.onNodeWithContentDescription("上一周").performClick()
            f.await { f.home.uiState.value.selectedDate == monday &&
                f.home.uiState.value.scheduleSections.flatMap { it.items }.distinctBy { it.occurrenceId }.size == events.size }
            assertWeek(monday, events)
            if (offGrid) {
                openDetails(selected)
                compose.onNodeWithText("删除").performClick()
                compose.onNodeWithText("取消").performClick()
                assertEquals(events.size, f.schedules.scheduleEvents.first().size)
                openDetails(selected)
                compose.onNodeWithText("删除").performClick()
                compose.onNodeWithText("删除").performClick()
                f.await { !f.home.uiState.value.isEditingSchedule &&
                    f.home.uiState.value.scheduleSections.flatMap { it.items }.none { it.eventId == selected.id } }
                assertEquals(events.filterNot { it.id == selected.id }.map { it.id }.toSet(),
                    f.schedules.scheduleEvents.first().map { it.id }.toSet())
                compose.onNodeWithContentDescription("课表操作").performClick()
                compose.onNodeWithText("课外日程（1）").assertDoesNotExist()
            }
        } finally {
            withContext(NonCancellable) {
                withContext(Dispatchers.Main) { f.store.clear() }
                f.scope.cancel()
                f.scope.coroutineContext[Job]?.join()
                f.files.forEach { it.delete() }
                f.database.close()
                f.knowledge.close()
                f.network.dispatcher.executorService.shutdownNow()
                f.network.connectionPool.evictAll()
            }
        }
    }

    private fun assertWeek(monday: LocalDate, events: List<ScheduleEvent>) {
        val format = DateTimeFormatter.ofPattern("MM/dd")
        repeat(7) { compose.onNodeWithText(monday.plusDays(it.toLong()).format(format)).assertIsDisplayed() }
        val times = listOf("08:00", "08:50", "09:55", "10:45", "11:35", "13:20", "14:10",
            "15:15", "16:05", "16:55", "18:30", "19:20", "20:10")
        times.forEachIndexed { index, time ->
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText("${index + 1}\n$time").assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(results)) }
            assertTrue(results.isNotEmpty())
            results.forEach { assertFalse("Period ${index + 1} and $time must both fit", it.hasVisualOverflow) }
        }
        events.filter { !it.allDay && it.startAt.toLocalDate() == it.endAt.toLocalDate() }.forEach { event ->
            val results = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(gridText(event)).assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(results)) }
            assertTrue(results.isNotEmpty())
            results.forEach { layout ->
                assertFalse("${event.title} must fit in its course block", layout.hasVisualOverflow)
                assertEquals(gridText(event).length, layout.getLineEnd(layout.lineCount - 1))
                assertEquals(TextAlign.Center, layout.layoutInput.style.textAlign)
            }
        }
    }

    private fun gridText(event: ScheduleEvent): String {
        return "${event.title}\n${event.location.lineSequence().first()}"
    }

    private fun openDetails(event: ScheduleEvent) {
        if (event.allDay || event.startAt.toLocalDate() != event.endAt.toLocalDate()) {
            compose.onNodeWithContentDescription("课表操作").performClick()
            compose.onNodeWithText("课外日程（1）").performClick()
            compose.onNodeWithText(event.title).performClick()
        } else {
            compose.onNodeWithText(gridText(event)).performClick()
        }
    }

    private fun assertDetailTextComplete(text: String): List<TextLayoutResult> {
        val results = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(results)) }
        assertTrue(results.isNotEmpty())
        results.forEach { layout ->
            assertFalse("Detail text must fit vertically: $text", layout.didOverflowHeight)
            assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1))
            assertEquals(InstrumentationRegistry.getInstrumentation().targetContext.resources.configuration.fontScale,
                layout.layoutInput.density.fontScale, 0.01f)
            repeat(layout.lineCount) { line ->
                assertFalse("Detail text must not use ellipses", layout.isLineEllipsized(line))
                assertTrue("Detail text must fit horizontally", layout.getLineLeft(line) >= -1f)
                assertTrue("Detail text must fit horizontally", layout.getLineRight(line) <= layout.size.width + 1f)
                assertTrue("Every detail line must fit vertically", layout.getLineBottom(line) <= layout.size.height + 1f)
            }
        }
        return results
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        Thread.sleep(300) // Let the native dialog-window transition finish before capturing it.
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "qa-course-week-$name.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }
}
