package com.github.garynasser.correction_notebook.data.repository

import com.github.garynasser.correction_notebook.data.model.home.ScheduleEvent
import com.github.garynasser.correction_notebook.data.model.home.ScheduleOccurrence
import com.github.garynasser.correction_notebook.data.model.home.ScheduleSourceType
import org.dmfs.rfc5545.DateTime
import org.dmfs.rfc5545.recur.InvalidRecurrenceRuleException
import org.dmfs.rfc5545.recur.RecurrenceRule
import org.dmfs.rfc5545.recur.RecurrenceRuleIterator
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.TimeZone

private data class RecurrenceInstanceKey(
    val source: ScheduleSourceType,
    val calendarId: String?,
    val uid: String,
    val originalStart: LocalDateTime
)

internal fun buildScheduleOccurrences(
    events: List<ScheduleEvent>, startDate: LocalDate, endDate: LocalDate
): List<ScheduleOccurrence> {
    if (endDate.isBefore(startDate)) return emptyList()
    val overrides = events.filter { it.recurrenceId != null }
    val overriddenInstances = overrides.mapNotNull { it.instanceKey(it.recurrenceId!!) }.toSet()
    // Detached instances use their actual date, even when the original date lies outside this week.
    val detached = overrides.map { it.toOccurrence(it.startAt, it.endAt) }
    val regular = events.filter { it.recurrenceId == null }.flatMap { event ->
        val excluded = event.exDateList.toSet()
        val starts = if (event.recurrenceRule.isNullOrBlank()) sequenceOf(event.startAt)
            else recurringStarts(event, startDate, endDate)
        starts.filter { it !in excluded && event.instanceKey(it) !in overriddenInstances }
            .map { event.toOccurrence(it, event.endForOccurrence(it)) }.toList()
    }
    return (regular + detached).filter { scheduleOccurrenceOverlapsRange(it, startDate, endDate) }
        .sortedWith(compareBy<ScheduleOccurrence> { it.startAt }.thenBy { it.title })
}

internal fun scheduleRecurrenceIterator(event: ScheduleEvent): RecurrenceRuleIterator {
    try {
        val rule = RecurrenceRule(requireNotNull(event.recurrenceRule), RecurrenceRule.RfcMode.RFC5545_STRICT)
        return rule.iterator(event.recurrenceDateTime(event.startAt))
    } catch (error: InvalidRecurrenceRuleException) {
        throw IllegalArgumentException("重复规则无效：${error.message}", error)
    }
}

private fun recurringStarts(event: ScheduleEvent, startDate: LocalDate, endDate: LocalDate): Sequence<LocalDateTime> {
    val iterator = try {
        scheduleRecurrenceIterator(event)
    } catch (_: IllegalArgumentException) {
        // Keep a damaged legacy rule from hiding every other course in the timetable.
        return sequenceOf(event.startAt)
    }
    val systemZone = ZoneId.systemDefault()
    val duration = event.duration()
    val earliestStart = if (event.hasAbsoluteTime()) {
        startDate.atStartOfDay(systemZone).minus(duration).toLocalDateTime()
    } else startDate.atStartOfDay().minus(duration)
    val endExclusive = endDate.plusDays(1).atStartOfDay()
    iterator.fastForward(event.recurrenceDateTime(earliestStart))
    return sequence {
        while (iterator.hasNext()) {
            val next = iterator.nextDateTime()
            // The library encodes floating date fields as UTC, without giving them an absolute timezone.
            val displayZone = if (next.timeZone == null) ZoneOffset.UTC else systemZone
            val start = LocalDateTime.ofInstant(Instant.ofEpochMilli(next.timestamp), displayZone)
            if (!start.isBefore(endExclusive)) break
            yield(start)
        }
    }
}

private fun ScheduleEvent.recurrenceDateTime(local: LocalDateTime): DateTime = when {
    allDay -> DateTime(local.year, local.monthValue - 1, local.dayOfMonth)
    hasAbsoluteTime() -> DateTime(TimeZone.getTimeZone(ZoneId.of(timezoneId)),
        local.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
    else -> DateTime(local.year, local.monthValue - 1, local.dayOfMonth, local.hour, local.minute, local.second)
}

private fun ScheduleEvent.hasAbsoluteTime() = !allDay && !timezoneId.isNullOrBlank()

private fun ScheduleEvent.duration(): Duration = if (hasAbsoluteTime()) {
    val zone = ZoneId.systemDefault()
    Duration.between(startAt.atZone(zone).toInstant(), endAt.atZone(zone).toInstant())
} else Duration.between(startAt, endAt)

private fun ScheduleEvent.endForOccurrence(start: LocalDateTime): LocalDateTime = if (hasAbsoluteTime()) {
    start.atZone(ZoneId.systemDefault()).plus(duration()).toLocalDateTime()
} else start.plus(duration())

private fun ScheduleEvent.instanceKey(start: LocalDateTime): RecurrenceInstanceKey? =
    sourceEventUid?.trim()?.takeIf { it.isNotEmpty() }?.let {
        RecurrenceInstanceKey(sourceType, sourceCalendarId, it, start)
    }

private fun ScheduleEvent.toOccurrence(start: LocalDateTime, end: LocalDateTime) = ScheduleOccurrence(
    occurrenceId = "${id}_${start.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)}",
    eventId = id, title = title, description = description, location = location,
    startAt = start, endAt = end, allDay = allDay, sourceType = sourceType
)
