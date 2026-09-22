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
        val sourceCalendarId = buildIcsCalendarId(fileName, unfoldedLines)
        val incomingEvents = parseIcsEvents(unfoldedLines, sourceCalendarId)
            .distinctBy(::icsEventCompositeKey)
        if (incomingEvents.isEmpty()) error("ICS 文件中没有可导入的日程")

        val importedEvents = scheduleRepository.getImportedEvents()
        val replacedCalendarIds = resolveIcsReplacedCalendarIds(
            sourceCalendarId = sourceCalendarId,
            incomingEvents = incomingEvents,
            importedEvents = importedEvents,
            legacyExactCalendarId = buildLegacyIcsCalendarId(fileName, raw)
        )
        val existingEvents = importedEvents.filter { it.sourceCalendarId in replacedCalendarIds }

        val existingByKey = existingEvents.associateBy(::icsEventCompositeKey)
        val incomingByKey = incomingEvents.associateBy(::icsEventCompositeKey)

        val added = mutableListOf<IcsDiffItem>()
        val updated = mutableListOf<IcsDiffItem>()
        val conflicts = mutableListOf<IcsDiffItem>()

        incomingEvents.forEach { incoming ->
            val key = icsEventCompositeKey(incoming)
            val existing = existingByKey[key]
            when {
                existing == null -> added += incoming.toDiff(IcsDiffType.ADDED, "导入后会新增到日程表")
                !sameLogicalContent(existing, incoming) -> {
                    if (existing.lastImportedAt != null && existing.updatedAt > existing.lastImportedAt) {
                        conflicts += incoming.toDiff(IcsDiffType.CONFLICT, "合并时保留本地修改，覆盖时使用导入版本")
                    } else {
                        updated += incoming.toDiff(IcsDiffType.UPDATED, "导入后会更新原有日程")
                    }
                }
            }
        }

        val deleted = existingEvents
            .filter { icsEventCompositeKey(it) !in incomingByKey }
            .map { it.toDiff(IcsDiffType.DELETED, "覆盖模式下会删除该导入日程") }

        return IcsImportPreview(
            fileName = fileName,
            sourceCalendarId = sourceCalendarId,
            replacedCalendarIds = replacedCalendarIds,
            incomingEvents = incomingEvents,
            added = added,
            updated = updated,
            conflicts = conflicts,
            deleted = deleted
        )
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

internal fun parseIcsEvents(
    lines: List<String>,
    sourceCalendarId: String,
    importedAt: Long = System.currentTimeMillis()
): List<ScheduleEvent> {
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
                if (insideEvent) blocks += currentBlock.toList()
                insideEvent = false
            }
            else -> if (insideEvent) currentBlock += line
        }
    }
    if (insideEvent) throw IllegalArgumentException("ICS 文件中的最后一个日程未结束")

    return blocks.mapIndexed { index, block ->
        try {
            parseIcsEventBlock(block, sourceCalendarId, importedAt)
                ?: throw IllegalArgumentException("日程缺少开始时间或结束时间无效")
        } catch (error: IllegalArgumentException) {
            throw IllegalArgumentException("第 ${index + 1} 个日程格式有误：${error.message}", error)
        } catch (error: java.time.DateTimeException) {
            throw IllegalArgumentException("第 ${index + 1} 个日程日期或时区无效", error)
        }
    }
}

