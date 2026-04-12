package com.github.garynasser.correction_notebook.data.model.home

enum class PomodoroPhase {
    FOCUS, SHORT_BREAK, LONG_BREAK
}

data class PomodoroState(
    val phase: PomodoroPhase = PomodoroPhase.FOCUS,
    val timeRemainingSeconds: Int = 25 * 60,
    val completedPomodoros: Int = 0,
    val isRunning: Boolean = false,
    val totalFocusTimeMinutes: Int = 0,
    val settings: PomodoroSettings = PomodoroSettings()
)

sealed class TimerState {
    data object Idle : TimerState()
    data class Pomodoro(val state: PomodoroState) : TimerState()
    data class Countdown(val totalSeconds: Int, val remainingSeconds: Int, val isRunning: Boolean) : TimerState()
    data class CountdownFinished(val totalSeconds: Int) : TimerState()
    data class Stopwatch(val elapsedSeconds: Int, val isRunning: Boolean) : TimerState()
    data class StopwatchFinished(val elapsedSeconds: Int) : TimerState()
}
