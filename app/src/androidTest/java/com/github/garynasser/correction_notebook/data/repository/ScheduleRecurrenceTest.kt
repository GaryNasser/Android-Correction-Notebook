package com.github.garynasser.correction_notebook.data.repository

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.garynasser.correction_notebook.data.model.home.ScheduleEvent
import com.github.garynasser.correction_notebook.data.model.home.ScheduleOccurrence
import com.github.garynasser.correction_notebook.data.model.home.ScheduleRange
import com.github.garynasser.correction_notebook.data.model.home.ScheduleSourceType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ScheduleRecurrenceTest {
    private val start = LocalDateTime.of(2000, 1, 3, 8, 0)

    @Test
    fun overridesOnlyReplaceTheMatchingCalendarAndSource() {
        val first = course("Calendar A")
        val second = course("Calendar B").copy(sourceEventUid = first.sourceEventUid)
        val school = course("School").copy(sourceType = ScheduleSourceType.SCHOOL_IMPORT,
            sourceCalendarId = first.sourceCalendarId, sourceEventUid = first.sourceEventUid)
        val override = first.copy(id = UUID.randomUUID().toString(), title = "A rescheduled",
            recurrenceId = start, recurrenceRule = null, startAt = start.plusHours(2), endAt = start.plusHours(3))
        withEvents(listOf(first, second, school, override)) { repository, ids ->
            val occurrences = week(repository, ids, start.toLocalDate())
            assertEquals(setOf(override.id, second.id, school.id), occurrences.map { it.eventId }.toSet())
            assertEquals(3, occurrences.size)
        }
    }

    @Test
    fun futureOccurrenceMovedEarlierAppearsOnceInItsActualWeek() {
        val base = course("Base")
        val original = start.plusWeeks(2)
        val moved = base.copy(id = UUID.randomUUID().toString(), title = "Moved earlier", recurrenceRule = null,
            recurrenceId = original, startAt = start.plusDays(1), endAt = start.plusDays(1).plusHours(1))
        withEvents(listOf(base, moved)) { repository, ids ->
            assertEquals(listOf(base.id, moved.id), week(repository, ids, start.toLocalDate()).map { it.eventId })
            assertEquals(emptyList<ScheduleOccurrence>(), week(repository, ids, original.toLocalDate()))
        }
    }

    @Test
    fun detachedOverrideStillAppearsWithoutItsMasterOrWithAnExcludedOriginalDate() {
        val base = course("Base").copy(exDateList = listOf(start))
        val moved = base.copy(id = UUID.randomUUID().toString(), recurrenceRule = null, recurrenceId = start,
            startAt = start.plusDays(1), endAt = start.plusDays(1).plusHours(1))
        withEvents(listOf(base, moved)) { repository, ids ->
            assertEquals(listOf(moved.id), week(repository, ids, start.toLocalDate()).map { it.eventId })
        }
        withEvents(listOf(moved)) { repository, ids ->
            assertEquals(listOf(moved.id), week(repository, ids, start.toLocalDate()).map { it.eventId })
        }
    }

    @Test
    fun weeklyCountIsChronologicalRegardlessOfByDayOrder() {
        val base = course("Weekly count").copy(recurrenceRule = "FREQ=WEEKLY;BYDAY=FR,MO;COUNT=3")
        withEvents(listOf(base)) { repository, ids ->
            assertEquals(listOf(start.plusWeeks(1)), week(repository, ids, start.plusWeeks(1).toLocalDate()).map { it.startAt })
        }
    }

    @Test
    fun monthlyThirtyFirstSkipsShortMonthsWithoutDriftingOrUsingUpCount() {
        val first = LocalDateTime.of(2000, 1, 31, 8, 0)
        val base = course("Month end").copy(startAt = first, endAt = first.plusHours(1), recurrenceRule = "FREQ=MONTHLY;COUNT=3")
        withEvents(listOf(base)) { repository, ids ->
            assertEquals(listOf(first.plusMonths(2)), week(repository, ids, LocalDate.of(2000, 3, 27)).map { it.startAt })
            assertEquals(listOf(first.plusMonths(4)), week(repository, ids, LocalDate.of(2000, 5, 29)).map { it.startAt })
        }
    }

    @Test
    fun yearlyLeapDaySkipsNonLeapYears() {
        val first = LocalDateTime.of(2000, 2, 29, 8, 0)
        val base = course("Leap day").copy(startAt = first, endAt = first.plusHours(1), recurrenceRule = "FREQ=YEARLY;COUNT=2")
        withEvents(listOf(base)) { repository, ids ->
            assertEquals(listOf(LocalDateTime.of(2004, 2, 29, 8, 0)), week(repository, ids, LocalDate.of(2004, 2, 23)).map { it.startAt })
        }
    }

    @Test
    fun excludedDatesDoNotExtendCountAndOvernightCoursesOverlapTheNextWeek() {
        val base = course("Exclusion").copy(exDateList = listOf(start.plusWeeks(1)))
        val sunday = LocalDateTime.of(2000, 1, 2, 23, 0)
        val overnight = course("Overnight").copy(startAt = sunday, endAt = sunday.plusHours(2),
            recurrenceRule = "FREQ=WEEKLY;COUNT=3")
        withEvents(listOf(base, overnight)) { repository, ids ->
            assertEquals(listOf(sunday.plusWeeks(1), sunday.plusWeeks(2)),
                week(repository, ids, LocalDate.of(2000, 1, 10)).map { it.startAt })
            assertEquals(emptyList<ScheduleOccurrence>(), week(repository, ids, LocalDate.of(2000, 1, 24)))
        }
    }

    private fun course(title: String) = ScheduleEvent(title = title, startAt = start, endAt = start.plusHours(1),
        sourceType = ScheduleSourceType.ICS_IMPORT, sourceCalendarId = "qa_${UUID.randomUUID()}",
        sourceEventUid = "shared-course", recurrenceRule = "FREQ=WEEKLY;COUNT=3")

    private fun withEvents(events: List<ScheduleEvent>, block: suspend (ScheduleRepository, Set<String>) -> Unit) = runBlocking {
        val repository = ScheduleRepository(context = ApplicationProvider.getApplicationContext())
        try {
            events.forEach { repository.addEvent(it) }
            block(repository, events.map { it.id }.toSet())
        } finally {
            events.forEach { repository.deleteEvent(it.id) }
        }
    }

    private suspend fun week(repository: ScheduleRepository, ids: Set<String>, date: LocalDate) =
        repository.getEventsForRange(ScheduleRange.WEEK, date).flatMap { it.items }
            .filter { it.eventId in ids }.distinctBy { it.occurrenceId }.sortedBy { it.startAt }
}
