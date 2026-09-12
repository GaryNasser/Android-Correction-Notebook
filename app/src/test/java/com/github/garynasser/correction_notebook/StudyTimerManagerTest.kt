package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.model.home.PomodoroPhase
import com.github.garynasser.correction_notebook.data.model.home.PomodoroSettings
import com.github.garynasser.correction_notebook.data.model.home.TimerState
import com.github.garynasser.correction_notebook.domain.usecase.StudyTimerManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StudyTimerManagerTest {
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
}
