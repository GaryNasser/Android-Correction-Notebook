package com.github.garynasser.correction_notebook.data.model.home

import com.google.gson.JsonObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

data class DeadlineItem(
    val id: String,
    val activityId: Long,
    val courseId: Long,
    val courseName: String,
    val title: String,
    val kind: String,
    val deadline: Long?,
    val startTime: Long?,
    val submitted: Boolean?,
    val closed: Boolean,
    val description: String = ""
) {
    val platformUrl: String get() = "https://zy-eclass.bit.edu.cn/course/$courseId/learning-activity"
}

internal fun parseDeadline(
    row: JsonObject, courseId: Long, courseName: String, kind: String
): DeadlineItem {
    fun text(key: String) = row.get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    val id = text("id").toLongOrNull()?.takeIf { it > 0 } ?: error("学习平台任务编号无效")
    val title = text("title").trim().ifBlank { error("学习平台任务名称为空") }
    val count = text("user_submit_count").toIntOrNull()
    return DeadlineItem(
        id = "$kind-$id", activityId = id, courseId = courseId, courseName = courseName,
        title = title, kind = kind,
        deadline = parseDeadlineTime(text("end_time").ifBlank { text("deadline") }),
        startTime = parseDeadlineTime(text("start_time")),
        submitted = count?.let { it > 0 }, closed = text("is_closed") == "true",
        description = row.get("data")?.takeIf { it.isJsonObject }?.asJsonObject
            ?.get("description")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    )
}

internal fun parseDeadlineTime(value: String): Long? {
    if (value.isBlank()) return null
    return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
        ?: runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }.getOrNull()
        ?: runCatching { LocalDateTime.parse(value.replace(' ', 'T')).atZone(ZoneId.of("Asia/Shanghai")).toInstant().toEpochMilli() }.getOrNull()
        ?: throw IllegalArgumentException("学习平台截止时间格式异常")
}
