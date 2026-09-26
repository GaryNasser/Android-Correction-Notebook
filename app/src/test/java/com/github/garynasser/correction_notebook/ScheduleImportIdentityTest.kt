package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.model.home.ScheduleEvent
import com.github.garynasser.correction_notebook.data.model.home.IcsImportPreview
import com.github.garynasser.correction_notebook.data.model.home.ImportDecision
import com.github.garynasser.correction_notebook.data.model.home.ScheduleOccurrence
import com.github.garynasser.correction_notebook.data.model.home.ScheduleSourceType
import com.github.garynasser.correction_notebook.data.repository.buildIcsCalendarId
import com.github.garynasser.correction_notebook.data.repository.buildLegacyIcsCalendarId
import com.github.garynasser.correction_notebook.data.repository.buildPreviousIcsCalendarId
import com.github.garynasser.correction_notebook.data.repository.applyIcsImport
import com.github.garynasser.correction_notebook.data.repository.icsEventCompositeKey
import com.github.garynasser.correction_notebook.data.repository.parseIcsEvents
import com.github.garynasser.correction_notebook.data.repository.resolveIcsReplacedCalendarIds
import com.github.garynasser.correction_notebook.data.repository.scheduleOccurrenceOverlapsDate
import com.github.garynasser.correction_notebook.data.repository.scheduleOccurrenceOverlapsRange
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ScheduleImportIdentityTest {
    @Test
    fun calendarIdIgnoresChangingEventContent() {
        val first = buildIcsCalendarId(
            "BITStudy.ics",
            listOf("PRODID:-//BIT//Calendar//CN", "X-WR-CALNAME:课表", "SUMMARY:高等数学")
        )
        val updated = buildIcsCalendarId(
            "BITStudy.ics",
            listOf("PRODID:-//BIT//Calendar//CN", "X-WR-CALNAME:课表", "SUMMARY:大学物理")
        )

        assertEquals(first, updated)
    }

    @Test
    fun calendarIdSeparatesDifferentCalendarMetadata() {
        val classCalendar = buildIcsCalendarId("calendar.ics", listOf("X-WR-CALNAME:课表"))
        val examCalendar = buildIcsCalendarId("calendar.ics", listOf("X-WR-CALNAME:考试"))

        assertNotEquals(classCalendar, examCalendar)
    }

    @Test
    fun calendarIdIgnoresRenamedExportWhenMetadataExists() {
        val metadata = listOf("PRODID:-//BIT//Calendar//CN", "X-WR-CALNAME:课表")

        assertEquals(
            buildIcsCalendarId("calendar.ics", metadata),
            buildIcsCalendarId("calendar (1).ics", metadata)
        )
        assertNotEquals(
            buildIcsCalendarId("calendar.ics", emptyList()),
            buildIcsCalendarId("calendar (1).ics", emptyList())
        )
        assertNotEquals(
            buildIcsCalendarId("calendar.ics", listOf("PRODID:-//Generic//Calendar//EN")),
            buildIcsCalendarId("other.ics", listOf("PRODID:-//Generic//Calendar//EN"))
        )
    }

    @Test
    fun replacementIncludesPreviousStableIdentity() {
        val lines = listOf("PRODID:-//BIT//Calendar//CN", "X-WR-CALNAME:课表")
        val previousId = buildPreviousIcsCalendarId("calendar.ics", lines)

        assertEquals(
            setOf("ics_v3_current", previousId),
            resolveIcsReplacedCalendarIds(
                sourceCalendarId = "ics_v3_current",
                incomingEvents = emptyList(),
                importedEvents = emptyList(),
                previousStableCalendarId = previousId
            )
        )
    }

    @Test
    fun eventKeyFallsBackToContentWhenUidIsMissing() {
        val first = scheduleEvent(title = "高等数学", hour = 8)
        val second = scheduleEvent(title = "大学物理", hour = 8)

        assertNotEquals(icsEventCompositeKey(first), icsEventCompositeKey(second))
    }

    @Test
    fun eventKeyUsesUidAcrossContentUpdates() {
        val first = scheduleEvent(title = "高等数学", hour = 8, uid = "course-1@bit.edu.cn")
        val updated = scheduleEvent(title = "高等数学（调课）", hour = 10, uid = "course-1@bit.edu.cn")

        assertEquals(icsEventCompositeKey(first), icsEventCompositeKey(updated))
    }

    @Test
    fun matchingUidMigratesLegacyCalendarSource() {
        val incoming = scheduleEvent(title = "高等数学（调课）", hour = 10, uid = "course-1@bit.edu.cn")
            .copy(sourceCalendarId = "ics_v2_stable")
        val legacy = scheduleEvent(title = "高等数学", hour = 8, uid = "course-1@bit.edu.cn")
            .copy(sourceCalendarId = "ics_0123456789abcdef0123456789abcdef")

        val replaced = resolveIcsReplacedCalendarIds(
            sourceCalendarId = "ics_v2_stable",
            incomingEvents = listOf(incoming),
            importedEvents = listOf(legacy)
        )

        assertEquals(setOf("ics_0123456789abcdef0123456789abcdef", "ics_v2_stable"), replaced)
    }

    @Test
    fun identicalUidlessFileMigratesItsExactLegacySource() {
        val oldId = buildLegacyIcsCalendarId("course.ics", "BEGIN:VCALENDAR\nEND:VCALENDAR")
        val oldEvent = scheduleEvent("高等数学", 8).copy(sourceCalendarId = oldId)

        assertEquals(
            setOf(oldId, "ics_v2_current"),
            resolveIcsReplacedCalendarIds(
                sourceCalendarId = "ics_v2_current",
                incomingEvents = listOf(scheduleEvent("高等数学", 8)),
                importedEvents = listOf(oldEvent),
                legacyExactCalendarId = oldId
            )
        )
    }

    @Test
    fun matchingUidDoesNotReplaceAnotherStableCalendar() {
        val incoming = scheduleEvent(title = "高等数学", hour = 8, uid = "shared")
        val separate = scheduleEvent(title = "高等数学", hour = 8, uid = "shared")
            .copy(sourceCalendarId = "ics_v2_other")

        assertEquals(
            setOf("ics_v2_current"),
            resolveIcsReplacedCalendarIds("ics_v2_current", listOf(incoming), listOf(separate))
        )
    }

    @Test
    fun partialLegacyUidOverlapDoesNotReplaceWholeCalendar() {
        val incoming = scheduleEvent(title = "高等数学", hour = 8, uid = "shared")
        val oldEvents = listOf(
            scheduleEvent(title = "高等数学", hour = 8, uid = "shared"),
            scheduleEvent(title = "大学物理", hour = 10, uid = "missing")
        ).map { it.copy(sourceCalendarId = "ics_0123456789abcdef0123456789abcdef") }

        assertEquals(
            setOf("ics_v2_current"),
            resolveIcsReplacedCalendarIds("ics_v2_current", listOf(incoming), oldEvents)
        )
    }

    @Test
    fun malformedEventRejectsImportInsteadOfSilentlyDroppingIt() {
        val lines = listOf(
            "BEGIN:VEVENT", "UID:valid", "DTSTART:20260912T080000", "DTEND:20260912T090000", "END:VEVENT",
            "BEGIN:VEVENT", "UID:broken", "DTSTART:invalid", "DTEND:20260912T110000", "END:VEVENT"
        )

        val error = assertThrows(IllegalArgumentException::class.java) {
            parseIcsEvents(lines, "ics_v2_test")
        }
        assertTrue(error.message.orEmpty().contains("第 2 个日程"))
    }

    @Test
    fun parserAcceptsShortIcsTimeAndQuotedTimezone() {
        val events = parseIcsEvents(
            listOf(
                "BEGIN:VEVENT", "DTSTART;TZID=\"Asia/Shanghai\":20260912T0800",
                "DTEND;TZID=\"Asia/Shanghai\":20260912T0900", "END:VEVENT"
            ),
            "ics_v2_test"
        )

        assertEquals(1, events.size)
        val expected = LocalDateTime.of(2026, 9, 12, 8, 0)
            .atZone(ZoneId.of("Asia/Shanghai"))
            .withZoneSameInstant(ZoneId.systemDefault())
            .toLocalDateTime()
        assertEquals(expected, events.single().startAt)
    }

    @Test
    fun unterminatedEventRejectsImport() {
        val error = assertThrows(IllegalArgumentException::class.java) {
            parseIcsEvents(listOf("BEGIN:VEVENT", "DTSTART:20260912T080000"), "ics_v2_test")
        }
        assertTrue(error.message.orEmpty().contains("未结束"))
    }

    @Test
    fun mergeKeepsLocallyEditedEventWhileOverwriteReplacesIt() {
        val local = scheduleEvent("本地修改", 8, "course-1")
            .copy(sourceCalendarId = "ics_v2_test", lastImportedAt = 100L, updatedAt = 200L)
        val incoming = scheduleEvent("导入版本", 10, "course-1")
            .copy(sourceCalendarId = "ics_v2_test", lastImportedAt = 300L, updatedAt = 300L)
        val preview = previewOf(incoming)

        assertEquals(listOf(local), applyIcsImport(listOf(local), preview, ImportDecision.MERGE))
        assertEquals(listOf(incoming), applyIcsImport(listOf(local), preview, ImportDecision.OVERWRITE))
    }

    @Test
    fun overwriteRemovesOnlyMatchedCalendarSources() {
        val old = scheduleEvent("旧课程", 8, "course-1")
            .copy(sourceCalendarId = "ics_0123456789abcdef0123456789abcdef")
        val unrelated = scheduleEvent("其他日历", 10, "other")
            .copy(sourceCalendarId = "ics_v2_other")
        val incoming = scheduleEvent("新课程", 8, "course-1")
            .copy(sourceCalendarId = "ics_v2_test")
        val preview = previewOf(incoming).copy(replacedCalendarIds = setOf(old.sourceCalendarId!!))

        assertEquals(
            listOf(unrelated, incoming),
            applyIcsImport(listOf(old, unrelated), preview, ImportDecision.OVERWRITE)
        )
    }

    private fun previewOf(event: ScheduleEvent): IcsImportPreview {
        return IcsImportPreview(
            fileName = "test.ics",
            sourceCalendarId = "ics_v2_test",
            incomingEvents = listOf(event),
            added = emptyList(),
            updated = emptyList(),
            conflicts = emptyList(),
            deleted = emptyList()
        )
    }

    @Test
    fun allDayEndDateIsExclusive() {
        val occurrence = occurrence(
            startAt = LocalDateTime.of(2026, 9, 12, 0, 0),
            endAt = LocalDateTime.of(2026, 9, 13, 0, 0),
            allDay = true
        )

        assertTrue(scheduleOccurrenceOverlapsDate(occurrence, LocalDate.of(2026, 9, 12)))
        assertFalse(scheduleOccurrenceOverlapsDate(occurrence, LocalDate.of(2026, 9, 13)))
    }

    @Test
    fun eventEndingAtMidnightDoesNotAppearOnNextDay() {
        val occurrence = occurrence(
            startAt = LocalDateTime.of(2026, 9, 12, 23, 0),
            endAt = LocalDateTime.of(2026, 9, 13, 0, 0)
        )

        assertTrue(scheduleOccurrenceOverlapsRange(
            occurrence,
            LocalDate.of(2026, 9, 12),
            LocalDate.of(2026, 9, 12)
        ))
        assertFalse(scheduleOccurrenceOverlapsRange(
            occurrence,
            LocalDate.of(2026, 9, 13),
            LocalDate.of(2026, 9, 13)
        ))
    }

    private fun scheduleEvent(title: String, hour: Int, uid: String? = null): ScheduleEvent {
        return ScheduleEvent(
            title = title,
            startAt = LocalDateTime.of(2026, 9, 12, hour, 0),
            endAt = LocalDateTime.of(2026, 9, 12, hour + 1, 0),
            sourceType = ScheduleSourceType.ICS_IMPORT,
            sourceEventUid = uid
        )
    }

    private fun occurrence(
        startAt: LocalDateTime,
        endAt: LocalDateTime,
        allDay: Boolean = false
    ): ScheduleOccurrence {
        return ScheduleOccurrence(
            occurrenceId = "test",
            eventId = "event",
            title = "测试日程",
            description = "",
            location = "",
            startAt = startAt,
            endAt = endAt,
            allDay = allDay,
            sourceType = ScheduleSourceType.ICS_IMPORT
        )
    }
}
