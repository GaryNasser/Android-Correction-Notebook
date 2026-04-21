package com.github.garynasser.correction_notebook.domain.usecase

import com.github.garynasser.correction_notebook.data.model.home.PomodoroPhase
import com.github.garynasser.correction_notebook.data.model.home.PomodoroSettings
import com.github.garynasser.correction_notebook.data.model.home.PomodoroState
import com.github.garynasser.correction_notebook.data.model.home.SessionType
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

    // Callbacks for alerts and time tracking
    var onTimerFinished: (() -> Unit)? = null
    var onPomodoroPhaseChanged: ((PomodoroPhase) -> Unit)? = null
    var onStopwatchReset: ((Int) -> Unit)? = null  // Called with elapsed minutes when stopwatch is reset
    var onCountdownReset: ((Int) -> Unit)? = null  // Called with elapsed minutes when countdown is reset

    data class SessionSnapshot(
        val sessionType: SessionType,
        val durationMinutes: Int,
        val pomodoroCount: Int = 0
    )

    fun startPomodoro(settings: PomodoroSettings = PomodoroSettings()) {
        stopTimer()
        pomodoroState = PomodoroState(
            phase = PomodoroPhase.FOCUS,
            timeRemainingSeconds = settings.focusMinutes * 60,
            isRunning = true,
            settings = settings
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
            else -> {}
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
                val currentElapsed = state.elapsedSeconds
                _timerState.value = state.copy(isRunning = true)
                startStopwatchJob(currentElapsed)
            }
            else -> {}
        }
    }

    fun skip() {
        when (val state = _timerState.value) {
            is TimerState.Pomodoro -> {
                handlePomodoroPhaseEnd(skipped = true)
            }
            is TimerState.Countdown -> {
                _timerState.value = TimerState.CountdownFinished(state.totalSeconds)
            }
            is TimerState.Stopwatch -> {
                // Just continue, no skip for stopwatch
            }
            else -> {}
        }
    }

    fun stop() {
        stopTimer()
        when (val state = _timerState.value) {
            is TimerState.Pomodoro -> {
                // Calculate elapsed focus time before stopping
                val settings = pomodoroState.settings
                val currentPhaseElapsed = when (pomodoroState.phase) {
                    PomodoroPhase.FOCUS -> settings.focusMinutes * 60 - pomodoroState.timeRemainingSeconds
                    PomodoroPhase.SHORT_BREAK -> settings.shortBreakMinutes * 60 - pomodoroState.timeRemainingSeconds
                    PomodoroPhase.LONG_BREAK -> settings.longBreakMinutes * 60 - pomodoroState.timeRemainingSeconds
                }
                val totalElapsedSeconds = pomodoroState.completedPomodoros * settings.focusMinutes * 60 + currentPhaseElapsed
                _timerState.value = TimerState.Idle
            }
            is TimerState.Countdown -> {
                _timerState.value = TimerState.CountdownFinished(state.totalSeconds)
            }
            is TimerState.CountdownFinished -> {
                // Already finished, do nothing
            }
            is TimerState.Stopwatch -> {
                _timerState.value = TimerState.StopwatchFinished(state.elapsedSeconds)
            }
            is TimerState.StopwatchFinished -> {
                // Already finished, do nothing
            }
            TimerState.Idle -> {}
        }
    }

    fun reset() {
        val settings = pomodoroState.settings
        when (val state = _timerState.value) {
            is TimerState.Pomodoro -> {
                pomodoroState = PomodoroState(
                    phase = PomodoroPhase.FOCUS,
                    timeRemainingSeconds = settings.focusMinutes * 60,
                    isRunning = false,
                    completedPomodoros = 0,
                    totalFocusTimeMinutes = 0,
                    settings = settings
                )
                _timerState.value = TimerState.Pomodoro(pomodoroState)
                stopTimer()
            }
            is TimerState.Countdown -> {
                // Save elapsed time before reset
                val elapsedMinutes = (state.totalSeconds - state.remainingSeconds) / 60
                onCountdownReset?.invoke(elapsedMinutes)
                _timerState.value = state.copy(
                    remainingSeconds = state.totalSeconds,
                    isRunning = false
                )
                stopTimer()
            }
            is TimerState.CountdownFinished -> {
                // Save elapsed time before reset
                val elapsedMinutes = state.totalSeconds / 60
                onCountdownReset?.invoke(elapsedMinutes)
                _timerState.value = TimerState.Countdown(
                    totalSeconds = state.totalSeconds,
                    remainingSeconds = state.totalSeconds,
                    isRunning = false
                )
            }
            is TimerState.Stopwatch -> {
                // Save elapsed time before reset
                val elapsedMinutes = state.elapsedSeconds / 60
                onStopwatchReset?.invoke(elapsedMinutes)
                _timerState.value = state.copy(
                    elapsedSeconds = 0,
                    isRunning = false
                )
                stopTimer()
            }
            is TimerState.StopwatchFinished -> {
                // Save elapsed time before reset
                val elapsedMinutes = state.elapsedSeconds / 60
                onStopwatchReset?.invoke(elapsedMinutes)
                _timerState.value = TimerState.Stopwatch(
                    elapsedSeconds = 0,
                    isRunning = false
                )
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

    private fun handlePomodoroPhaseEnd(skipped: Boolean = false) {
        val settings = pomodoroState.settings
        val newPhase: PomodoroPhase

        pomodoroState = when (pomodoroState.phase) {
            PomodoroPhase.FOCUS -> {
                val elapsedFocusMinutes = maxOf(
                    0,
                    (settings.focusMinutes * 60 - pomodoroState.timeRemainingSeconds) / 60
                )
                val newCompleted = if (skipped) {
                    pomodoroState.completedPomodoros
                } else {
                    pomodoroState.completedPomodoros + 1
                }
                newPhase = if (newCompleted % settings.pomodorosBeforeLongBreak == 0) {
                    PomodoroPhase.LONG_BREAK
                } else {
                    PomodoroPhase.SHORT_BREAK
                }
                val focusMinutesToAdd = if (skipped) elapsedFocusMinutes else settings.focusMinutes
                if (newPhase == PomodoroPhase.LONG_BREAK) {
                    pomodoroState.copy(
                        phase = PomodoroPhase.LONG_BREAK,
                        timeRemainingSeconds = settings.longBreakMinutes * 60,
                        completedPomodoros = newCompleted,
                        totalFocusTimeMinutes = pomodoroState.totalFocusTimeMinutes + focusMinutesToAdd
                    )
                } else {
                    pomodoroState.copy(
                        phase = PomodoroPhase.SHORT_BREAK,
                        timeRemainingSeconds = settings.shortBreakMinutes * 60,
                        completedPomodoros = newCompleted,
                        totalFocusTimeMinutes = pomodoroState.totalFocusTimeMinutes + focusMinutesToAdd
                    )
                }
            }
            PomodoroPhase.SHORT_BREAK, PomodoroPhase.LONG_BREAK -> {
                newPhase = PomodoroPhase.FOCUS
                pomodoroState.copy(
                    phase = PomodoroPhase.FOCUS,
                    timeRemainingSeconds = settings.focusMinutes * 60
                )
            }
        }
        _timerState.value = TimerState.Pomodoro(pomodoroState)

        // Trigger alert for phase change
        onTimerFinished?.invoke()
        onPomodoroPhaseChanged?.invoke(newPhase)
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
                        totalSeconds = state.totalSeconds,
                        remainingSeconds = remaining,
                        isRunning = true
                    )
                } else {
                    break
                }
            }
            if (remaining <= 0) {
                _timerState.value = TimerState.CountdownFinished(remainingSeconds)
                onTimerFinished?.invoke()
            }
        }
    }

    private fun startStopwatchJob(initialElapsed: Int = 0) {
        timerJob = scope.launch {
            var elapsed = initialElapsed
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
        return getElapsedSeconds() / 60
    }

    fun getElapsedSeconds(): Int {
        return when (val state = _timerState.value) {
            is TimerState.Pomodoro -> {
                val settings = state.state.settings
                val focusTime = settings.focusMinutes * 60
                val remaining = state.state.timeRemainingSeconds
                val elapsed = focusTime - remaining
                (state.state.completedPomodoros * settings.focusMinutes * 60) + maxOf(0, elapsed)
            }
            is TimerState.Stopwatch -> state.elapsedSeconds
            is TimerState.StopwatchFinished -> state.elapsedSeconds
            is TimerState.Countdown -> state.totalSeconds - state.remainingSeconds
            is TimerState.CountdownFinished -> state.totalSeconds
            else -> 0
        }
    }

    fun getElapsedMinutesForCurrentSession(): Int {
        return getElapsedSeconds() / 60
    }

    fun getCurrentSessionSnapshot(): SessionSnapshot? {
        return when (val state = _timerState.value) {
            is TimerState.Pomodoro -> {
                val settings = state.state.settings
                val currentFocusMinutes = if (state.state.phase == PomodoroPhase.FOCUS) {
                    val elapsedFocusSeconds = settings.focusMinutes * 60 - state.state.timeRemainingSeconds
                    maxOf(0, elapsedFocusSeconds) / 60
                } else {
                    0
                }
                val totalFocusMinutes = state.state.totalFocusTimeMinutes + currentFocusMinutes
                if (totalFocusMinutes <= 0 && state.state.completedPomodoros <= 0) {
                    null
                } else {
                    SessionSnapshot(
                        sessionType = SessionType.POMODORO,
                        durationMinutes = totalFocusMinutes,
                        pomodoroCount = state.state.completedPomodoros
                    )
                }
            }
            is TimerState.Countdown -> {
                val elapsedMinutes = (state.totalSeconds - state.remainingSeconds) / 60
                if (elapsedMinutes <= 0) null else SessionSnapshot(SessionType.COUNTDOWN, elapsedMinutes)
            }
            is TimerState.CountdownFinished -> {
                val elapsedMinutes = state.totalSeconds / 60
                if (elapsedMinutes <= 0) null else SessionSnapshot(SessionType.COUNTDOWN, elapsedMinutes)
            }
            is TimerState.Stopwatch -> {
                val elapsedMinutes = state.elapsedSeconds / 60
                if (elapsedMinutes <= 0) null else SessionSnapshot(SessionType.STOPWATCH, elapsedMinutes)
            }
            is TimerState.StopwatchFinished -> {
                val elapsedMinutes = state.elapsedSeconds / 60
                if (elapsedMinutes <= 0) null else SessionSnapshot(SessionType.STOPWATCH, elapsedMinutes)
            }
            TimerState.Idle -> null
        }
    }
}
