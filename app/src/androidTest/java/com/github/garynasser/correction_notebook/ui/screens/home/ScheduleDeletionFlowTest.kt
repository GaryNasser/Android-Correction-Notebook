package com.github.garynasser.correction_notebook.ui.screens.home

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.model.home.ScheduleEvent
import com.github.garynasser.correction_notebook.data.model.home.ScheduleSourceType
import com.github.garynasser.correction_notebook.ui.components.LocalAiEnabled
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ScheduleDeletionFlowTest {
    @get:Rule val compose = createComposeRule()
    private val monday = LocalDate.of(2026, 10, 5)
    private val course = ScheduleEvent(
        id = "repeating-course", title = "计算理论与算法分析设计", location = "文萃楼 M134",
        startAt = monday.atTime(8, 0), endAt = monday.atTime(9, 35),
        recurrenceRule = "FREQ=WEEKLY;COUNT=3", sourceType = ScheduleSourceType.ICS_IMPORT,
        sourceCalendarId = "calendar-a", sourceEventUid = "course-uid"
    )

    @Test fun defaultDeletionRemovesOnlyThisWeekAndPreservesTheOtherWeeks() = withFixture { f ->
        openDelete()
        compose.onNodeWithText("仅这一次").performScrollTo().assertIsSelected()
        compose.onNodeWithText("整组日程").performScrollTo().performClick().assertIsSelected()
        compose.onNodeWithText("取消").performClick()
        assertEquals(course, f.schedules.scheduleEvents.first().single())
        openDelete()
        compose.onNodeWithText("仅这一次").performScrollTo().assertIsSelected()
        compose.onNodeWithText("整组日程").assertIsNotSelected()
        listOf("删除", "取消").forEach { compose.onNodeWithText(it).assertHeightIsAtLeast(48.dp).assertIsDisplayed() }
        capture("single")
        compose.onNodeWithText("删除").performClick()
        f.await { !f.home.uiState.value.isEditingSchedule && f.home.uiState.value.scheduleSections.flatMap { it.items }.isEmpty() }
        compose.onNodeWithContentDescription("下一周").performClick()
        f.await { f.home.uiState.value.selectedDate == monday.plusWeeks(1) &&
            f.home.uiState.value.scheduleSections.flatMap { it.items }.any { it.eventId == course.id } }
        compose.onNodeWithText(course.title, substring = true).assertIsDisplayed()
        assertEquals(listOf(course.startAt), f.schedules.scheduleEvents.first().single().exDateList)
    }

    @Test fun entireSeriesDeletionRemovesMovedDatesButKeepsAnotherCalendar() {
        val moved = course.copy(id = "moved", title = "调课后计算理论", recurrenceRule = null,
            recurrenceId = course.startAt.plusWeeks(2), startAt = course.startAt.plusDays(1), endAt = course.endAt.plusDays(1))
        val other = course.copy(id = "other", title = "另一份日历的课程", sourceCalendarId = "calendar-b")
        withFixture(listOf(course, moved, other)) { f ->
            openDelete()
            compose.onNodeWithText("整组日程").performScrollTo().performClick()
            compose.onNodeWithText("此重复日程的所有日期和调课记录都会删除，无法撤销。").performScrollTo().assertIsDisplayed()
            capture("series")
            compose.onNodeWithText("删除").performClick()
            f.await { !f.home.uiState.value.isEditingSchedule &&
                f.home.uiState.value.scheduleSections.flatMap { it.items }.all { it.eventId == other.id } }
            assertEquals(listOf(other), f.schedules.scheduleEvents.first())
        }
    }

    @Test fun deletingAMovedCourseDoesNotBringBackItsOriginalWeek() {
        val moved = course.copy(id = "moved", title = "调课后计算理论", recurrenceRule = null,
            recurrenceId = course.startAt.plusWeeks(2), startAt = course.startAt.plusDays(1), endAt = course.endAt.plusDays(1))
        withFixture(listOf(course, moved)) { f ->
            compose.onNodeWithText(moved.title, substring = true).performClick()
            compose.onNodeWithText("删除").performClick()
            compose.onNodeWithText("仅这一次").performScrollTo().assertIsSelected()
            compose.onNodeWithText("删除").performClick()
            f.await { !f.home.uiState.value.isEditingSchedule &&
                f.home.uiState.value.scheduleSections.flatMap { it.items }.none { it.eventId == moved.id } }
            val persisted = f.schedules.scheduleEvents.first().single()
            assertEquals(course.copy(exDateList = listOf(moved.recurrenceId!!), updatedAt = persisted.updatedAt), persisted)
            repeat(2) { compose.onNodeWithContentDescription("下一周").performClick() }
            f.await { f.home.uiState.value.selectedDate == monday.plusWeeks(2) &&
                f.home.uiState.value.scheduleSections.flatMap { it.items }.isEmpty() }
            compose.onNodeWithText(course.title, substring = true).assertDoesNotExist()
        }
    }

    @Test fun aFailedDeletionKeepsTheSeriesAndRetryDoesNotSubmitTwice() = withFixture { f ->
        openDelete()
        f.scheduleWrites.fail = true
        compose.onNodeWithText("删除").performClick()
        f.await { f.scheduleWrites.attempts.get() == 2 && !f.home.uiState.value.isEditingSchedule }
        assertEquals(listOf(course), f.schedules.scheduleEvents.first())
        openDelete()
        f.scheduleWrites.fail = false
        val release = CompletableDeferred<Unit>().also { f.scheduleWrites.gate = it }
        compose.onNodeWithText("删除").performClick()
        f.await { f.scheduleWrites.attempts.get() == 3 && f.home.uiState.value.isEditingSchedule }
        withContext(Dispatchers.Main) {
            f.home.deleteSchedule(f.home.uiState.value.scheduleSections.flatMap { it.items }.first(), deleteSeries = true)
        }
        assertEquals(3, f.scheduleWrites.attempts.get())
        release.complete(Unit)
        f.await { !f.home.uiState.value.isEditingSchedule && f.home.uiState.value.scheduleSections.flatMap { it.items }.isEmpty() }
        val persisted = f.schedules.scheduleEvents.first().single()
        assertEquals(listOf(course.startAt), persisted.exDateList)
        assertEquals(3, f.scheduleWrites.attempts.get())
    }

    private fun openDelete() {
        compose.onNodeWithText(course.title, substring = true).performClick()
        compose.onNodeWithText("删除").performClick()
    }

    private fun withFixture(
        events: List<ScheduleEvent> = listOf(course),
        test: suspend (HomeFormSaveFailureTest.Fixture) -> Unit
    ) = runBlocking {
        val f = HomeFormSaveFailureTest.Fixture()
        try {
            withContext(Dispatchers.Main) { f.create(); f.home.setSelectedWeek(monday) }
            events.forEach { f.schedules.addEvent(it) }
            f.await { f.home.uiState.value.selectedDate == monday &&
                f.home.uiState.value.scheduleSections.flatMap { it.items }.any { it.eventId == course.id } }
            compose.setContent {
                CompositionLocalProvider(LocalAiEnabled provides false) {
                    CorrectionNotebookTheme(darkTheme = InstrumentationRegistry.getArguments().getString("qaDark") == "true",
                        dynamicColor = false) { HomeScreen(f.home, f.statistics) }
                }
            }
            test(f)
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

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        Thread.sleep(300)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "qa-schedule-delete-$name.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }
}
