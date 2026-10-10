package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.model.home.IcsImportPreview
import com.github.garynasser.correction_notebook.data.model.home.ImportDecision
import com.github.garynasser.correction_notebook.data.model.home.ScheduleEvent
import com.github.garynasser.correction_notebook.data.model.home.ScheduleSourceType
import com.github.garynasser.correction_notebook.data.repository.applyIcsImport
import com.github.garynasser.correction_notebook.data.repository.buildScheduleOccurrences
import com.github.garynasser.correction_notebook.data.repository.deleteScheduleOccurrence
import com.github.garynasser.correction_notebook.data.repository.parseIcsEvents
import com.github.garynasser.correction_notebook.data.repository.resolveIcsReplacedCalendarIds
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class IcsSourceMigrationTest {
    private val calendar = "ics_v3_current"
    private val previous = "ics_v2_previous"
    private val legacy = "ics_0123456789abcdef0123456789abcdef"
    private val monday = LocalDate.of(2026, 10, 5)
    private val imported = parseIcsEvents(listOf(
        "BEGIN:VEVENT", "UID:course", "DTSTART:20261005T080000", "DTEND:20261005T093500",
        "RRULE:FREQ=WEEKLY;COUNT=3", "SUMMARY:Imported course", "LOCATION:Imported room", "END:VEVENT",
        "BEGIN:VEVENT", "UID:course", "RECURRENCE-ID:20261019T080000",
        "DTSTART:20261006T132000", "DTEND:20261006T145500", "SUMMARY:Moved course", "END:VEVENT"
    ), calendar)

    @Test fun editedMastersKeepTheirContentButRecognizeNewOverridesAfterMigration() {
        listOf(previous, legacy).forEach { oldCalendar ->
            val local = old(imported[0], oldCalendar).copy(title = "Local course", location = "Local room",
                description = "Local notes", exDateList = listOf(monday.plusWeeks(1).atTime(8, 0)), updatedAt = 200)
            val unrelated = listOf(
                local.copy(id = "other-calendar", sourceCalendarId = "ics_v3_other"),
                local.copy(id = "school", sourceType = ScheduleSourceType.SCHOOL_IMPORT),
                local.copy(id = "manual", sourceType = ScheduleSourceType.MANUAL)
            )
            val current = listOf(local) + unrelated
            val preview = preview(current, imported)
            val saved = applyIcsImport(current, preview, ImportDecision.MERGE)
            assertEquals(emptyList<Any>(), occurrences(saved.filterNot { it in unrelated }, monday.plusWeeks(2)))
            assertEquals(local.copy(sourceCalendarId = calendar), saved.single { it.id == local.id })
            assertEquals(unrelated, saved.filter { it.id in unrelated.map(ScheduleEvent::id) })
            assertEquals(listOf(local.id, imported[1].id), occurrences(saved.filterNot { it in unrelated }, monday).map { it.eventId })
            assertEquals(saved, applyIcsImport(saved, preview, ImportDecision.MERGE))
        }
    }

    @Test fun editedOverridesStillSuppressTheNewMasterAtTheirOriginalDate() {
        listOf(previous, legacy).forEach { oldCalendar ->
            val master = old(imported[0], oldCalendar)
            val moved = old(imported[1], oldCalendar).copy(title = "Local moved course", location = "Local room", updatedAt = 200)
            val current = listOf(master, moved)
            val saved = applyIcsImport(current, preview(current, imported), ImportDecision.MERGE)
            assertEquals(emptyList<Any>(), occurrences(saved, monday.plusWeeks(2)))
            assertEquals(moved.copy(sourceCalendarId = calendar), saved.single { it.id == moved.id })
            assertEquals(listOf(imported[0].id, moved.id), occurrences(saved, monday).map { it.eventId })
        }
    }

    @Test fun mergeMigratesRetainedEventsEvenWhenTheyAreMissingFromTheIncomingFile() {
        listOf(previous, legacy).forEach { oldCalendar ->
            val current = imported.map { old(it, oldCalendar) }
            listOf(listOf(imported[0]), listOf(imported[1])).forEach { incoming ->
                val saved = applyIcsImport(current, preview(current, incoming), ImportDecision.MERGE)
                assertEquals(emptyList<Any>(), occurrences(saved, monday.plusWeeks(2)))
                assertEquals(setOf(calendar), saved.map { it.sourceCalendarId }.toSet())
                val retained = current.single { it.recurrenceId != incoming.single().recurrenceId }
                assertEquals(retained.copy(sourceCalendarId = calendar), saved.single { it.id == retained.id })
            }
        }
    }

    @Test fun singleAndSeriesDeletionStayConnectedWithoutChangingAnotherCalendar() {
        val master = old(imported[0], previous).copy(updatedAt = 200)
        val moved = old(imported[1], previous)
        val other = moved.copy(id = "other-calendar", sourceCalendarId = "ics_v3_other")
        val current = listOf(master, moved, other)
        val saved = applyIcsImport(current, preview(current, listOf(imported[0])), ImportDecision.MERGE)
        val occurrence = occurrences(saved, monday).single { it.eventId == moved.id }
        assertEquals(listOf(other), deleteScheduleOccurrence(saved, occurrence, deleteSeries = true))
        val deleted = deleteScheduleOccurrence(saved, occurrence, deleteSeries = false, updatedAt = 300)
        assertEquals(master.copy(sourceCalendarId = calendar, exDateList = listOf(moved.recurrenceId!!), updatedAt = 300),
            deleted.single { it.id == master.id })
        assertEquals(other, deleted.single { it.id == other.id })
        assertEquals(emptyList<Any>(), occurrences(deleted.filterNot { it.id == other.id }, monday.plusWeeks(2)))
    }

    private fun old(event: ScheduleEvent, source: String) = event.copy(id = "old-${event.id}",
        sourceCalendarId = source, lastImportedAt = 100, updatedAt = 100)

    private fun preview(current: List<ScheduleEvent>, incoming: List<ScheduleEvent>) = IcsImportPreview(
        fileName = "course.ics", sourceCalendarId = calendar,
        replacedCalendarIds = resolveIcsReplacedCalendarIds(calendar, incoming,
            current.filter { it.sourceType == ScheduleSourceType.ICS_IMPORT }, previousStableCalendarId = previous),
        incomingEvents = incoming, added = emptyList(), updated = emptyList(), conflicts = emptyList(), deleted = emptyList()
    )

    private fun occurrences(events: List<ScheduleEvent>, date: LocalDate) = buildScheduleOccurrences(events, date, date.plusDays(6))
}
