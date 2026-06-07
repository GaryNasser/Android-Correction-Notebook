package com.github.garynasser.correction_notebook.data.repository

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.garynasser.correction_notebook.data.model.home.IcsImportPreview
import com.github.garynasser.correction_notebook.data.model.home.ImportDecision
import com.github.garynasser.correction_notebook.data.model.home.ScheduleEvent
import com.github.garynasser.correction_notebook.data.model.home.ScheduleOccurrence
import com.github.garynasser.correction_notebook.data.model.home.ScheduleRange
import com.github.garynasser.correction_notebook.data.model.home.ScheduleSection
import com.github.garynasser.correction_notebook.data.model.home.ScheduleSourceType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

private val Context.scheduleDataStore: DataStore<Preferences> by preferencesDataStore("schedule_prefs")

class ScheduleRepository(private val context: Context) {

    private val scheduleEventsKey = stringPreferencesKey("schedule_events")
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    val scheduleEvents: Flow<List<ScheduleEvent>> = context.scheduleDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            prefs[scheduleEventsKey]?.let(::parseScheduleEvents) ?: emptyList()
        }

    suspend fun addEvent(event: ScheduleEvent) {
        context.scheduleDataStore.edit { prefs ->
            val current = prefs[scheduleEventsKey]?.let(::parseScheduleEvents) ?: emptyList()
            prefs[scheduleEventsKey] = serializeScheduleEvents(current + event)
        }
    }

    suspend fun updateEvent(event: ScheduleEvent) {
        context.scheduleDataStore.edit { prefs ->
            val current = prefs[scheduleEventsKey]?.let(::parseScheduleEvents) ?: emptyList()
            prefs[scheduleEventsKey] = serializeScheduleEvents(
                current.map { if (it.id == event.id) event.copy(updatedAt = System.currentTimeMillis()) else it }
            )
        }
    }

    suspend fun deleteEvent(eventId: String) {
        context.scheduleDataStore.edit { prefs ->
            val current = prefs[scheduleEventsKey]?.let(::parseScheduleEvents) ?: emptyList()
            prefs[scheduleEventsKey] = serializeScheduleEvents(current.filterNot { it.id == eventId })
        }
    }

    suspend fun getEventById(eventId: String): ScheduleEvent? {
        return scheduleEvents.first().firstOrNull { it.id == eventId }
    }

    suspend fun getEventsForRange(range: ScheduleRange, today: LocalDate = LocalDate.now()): List<ScheduleSection> {
        val (startDate, endDate) = when (range) {
            ScheduleRange.TODAY -> today to today
            ScheduleRange.TOMORROW -> today.plusDays(1) to today.plusDays(1)
            ScheduleRange.WEEK -> today to today.plusDays(6)
        }
        val occurrences = buildOccurrences(scheduleEvents.first(), startDate, endDate)
        return (0..java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate).toInt()).map { offset ->
            val date = startDate.plusDays(offset.toLong())
            val items = occurrences
                .filter { occurrence -> occurrence.startAt.toLocalDate() == date || overlapsDate(occurrence, date) }
                .sortedWith(compareBy<ScheduleOccurrence> { !it.allDay }.thenBy { it.startAt })
            ScheduleSection(
                title = when (range) {
                    ScheduleRange.TODAY -> "今天"
                    ScheduleRange.TOMORROW -> "明天"
                    ScheduleRange.WEEK -> "${date.monthValue}月${date.dayOfMonth}日"
                },
                date = date,
                items = items
            )
        }
    }

    suspend fun applyImportPreview(
        preview: IcsImportPreview,
        decision: ImportDecision
    ) {
        context.scheduleDataStore.edit { prefs ->
            val current = prefs[scheduleEventsKey]?.let(::parseScheduleEvents) ?: emptyList()
            val retained = when (decision) {
                ImportDecision.MERGE -> {
                    val incomingIds = preview.incomingEvents.map { compositeKey(it) }.toSet()
                    current.filterNot { event ->
                        event.sourceType == ScheduleSourceType.ICS_IMPORT &&
                            event.sourceCalendarId == preview.sourceCalendarId &&
                            compositeKey(event) in incomingIds
                    }
                }
                ImportDecision.OVERWRITE -> {
                    current.filterNot {
                        it.sourceType == ScheduleSourceType.ICS_IMPORT &&
                            it.sourceCalendarId == preview.sourceCalendarId
                    }
                }
            }
            prefs[scheduleEventsKey] = serializeScheduleEvents(retained + preview.incomingEvents)
        }
    }

    suspend fun getImportedEventsForCalendar(calendarId: String): List<ScheduleEvent> {
        return scheduleEvents.first().filter {
            it.sourceType == ScheduleSourceType.ICS_IMPORT && it.sourceCalendarId == calendarId
        }
    }

    private fun buildOccurrences(
        events: List<ScheduleEvent>,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<ScheduleOccurrence> {
        val overrides = events
            .filter { it.recurrenceId != null && !it.sourceEventUid.isNullOrBlank() }
            .associateBy { overrideKey(it.sourceEventUid!!, it.recurrenceId!!) }

        return events
            .filter { it.recurrenceId == null }
            .flatMap { event ->
                if (event.recurrenceRule.isNullOrBlank()) {
                    val occurrence = event.toOccurrence(event.startAt, event.endAt)
                    if (overlapsRange(occurrence, startDate, endDate)) listOf(occurrence) else emptyList()
                } else {
                    expandRecurringEvent(event, overrides, startDate, endDate)
                }
            }
            .sortedWith(compareBy<ScheduleOccurrence> { it.startAt }.thenBy { it.title })
    }

    private fun expandRecurringEvent(
        event: ScheduleEvent,
        overrides: Map<String, ScheduleEvent>,
        startDate: LocalDate,
        endDate: LocalDate
    ): List<ScheduleOccurrence> {
        val ruleParts = parseRecurrenceRule(event.recurrenceRule)
        val freq = ruleParts["FREQ"] ?: return emptyList()
        val interval = ruleParts["INTERVAL"]?.toLongOrNull()?.coerceAtLeast(1) ?: 1L
        val countLimit = ruleParts["COUNT"]?.toIntOrNull()
        val until = ruleParts["UNTIL"]?.let { parseIcsDateTime(it, null).first }
        val byDays = ruleParts["BYDAY"]
            ?.split(",")
            ?.mapNotNull(::parseDayOfWeek)
            .orEmpty()

        val results = mutableListOf<ScheduleOccurrence>()
        val duration = java.time.Duration.between(event.startAt, event.endAt)
        var generated = 0

        when (freq) {
            "DAILY" -> {
                var current = event.startAt
                while (!current.toLocalDate().isAfter(endDate) && !isPastLimit(generated, countLimit, current, until)) {
                    addOccurrenceIfNeeded(event, current, duration, overrides, startDate, endDate, results)
                    generated++
                    current = current.plusDays(interval)
                }
            }
            "WEEKLY" -> {
                if (byDays.isEmpty()) {
                    var current = event.startAt
                    while (!current.toLocalDate().isAfter(endDate) && !isPastLimit(generated, countLimit, current, until)) {
                        addOccurrenceIfNeeded(event, current, duration, overrides, startDate, endDate, results)
                        generated++
                        current = current.plusWeeks(interval)
                    }
                } else {
                    var weekStart = event.startAt.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                    while (!weekStart.isAfter(endDate) && (countLimit == null || generated < countLimit)) {
                        byDays.forEach { day ->
                            val occurrenceDate = weekStart.with(TemporalAdjusters.nextOrSame(day))
                            val weeksBetween = java.time.temporal.ChronoUnit.WEEKS.between(
                                event.startAt.toLocalDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)),
                                weekStart
                            )
                            if (weeksBetween % interval != 0L) return@forEach
                            val current = LocalDateTime.of(occurrenceDate, event.startAt.toLocalTime())
                            if (current.isBefore(event.startAt) || isPastLimit(generated, countLimit, current, until)) return@forEach
                            if (occurrenceDate.isAfter(endDate)) return@forEach
                            addOccurrenceIfNeeded(event, current, duration, overrides, startDate, endDate, results)
                            generated++
                        }
                        weekStart = weekStart.plusWeeks(1)
                    }
                }
            }
            "MONTHLY" -> {
                var current = event.startAt
                while (!current.toLocalDate().isAfter(endDate) && !isPastLimit(generated, countLimit, current, until)) {
                    addOccurrenceIfNeeded(event, current, duration, overrides, startDate, endDate, results)
                    generated++
                    current = current.plusMonths(interval)
                }
            }
        }

        return results
    }

    private fun addOccurrenceIfNeeded(
        event: ScheduleEvent,
        occurrenceStart: LocalDateTime,
        duration: java.time.Duration,
        overrides: Map<String, ScheduleEvent>,
        startDate: LocalDate,
        endDate: LocalDate,
        output: MutableList<ScheduleOccurrence>
    ) {
        if (event.exDateList.any { it == occurrenceStart }) return
        val override = event.sourceEventUid?.let { overrides[overrideKey(it, occurrenceStart)] }
        val occurrence = if (override != null) {
            override.toOccurrence(override.startAt, override.endAt)
        } else {
            event.toOccurrence(occurrenceStart, occurrenceStart.plus(duration))
        }
        if (overlapsRange(occurrence, startDate, endDate)) {
            output += occurrence
        }
    }

    private fun overlapsRange(
        occurrence: ScheduleOccurrence,
        startDate: LocalDate,
        endDate: LocalDate
    ): Boolean {
        val occurrenceStart = occurrence.startAt.toLocalDate()
        val occurrenceEnd = occurrence.endAt.toLocalDate()
        return !occurrenceEnd.isBefore(startDate) && !occurrenceStart.isAfter(endDate)
    }

    private fun overlapsDate(occurrence: ScheduleOccurrence, date: LocalDate): Boolean {
        return !occurrence.endAt.toLocalDate().isBefore(date) && !occurrence.startAt.toLocalDate().isAfter(date)
    }

    private fun parseRecurrenceRule(rrule: String?): Map<String, String> {
        return rrule
            ?.split(";")
            ?.mapNotNull { part ->
                val pieces = part.split("=")
                if (pieces.size == 2) pieces[0] to pieces[1] else null
            }
            ?.toMap()
            .orEmpty()
    }

    private fun parseDayOfWeek(value: String): DayOfWeek? {
        return when (value.uppercase()) {
            "MO" -> DayOfWeek.MONDAY
            "TU" -> DayOfWeek.TUESDAY
            "WE" -> DayOfWeek.WEDNESDAY
            "TH" -> DayOfWeek.THURSDAY
            "FR" -> DayOfWeek.FRIDAY
            "SA" -> DayOfWeek.SATURDAY
            "SU" -> DayOfWeek.SUNDAY
            else -> null
        }
    }

    private fun isPastLimit(
        generated: Int,
        countLimit: Int?,
        current: LocalDateTime,
        until: LocalDateTime?
    ): Boolean {
        return (countLimit != null && generated >= countLimit) || (until != null && current.isAfter(until))
    }

    private fun ScheduleEvent.toOccurrence(
        occurrenceStart: LocalDateTime,
        occurrenceEnd: LocalDateTime
    ): ScheduleOccurrence {
        return ScheduleOccurrence(
            occurrenceId = "${id}_${occurrenceStart.format(formatter)}",
            eventId = id,
            title = title,
            description = description,
            location = location,
            startAt = occurrenceStart,
            endAt = occurrenceEnd,
            allDay = allDay,
            sourceType = sourceType
        )
    }

    private fun compositeKey(event: ScheduleEvent): String {
        return listOf(
            event.sourceEventUid.orEmpty(),
            event.recurrenceId?.format(formatter).orEmpty()
        ).joinToString("#")
    }

    private fun overrideKey(uid: String, recurrenceId: LocalDateTime): String {
        return listOf(uid, recurrenceId.format(formatter)).joinToString("#")
    }

    private fun serializeScheduleEvents(items: List<ScheduleEvent>): String {
        return items.joinToString("|||") { item ->
            listOf(
                Uri.encode(item.id),
                Uri.encode(item.title),
                Uri.encode(item.description),
                Uri.encode(item.location),
                item.startAt.format(formatter),
                item.endAt.format(formatter),
                item.allDay.toString(),
                Uri.encode(item.timezoneId ?: ""),
                item.sourceType.name,
                Uri.encode(item.sourceCalendarId ?: ""),
                Uri.encode(item.sourceEventUid ?: ""),
                Uri.encode(item.recurrenceRule ?: ""),
                item.recurrenceId?.format(formatter) ?: "",
                Uri.encode(item.exDateList.joinToString("," ) { ex -> ex.format(formatter) }),
                item.lastImportedAt?.toString() ?: "",
                item.updatedAt.toString()
            ).joinToString(":::")
        }
    }

    private fun parseScheduleEvents(raw: String): List<ScheduleEvent> {
        if (raw.isBlank()) return emptyList()
        return raw.split("|||").mapNotNull { itemStr ->
            val parts = itemStr.split(":::")
            if (parts.size < 16) return@mapNotNull null
            val sourceType = runCatching { ScheduleSourceType.valueOf(parts[8]) }.getOrDefault(ScheduleSourceType.MANUAL)
            fun decodeText(index: Int): String {
                val decoded = Uri.decode(parts[index])
                return if (sourceType == ScheduleSourceType.ICS_IMPORT) unescapeIcsText(decoded) else decoded
            }
            ScheduleEvent(
                id = Uri.decode(parts[0]),
                title = decodeText(1),
                description = decodeText(2),
                location = decodeText(3),
                startAt = LocalDateTime.parse(parts[4], formatter),
                endAt = LocalDateTime.parse(parts[5], formatter),
                allDay = parts[6].toBoolean(),
                timezoneId = Uri.decode(parts[7]).ifBlank { null },
                sourceType = sourceType,
                sourceCalendarId = Uri.decode(parts[9]).ifBlank { null },
                sourceEventUid = Uri.decode(parts[10]).ifBlank { null },
                recurrenceRule = Uri.decode(parts[11]).ifBlank { null },
                recurrenceId = parts[12].ifBlank { null }?.let { LocalDateTime.parse(it, formatter) },
                exDateList = Uri.decode(parts[13]).ifBlank { "" }
                    .split(",")
                    .filter { it.isNotBlank() }
                    .map { LocalDateTime.parse(it, formatter) },
                lastImportedAt = parts[14].ifBlank { null }?.toLongOrNull(),
                updatedAt = parts[15].toLongOrNull() ?: System.currentTimeMillis()
            )
        }
    }

    companion object {
        fun parseIcsDateTime(raw: String, tzid: String?): Pair<LocalDateTime, Boolean> {
            val cleaned = raw.trim()
            return when {
                cleaned.length == 8 -> {
                    val date = LocalDate.parse(cleaned, DateTimeFormatter.BASIC_ISO_DATE)
                    LocalDateTime.of(date, LocalTime.MIDNIGHT) to true
                }
                cleaned.endsWith("Z") -> {
                    val utc = java.time.ZonedDateTime.parse(cleaned, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX"))
                    utc.withZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDateTime() to false
                }
                else -> {
                    val value = LocalDateTime.parse(cleaned, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
                    if (tzid.isNullOrBlank()) {
                        value to false
                    } else {
                        val zoned = value.atZone(java.time.ZoneId.of(tzid))
                            .withZoneSameInstant(java.time.ZoneId.systemDefault())
                            .toLocalDateTime()
                        zoned to false
                    }
                }
            }
        }
    }
}
