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

    @Test fun mergingTheOriginalImportDoesNotRestoreALocallyDeletedMovedInstance() {
        val events = listOf(course, moved)
        val selected = resolve(events).single { it.eventId == moved.id }
        val local = deleteScheduleOccurrence(events, selected, false, updatedAt = 200)
        val incoming = events.map { it.copy(id = "reimport-${it.id}", lastImportedAt = 300, updatedAt = 300) }
        val preview = IcsImportPreview("course.ics", "calendar-a", incomingEvents = incoming,
            added = emptyList(), updated = emptyList(), conflicts = emptyList(), deleted = emptyList())
        assertEquals(local, applyIcsImport(local, preview, ImportDecision.MERGE))
        assertEquals(listOf(start, start.plusWeeks(1)), resolve(applyIcsImport(local, preview, ImportDecision.MERGE)).map { it.startAt })
        assertEquals(incoming, applyIcsImport(local, preview, ImportDecision.OVERWRITE))
    }

    @Test fun deletingAMovedInstanceWithAnImportedExclusionStillMarksTheMasterAsLocallyEdited() {
        val events = listOf(course.copy(exDateList = listOf(moved.recurrenceId!!)), moved)
        val selected = resolve(events).single { it.eventId == moved.id }
        val local = deleteScheduleOccurrence(events, selected, false, updatedAt = 200)
        assertEquals(events.first().copy(updatedAt = 200), local.single())
        assertEquals(local, applyIcsImport(local, preview(events), ImportDecision.MERGE))
        assertEquals(local, deleteScheduleOccurrence(local, selected, false, updatedAt = 300))
    }

    @Test fun anExistingMovedInstanceIsNotHiddenByItsLocallyEditedMaster() {
        val master = course.copy(exDateList = listOf(moved.recurrenceId!!), updatedAt = 200)
        val incomingMoved = moved.copy(id = "incoming-moved", title = "更新后的调课", startAt = moved.startAt.plusHours(1))
        val merged = applyIcsImport(listOf(master, moved), preview(listOf(course, incomingMoved)), ImportDecision.MERGE)
        assertEquals(listOf(master, incomingMoved), merged)
        assertTrue(resolve(merged).any { it.eventId == incomingMoved.id })
    }

    @Test fun importedExclusionsDoNotBlockNewDetachedInstances() {
        val imported = course.copy(exDateList = listOf(moved.recurrenceId!!))
        val merged = applyIcsImport(listOf(imported), preview(listOf(imported, moved)), ImportDecision.MERGE)
        assertEquals(listOf(imported, moved), merged)
        assertTrue(resolve(merged).any { it.eventId == moved.id })
    }

    @Test fun localExclusionsOnlyProtectTheirMatchingOriginalDate() {
        val master = course.copy(exDateList = listOf(start.plusWeeks(1)), updatedAt = 200)
        val merged = applyIcsImport(listOf(master), preview(listOf(course, moved)), ImportDecision.MERGE)
        assertEquals(listOf(master, moved), merged)
        assertTrue(resolve(merged).any { it.eventId == moved.id })
    }

    @Test fun anotherCalendarOrSourceCannotBlockThisCalendarsDetachedInstance() {
        val others = listOf(course.copy(id = "other-calendar", sourceCalendarId = "calendar-b",
            exDateList = listOf(moved.recurrenceId!!), updatedAt = 200),
            course.copy(id = "school", sourceType = ScheduleSourceType.SCHOOL_IMPORT,
                exDateList = listOf(moved.recurrenceId!!), updatedAt = 200))
        assertEquals(others + course + moved, applyIcsImport(others, preview(listOf(course, moved)), ImportDecision.MERGE))
    }

    @Test fun aMatchedLegacyCalendarStillProtectsItsLocallyDeletedMovedInstance() {
        val old = course.copy(sourceCalendarId = "legacy", exDateList = listOf(moved.recurrenceId!!), updatedAt = 200)
        val incoming = preview(listOf(course, moved)).copy(replacedCalendarIds = setOf("legacy"))
        val merged = applyIcsImport(listOf(old), incoming, ImportDecision.MERGE)
        assertEquals(listOf(old.copy(sourceCalendarId = incoming.sourceCalendarId)), merged)
        assertEquals(listOf(start, start.plusWeeks(1)), resolve(merged).map { it.startAt })
        assertEquals(merged, applyIcsImport(merged, incoming, ImportDecision.MERGE))
    }

    @Test fun anUnidentifiedMasterCannotExcludeAnUnidentifiedDetachedInstance() {
        val master = course.copy(sourceEventUid = null, exDateList = listOf(moved.recurrenceId!!), updatedAt = 200)
        val unidentifiedMoved = moved.copy(sourceEventUid = null)
        assertEquals(listOf(master, unidentifiedMoved), applyIcsImport(listOf(master),
            preview(listOf(master, unidentifiedMoved)), ImportDecision.MERGE))
    }

    private fun preview(events: List<ScheduleEvent>) = IcsImportPreview("course.ics", "calendar-a", incomingEvents = events,
        added = emptyList(), updated = emptyList(), conflicts = emptyList(), deleted = emptyList())

    private fun resolve(events: List<ScheduleEvent>) = buildScheduleOccurrences(events, start.toLocalDate(), start.plusWeeks(3).toLocalDate())
}
