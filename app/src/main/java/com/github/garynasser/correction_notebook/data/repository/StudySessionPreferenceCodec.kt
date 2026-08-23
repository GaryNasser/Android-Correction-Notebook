package com.github.garynasser.correction_notebook.data.repository

import com.github.garynasser.correction_notebook.data.model.home.SessionType
import com.github.garynasser.correction_notebook.data.model.home.StudySession
import com.google.gson.Gson
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

internal object StudySessionPreferenceCodec {
    private val gson = Gson()

    fun serializeSessions(sessions: List<StudySession>): String {
        return gson.toJson(sessions.map(StudySessionDto::from))
    }

    fun parseSessions(raw: String): List<StudySession> {
        if (raw.isBlank()) return emptyList()
        return parseJsonArray(raw)
            ?.mapNotNull(StudySessionDto::toModel)
            ?: parseLegacySessions(raw)
    }

    private fun parseJsonArray(raw: String): List<StudySessionDto>? {
        if (!raw.trimStart().startsWith("[")) return null
        return runCatching {
            gson.fromJson(raw, Array<StudySessionDto>::class.java).toList()
        }.getOrNull()
    }

    private fun parseLegacySessions(raw: String): List<StudySession> {
        return raw.split("|||").mapNotNull { sessionStr ->
            runCatching {
                val parts = sessionStr.split(":::")
                if (parts.size < 6) return@mapNotNull null
                if (parts[1].isBlank()) return@mapNotNull null
                StudySession(
                    id = parts[0],
                    subject = parts[1],
                    startTime = LocalDateTime.parse(parts[2]),
                    endTime = parts[3].takeIf { it.isNotBlank() }?.let(LocalDateTime::parse),
                    durationMinutes = (parts[4].toIntOrNull() ?: 0).coerceAtLeast(0),
                    sessionType = sessionTypeOrDefault(parts[5]),
                    pomodoroCount = (parts.getOrNull(6)?.toIntOrNull() ?: 0).coerceAtLeast(0)
                )
            }.getOrNull()
        }
    }

    private fun sessionTypeOrDefault(raw: String): SessionType {
        return runCatching { SessionType.valueOf(raw) }.getOrDefault(SessionType.POMODORO)
    }

    private data class StudySessionDto(
        val id: String?,
        val subject: String?,
        val startTime: String?,
        val endTime: String?,
        val durationMinutes: Int?,
        val sessionType: String?,
        val pomodoroCount: Int?
    ) {
        fun toModel(): StudySession? {
            return runCatching {
                val normalizedSubject = subject?.takeIf { it.isNotBlank() } ?: return null
                val normalizedStartTime = startTime?.takeIf { it.isNotBlank() } ?: return null
                StudySession(
                    id = id?.takeIf { it.isNotBlank() } ?: java.util.UUID.randomUUID().toString(),
                    subject = normalizedSubject,
                    startTime = LocalDateTime.parse(normalizedStartTime),
                    endTime = endTime?.takeIf { it.isNotBlank() }?.let(LocalDateTime::parse),
                    durationMinutes = (durationMinutes ?: 0).coerceAtLeast(0),
                    sessionType = sessionTypeOrDefault(sessionType.orEmpty()),
                    pomodoroCount = (pomodoroCount ?: 0).coerceAtLeast(0)
                )
            }.getOrNull()
        }

        companion object {
            fun from(session: StudySession): StudySessionDto = StudySessionDto(
                id = session.id,
                subject = session.subject,
                startTime = session.startTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                endTime = session.endTime?.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                durationMinutes = session.durationMinutes.coerceAtLeast(0),
                sessionType = session.sessionType.name,
                pomodoroCount = session.pomodoroCount.coerceAtLeast(0)
            )
        }
    }
}
