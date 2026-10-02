package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.model.home.PomodoroPhase
import com.github.garynasser.correction_notebook.data.model.home.PomodoroSettings
import com.github.garynasser.correction_notebook.data.model.home.TimerState
import com.github.garynasser.correction_notebook.domain.usecase.StudyTimerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class StudyTimerManagerTest {
    private fun withClock(block: (StudyTimerManager, (Long) -> Unit) -> Unit) {
        var millis = 0L
        val scope = CoroutineScope(Job() + Dispatchers.Unconfined)
        val manager = StudyTimerManager(scope) { millis }
        try {
            block(manager) { millis += it }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun delayedStopwatchTickUsesActualElapsedTime() = withClock { timer, advance ->
        timer.startStopwatch()
        advance(180_000)
        assertEquals(180, timer.getElapsedSeconds())
        assertEquals(3, timer.getCurrentSessionSnapshot()!!.durationMinutes)
    }

    @Test
    fun pauseExcludesWaitingTimeAndPreservesPartialSeconds() = withClock { timer, advance ->
        timer.startStopwatch()
        advance(800)
        timer.pause()
        advance(30_000)
        assertEquals(0, timer.getElapsedSeconds())
        timer.resume()
        advance(200)
        assertEquals(1, timer.getElapsedSeconds())
        assertEquals(1, timer.getCurrentSessionSnapshot()!!.durationMinutes)
    }

    @Test
    fun repeatedResumeCannotRestartTheClockOrCreateAnExtraTick() = withClock { timer, advance ->
        timer.startCountdown(1)
        advance(10_000)
        timer.resume()
        timer.resume()
        advance(5_000)
        assertEquals(15, timer.getElapsedSeconds())
        timer.pause()
        assertEquals(45, (timer.timerState.value as TimerState.Countdown).remainingSeconds)
    }

    @Test
    fun delayedCountdownFinishesExactlyOnceAndStopsCounting() = withClock { timer, advance ->
        var alarms = 0
        timer.onTimerFinished = { alarms++ }
        timer.startCountdown(1)
        advance(90_000)
        assertEquals(60, timer.getElapsedSeconds())
        assertTrue(timer.timerState.value is TimerState.CountdownFinished)
        advance(90_000)
        timer.resume()
        assertEquals(60, timer.getElapsedSeconds())
        assertEquals(1, alarms)
    }

    @Test
    fun delayedPomodoroCatchesUpPhasesWithoutCountingBreaksAsStudy() = withClock { timer, advance ->
        val phases = mutableListOf<PomodoroPhase>()
        timer.onPomodoroPhaseChanged = { phases += it }
        timer.startPomodoro(PomodoroSettings(1, 1, 2, 2))
        advance(225_000)
        assertEquals(120, timer.getElapsedSeconds())
        val state = (timer.timerState.value as TimerState.Pomodoro).state
        assertEquals(PomodoroPhase.LONG_BREAK, state.phase)
        assertEquals(75, state.timeRemainingSeconds)
        assertEquals(2, state.completedPomodoros)
        assertEquals(2, timer.getCurrentSessionSnapshot()!!.durationMinutes)
        assertEquals(listOf(PomodoroPhase.SHORT_BREAK, PomodoroPhase.FOCUS, PomodoroPhase.LONG_BREAK), phases)
    }

    @Test
    fun skippedFocusKeepsSecondsButDoesNotCountCompletedPomodorosOrRingAlarm() = withClock { timer, advance ->
        var alarms = 0
        timer.onTimerFinished = { alarms++ }
        timer.startPomodoro(PomodoroSettings(1, 1, 1, 4))
        advance(30_000)
        timer.skip()
        timer.skip()
        advance(30_000)
        timer.skip()
        assertEquals(60, timer.getElapsedSeconds())
        val snapshot = timer.getCurrentSessionSnapshot()!!
        assertEquals(1, snapshot.durationMinutes)
        assertEquals(0, snapshot.pomodoroCount)
        assertEquals(0, alarms)
    }

    @Test
    fun resetProducesAPausedFreshTimerAndStopClearsTheFinishedState() = withClock { timer, advance ->
        timer.startStopwatch()
        advance(61_000)
        assertEquals(2, timer.getCurrentSessionSnapshot()!!.durationMinutes)
        timer.reset()
        assertEquals(TimerState.Stopwatch(0, false), timer.timerState.value)
        assertNull(timer.getCurrentSessionSnapshot())
        advance(20_000)
        timer.resume()
        advance(1_000)
        assertEquals(1, timer.getElapsedSeconds())
        timer.stop()
        assertEquals(TimerState.Idle, timer.timerState.value)
        assertNull(timer.getCurrentSessionSnapshot())
    }

    @Test
    fun zeroLengthPomodoroPreferencesCannotCreateAnInfinitePhaseLoop() = withClock { timer, advance ->
        timer.startPomodoro(PomodoroSettings(0, 0, 0, 0))
        advance(125_000)
        assertEquals(65, timer.getElapsedSeconds())
        assertEquals(PomodoroPhase.FOCUS, (timer.timerState.value as TimerState.Pomodoro).state.phase)
    }

    @Test
    fun skippingFocusStartsTheBreakTimer() = runBlocking {
        val manager = StudyTimerManager(this)
        manager.startPomodoro(
            PomodoroSettings(
                focusMinutes = 1,
                shortBreakMinutes = 1,
                longBreakMinutes = 1,
                pomodorosBeforeLongBreak = 4
            )
        )

        manager.skip()
        delay(1_100)

        val state = manager.timerState.value as TimerState.Pomodoro
        assertEquals(PomodoroPhase.SHORT_BREAK, state.state.phase)
        assertTrue(state.state.timeRemainingSeconds < 60)
        manager.pause()
    }

    @Test
    fun skippingCountdownCannotBeOverwrittenByThePreviousTimerJob() = runBlocking {
        val manager = StudyTimerManager(this)
        manager.startCountdown(minutes = 1)

        manager.skip()
        delay(1_100)

        assertTrue(manager.timerState.value is TimerState.CountdownFinished)
    }
}
