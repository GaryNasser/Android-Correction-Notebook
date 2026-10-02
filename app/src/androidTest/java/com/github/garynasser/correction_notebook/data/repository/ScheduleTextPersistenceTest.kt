package com.github.garynasser.correction_notebook.data.repository

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.garynasser.correction_notebook.data.model.home.IcsImportPreview
import com.github.garynasser.correction_notebook.data.model.home.ImportDecision
import com.github.garynasser.correction_notebook.data.model.home.ScheduleEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ScheduleTextPersistenceTest {
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
