package com.github.garynasser.correction_notebook.data.repository

import android.content.Context
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters

private val Context.scheduleDataStore: DataStore<Preferences> by preferencesDataStore("schedule_prefs")

class ScheduleRepository internal constructor(private val dataStore: DataStore<Preferences>) {
    constructor(context: Context) : this(context.scheduleDataStore)

    private val scheduleEventsKey = stringPreferencesKey("schedule_events")

    val scheduleEvents: Flow<List<ScheduleEvent>> = dataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            prefs[scheduleEventsKey]?.let(SchedulePreferenceCodec::parseEvents) ?: emptyList()
        }

    suspend fun addEvent(event: ScheduleEvent) {
        dataStore.edit { prefs ->
            val current = prefs[scheduleEventsKey]?.let(SchedulePreferenceCodec::parseEvents) ?: emptyList()
            prefs[scheduleEventsKey] = SchedulePreferenceCodec.serializeEvents(current + event)
        }
    }

    suspend fun updateEvent(event: ScheduleEvent) {
        dataStore.edit { prefs ->
            val current = prefs[scheduleEventsKey]?.let(SchedulePreferenceCodec::parseEvents) ?: emptyList()
            prefs[scheduleEventsKey] = SchedulePreferenceCodec.serializeEvents(
                current.map { if (it.id == event.id) event.copy(updatedAt = System.currentTimeMillis()) else it }
            )
        }
    }

    suspend fun deleteEvent(eventId: String) {
        dataStore.edit { prefs ->
            val current = prefs[scheduleEventsKey]?.let(SchedulePreferenceCodec::parseEvents) ?: emptyList()
            prefs[scheduleEventsKey] = SchedulePreferenceCodec.serializeEvents(current.filterNot { it.id == eventId })
        }
    }

    suspend fun getEventById(eventId: String): ScheduleEvent? {
        return scheduleEvents.first().firstOrNull { it.id == eventId }
    }

    suspend fun getEventsForRange(range: ScheduleRange, today: LocalDate = LocalDate.now()): List<ScheduleSection> = withContext(Dispatchers.Default) {
        val (startDate, endDate) = scheduleDateRange(range, today)
        val occurrences = buildScheduleOccurrences(scheduleEvents.first(), startDate, endDate)
        (0..java.time.temporal.ChronoUnit.DAYS.between(startDate, endDate).toInt()).map { offset ->
            val date = startDate.plusDays(offset.toLong())
            val items = occurrences
                .filter { occurrence -> scheduleOccurrenceOverlapsDate(occurrence, date) }
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
        dataStore.edit { prefs ->
            val current = prefs[scheduleEventsKey]?.let(SchedulePreferenceCodec::parseEvents) ?: emptyList()
            prefs[scheduleEventsKey] = SchedulePreferenceCodec.serializeEvents(
                applyIcsImport(current, preview, decision)
            )
        }
    }

    suspend fun applySchoolSchedule(termId: String, events: List<ScheduleEvent>) {
        val calendarId = schoolCalendarId(termId)
        val importedAt = System.currentTimeMillis()
        dataStore.edit { prefs ->
            val current = prefs[scheduleEventsKey]?.let(SchedulePreferenceCodec::parseEvents) ?: emptyList()
            val retained = current.filterNot {
                it.sourceType == ScheduleSourceType.SCHOOL_IMPORT &&
                    it.sourceCalendarId == calendarId
            }
            val normalizedEvents = events.map { event ->
                event.copy(
                    sourceType = ScheduleSourceType.SCHOOL_IMPORT,
                    sourceCalendarId = calendarId,
                    lastImportedAt = importedAt,
                    updatedAt = importedAt
                )
            }
            prefs[scheduleEventsKey] = SchedulePreferenceCodec.serializeEvents(retained + normalizedEvents)
        }
    }

    suspend fun getImportedEventsForCalendar(calendarId: String): List<ScheduleEvent> {
        return scheduleEvents.first().filter {
            it.sourceType == ScheduleSourceType.ICS_IMPORT && it.sourceCalendarId == calendarId
        }
    }

    suspend fun getImportedEvents(): List<ScheduleEvent> {
        return scheduleEvents.first().filter { it.sourceType == ScheduleSourceType.ICS_IMPORT }
    }

    companion object {
        fun schoolCalendarId(termId: String): String = "school_${termId.trim()}"

        fun parseIcsDateTime(raw: String, tzid: String?): Pair<LocalDateTime, Boolean> {
            val cleaned = raw.trim()
            return when {
                cleaned.length == 8 -> {
                    val date = LocalDate.parse(cleaned, DateTimeFormatter.BASIC_ISO_DATE)
                    LocalDateTime.of(date, LocalTime.MIDNIGHT) to true
                }
                cleaned.endsWith("Z") -> {
                    val utc = listOf(
                        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX"),
                        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmX")
                    ).firstNotNullOfOrNull { formatter ->
                        runCatching { java.time.ZonedDateTime.parse(cleaned, formatter) }.getOrNull()
                    } ?: throw IllegalArgumentException("Unsupported ICS date-time: $raw")
                    utc.withZoneSameInstant(java.time.ZoneId.systemDefault()).toLocalDateTime() to false
                }
                else -> {
                    val value = listOf(
                        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"),
                        DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm")
                    ).firstNotNullOfOrNull { formatter ->
                        runCatching { LocalDateTime.parse(cleaned, formatter) }.getOrNull()
                    } ?: throw IllegalArgumentException("Unsupported ICS date-time: $raw")
                    if (tzid.isNullOrBlank()) {
                        value to false
                    } else {
                        val zoned = value.atZone(java.time.ZoneId.of(tzid.trim('"')))
                            .withZoneSameInstant(java.time.ZoneId.systemDefault())
                            .toLocalDateTime()
                        zoned to false
                    }
                }
            }
        }
    }
}

