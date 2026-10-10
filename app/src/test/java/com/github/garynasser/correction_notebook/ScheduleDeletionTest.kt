package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.model.home.*
import com.github.garynasser.correction_notebook.data.repository.applyIcsImport
import com.github.garynasser.correction_notebook.data.repository.buildScheduleOccurrences
import com.github.garynasser.correction_notebook.data.repository.deleteScheduleOccurrence
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test

class ScheduleDeletionTest {
    private val start = LocalDateTime.of(2026, 10, 5, 8, 0)
    private val course = ScheduleEvent(id = "course", title = "计算理论", startAt = start, endAt = start.plusHours(1),
        sourceType = ScheduleSourceType.ICS_IMPORT, sourceCalendarId = "calendar-a", sourceEventUid = "course-uid",
        recurrenceRule = "FREQ=WEEKLY;COUNT=3", lastImportedAt = 100, updatedAt = 100)
    private val moved = course.copy(id = "moved", recurrenceRule = null, recurrenceId = start.plusWeeks(2),
        startAt = start.plusDays(1), endAt = start.plusDays(1).plusHours(1))

    @Test fun oneOccurrenceIsExcludedWithoutExtendingTheRepeatCount() {
        val events = listOf(course)
        val selected = resolve(events).first()
        assertTrue(selected.isRecurring)
        val result = deleteScheduleOccurrence(events, selected, false, updatedAt = 200)
        assertEquals(listOf(start.plusWeeks(1), start.plusWeeks(2)), resolve(result).map { it.startAt })
        assertEquals(course.copy(exDateList = listOf(start), updatedAt = 200), result.single())
    }

    @Test fun replayingASingleDeletionDoesNotDuplicateTheExclusionOrChangeOtherFields() {
        val events = listOf(course.copy(description = "备注", exDateList = listOf(start.plusWeeks(1))))
        val selected = resolve(events).first()
        val once = deleteScheduleOccurrence(events, selected, false, updatedAt = 200)
        assertEquals(once, deleteScheduleOccurrence(once, selected, false, updatedAt = 300))
        assertEquals(listOf(start.plusWeeks(1), start), once.single().exDateList)
    }

    @Test fun deletingAMovedInstanceDoesNotRestoreItsOriginalDate() {
        val events = listOf(course, moved)
        val selected = resolve(events).single { it.eventId == moved.id }
        assertTrue(selected.isRecurring)
        val result = deleteScheduleOccurrence(events, selected, false, updatedAt = 200)
        assertEquals(listOf(start, start.plusWeeks(1)), resolve(result).map { it.startAt })
        assertEquals(listOf(start.plusWeeks(2)), result.single().exDateList)
    }

    @Test fun deletingAnEntireSeriesAlsoRemovesItsMovedInstances() {
        val events = listOf(course, moved)
        for (selected in resolve(events).filter { it.startAt <= moved.startAt }) {
            assertTrue(deleteScheduleOccurrence(events, selected, true).isEmpty())
        }
    }

    @Test fun matchingUidsInOtherCalendarsOrSourcesAreNeverDeletedOrEdited() {
        val others = listOf(course.copy(id = "other-calendar", sourceCalendarId = "calendar-b"),
            course.copy(id = "school", sourceType = ScheduleSourceType.SCHOOL_IMPORT),
            course.copy(id = "other-uid", sourceEventUid = "other"))
        val events = listOf(course, moved) + others
        val selected = resolve(events).single { it.eventId == moved.id }
        assertEquals(others, deleteScheduleOccurrence(events, selected, true))
        assertEquals(others, deleteScheduleOccurrence(events, selected, false).filter { it.id in others.map { event -> event.id } })
    }

    @Test fun missingUidsDoNotGroupIndependentRecordsTogether() {
        val first = course.copy(sourceEventUid = " ")
        val other = first.copy(id = "other", sourceEventUid = null)
        val events = listOf(first, other)
        val selected = resolve(events).first { it.eventId == first.id }
        assertEquals(listOf(other), deleteScheduleOccurrence(events, selected, true))
        assertEquals(other, deleteScheduleOccurrence(events, selected, false).last())
    }

    @Test fun anOrphanedMovedInstanceCanBeDeletedWithoutRemovingAnUnrelatedCalendar() {
        val other = course.copy(id = "other", sourceCalendarId = "calendar-b")
        val events = listOf(moved, other)
        val selected = resolve(events).single { it.eventId == moved.id }
        assertEquals(listOf(other), deleteScheduleOccurrence(events, selected, false))
    }

    @Test fun aNonRecurringEventHasNoSeriesScopeAndOnlyItsRecordIsDeleted() {
        val regular = course.copy(id = "regular", recurrenceRule = null)
        val events = listOf(regular, course)
        val selected = resolve(events).single { it.eventId == regular.id }
        assertFalse(selected.isRecurring)
        assertEquals(listOf(course), deleteScheduleOccurrence(events, selected, false))
        assertEquals(listOf(course), deleteScheduleOccurrence(events, selected, true))
    }

    @Test fun aMissingOwnerMakesARepeatedDeleteANoOp() {
        val events = listOf(course)
        val selected = resolve(events).first()
        val remaining = listOf(course.copy(id = "other"))
        assertEquals(remaining, deleteScheduleOccurrence(remaining, selected, true))
    }

    @Test fun mergingTheOriginalImportKeepsALocallyDeletedOccurrence() {
        val events = listOf(course)
        val selected = resolve(events).first()
        val local = deleteScheduleOccurrence(events, selected, false, updatedAt = 200)
        val incoming = course.copy(id = "reimport", lastImportedAt = 300, updatedAt = 300)
        val preview = IcsImportPreview("course.ics", "calendar-a", incomingEvents = listOf(incoming),
            added = emptyList(), updated = emptyList(), conflicts = emptyList(), deleted = emptyList())
        assertEquals(local, applyIcsImport(local, preview, ImportDecision.MERGE))
        assertEquals(listOf(incoming), applyIcsImport(local, preview, ImportDecision.OVERWRITE))
    }

    private fun resolve(events: List<ScheduleEvent>) = buildScheduleOccurrences(events, start.toLocalDate(), start.plusWeeks(3).toLocalDate())
}
