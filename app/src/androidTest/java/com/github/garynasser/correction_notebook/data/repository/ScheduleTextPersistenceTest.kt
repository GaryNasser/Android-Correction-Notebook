package com.github.garynasser.correction_notebook.data.repository

import android.content.Context
import androidx.core.content.FileProvider
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.garynasser.correction_notebook.data.model.home.IcsImportPreview
import com.github.garynasser.correction_notebook.data.model.home.ImportDecision
import com.github.garynasser.correction_notebook.data.model.home.ScheduleEvent
import com.github.garynasser.correction_notebook.data.model.home.ScheduleSourceType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import com.github.garynasser.correction_notebook.data.model.home.ScheduleRange

@RunWith(AndroidJUnit4::class)
class ScheduleTextPersistenceTest {
    @Test
    fun timezoneOnlyChangeIsPreviewedAndChangesFutureCourseTimes() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ScheduleRepository(context)
        val importer = IcsImportRepository(context, repository)
        val token = UUID.randomUUID().toString()
        val file = File(File(context.filesDir, "knowledge_base").apply { mkdirs() }, "qa_zone_$token.ics")
        val raw = listOf("BEGIN:VCALENDAR", "VERSION:2.0", "X-WR-CALNAME:$token", "BEGIN:VEVENT", "UID:$token",
            "DTSTART;TZID=America/New_York:20000103T090000", "DTEND;TZID=America/New_York:20000103T100000",
            "RRULE:FREQ=WEEKLY;COUNT=30", "SUMMARY:Timezone fixture", "END:VEVENT", "END:VCALENDAR").joinToString("\r\n")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        var calendarId: String? = null
        try {
            file.writeText(raw)
            val first = importer.buildPreview(uri)
            calendarId = first.sourceCalendarId
            repository.applyImportPreview(first, ImportDecision.MERGE)
            file.writeText(raw.replace("America/New_York", "America/Bogota"))
            val update = importer.buildPreview(uri)
            assertEquals(first.incomingEvents.single().startAt, update.incomingEvents.single().startAt)
            assertEquals(1, update.updated.size)
            assertTrue(update.added.isEmpty() && update.conflicts.isEmpty() && update.deleted.isEmpty())
            repository.applyImportPreview(update, ImportDecision.MERGE)
            val stored = repository.getImportedEventsForCalendar(first.sourceCalendarId).single()
            assertEquals("America/Bogota", stored.timezoneId)
            val expected = LocalDateTime.of(2000, 4, 3, 9, 0).atZone(ZoneId.of("America/Bogota"))
                .withZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime()
            assertTrue(repository.getEventsForRange(ScheduleRange.WEEK, expected.toLocalDate())
                .flatMap { it.items }.any { it.eventId == stored.id && it.startAt == expected })
        } finally {
            calendarId?.let { repository.getImportedEventsForCalendar(it).forEach { event -> repository.deleteEvent(event.id) } }
            file.delete()
        }
    }

    @Test
    fun fileUriImportReimportConflictAndOverwritePreserveTextAndUnrelatedEvents() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val repository = ScheduleRepository(context)
        val importer = IcsImportRepository(context, repository)
        val token = UUID.randomUUID().toString()
        val file = File(File(context.filesDir, "knowledge_base").apply { mkdirs() }, "qa_$token.ics")
        val raw = listOf(
            "BEGIN:VCALENDAR", "VERSION:2.0", "PRODID:-//BITStudy QA//EN", "X-WR-CALNAME:$token",
            "BEGIN:VEVENT", "UID:$token", "DTSTART:20000103T080000", "DTEND:20000103T093500",
            "SUMMARY:Regular expressions \\\\n and \\\\N",
            "LOCATION:文萃楼 ", " M134 \\\\north\\n良乡校区",
            "DESCRIPTION:Path C:\\\\notes\\\\new\\nBring notes", "END:VEVENT", "END:VCALENDAR"
        ).joinToString("\r\n")
        file.writeText(raw)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        var calendarId: String? = null
        val unrelated = ScheduleEvent(title = "Unrelated fixture", startAt = LocalDateTime.of(2000, 1, 3, 8, 0),
            endAt = LocalDateTime.of(2000, 1, 3, 9, 35))
        val otherCalendar = unrelated.copy(id = "$token-other", sourceType = ScheduleSourceType.ICS_IMPORT,
            sourceCalendarId = "$token-other-calendar", sourceEventUid = token)
        try {
            repository.addEvent(unrelated)
            repository.addEvent(otherCalendar)
            val first = importer.buildPreview(uri)
            calendarId = first.sourceCalendarId
            assertEquals(file.name, first.fileName)
            assertEquals(1, first.added.size)
            val expected = first.incomingEvents.single()
            assertEquals("文萃楼 M134 \\north\n良乡校区", expected.location)
            repository.applyImportPreview(first, ImportDecision.MERGE)
            repeat(2) {
                val unchanged = importer.buildPreview(uri)
                assertEquals(0, unchanged.added.size + unchanged.updated.size + unchanged.conflicts.size + unchanged.deleted.size)
                repository.applyImportPreview(unchanged, ImportDecision.MERGE)
                assertTextUnchanged(expected, repository.getImportedEventsForCalendar(first.sourceCalendarId).single())
            }
            val stored = repository.getImportedEventsForCalendar(first.sourceCalendarId).single()
            repository.updateEvent(stored.copy(title = "Locally edited course", lastImportedAt = 1L))
            val conflict = importer.buildPreview(uri)
            assertEquals(1, conflict.conflicts.size)
            assertTrue(conflict.added.isEmpty() && conflict.updated.isEmpty() && conflict.deleted.isEmpty())
            repository.applyImportPreview(conflict, ImportDecision.MERGE)
            assertEquals("Locally edited course", repository.getImportedEventsForCalendar(first.sourceCalendarId).single().title)
            repository.applyImportPreview(importer.buildPreview(uri), ImportDecision.OVERWRITE)
            assertTextUnchanged(expected, repository.getImportedEventsForCalendar(first.sourceCalendarId).single())
            assertEquals(unrelated, repository.getEventById(unrelated.id))
            assertEquals(otherCalendar, repository.getEventById(otherCalendar.id))

            val beforeInvalidImport = repository.getImportedEventsForCalendar(first.sourceCalendarId)
            file.writeText(raw.replace("DTSTART:20000103T080000", "DTSTART:invalid"))
            val error = try {
                importer.buildPreview(uri)
                throw AssertionError("Invalid file must reject the preview")
            } catch (error: IllegalArgumentException) {
                error
            }
            assertTrue(error.message.orEmpty().contains("第 1 个日程"))
            assertEquals(beforeInvalidImport, repository.getImportedEventsForCalendar(first.sourceCalendarId))
        } finally {
            calendarId?.let { repository.getImportedEventsForCalendar(it).forEach { event -> repository.deleteEvent(event.id) } }
            repository.deleteEvent(unrelated.id)
            repository.deleteEvent(otherCalendar.id)
            file.delete()
        }
    }

    @Test
    fun importedTextRemainsUnchangedAfterReadsAndUnrelatedScheduleWrites() = runBlocking {
        val repository = ScheduleRepository(ApplicationProvider.getApplicationContext())
        val calendarId = "qa_${UUID.randomUUID()}"
        val event = parseIcsEvents(
            listOf(
                "BEGIN:VEVENT", "UID:$calendarId",
                "DTSTART:20000103T080000", "DTEND:20000103T093500",
                "SUMMARY:Regular expressions \\\\n and \\\\N",
                "LOCATION:文萃楼 M134 \\\\north\\n良乡校区",
                "DESCRIPTION:Path C:\\\\notes\\\\new\\nBring notes",
                "END:VEVENT"
            ),
            calendarId
        ).single()
        val unrelated = ScheduleEvent(
            title = "Unrelated schedule fixture", startAt = event.startAt, endAt = event.endAt
        )
        try {
            repository.applyImportPreview(
                IcsImportPreview("test.ics", calendarId, incomingEvents = listOf(event),
                    added = emptyList(), updated = emptyList(), conflicts = emptyList(), deleted = emptyList()),
                ImportDecision.MERGE
            )
            assertEquals("Regular expressions \\n and \\N", event.title)
            assertEquals("文萃楼 M134 \\north\n良乡校区", event.location)
            assertEquals("Path C:\\notes\\new\nBring notes", event.description)
            repeat(3) {
                assertTextUnchanged(event, repository.getEventById(event.id)!!)
                repository.addEvent(unrelated.copy(id = "${unrelated.id}_$it"))
            }
            repository.updateEvent(event.copy(description = "Edited path C:\\notes\\new"))
            assertEquals("Edited path C:\\notes\\new", repository.getEventById(event.id)?.description)
            assertEquals(event.location, repository.getEventById(event.id)?.location)
        } finally {
            repository.deleteEvent(event.id)
            repeat(3) { repository.deleteEvent("${unrelated.id}_$it") }
        }
    }

    private fun assertTextUnchanged(expected: ScheduleEvent, actual: ScheduleEvent) {
        assertEquals(expected.title, actual.title)
        assertEquals(expected.description, actual.description)
        assertEquals(expected.location, actual.location)
    }
}
