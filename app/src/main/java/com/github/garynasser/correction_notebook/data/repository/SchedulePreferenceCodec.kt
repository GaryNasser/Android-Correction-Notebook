package com.github.garynasser.correction_notebook.data.repository

import android.net.Uri
import com.github.garynasser.correction_notebook.data.model.home.ScheduleEvent
import com.github.garynasser.correction_notebook.data.model.home.ScheduleSourceType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal object SchedulePreferenceCodec {
    private val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME
    private const val DECODED_TEXT_FORMAT = "decoded_text_v2"

    fun serializeEvents(items: List<ScheduleEvent>): String {
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
                Uri.encode(item.exDateList.joinToString(",") { it.format(formatter) }),
                item.lastImportedAt?.toString() ?: "",
                item.updatedAt.toString(),
                DECODED_TEXT_FORMAT
            ).joinToString(":::")
        }
    }

    fun parseEvents(raw: String): List<ScheduleEvent> {
        if (raw.isBlank()) return emptyList()
        return raw.split("|||").mapNotNull { parseEventParts(it.split(":::")) }
    }

    private fun parseEventParts(parts: List<String>): ScheduleEvent? {
        if (parts.size < 16) return null
        return runCatching {
            val sourceType = runCatching { ScheduleSourceType.valueOf(parts[8]) }
                .getOrDefault(ScheduleSourceType.MANUAL)
            // Legacy imports stored raw ICS text; current imports have already unescaped it.
            val legacyIcsText = sourceType == ScheduleSourceType.ICS_IMPORT &&
                parts.getOrNull(16) != DECODED_TEXT_FORMAT
            fun decodeText(index: Int): String {
                val decoded = Uri.decode(parts[index])
                return if (legacyIcsText) unescapeIcsText(decoded) else decoded
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
                exDateList = Uri.decode(parts[13]).split(",")
                    .filter { it.isNotBlank() }
                    .map { LocalDateTime.parse(it, formatter) },
                lastImportedAt = parts[14].ifBlank { null }?.toLongOrNull(),
                updatedAt = parts[15].toLongOrNull() ?: System.currentTimeMillis()
            )
        }.getOrNull()
    }
}
