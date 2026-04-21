package com.github.garynasser.correction_notebook.data.model.home

import java.time.LocalDateTime

data class StudySession(
    val id: String = java.util.UUID.randomUUID().toString(),
    val subject: String,
    val startTime: LocalDateTime,
    val endTime: LocalDateTime? = null,
    val durationMinutes: Int = 0,
    val sessionType: SessionType = SessionType.POMODORO,
    val pomodoroCount: Int = 0
)

enum class SessionType {
    POMODORO, COUNTDOWN, STOPWATCH
}