internal fun scheduleDateRange(range: ScheduleRange, selectedDate: LocalDate): Pair<LocalDate, LocalDate> {
    return when (range) {
        ScheduleRange.TODAY -> selectedDate to selectedDate
        ScheduleRange.TOMORROW -> selectedDate.plusDays(1) to selectedDate.plusDays(1)
        ScheduleRange.WEEK -> {
            val monday = selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            monday to monday.plusDays(6)
        }
    }
}

internal fun icsEventCompositeKey(event: ScheduleEvent): String {
    val recurrenceId = event.recurrenceId?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME).orEmpty()
    val uid = event.sourceEventUid?.trim().orEmpty()
    if (uid.isNotEmpty()) return "uid#$uid#$recurrenceId"

    return listOf(
        "fallback",
        event.title.trim(),
        event.startAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
        event.endAt.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
        recurrenceId
    ).joinToString("#")
}

internal fun applyIcsImport(
    current: List<ScheduleEvent>,
    preview: IcsImportPreview,
    decision: ImportDecision
): List<ScheduleEvent> {
    val sourceIds = preview.replacedCalendarIds + preview.sourceCalendarId
    if (decision == ImportDecision.OVERWRITE) {
        return current.filterNot {
            it.sourceType == ScheduleSourceType.ICS_IMPORT && it.sourceCalendarId in sourceIds
        } + preview.incomingEvents
    }

    val incomingKeys = preview.incomingEvents.map(::icsEventCompositeKey).toSet()
    val locallyEditedKeys = current
        .filter {
            it.sourceType == ScheduleSourceType.ICS_IMPORT &&
                it.sourceCalendarId in sourceIds &&
                it.lastImportedAt != null &&
                it.updatedAt > it.lastImportedAt
        }
        .map(::icsEventCompositeKey)
        .toSet()
    val retained = current.filterNot { event ->
        event.sourceType == ScheduleSourceType.ICS_IMPORT &&
            event.sourceCalendarId in sourceIds &&
            icsEventCompositeKey(event) in incomingKeys &&
            icsEventCompositeKey(event) !in locallyEditedKeys
    }
    return retained + preview.incomingEvents.filterNot {
        icsEventCompositeKey(it) in locallyEditedKeys
    }
}

internal fun scheduleOccurrenceOverlapsDate(
    occurrence: ScheduleOccurrence,
    date: LocalDate
): Boolean {
    val dayStart = date.atStartOfDay()
    val nextDayStart = date.plusDays(1).atStartOfDay()
    return occurrence.startAt < nextDayStart && occurrence.endAt > dayStart
}

internal fun scheduleOccurrenceOverlapsRange(
    occurrence: ScheduleOccurrence,
    startDate: LocalDate,
    endDate: LocalDate
): Boolean {
    if (endDate.isBefore(startDate)) return false
    val rangeStart = startDate.atStartOfDay()
    val rangeEndExclusive = endDate.plusDays(1).atStartOfDay()
    return occurrence.startAt < rangeEndExclusive && occurrence.endAt > rangeStart
}
