package com.github.garynasser.correction_notebook.data.local

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.studyDataStore: DataStore<Preferences> by preferencesDataStore("study_prefs")

class StudyPreferencesManager(private val context: Context) {

    companion object {
        private val TODO_ITEMS_KEY = stringPreferencesKey("todo_items")
        private val STUDY_SESSIONS_KEY = stringPreferencesKey("study_sessions")
        private val DAILY_STATS_KEY = stringPreferencesKey("daily_stats")
        private val TOTAL_STUDY_MINUTES_KEY = intPreferencesKey("total_study_minutes")
        private val POMODOROS_COMPLETED_KEY = intPreferencesKey("pomodoros_completed")
        private val LAST_STUDY_DATE_KEY = stringPreferencesKey("last_study_date")
        private val BACKGROUND_IMAGE_URI_KEY = stringPreferencesKey("background_image_uri")
        private val ORIENTATION_LANDSCAPE_KEY = booleanPreferencesKey("orientation_landscape")
    }

    val totalStudyMinutes: Flow<Int> = context.studyDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[TOTAL_STUDY_MINUTES_KEY] ?: 0 }

    val pomodorosCompleted: Flow<Int> = context.studyDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[POMODOROS_COMPLETED_KEY] ?: 0 }

    val backgroundImageUri: Flow<String?> = context.studyDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[BACKGROUND_IMAGE_URI_KEY] }

    val isLandscapeOrientation: Flow<Boolean> = context.studyDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[ORIENTATION_LANDSCAPE_KEY] ?: false }

    suspend fun getTotalStudyMinutes(): Int {
        return totalStudyMinutes.first()
    }

    suspend fun addStudyMinutes(minutes: Int) {
        context.studyDataStore.edit { prefs ->
            val current = prefs[TOTAL_STUDY_MINUTES_KEY] ?: 0
            prefs[TOTAL_STUDY_MINUTES_KEY] = current + minutes
            prefs[LAST_STUDY_DATE_KEY] = LocalDate.now().toString()
        }
    }

    suspend fun incrementPomodoros() {
        context.studyDataStore.edit { prefs ->
            val current = prefs[POMODOROS_COMPLETED_KEY] ?: 0
            prefs[POMODOROS_COMPLETED_KEY] = current + 1
        }
    }

    suspend fun getTodayStudyMinutes(): Int {
        val lastDate = context.studyDataStore.data
            .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
            .map { it[LAST_STUDY_DATE_KEY] }
            .first()
        return if (lastDate == LocalDate.now().toString()) {
            totalStudyMinutes.first()
        } else {
            0
        }
    }

    suspend fun setBackgroundImage(uri: String?) {
        context.studyDataStore.edit { prefs ->
            if (uri != null) {
                prefs[BACKGROUND_IMAGE_URI_KEY] = uri
            } else {
                prefs.remove(BACKGROUND_IMAGE_URI_KEY)
            }
        }
    }

    suspend fun setLandscapeOrientation(isLandscape: Boolean) {
        context.studyDataStore.edit { prefs ->
            prefs[ORIENTATION_LANDSCAPE_KEY] = isLandscape
        }
    }
}
