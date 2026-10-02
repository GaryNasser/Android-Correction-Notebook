package com.github.garynasser.correction_notebook.ui.screens.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.garynasser.correction_notebook.MainActivity
import com.github.garynasser.correction_notebook.data.model.home.ScheduleEvent
import com.github.garynasser.correction_notebook.data.model.home.ScheduleSourceType
import com.github.garynasser.correction_notebook.data.repository.ScheduleRepository
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.UUID

class ScheduleRecurrenceFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun weekNavigationShowsMovedCoursesOnlyOnTheirActualDateAndKeepsTheOtherCalendar() {
        val repository = ScheduleRepository(compose.activity.applicationContext)
        val token = UUID.randomUUID().toString().take(8)
        val monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val start = monday.atTime(8, 0)
        val base = ScheduleEvent(title = "QA-A-$token", location = "文萃楼 M134\n良乡校区",
            startAt = start, endAt = start.plusHours(1), sourceType = ScheduleSourceType.ICS_IMPORT,
            sourceCalendarId = "qa_calendar_a_$token", sourceEventUid = token, recurrenceRule = "FREQ=WEEKLY;COUNT=3")
        val otherCalendar = base.copy(id = UUID.randomUUID().toString(), title = "QA-B-$token",
            sourceCalendarId = "qa_calendar_b_$token")
        val movedStart = monday.plusDays(1).atTime(13, 20)
        val moved = base.copy(id = UUID.randomUUID().toString(), title = "QA-M-$token", recurrenceRule = null,
            recurrenceId = start.plusWeeks(2), startAt = movedStart, endAt = movedStart.plusMinutes(95))
        val fixtures = listOf(base, otherCalendar, moved)
        try {
            runBlocking { fixtures.forEach { repository.addEvent(it) } }
            waitFor(base.title, present = true)
            waitFor(otherCalendar.title, present = true)
            waitFor(moved.title, present = true)
            compose.onNodeWithText(moved.title, substring = true).assertIsDisplayed()

            compose.onNodeWithContentDescription("下一周").performClick()
            waitFor(moved.title, present = false)
            compose.onNodeWithContentDescription("下一周").performClick()
            waitFor(base.title, present = false)
            waitFor(otherCalendar.title, present = true)
            compose.onNodeWithText(otherCalendar.title, substring = true).assertIsDisplayed()
            compose.activityRule.scenario.recreate()
            waitFor(otherCalendar.title, present = true)
            waitFor(base.title, present = false)

            compose.onNodeWithContentDescription("上一周").performClick()
            waitFor(base.title, present = true)
            compose.onNodeWithContentDescription("上一周").performClick()
            waitFor(moved.title, present = true)
            compose.onNodeWithText(moved.title, substring = true).assertIsDisplayed()
        } finally {
            runBlocking { fixtures.forEach { repository.deleteEvent(it.id) } }
        }
    }

    private fun waitFor(title: String, present: Boolean) {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(title, substring = true).fetchSemanticsNodes().isNotEmpty() == present
        }
    }
}
