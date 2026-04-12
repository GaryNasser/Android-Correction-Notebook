package com.github.garynasser.correction_notebook.data.local

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.github.garynasser.correction_notebook.data.model.home.PomodoroSettings
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
        private val FOCUS_MINUTES_KEY = intPreferencesKey("focus_minutes")
        private val SHORT_BREAK_MINUTES_KEY = intPreferencesKey("short_break_minutes")
        private val LONG_BREAK_MINUTES_KEY = intPreferencesKey("long_break_minutes")
        private val POMODOROS_BEFORE_LONG_BREAK_KEY = intPreferencesKey("pomodoros_before_long_break")
        private val SOUND_ENABLED_KEY = booleanPreferencesKey("sound_enabled")
        private val VIBRATION_ENABLED_KEY = booleanPreferencesKey("vibration_enabled")
    }

    val pomodoroSettings: Flow<PomodoroSettings> = context.studyDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            PomodoroSettings(
                focusMinutes = prefs[FOCUS_MINUTES_KEY] ?: 25,
                shortBreakMinutes = prefs[SHORT_BREAK_MINUTES_KEY] ?: 5,
                longBreakMinutes = prefs[LONG_BREAK_MINUTES_KEY] ?: 15,
                pomodorosBeforeLongBreak = prefs[POMODOROS_BEFORE_LONG_BREAK_KEY] ?: 4
            )
        }

    val soundEnabled: Flow<Boolean> = context.studyDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[SOUND_ENABLED_KEY] ?: true }

    val vibrationEnabled: Flow<Boolean> = context.studyDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[VIBRATION_ENABLED_KEY] ?: true }

    suspend fun updatePomodoroSettings(settings: PomodoroSettings) {
        context.studyDataStore.edit { prefs ->
            prefs[FOCUS_MINUTES_KEY] = settings.focusMinutes
            prefs[SHORT_BREAK_MINUTES_KEY] = settings.shortBreakMinutes
            prefs[LONG_BREAK_MINUTES_KEY] = settings.longBreakMinutes
            prefs[POMODOROS_BEFORE_LONG_BREAK_KEY] = settings.pomodorosBeforeLongBreak
        }
    }

    suspend fun setSoundEnabled(enabled: Boolean) {
        context.studyDataStore.edit { prefs ->
            prefs[SOUND_ENABLED_KEY] = enabled
        }
    }

    suspend fun setVibrationEnabled(enabled: Boolean) {
        context.studyDataStore.edit { prefs ->
            prefs[VIBRATION_ENABLED_KEY] = enabled
        }
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
