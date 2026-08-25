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
        .map { prefs -> prefs[progressKey]?.let(CourseLearningPreferenceCodec::parseProgressItems).orEmpty() }

    val notes: Flow<List<CourseNote>> = context.courseLearningDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs -> prefs[notesKey]?.let(CourseLearningPreferenceCodec::parseNotes).orEmpty() }

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
            val current = prefs[progressKey]?.let(CourseLearningPreferenceCodec::parseProgressItems).orEmpty()
            val existing = current.firstOrNull { it.courseId == courseId }
            val updatedItem = (existing ?: CourseProgress(courseId = courseId)).copy(
                courseName = courseName.ifBlank { existing?.courseName.orEmpty() },
                lastSectionId = sectionId,
                lastSectionTitle = sectionTitle,
                lastVideoUrl = videoUrl,
                totalSections = maxOf(totalSections, existing?.totalSections ?: 0),
                lastAccessedAt = System.currentTimeMillis()
            )
            prefs[progressKey] = CourseLearningPreferenceCodec.serializeProgressItems(
                current.upsert(updatedItem) { it.courseId == updatedItem.courseId }
            )
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
            val current = prefs[progressKey]?.let(CourseLearningPreferenceCodec::parseProgressItems).orEmpty()
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
            prefs[progressKey] = CourseLearningPreferenceCodec.serializeProgressItems(
                current.upsert(updatedItem) { it.courseId == updatedItem.courseId }
            )
        }
    }

    suspend fun saveNote(note: CourseNote) {
        context.courseLearningDataStore.edit { prefs ->
            val current = prefs[notesKey]?.let(CourseLearningPreferenceCodec::parseNotes).orEmpty()
            prefs[notesKey] = CourseLearningPreferenceCodec.serializeNotes(
                (current + note).sortedByDescending { it.createdAt }.take(200)
            )
        }
    }

    suspend fun getNotesForCourse(courseId: Int): List<CourseNote> {
        return notes.first().filter { it.courseId == courseId }.sortedByDescending { it.createdAt }
    }

    private fun <T> List<T>.upsert(item: T, same: (T) -> Boolean): List<T> {
        return if (any(same)) map { if (same(it)) item else it } else this + item
    }
}
