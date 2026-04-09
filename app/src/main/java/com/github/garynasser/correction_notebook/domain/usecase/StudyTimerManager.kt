package com.github.garynasser.correction_notebook.domain.usecase

import com.github.garynasser.correction_notebook.data.model.home.PomodoroPhase
import com.github.garynasser.correction_notebook.data.model.home.PomodoroState
import com.github.garynasser.correction_notebook.data.model.home.TimerState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class StudyTimerManager(
    private val scope: CoroutineScope
) {
    private val _timerState = MutableStateFlow<TimerState>(TimerState.Idle)
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    private var timerJob: Job? = null
    private var pomodoroState = PomodoroState()

    fun startPomodoro() {
        stopTimer()
        pomodoroState = PomodoroState(
            phase = PomodoroPhase.FOCUS,
            timeRemainingSeconds = PomodoroState.FOCUS_MINUTES * 60,
            isRunning = true
        )
        _timerState.value = TimerState.Pomodoro(pomodoroState)
        startTimerJob()
    }

    fun startCountdown(minutes: Int) {
        stopTimer()
        val totalSeconds = minutes * 60
        _timerState.value = TimerState.Countdown(
            totalSeconds = totalSeconds,
            remainingSeconds = totalSeconds,
            isRunning = true
        )
        startCountdownJob(totalSeconds)
    }

    fun startStopwatch() {
        stopTimer()
        _timerState.value = TimerState.Stopwatch(
            elapsedSeconds = 0,
            isRunning = true
        )
        startStopwatchJob()
    }

    fun pause() {
        when (val state = _timerState.value) {
            is TimerState.Pomodoro -> {
                pomodoroState = state.state.copy(isRunning = false)
                _timerState.value = TimerState.Pomodoro(pomodoroState)
            }
            is TimerState.Countdown -> {
                _timerState.value = state.copy(isRunning = false)
            }
            is TimerState.Stopwatch -> {
                _timerState.value = state.copy(isRunning = false)
            }
            TimerState.Idle -> {}
        }
        timerJob?.cancel()
    }

    fun resume() {
        when (val state = _timerState.value) {
            is TimerState.Pomodoro -> {
                pomodoroState = state.state.copy(isRunning = true)
                _timerState.value = TimerState.Pomodoro(pomodoroState)
                startTimerJob()
            }
            is TimerState.Countdown -> {
                _timerState.value = state.copy(isRunning = true)
                startCountdownJob(state.remainingSeconds)
            }
            is TimerState.Stopwatch -> {
                _timerState.value = state.copy(isRunning = true)
                startStopwatchJob()
            }
            TimerState.Idle -> {}
        }
    }

    fun skip() {
        when (val state = _timerState.value) {
            is TimerState.Pomodoro -> {
                handlePomodoroPhaseEnd()
            }
            is TimerState.Countdown -> {
                _timerState.value = TimerState.Idle
            }
            is TimerState.Stopwatch -> {
                // Just continue, no skip for stopwatch
            }
            TimerState.Idle -> {}
        }
    }

    fun stop() {
        stopTimer()
        _timerState.value = TimerState.Idle
    }

    fun reset() {
        when (val state = _timerState.value) {
            is TimerState.Pomodoro -> {
                pomodoroState = PomodoroState(
                    phase = PomodoroPhase.FOCUS,
                    timeRemainingSeconds = PomodoroState.FOCUS_MINUTES * 60,
                    isRunning = false,
                    completedPomodoros = 0,
                    totalFocusTimeMinutes = 0
                )
                _timerState.value = TimerState.Pomodoro(pomodoroState)
                stopTimer()
            }
            is TimerState.Countdown -> {
                _timerState.value = state.copy(
                    remainingSeconds = state.totalSeconds,
                    isRunning = false
                )
                stopTimer()
            }
            is TimerState.Stopwatch -> {
                _timerState.value = state.copy(
                    elapsedSeconds = 0,
                    isRunning = false
                )
                stopTimer()
            }
            TimerState.Idle -> {}
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun startTimerJob() {
        timerJob = scope.launch {
            while (pomodoroState.timeRemainingSeconds > 0 && pomodoroState.isRunning) {
                delay(1000)
                pomodoroState = pomodoroState.copy(
                    timeRemainingSeconds = pomodoroState.timeRemainingSeconds - 1
                )
                _timerState.value = TimerState.Pomodoro(pomodoroState)
            }
            if (pomodoroState.timeRemainingSeconds <= 0) {
                handlePomodoroPhaseEnd()
            }
        }
    }

    private fun handlePomodoroPhaseEnd() {
        pomodoroState = when (pomodoroState.phase) {
            PomodoroPhase.FOCUS -> {
                val newCompleted = pomodoroState.completedPomodoros + 1
                if (newCompleted % PomodoroState.POMODOROS_BEFORE_LONG_BREAK == 0) {
                    pomodoroState.copy(
                        phase = PomodoroPhase.LONG_BREAK,
                        timeRemainingSeconds = PomodoroState.LONG_BREAK_MINUTES * 60,
                        completedPomodoros = newCompleted,
                        totalFocusTimeMinutes = pomodoroState.totalFocusTimeMinutes + PomodoroState.FOCUS_MINUTES
                    )
                } else {
                    pomodoroState.copy(
                        phase = PomodoroPhase.SHORT_BREAK,
                        timeRemainingSeconds = PomodoroState.SHORT_BREAK_MINUTES * 60,
                        completedPomodoros = newCompleted,
                        totalFocusTimeMinutes = pomodoroState.totalFocusTimeMinutes + PomodoroState.FOCUS_MINUTES
                    )
                }
            }
            PomodoroPhase.SHORT_BREAK, PomodoroPhase.LONG_BREAK -> {
                pomodoroState.copy(
                    phase = PomodoroPhase.FOCUS,
                    timeRemainingSeconds = PomodoroState.FOCUS_MINUTES * 60
                )
            }
        }
        _timerState.value = TimerState.Pomodoro(pomodoroState)
    }

    private fun startCountdownJob(remainingSeconds: Int) {
        timerJob = scope.launch {
            var remaining = remainingSeconds
            while (remaining > 0) {
                val state = _timerState.value
                if (state is TimerState.Countdown && state.isRunning) {
                    delay(1000)
                    remaining--
                    _timerState.value = TimerState.Countdown(
                        totalSeconds = (state as TimerState.Countdown).totalSeconds,
                        remainingSeconds = remaining,
                        isRunning = true
                    )
                } else {
                    break
                }
            }
            if (remaining <= 0) {
                _timerState.value = TimerState.Idle
            }
        }
    }

    private fun startStopwatchJob() {
        timerJob = scope.launch {
            var elapsed = 0
            while (true) {
                val state = _timerState.value
                if (state is TimerState.Stopwatch && state.isRunning) {
                    delay(1000)
                    elapsed++
                    _timerState.value = TimerState.Stopwatch(
                        elapsedSeconds = elapsed,
                        isRunning = true
                    )
                } else {
                    break
                }
            }
        }
    }

    fun getElapsedMinutes(): Int {
        return when (val state = _timerState.value) {
            is TimerState.Pomodoro -> state.state.totalFocusTimeMinutes
            is TimerState.Stopwatch -> state.elapsedSeconds / 60
            else -> 0
        }
    }
}
