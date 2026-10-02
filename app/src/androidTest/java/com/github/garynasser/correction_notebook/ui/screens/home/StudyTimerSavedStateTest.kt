package com.github.garynasser.correction_notebook.ui.screens.home

import android.os.Bundle
import android.os.Parcel
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.garynasser.correction_notebook.data.model.home.PomodoroPhase
import com.github.garynasser.correction_notebook.data.model.home.PomodoroSettings
import com.github.garynasser.correction_notebook.data.model.home.PomodoroState
import com.github.garynasser.correction_notebook.data.model.home.TimerState
import com.github.garynasser.correction_notebook.domain.usecase.StudyTimerManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class StudyTimerSavedStateTest {
    @Test
    fun everyTimerStateKeepsItsValuesThroughAndroidParcelSerialization() {
        val states = listOf(
            TimerState.Pomodoro(PomodoroState(PomodoroPhase.LONG_BREAK, 321, 4, true, 100, PomodoroSettings())),
            TimerState.Pomodoro(PomodoroState(PomodoroPhase.FOCUS, 1499, 4, false, 100, PomodoroSettings())),
            TimerState.Countdown(600, 333, true),
            TimerState.Countdown(600, 333, false),
            TimerState.CountdownFinished(600),
            TimerState.Stopwatch(123, true),
            TimerState.Stopwatch(123, false),
            TimerState.StopwatchFinished(123)
        )
        states.forEach { state ->
            val expected = SavedStudyTimer(
                "session-id", LocalDateTime.of(2026, 10, 2, 12, 30),
                StudyTimerManager.Checkpoint(state, 1_234_567L, 789L, 6_001)
            )
            val parcel = Parcel.obtain()
            try {
                parcel.writeBundle(expected.toBundle())
                parcel.setDataPosition(0)
                assertEquals(expected, parcel.readBundle(javaClass.classLoader)!!.toSavedStudyTimer())
            } finally {
                parcel.recycle()
            }
        }
    }

    @Test
    fun idleOrInvalidStateCannotCreateAnUnusableRestoredTimer() {
        assertNull(Bundle().toSavedStudyTimer())
        val valid = SavedStudyTimer(
            "id", LocalDateTime.now(),
            StudyTimerManager.Checkpoint(TimerState.Stopwatch(10, false), 100L, 0L, 0)
        ).toBundle()
        assertNull(Bundle(valid).apply { putString("type", "unknown") }.toSavedStudyTimer())
        assertNull(Bundle(valid).apply { putString("startedAt", "invalid date") }.toSavedStudyTimer())
        assertNull(Bundle(valid).apply { putLong("partial", 1_000L) }.toSavedStudyTimer())
        assertNull(Bundle(valid).apply { putInt("elapsed", -1) }.toSavedStudyTimer())
    }
}
