package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.model.home.ScheduleEvent
import com.github.garynasser.correction_notebook.data.repository.buildScheduleOccurrences
import com.github.garynasser.correction_notebook.data.repository.parseIcsEvents
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.TimeZone

class ScheduleOccurrenceResolverTest {
    private val start = LocalDateTime.of(2000, 1, 3, 8, 0)
    private fun event(rule: String) = ScheduleEvent(title = "Course", startAt = start, endAt = start.plusHours(1), recurrenceRule = rule)

    @Test
    fun dailyIntervalsAndUntilIncludeTheExactLastInstance() {
        val course = event("FREQ=DAILY;INTERVAL=2;UNTIL=20000107T080000")
        assertEquals(listOf(start, start.plusDays(2), start.plusDays(4)),
            resolve(course, start.toLocalDate(), LocalDate.of(2000, 1, 9)).map { it.startAt })
    }

    @Test
    fun allDayUntilIncludesItsDateAndKeepsAnExclusiveEnd() {
        val course = event("FREQ=DAILY;UNTIL=20000105").copy(allDay = true,
            startAt = start.toLocalDate().atStartOfDay(), endAt = start.toLocalDate().plusDays(1).atStartOfDay())
        val occurrences = resolve(course, LocalDate.of(2000, 1, 3), LocalDate.of(2000, 1, 9))
        assertEquals(listOf(3, 4, 5), occurrences.map { it.startAt.dayOfMonth })
        assertTrue(occurrences.all { it.endAt == it.startAt.plusDays(1) && it.allDay })
    }

    @Test
    fun weeklyIntervalsRespectTheExplicitWeekStart() {
        val first = LocalDateTime.of(1997, 8, 5, 9, 0)
        val course = event("FREQ=WEEKLY;INTERVAL=2;COUNT=4;BYDAY=TU,SU;WKST=MO")
            .copy(startAt = first, endAt = first.plusHours(1))
        val from = LocalDate.of(1997, 8, 1)
        val to = LocalDate.of(1997, 8, 31)
        assertEquals(listOf(5, 10, 19, 24), resolve(course, from, to).map { it.startAt.dayOfMonth })
        assertEquals(listOf(5, 17, 19, 31), resolve(course.copy(recurrenceRule =
            "FREQ=WEEKLY;INTERVAL=2;COUNT=4;BYDAY=TU,SU;WKST=SU"), from, to).map { it.startAt.dayOfMonth })
    }

    @Test
    fun monthlyOrdinalWeekdayAndSetPositionAreSupported() {
        val first = LocalDateTime.of(2000, 1, 31, 8, 0)
        val course = event("FREQ=MONTHLY;BYDAY=-1MO;COUNT=3").copy(startAt = first, endAt = first.plusHours(1))
        assertEquals(listOf(31, 28, 27), resolve(course, first.toLocalDate(), LocalDate.of(2000, 3, 31)).map { it.startAt.dayOfMonth })
        assertEquals(listOf(31, 29, 31), resolve(course.copy(recurrenceRule =
            "FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-1;COUNT=3"), first.toLocalDate(),
            LocalDate.of(2000, 3, 31)).map { it.startAt.dayOfMonth })
    }

    @Test
    fun invalidRulesRejectTheImportWithAnIndexedError() {
        listOf("FREQ=UNKNOWN", "FREQ=DAILY;INTERVAL=0", "FREQ=DAILY;COUNT=0",
            "FREQ=DAILY;COUNT=3;UNTIL=20000110T080000").forEach { rule ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                parseIcsEvents(listOf("BEGIN:VEVENT", "DTSTART:20000103T080000", "RRULE:$rule", "END:VEVENT"), "test")
            }
            assertTrue(error.message.orEmpty().contains("第 1 个日程"))
        }
    }

    @Test
    fun malformedLegacyRuleDoesNotHideOtherEvents() {
        val damaged = event("FREQ=UNKNOWN")
        val regular = damaged.copy(id = "regular", recurrenceRule = null)
        assertEquals(setOf(damaged.id, regular.id), buildScheduleOccurrences(listOf(damaged, regular),
            start.toLocalDate(), start.toLocalDate()).map { it.eventId }.toSet())
    }

    @Test(timeout = 2000)
    fun infiniteRulesFastForwardToTheRequestedWeekAndFiniteCountDoesNotRestart() {
        val course = event("FREQ=DAILY")
        val from = LocalDate.of(2060, 1, 5)
        assertEquals(7, resolve(course, from, from.plusDays(6)).size)
        assertEquals(emptyList<Any>(), resolve(course.copy(recurrenceRule = "FREQ=DAILY;COUNT=3"), from, from.plusDays(6)))
    }

    @Test
    fun zonedWeeklyCoursesKeepTheirSourceWallTimeAcrossDaylightSaving() = withZone("UTC") {
        val course = parseIcsEvents(listOf("BEGIN:VEVENT", "DTSTART;TZID=America/New_York:20260301T090000",
            "DTEND;TZID=America/New_York:20260301T100000", "RRULE:FREQ=WEEKLY;COUNT=3", "END:VEVENT"), "test").single()
        val occurrences = resolve(course, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 15))
        assertEquals(listOf(14, 13, 13), occurrences.map { it.startAt.hour })
        assertTrue(occurrences.all { it.endAt == it.startAt.plusHours(1) })
    }

    @Test
    fun utcRecurrencesKeepTheirAbsoluteTimeAcrossLocalDaylightSaving() = withZone("America/New_York") {
        val course = parseIcsEvents(listOf("BEGIN:VEVENT", "DTSTART:20260301T140000Z", "DTEND:20260301T150000Z",
            "RRULE:FREQ=WEEKLY;COUNT=3", "END:VEVENT"), "test").single()
        assertEquals("UTC", course.timezoneId)
        assertEquals(listOf(9, 10, 10), resolve(course, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 15)).map { it.startAt.hour })
    }

    @Test
    fun floatingCoursesRemainAtTheirLocalWallTime() = withZone("America/New_York") {
        val first = LocalDateTime.of(2026, 3, 1, 9, 0)
        val course = event("FREQ=WEEKLY;COUNT=3").copy(startAt = first, endAt = first.plusHours(1))
        assertEquals(listOf(9, 9, 9), resolve(course, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 15)).map { it.startAt.hour })
    }

    private fun resolve(course: ScheduleEvent, from: LocalDate, to: LocalDate) = buildScheduleOccurrences(listOf(course), from, to)

    private fun withZone(id: String, block: () -> Unit) {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(id))
            block()
        } finally {
            TimeZone.setDefault(original)
        }
    }
}
