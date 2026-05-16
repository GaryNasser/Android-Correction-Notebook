package com.github.garynasser.correction_notebook.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.garynasser.correction_notebook.data.model.yanhe.CourseNote
import com.github.garynasser.correction_notebook.data.model.yanhe.CourseProgress
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.courseLearningDataStore: DataStore<Preferences> by preferencesDataStore("course_learning_prefs")

class CourseLearningRepository(private val context: Context) {
    private val progressKey = stringPreferencesKey("course_progress")
    private val notesKey = stringPreferencesKey("course_notes")

    val progressItems: Flow<List<CourseProgress>> = context.courseLearningDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[progressKey]?.let(::parseProgressItems).orEmpty() }

    val notes: Flow<List<CourseNote>> = context.courseLearningDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[notesKey]?.let(::parseNotes).orEmpty() }

    suspend fun getRecentProgress(limit: Int = 3): List<CourseProgress> {
        return progressItems.first()
            .sortedByDescending { it.lastAccessedAt }
            .take(limit)
    }

    suspend fun getProgressForCourse(courseId: Int): CourseProgress? {
        return progressItems.first().firstOrNull { it.courseId == courseId }
    }

    suspend fun recordWatch(
        courseId: Int,
        courseName: String,
        sectionId: Int,
        sectionTitle: String,
        videoUrl: String,
        totalSections: Int
    ) {
        context.courseLearningDataStore.edit { prefs ->
            val current = prefs[progressKey]?.let(::parseProgressItems).orEmpty()
            val existing = current.firstOrNull { it.courseId == courseId }
            val updatedItem = (existing ?: CourseProgress(courseId = courseId)).copy(
                courseName = courseName.ifBlank { existing?.courseName.orEmpty() },
                lastSectionId = sectionId,
                lastSectionTitle = sectionTitle,
                lastVideoUrl = videoUrl,
                totalSections = maxOf(totalSections, existing?.totalSections ?: 0),
                lastAccessedAt = System.currentTimeMillis()
            )
            prefs[progressKey] = serializeProgressItems(current.upsert(updatedItem) { it.courseId == updatedItem.courseId })
        }
    }

    suspend fun setSectionCompleted(
        courseId: Int,
        courseName: String,
        sectionId: Int,
        sectionTitle: String,
        totalSections: Int,
        completed: Boolean
    ) {
        context.courseLearningDataStore.edit { prefs ->
            val current = prefs[progressKey]?.let(::parseProgressItems).orEmpty()
            val existing = current.firstOrNull { it.courseId == courseId }
            val completedIds = existing?.completedSectionIds.orEmpty().toMutableSet().apply {
                if (completed) add(sectionId) else remove(sectionId)
            }
            val updatedItem = (existing ?: CourseProgress(courseId = courseId)).copy(
                courseName = courseName.ifBlank { existing?.courseName.orEmpty() },
                lastSectionId = sectionId,
                lastSectionTitle = sectionTitle,
                completedSectionIds = completedIds,
                totalSections = maxOf(totalSections, existing?.totalSections ?: 0),
                lastAccessedAt = System.currentTimeMillis()
            )
            prefs[progressKey] = serializeProgressItems(current.upsert(updatedItem) { it.courseId == updatedItem.courseId })
        }
    }

    suspend fun saveNote(note: CourseNote) {
        context.courseLearningDataStore.edit { prefs ->
            val current = prefs[notesKey]?.let(::parseNotes).orEmpty()
            prefs[notesKey] = serializeNotes((current + note).sortedByDescending { it.createdAt }.take(200))
        }
    }

    suspend fun getNotesForCourse(courseId: Int): List<CourseNote> {
        return notes.first().filter { it.courseId == courseId }.sortedByDescending { it.createdAt }
    }

    private fun serializeProgressItems(items: List<CourseProgress>): String {
        return items.joinToString("|||") { item ->
            listOf(
                item.courseId.toString(),
                item.courseName,
                item.lastSectionId.toString(),
                item.lastSectionTitle,
                item.lastVideoUrl,
                item.completedSectionIds.joinToString(","),
                item.totalSections.toString(),
                item.watchedMinutes.toString(),
                item.lastAccessedAt.toString()
            ).joinToString(":::")
        }
    }

    private fun parseProgressItems(raw: String): List<CourseProgress> {
        if (raw.isBlank()) return emptyList()
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

    private fun serializeNotes(items: List<CourseNote>): String {
        return items.joinToString("|||") { item ->
            listOf(
                item.id,
                item.courseId.toString(),
                item.courseName,
                item.sectionId.toString(),
                item.sectionTitle,
                item.content,
                item.aiGenerated.toString(),
                item.createdAt.toString()
            ).joinToString(":::")
        }
    }

    private fun parseNotes(raw: String): List<CourseNote> {
        if (raw.isBlank()) return emptyList()
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

    private fun <T> List<T>.upsert(item: T, same: (T) -> Boolean): List<T> {
        return if (any(same)) map { if (same(it)) item else it } else this + item
    }
}