private fun parseIcsEventBlock(
    block: List<String>,
    sourceCalendarId: String,
    importedAt: Long
): ScheduleEvent? {
    val fields = mutableMapOf<String, MutableList<Pair<Map<String, String>, String>>>()
    block.forEach { rawLine ->
        val separator = rawLine.indexOf(':')
        if (separator <= 0) return@forEach
        val keyPart = rawLine.substring(0, separator)
        val valuePart = rawLine.substring(separator + 1)
        val keyPieces = keyPart.split(";")
        val name = keyPieces.first().uppercase()
        val params = keyPieces.drop(1).mapNotNull { param ->
            val parts = param.split("=", limit = 2)
            if (parts.size == 2) parts[0].uppercase() to parts[1].trim('"') else null
        }.toMap()
        fields.getOrPut(name) { mutableListOf() }.add(params to valuePart)
    }

    val startField = fields["DTSTART"]?.firstOrNull() ?: return null
    val endField = fields["DTEND"]?.firstOrNull()
    val (startAt, inferredAllDay) = ScheduleRepository.parseIcsDateTime(
        startField.second,
        startField.first["TZID"]
    )
    val (endAt, explicitAllDay) = endField?.let {
        ScheduleRepository.parseIcsDateTime(it.second, it.first["TZID"])
    } ?: (if (inferredAllDay) startAt.plusDays(1) else startAt.plusHours(1)) to inferredAllDay
    if (!endAt.isAfter(startAt)) return null

    val exDates = fields["EXDATE"].orEmpty().flatMap { (params, value) ->
        value.split(",").mapNotNull { item ->
            runCatching { ScheduleRepository.parseIcsDateTime(item, params["TZID"]).first }.getOrNull()
        }
    }
    return ScheduleEvent(
        title = fields["SUMMARY"]?.firstOrNull()?.second
            ?.let(::unescapeIcsText)
            ?.ifBlank { "未命名日程" }
            ?: "未命名日程",
        description = fields["DESCRIPTION"]?.firstOrNull()?.second?.let(::unescapeIcsText).orEmpty(),
        location = fields["LOCATION"]?.firstOrNull()?.second?.let(::unescapeIcsText).orEmpty(),
        startAt = startAt,
        endAt = endAt,
        allDay = inferredAllDay || explicitAllDay,
        timezoneId = startField.first["TZID"],
        sourceType = ScheduleSourceType.ICS_IMPORT,
        sourceCalendarId = sourceCalendarId,
        sourceEventUid = fields["UID"]?.firstOrNull()?.second?.trim()?.takeIf(String::isNotEmpty),
        recurrenceRule = fields["RRULE"]?.firstOrNull()?.second,
        recurrenceId = fields["RECURRENCE-ID"]?.firstOrNull()?.let { (params, value) ->
            ScheduleRepository.parseIcsDateTime(value, params["TZID"]).first
        },
        exDateList = exDates,
        lastImportedAt = importedAt,
        updatedAt = importedAt
    )
}

internal fun buildIcsCalendarId(fileName: String, lines: List<String>): String {
    fun calendarProperty(name: String): String {
        return lines.firstOrNull { line ->
            line.substringBefore(':').substringBefore(';').equals(name, ignoreCase = true)
        }?.substringAfter(':')?.let(::unescapeIcsText).orEmpty()
    }

    val identity = listOf(
        fileName.trim().lowercase(),
        calendarProperty("X-WR-CALNAME").lowercase(),
        calendarProperty("PRODID").lowercase()
    ).joinToString("::")
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(identity.toByteArray())
        .take(16)
        .joinToString("") { "%02x".format(it) }
    return "ics_v2_$digest"
}

internal fun buildLegacyIcsCalendarId(fileName: String, raw: String): String {
    val digest = MessageDigest.getInstance("MD5")
        .digest("$fileName::$raw".toByteArray())
        .joinToString("") { "%02x".format(it) }
    return "ics_$digest"
}

internal fun resolveIcsReplacedCalendarIds(
    sourceCalendarId: String,
    incomingEvents: List<ScheduleEvent>,
    importedEvents: List<ScheduleEvent>,
    legacyExactCalendarId: String? = null
): Set<String> {
    val incomingUids = incomingEvents
        .mapNotNull { event -> event.sourceEventUid?.trim()?.takeIf(String::isNotEmpty) }
        .toSet()
    val legacyId = Regex("ics_[a-f0-9]{32}")
    val matchingLegacyIds = importedEvents
        .groupBy { it.sourceCalendarId }
        .filter { (id, events) ->
            id != null && legacyId.matches(id) &&
                events.mapNotNull { it.sourceEventUid?.trim()?.takeIf(String::isNotEmpty) }
                    .toSet()
                    .let { it.isNotEmpty() && incomingUids.containsAll(it) }
        }
        .keys
        .filterNotNull()
    return matchingLegacyIds.toSet() + sourceCalendarId + listOfNotNull(legacyExactCalendarId)
}
