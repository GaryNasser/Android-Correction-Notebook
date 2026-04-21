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
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore("session_prefs")

class StudySessionRepository(private val context: Context) {

    private val sessionsKey = stringPreferencesKey("study_sessions")

    val sessions: Flow<List<StudySession>> = context.sessionDataStore.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { prefs ->
            prefs[sessionsKey]?.let { json -> parseSessions(json) } ?: emptyList()
        }

    suspend fun addSession(session: StudySession) {
        context.sessionDataStore.edit { prefs ->
            val current = prefs[sessionsKey]?.let { parseSessions(it) } ?: emptyList()
            val updated = current + session
            prefs[sessionsKey] = serializeSessions(updated)
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

    private fun serializeSessions(sessions: List<StudySession>): String {
        return sessions.joinToString("|||") { session ->
            listOf(
                session.id,
                session.subject,
                session.startTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                session.endTime?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) ?: "",
                session.durationMinutes.toString(),
                session.sessionType.name,
                session.pomodoroCount.toString()
            ).joinToString(":::")
        }
    }

    private fun parseSessions(json: String): List<StudySession> {
        if (json.isBlank()) return emptyList()
        return json.split("|||").mapNotNull { sessionStr ->
            val parts = sessionStr.split(":::")
            if (parts.size >= 6) {
                StudySession(
                    id = parts[0],
                    subject = parts[1],
                    startTime = LocalDateTime.parse(parts[2]),
                    endTime = if (parts[3].isNotBlank()) LocalDateTime.parse(parts[3]) else null,
                    durationMinutes = parts[4].toIntOrNull() ?: 0,
                    sessionType = try {
                        com.github.garynasser.correction_notebook.data.model.home.SessionType.valueOf(parts[5])
                    } catch (e: Exception) {
                        com.github.garynasser.correction_notebook.data.model.home.SessionType.POMODORO
                    },
                    pomodoroCount = parts.getOrNull(6)?.toIntOrNull() ?: 0
                )
            } else null
        }
    }
}
