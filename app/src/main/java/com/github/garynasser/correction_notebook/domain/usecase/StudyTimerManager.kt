package com.github.garynasser.correction_notebook.domain.usecase

import com.github.garynasser.correction_notebook.data.model.home.PomodoroPhase
import com.github.garynasser.correction_notebook.data.model.home.PomodoroSettings
import com.github.garynasser.correction_notebook.data.model.home.PomodoroState
import com.github.garynasser.correction_notebook.data.model.home.SessionType
import com.github.garynasser.correction_notebook.data.model.home.TimerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class StudyTimerManager(
    private val scope: CoroutineScope,
    private val elapsedRealtimeMillis: () -> Long = { System.nanoTime() / 1_000_000 }
) {
    private val _timerState = MutableStateFlow<TimerState>(TimerState.Idle)
    val timerState: StateFlow<TimerState> = _timerState.asStateFlow()

    private var timerJob: Job? = null
    private var lastTickMillis = 0L
    private var partialSecondMillis = 0L
    private var completedFocusSeconds = 0

    var onTimerFinished: (() -> Unit)? = null
    var onPomodoroPhaseChanged: ((PomodoroPhase) -> Unit)? = null

    data class SessionSnapshot(
        val sessionType: SessionType,
        val durationMinutes: Int,
        val pomodoroCount: Int = 0
    )

    fun startPomodoro(settings: PomodoroSettings = PomodoroSettings()) {
        stopTimer()
        partialSecondMillis = 0
        completedFocusSeconds = 0
        val safeSettings = settings.copy(
            focusMinutes = settings.focusMinutes.coerceAtLeast(1),
            shortBreakMinutes = settings.shortBreakMinutes.coerceAtLeast(1),
            longBreakMinutes = settings.longBreakMinutes.coerceAtLeast(1)
        )
        _timerState.value = TimerState.Pomodoro(
            PomodoroState(
                timeRemainingSeconds = safeSettings.focusMinutes * 60,
                isRunning = true,
                settings = safeSettings
            )
        )
        startTimerJob()
    }

    fun startCountdown(minutes: Int) {
        stopTimer()
        partialSecondMillis = 0
        val seconds = minutes.coerceAtLeast(1) * 60
        _timerState.value = TimerState.Countdown(seconds, seconds, isRunning = true)
        startTimerJob()
    }

    fun startStopwatch() {
        stopTimer()
        partialSecondMillis = 0
        _timerState.value = TimerState.Stopwatch(elapsedSeconds = 0, isRunning = true)
        startTimerJob()
    }

    fun pause() {
        updateElapsedTime()
        stopTimer()
        setRunning(false)
    }

    fun resume() {
        if (_timerState.value.isRunning()) return
        setRunning(true)
        if (_timerState.value.isRunning()) startTimerJob()
    }

    fun skip() {
        updateElapsedTime()
        when (val state = _timerState.value) {
            is TimerState.Pomodoro -> {
                stopTimer()
                partialSecondMillis = 0
                handlePomodoroPhaseEnd(skipped = true)
                if (_timerState.value.isRunning()) startTimerJob()
            }
            is TimerState.Countdown -> {
                stopTimer()
                _timerState.value = TimerState.CountdownFinished(state.totalSeconds)
            }
            else -> Unit
        }
    }

    fun stop() {
        updateElapsedTime()
        stopTimer()
        _timerState.value = TimerState.Idle
        partialSecondMillis = 0
    }

    fun reset() {
        updateElapsedTime()
        stopTimer()
        partialSecondMillis = 0
        when (val state = _timerState.value) {
            is TimerState.Pomodoro -> {
                completedFocusSeconds = 0
                _timerState.value = TimerState.Pomodoro(
                    PomodoroState(
                        timeRemainingSeconds = state.state.settings.focusMinutes * 60,
                        settings = state.state.settings
                    )
                )
            }
            is TimerState.Countdown -> {
                _timerState.value = state.copy(remainingSeconds = state.totalSeconds, isRunning = false)
            }
            is TimerState.CountdownFinished -> {
                _timerState.value = TimerState.Countdown(state.totalSeconds, state.totalSeconds, isRunning = false)
            }
            is TimerState.Stopwatch, is TimerState.StopwatchFinished -> {
                _timerState.value = TimerState.Stopwatch(elapsedSeconds = 0, isRunning = false)
            }
            TimerState.Idle -> Unit
        }
    }

    private fun setRunning(running: Boolean) {
        _timerState.value = when (val state = _timerState.value) {
            is TimerState.Pomodoro -> state.copy(state = state.state.copy(isRunning = running))
            is TimerState.Countdown -> state.copy(isRunning = running)
            is TimerState.Stopwatch -> state.copy(isRunning = running)
            else -> state
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun startTimerJob() {
        stopTimer()
        lastTickMillis = elapsedRealtimeMillis()
        timerJob = scope.launch {
            while (_timerState.value.isRunning()) {
                delay(1_000)
                updateElapsedTime()
            }
        }
    }

    private fun updateElapsedTime() {
        if (!_timerState.value.isRunning()) return
        val now = elapsedRealtimeMillis()
        val elapsedMillis = partialSecondMillis + (now - lastTickMillis).coerceAtLeast(0)
        lastTickMillis = now
        partialSecondMillis = elapsedMillis % 1_000
        val seconds = (elapsedMillis / 1_000).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        if (seconds == 0) return

        when (val state = _timerState.value) {
            is TimerState.Countdown -> {
                val remaining = (state.remainingSeconds - seconds).coerceAtLeast(0)
                if (remaining == 0) {
                    _timerState.value = TimerState.CountdownFinished(state.totalSeconds)
                    onTimerFinished?.invoke()
                } else {
                    _timerState.value = state.copy(remainingSeconds = remaining)
                }
            }
            is TimerState.Stopwatch -> {
                _timerState.value = state.copy(elapsedSeconds = state.elapsedSeconds + seconds)
            }
            is TimerState.Pomodoro -> advancePomodoro(seconds)
            else -> Unit
        }
    }

    private fun advancePomodoro(seconds: Int) {
        var remainingElapsed = seconds
        // Catch up every phase, including breaks, after a delayed background tick.
        while (remainingElapsed > 0) {
            val state = (_timerState.value as? TimerState.Pomodoro)?.state ?: return
            val consumed = minOf(remainingElapsed, state.timeRemainingSeconds)
            remainingElapsed -= consumed
            _timerState.value = TimerState.Pomodoro(
                state.copy(timeRemainingSeconds = state.timeRemainingSeconds - consumed)
            )
            if (consumed == state.timeRemainingSeconds) handlePomodoroPhaseEnd()
        }
    }

    private fun handlePomodoroPhaseEnd(skipped: Boolean = false) {
        val state = (_timerState.value as TimerState.Pomodoro).state
        val settings = state.settings
        val next = when (state.phase) {
            PomodoroPhase.FOCUS -> {
                completedFocusSeconds += settings.focusMinutes * 60 - state.timeRemainingSeconds
                val completed = state.completedPomodoros + if (skipped) 0 else 1
                val longBreak = completed > 0 && completed % settings.pomodorosBeforeLongBreak.coerceAtLeast(1) == 0
                state.copy(
                    phase = if (longBreak) PomodoroPhase.LONG_BREAK else PomodoroPhase.SHORT_BREAK,
                    timeRemainingSeconds = (if (longBreak) settings.longBreakMinutes else settings.shortBreakMinutes) * 60,
                    completedPomodoros = completed,
                    totalFocusTimeMinutes = completedFocusSeconds / 60
                )
            }
            PomodoroPhase.SHORT_BREAK, PomodoroPhase.LONG_BREAK -> state.copy(
                phase = PomodoroPhase.FOCUS,
                timeRemainingSeconds = settings.focusMinutes * 60
            )
        }
        _timerState.value = TimerState.Pomodoro(next)
        if (!skipped) onTimerFinished?.invoke()
        onPomodoroPhaseChanged?.invoke(next.phase)
    }

    fun getElapsedMinutes(): Int = getElapsedSeconds() / 60

    fun getElapsedSeconds(): Int {
        updateElapsedTime()
        return when (val state = _timerState.value) {
            is TimerState.Pomodoro -> completedFocusSeconds + if (state.state.phase == PomodoroPhase.FOCUS) {
                state.state.settings.focusMinutes * 60 - state.state.timeRemainingSeconds
            } else 0
            is TimerState.Stopwatch -> state.elapsedSeconds
            is TimerState.StopwatchFinished -> state.elapsedSeconds
            is TimerState.Countdown -> state.totalSeconds - state.remainingSeconds
            is TimerState.CountdownFinished -> state.totalSeconds
            TimerState.Idle -> 0
        }
    }

    fun getElapsedMinutesForCurrentSession(): Int = getElapsedMinutes()

    fun getCurrentSessionSnapshot(): SessionSnapshot? {
        val minutes = roundUpToMinutes(getElapsedSeconds())
        if (minutes <= 0) return null
        return when (val state = _timerState.value) {
            is TimerState.Pomodoro -> SessionSnapshot(SessionType.POMODORO, minutes, state.state.completedPomodoros)
            is TimerState.Countdown, is TimerState.CountdownFinished -> SessionSnapshot(SessionType.COUNTDOWN, minutes)
            is TimerState.Stopwatch, is TimerState.StopwatchFinished -> SessionSnapshot(SessionType.STOPWATCH, minutes)
            TimerState.Idle -> null
        }
    }

    private fun roundUpToMinutes(seconds: Int): Int = if (seconds <= 0) 0 else (seconds + 59) / 60
}

private fun TimerState.isRunning(): Boolean = when (this) {
    is TimerState.Pomodoro -> state.isRunning
    is TimerState.Countdown -> isRunning
    is TimerState.Stopwatch -> isRunning
    else -> false
}
