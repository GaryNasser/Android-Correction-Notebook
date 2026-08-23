package com.github.garynasser.correction_notebook.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.github.garynasser.correction_notebook.data.model.home.DailyStats
import com.github.garynasser.correction_notebook.data.model.home.SessionType
import com.github.garynasser.correction_notebook.data.model.home.StudySession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore("session_prefs")

class StudySessionRepository(private val context: Context) {

    private val sessionsKey = stringPreferencesKey("study_sessions")

    val sessions: Flow<List<StudySession>> = context.sessionDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            prefs[sessionsKey]?.let(StudySessionPreferenceCodec::parseSessions) ?: emptyList()
        }

    suspend fun addSession(session: StudySession) {
        context.sessionDataStore.edit { prefs ->
            val current = prefs[sessionsKey]?.let(StudySessionPreferenceCodec::parseSessions) ?: emptyList()
            val updated = current + session
            prefs[sessionsKey] = StudySessionPreferenceCodec.serializeSessions(updated)
        }
    }

    suspend fun getTodaySessions(): List<StudySession> {
        val today = LocalDate.now()
        return sessions.first().filter {
            it.startTime.toLocalDate() == today
        }
    }

    suspend fun getWeekSessions(): List<StudySession> {
        val weekAgo = LocalDate.now().minusDays(7)
        return sessions.first().filter {
            !it.startTime.toLocalDate().isBefore(weekAgo)
        }
    }

    suspend fun getSessionsBetween(startDate: LocalDate, endDate: LocalDate): List<StudySession> {
        return sessions.first().filter {
            val sessionDate = it.startTime.toLocalDate()
            !sessionDate.isBefore(startDate) && !sessionDate.isAfter(endDate)
        }
    }

    suspend fun getTodayStats(): DailyStats {
        val todaySessions = getTodaySessions()
        return buildDailyStats(LocalDate.now(), todaySessions)
    }

    fun buildDailyStats(date: LocalDate, sessions: List<StudySession>): DailyStats {
        val totalMinutes = sessions.sumOf { it.durationMinutes }
        val pomodoros = sessions.sumOf { session ->
            if (session.sessionType == SessionType.POMODORO) {
                session.pomodoroCount
            } else {
                0
            }
        }
        return DailyStats(
            date = date,
            totalStudyMinutes = totalMinutes,
            completedPomodoros = pomodoros
        )
    }

}
