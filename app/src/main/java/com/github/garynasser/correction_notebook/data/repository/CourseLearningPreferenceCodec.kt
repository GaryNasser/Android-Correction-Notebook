package com.github.garynasser.correction_notebook.data.repository

import com.github.garynasser.correction_notebook.data.model.yanhe.CourseNote
import com.github.garynasser.correction_notebook.data.model.yanhe.CourseProgress
import com.google.gson.Gson

internal object CourseLearningPreferenceCodec {
    private val gson = Gson()

    fun serializeProgressItems(items: List<CourseProgress>): String {
        return gson.toJson(items.map(CourseProgressDto::from))
    }

    fun parseProgressItems(raw: String): List<CourseProgress> {
        if (raw.isBlank()) return emptyList()
        return parseJsonArray(raw, Array<CourseProgressDto>::class.java)
            ?.mapNotNull(CourseProgressDto::toModel)
            ?: parseLegacyProgressItems(raw)
    }

    fun serializeNotes(items: List<CourseNote>): String {
        return gson.toJson(items.map(CourseNoteDto::from))
    }

    fun parseNotes(raw: String): List<CourseNote> {
        if (raw.isBlank()) return emptyList()
        return parseJsonArray(raw, Array<CourseNoteDto>::class.java)
            ?.mapNotNull(CourseNoteDto::toModel)
            ?: parseLegacyNotes(raw)
    }

    private fun <T> parseJsonArray(raw: String, type: Class<Array<T>>): List<T>? {
        if (!raw.trimStart().startsWith("[")) return null
        return runCatching { gson.fromJson(raw, type).toList() }.getOrNull()
    }

    private fun parseLegacyProgressItems(raw: String): List<CourseProgress> {
        return raw.split("|||").mapNotNull { row ->
            val parts = row.split(":::")
            if (parts.size < 9) return@mapNotNull null
            CourseProgress(
                courseId = parts[0].toIntOrNull() ?: return@mapNotNull null,
                courseName = parts[1],
                lastSectionId = parts[2].toIntOrNull() ?: 0,
                lastSectionTitle = parts[3],
                lastVideoUrl = parts[4],
                completedSectionIds = parts[5].split(",").mapNotNull { it.toIntOrNull() }.toSet(),
                totalSections = parts[6].toIntOrNull() ?: 0,
                watchedMinutes = parts[7].toIntOrNull() ?: 0,
                lastAccessedAt = parts[8].toLongOrNull() ?: System.currentTimeMillis()
            )
        }
    }

    private fun parseLegacyNotes(raw: String): List<CourseNote> {
        return raw.split("|||").mapNotNull { row ->
            val parts = row.split(":::")
            if (parts.size < 8) return@mapNotNull null
            CourseNote(
                id = parts[0],
                courseId = parts[1].toIntOrNull() ?: return@mapNotNull null,
                courseName = parts[2],
                sectionId = parts[3].toIntOrNull() ?: 0,
                sectionTitle = parts[4],
                content = parts[5],
                aiGenerated = parts[6].toBoolean(),
                createdAt = parts[7].toLongOrNull() ?: System.currentTimeMillis()
            )
        }
    }

    private data class CourseProgressDto(
        val courseId: Int?,
        val courseName: String?,
        val lastSectionId: Int?,
        val lastSectionTitle: String?,
        val lastVideoUrl: String?,
        val completedSectionIds: List<Int>?,
        val totalSections: Int?,
        val watchedMinutes: Int?,
        val lastAccessedAt: Long?
    ) {
        fun toModel(): CourseProgress? {
            return CourseProgress(
                courseId = courseId ?: return null,
                courseName = courseName.orEmpty(),
                lastSectionId = lastSectionId ?: 0,
                lastSectionTitle = lastSectionTitle.orEmpty(),
                lastVideoUrl = lastVideoUrl.orEmpty(),
                completedSectionIds = completedSectionIds.orEmpty().toSet(),
                totalSections = (totalSections ?: 0).coerceAtLeast(0),
                watchedMinutes = (watchedMinutes ?: 0).coerceAtLeast(0),
                lastAccessedAt = lastAccessedAt ?: System.currentTimeMillis()
            )
        }

        companion object {
            fun from(item: CourseProgress): CourseProgressDto = CourseProgressDto(
                courseId = item.courseId,
                courseName = item.courseName,
                lastSectionId = item.lastSectionId,
                lastSectionTitle = item.lastSectionTitle,
                lastVideoUrl = item.lastVideoUrl,
                completedSectionIds = item.completedSectionIds.sorted(),
                totalSections = item.totalSections.coerceAtLeast(0),
                watchedMinutes = item.watchedMinutes.coerceAtLeast(0),
                lastAccessedAt = item.lastAccessedAt
            )
        }
    }

    private data class CourseNoteDto(
        val id: String?,
        val courseId: Int?,
        val courseName: String?,
        val sectionId: Int?,
        val sectionTitle: String?,
        val content: String?,
        val aiGenerated: Boolean?,
        val createdAt: Long?
    ) {
        fun toModel(): CourseNote? {
            return CourseNote(
                id = id?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString(),
                courseId = courseId ?: return null,
                courseName = courseName.orEmpty(),
                sectionId = sectionId ?: 0,
                sectionTitle = sectionTitle.orEmpty(),
                content = content.orEmpty(),
                aiGenerated = aiGenerated ?: false,
                createdAt = createdAt ?: System.currentTimeMillis()
            )
        }

        companion object {
            fun from(item: CourseNote): CourseNoteDto = CourseNoteDto(
                id = item.id,
                courseId = item.courseId,
                courseName = item.courseName,
                sectionId = item.sectionId,
                sectionTitle = item.sectionTitle,
                content = item.content,
                aiGenerated = item.aiGenerated,
                createdAt = item.createdAt
            )
        }
    }
}
