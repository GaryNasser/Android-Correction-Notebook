package com.github.garynasser.correction_notebook.data.repository

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.garynasser.correction_notebook.data.model.home.ScheduleEvent
import com.github.garynasser.correction_notebook.data.model.home.ScheduleSourceType
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class SchedulePreferenceCodecTest {
    private val event = ScheduleEvent(
        id = "id ||| ::: % 中文", title = "Literal \\n and \\N, ; : ||| :::",
        description = "Path C:\\notes\\new\nSecond line \\, \\; \\\\",
        location = "文萃楼 M134 \\north\n良乡校区",
        startAt = LocalDateTime.of(2026, 10, 2, 8, 0),
        endAt = LocalDateTime.of(2026, 10, 2, 9, 35),
        timezoneId = "Asia/Shanghai", sourceCalendarId = "calendar", sourceEventUid = "uid",
        recurrenceRule = "FREQ=WEEKLY;COUNT=8", recurrenceId = LocalDateTime.of(2026, 10, 9, 8, 0),
        exDateList = listOf(LocalDateTime.of(2026, 10, 16, 8, 0)), lastImportedAt = 100L, updatedAt = 200L
    )

    @Test
    fun everySourceRoundTripsTextAndMetadataWithoutChangingContent() {
        val events = ScheduleSourceType.entries.map { event.copy(sourceType = it, allDay = it == ScheduleSourceType.MANUAL) }
        var restored = events
        repeat(4) {
            restored = SchedulePreferenceCodec.parseEvents(SchedulePreferenceCodec.serializeEvents(restored))
            assertEquals(events, restored)
        }
    }

    @Test
    fun legacyIcsTextIsDecodedOnceAndThenStaysStableAcrossWrites() {
        val imported = event.copy(sourceType = ScheduleSourceType.ICS_IMPORT)
        val legacy = legacyRecord(imported,
            title = "Regex \\\\n\\, lecture",
            description = "Path C:\\\\notes\\nSecond line",
            location = "文萃楼 M134\\n良乡校区")
        val expected = imported.copy(title = "Regex \\n, lecture",
            description = "Path C:\\notes\nSecond line", location = "文萃楼 M134\n良乡校区")
        val parsed = SchedulePreferenceCodec.parseEvents(legacy)
        assertEquals(listOf(expected), parsed)
        repeat(3) {
            assertEquals(parsed, SchedulePreferenceCodec.parseEvents(SchedulePreferenceCodec.serializeEvents(parsed)))
        }
    }

    @Test
    fun legacyManualAndSchoolRecordsKeepLiteralBackslashes() {
        listOf(ScheduleSourceType.MANUAL, ScheduleSourceType.SCHOOL_IMPORT).forEach { source ->
            val expected = event.copy(sourceType = source)
            assertEquals(listOf(expected), SchedulePreferenceCodec.parseEvents(legacyRecord(expected)))
        }
    }

    @Test
    fun oneDamagedLegacyRecordDoesNotHideTheRemainingValidEvents() {
        val valid = legacyRecord(event)
        val damaged = valid.split(":::").toMutableList().apply { this[4] = "invalid date" }.joinToString(":::")
        assertEquals(listOf(event), SchedulePreferenceCodec.parseEvents("broken|||$damaged|||$valid"))
    }

    private fun legacyRecord(
        item: ScheduleEvent, title: String = item.title,
        description: String = item.description, location: String = item.location
    ): String = listOf(
        Uri.encode(item.id), Uri.encode(title), Uri.encode(description), Uri.encode(location),
        item.startAt.toString(), item.endAt.toString(), item.allDay.toString(), Uri.encode(item.timezoneId.orEmpty()),
        item.sourceType.name, Uri.encode(item.sourceCalendarId.orEmpty()), Uri.encode(item.sourceEventUid.orEmpty()),
        Uri.encode(item.recurrenceRule.orEmpty()), item.recurrenceId?.toString().orEmpty(),
        Uri.encode(item.exDateList.joinToString(",")), item.lastImportedAt?.toString().orEmpty(), item.updatedAt.toString()
    ).joinToString(":::")
}
