package com.github.garynasser.correction_notebook.ui.screens.home

import android.os.Bundle
import androidx.core.os.bundleOf
import com.github.garynasser.correction_notebook.data.model.home.PomodoroPhase
import com.github.garynasser.correction_notebook.data.model.home.PomodoroSettings
import com.github.garynasser.correction_notebook.data.model.home.PomodoroState
import com.github.garynasser.correction_notebook.data.model.home.TimerState
import com.github.garynasser.correction_notebook.domain.usecase.StudyTimerManager
import java.time.LocalDateTime

internal data class SavedStudyTimer(
    val sessionId: String,
    val startedAt: LocalDateTime,
    val checkpoint: StudyTimerManager.Checkpoint
) {
    fun toBundle(): Bundle {
        val bundle = bundleOf(
            "sessionId" to sessionId,
            "startedAt" to startedAt.toString(),
            "clock" to checkpoint.elapsedRealtimeMillis,
            "partial" to checkpoint.partialSecondMillis,
            "focusSeconds" to checkpoint.completedFocusSeconds
        )
        when (val state = checkpoint.state) {
            is TimerState.Pomodoro -> {
                val pomodoro = state.state
                bundle.putString("type", "pomodoro")
                bundle.putString("phase", pomodoro.phase.name)
                bundle.putInt("remaining", pomodoro.timeRemainingSeconds)
                bundle.putBoolean("running", pomodoro.isRunning)
                bundle.putInt("completed", pomodoro.completedPomodoros)
                bundle.putInt("focusMinutes", pomodoro.settings.focusMinutes)
                bundle.putInt("shortBreakMinutes", pomodoro.settings.shortBreakMinutes)
                bundle.putInt("longBreakMinutes", pomodoro.settings.longBreakMinutes)
                bundle.putInt("longBreakInterval", pomodoro.settings.pomodorosBeforeLongBreak)
            }
            is TimerState.Countdown -> {
                bundle.putString("type", "countdown")
                bundle.putInt("total", state.totalSeconds)
                bundle.putInt("remaining", state.remainingSeconds)
                bundle.putBoolean("running", state.isRunning)
            }
            is TimerState.CountdownFinished -> {
                bundle.putString("type", "countdownFinished")
                bundle.putInt("total", state.totalSeconds)
            }
            is TimerState.Stopwatch -> {
                bundle.putString("type", "stopwatch")
                bundle.putInt("elapsed", state.elapsedSeconds)
                bundle.putBoolean("running", state.isRunning)
            }
            is TimerState.StopwatchFinished -> {
                bundle.putString("type", "stopwatchFinished")
                bundle.putInt("elapsed", state.elapsedSeconds)
            }
            TimerState.Idle -> Unit
        }
        return bundle
    }
}

internal fun Bundle.toSavedStudyTimer(): SavedStudyTimer? = runCatching {
    val sessionId = getString("sessionId")?.takeIf { it.isNotBlank() } ?: return null
    val startedAt = LocalDateTime.parse(getString("startedAt") ?: return null)
    val state = when (getString("type")) {
        "pomodoro" -> {
            val settings = PomodoroSettings(
                focusMinutes = getInt("focusMinutes"),
                shortBreakMinutes = getInt("shortBreakMinutes"),
                longBreakMinutes = getInt("longBreakMinutes"),
                pomodorosBeforeLongBreak = getInt("longBreakInterval")
            )
            require(settings.focusMinutes > 0 && settings.shortBreakMinutes > 0 && settings.longBreakMinutes > 0)
            val phase = PomodoroPhase.valueOf(getString("phase") ?: return null)
            val remaining = getInt("remaining")
            val phaseMinutes = when (phase) {
                PomodoroPhase.FOCUS -> settings.focusMinutes
                PomodoroPhase.SHORT_BREAK -> settings.shortBreakMinutes
                PomodoroPhase.LONG_BREAK -> settings.longBreakMinutes
            }
            require(remaining in 0..phaseMinutes * 60 && getInt("completed") >= 0)
            TimerState.Pomodoro(
                PomodoroState(
                    phase = phase,
                    timeRemainingSeconds = remaining,
                    completedPomodoros = getInt("completed"),
                    isRunning = getBoolean("running"),
                    totalFocusTimeMinutes = getInt("focusSeconds") / 60,
                    settings = settings
                )
            )
        }
        "countdown" -> {
            val total = getInt("total")
            val remaining = getInt("remaining")
            require(total > 0 && remaining in 0..total)
            TimerState.Countdown(total, remaining, getBoolean("running"))
        }
        "countdownFinished" -> {
            require(getInt("total") > 0)
            TimerState.CountdownFinished(getInt("total"))
        }
        "stopwatch" -> {
            require(getInt("elapsed") >= 0)
            TimerState.Stopwatch(getInt("elapsed"), getBoolean("running"))
        }
        "stopwatchFinished" -> {
            require(getInt("elapsed") >= 0)
            TimerState.StopwatchFinished(getInt("elapsed"))
        }
        else -> return null
    }
    require(getLong("clock") >= 0 && getLong("partial") in 0..999 && getInt("focusSeconds") >= 0)
    SavedStudyTimer(
        sessionId,
        startedAt,
        StudyTimerManager.Checkpoint(state, getLong("clock"), getLong("partial"), getInt("focusSeconds"))
    )
}.getOrNull()
