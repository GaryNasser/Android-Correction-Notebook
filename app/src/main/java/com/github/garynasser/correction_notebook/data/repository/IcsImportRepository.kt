package com.github.garynasser.correction_notebook.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.github.garynasser.correction_notebook.data.model.home.IcsDiffItem
import com.github.garynasser.correction_notebook.data.model.home.IcsDiffType
import com.github.garynasser.correction_notebook.data.model.home.IcsImportPreview
import com.github.garynasser.correction_notebook.data.model.home.ScheduleEvent
import com.github.garynasser.correction_notebook.data.model.home.ScheduleSourceType
import java.security.MessageDigest
import java.time.LocalDateTime

class IcsImportRepository(
    private val context: Context,
    private val scheduleRepository: ScheduleRepository
) {

    suspend fun buildPreview(uri: Uri): IcsImportPreview {
        val fileName = queryDisplayName(uri) ?: "calendar.ics"
        val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            ?: error("无法读取 ICS 文件")
        val unfoldedLines = unfoldLines(raw)
        val sourceCalendarId = buildCalendarId(fileName, raw)
        val incomingEvents = parseEvents(unfoldedLines, sourceCalendarId)
        val existingEvents = scheduleRepository.getImportedEventsForCalendar(sourceCalendarId)

        val existingByKey = existingEvents.associateBy(::compositeKey)
        val incomingByKey = incomingEvents.associateBy(::compositeKey)

        val added = mutableListOf<IcsDiffItem>()
        val updated = mutableListOf<IcsDiffItem>()
        val conflicts = mutableListOf<IcsDiffItem>()

        incomingEvents.forEach { incoming ->
            val key = compositeKey(incoming)
            val existing = existingByKey[key]
            when {
                existing == null -> added += incoming.toDiff(IcsDiffType.ADDED, "导入后会新增到日程表")
                !sameLogicalContent(existing, incoming) -> {
                    if (existing.lastImportedAt != null && existing.updatedAt > existing.lastImportedAt) {
                        conflicts += incoming.toDiff(IcsDiffType.CONFLICT, "本地已有修改，导入后可能覆盖")
                    } else {
                        updated += incoming.toDiff(IcsDiffType.UPDATED, "导入后会更新原有日程")
                    }
                }
            }
        }

        val deleted = existingEvents
            .filter { compositeKey(it) !in incomingByKey }
            .map { it.toDiff(IcsDiffType.DELETED, "覆盖模式下会删除该导入日程") }

        return IcsImportPreview(
            fileName = fileName,
            sourceCalendarId = sourceCalendarId,
            incomingEvents = incomingEvents,
            added = added,
            updated = updated,
            conflicts = conflicts,
            deleted = deleted
        )
    }

    private fun parseEvents(lines: List<String>, sourceCalendarId: String): List<ScheduleEvent> {
        val blocks = mutableListOf<List<String>>()
        var currentBlock = mutableListOf<String>()
        var insideEvent = false
        lines.forEach { line ->
            when (line) {
                "BEGIN:VEVENT" -> {
                    insideEvent = true
                    currentBlock = mutableListOf()
                }
                "END:VEVENT" -> {
                    if (insideEvent) {
                        blocks += currentBlock.toList()
                    }
                    insideEvent = false
                }
                else -> if (insideEvent) currentBlock += line
            }
        }

        val importedAt = System.currentTimeMillis()
        return blocks.mapNotNull { block ->
            val fields = mutableMapOf<String, MutableList<Pair<Map<String, String>, String>>>()
            block.forEach { rawLine ->
                val separator = rawLine.indexOf(':')
                if (separator <= 0) return@forEach
                val keyPart = rawLine.substring(0, separator)
                val valuePart = rawLine.substring(separator + 1)
                val keyPieces = keyPart.split(";")
                val name = keyPieces.first().uppercase()
                val params = keyPieces.drop(1).mapNotNull { param ->
                    val parts = param.split("=")
                    if (parts.size == 2) parts[0].uppercase() to parts[1] else null
                }.toMap()
                fields.getOrPut(name) { mutableListOf() }.add(params to valuePart)
            }

            val startField = fields["DTSTART"]?.firstOrNull() ?: return@mapNotNull null
            val endField = fields["DTEND"]?.firstOrNull()
            val (startAt, inferredAllDay) = ScheduleRepository.parseIcsDateTime(startField.second, startField.first["TZID"])
            val (endAt, explicitAllDay) = endField?.let {
                ScheduleRepository.parseIcsDateTime(it.second, it.first["TZID"])
            } ?: (if (inferredAllDay) startAt.plusDays(1) else startAt.plusHours(1)) to inferredAllDay
            val allDay = inferredAllDay || explicitAllDay
            val exDates = fields["EXDATE"].orEmpty().flatMap { (params, value) ->
                value.split(",").mapNotNull { item ->
                    runCatching { ScheduleRepository.parseIcsDateTime(item, params["TZID"]).first }.getOrNull()
                }
            }
            ScheduleEvent(
                title = fields["SUMMARY"]?.firstOrNull()?.second?.let(::unescapeIcsText)?.ifBlank { "未命名日程" } ?: "未命名日程",
                description = fields["DESCRIPTION"]?.firstOrNull()?.second?.let(::unescapeIcsText).orEmpty(),
                location = fields["LOCATION"]?.firstOrNull()?.second?.let(::unescapeIcsText).orEmpty(),
                startAt = startAt,
                endAt = endAt,
                allDay = allDay,
                timezoneId = startField.first["TZID"],
                sourceType = ScheduleSourceType.ICS_IMPORT,
                sourceCalendarId = sourceCalendarId,
                sourceEventUid = fields["UID"]?.firstOrNull()?.second,
                recurrenceRule = fields["RRULE"]?.firstOrNull()?.second,
                recurrenceId = fields["RECURRENCE-ID"]?.firstOrNull()?.let { (params, value) ->
                    ScheduleRepository.parseIcsDateTime(value, params["TZID"]).first
                },
                exDateList = exDates,
                lastImportedAt = importedAt,
                updatedAt = importedAt
            )
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (index >= 0 && cursor.moveToFirst()) cursor.getString(index) else null
        }
    }

    private fun unfoldLines(raw: String): List<String> {
        val result = mutableListOf<String>()
        raw.replace("\r\n", "\n").split('\n').forEach { line ->
            if (line.startsWith(" ") || line.startsWith("\t")) {
                if (result.isNotEmpty()) {
                    result[result.lastIndex] = result.last() + line.trimStart()
                }
            } else {
                result += line.trim()
            }
        }
        return result.filter { it.isNotBlank() }
    }

    private fun buildCalendarId(fileName: String, raw: String): String {
        val digest = MessageDigest.getInstance("MD5")
            .digest("$fileName::$raw".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "ics_$digest"
    }

    private fun compositeKey(event: ScheduleEvent): String {
        return listOf(
            event.sourceEventUid.orEmpty(),
            event.recurrenceId?.toString().orEmpty()
        ).joinToString("#")
    }

    private fun sameLogicalContent(lhs: ScheduleEvent, rhs: ScheduleEvent): Boolean {
        return lhs.title == rhs.title &&
            lhs.description == rhs.description &&
            lhs.location == rhs.location &&
            lhs.startAt == rhs.startAt &&
            lhs.endAt == rhs.endAt &&
            lhs.allDay == rhs.allDay &&
            lhs.recurrenceRule == rhs.recurrenceRule &&
            lhs.exDateList == rhs.exDateList
    }

    private fun ScheduleEvent.toDiff(type: IcsDiffType, detail: String): IcsDiffItem {
        return IcsDiffItem(
            type = type,
            title = title,
            startsAt = startAt,
            detail = detail
        )
    }
}

internal fun unescapeIcsText(raw: String): String {
    val builder = StringBuilder(raw.length)
    var index = 0
    while (index < raw.length) {
        val current = raw[index]
        if (current == '\\' && index + 1 < raw.length) {
            when (val next = raw[index + 1]) {
                'n', 'N' -> builder.append('\n')
                ',', ';', '\\' -> builder.append(next)
                else -> {
                    builder.append(current)
                    builder.append(next)
                }
            }
            index += 2
        } else {
            builder.append(current)
            index += 1
        }
    }
    return builder.toString().trim()
}
