package com.github.garynasser.correction_notebook.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.garynasser.correction_notebook.data.model.home.TimerState
import com.github.garynasser.correction_notebook.domain.usecase.StudyTimerManager
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ImmersiveStudyScreenTest {
    @get:Rule
    val compose = createComposeRule()
    private val timerScope = CoroutineScope(Job() + Dispatchers.Main.immediate)

    @After
    fun cleanup() = timerScope.cancel()

    @Test
    fun longTimerDigitsStayCenteredOnOneLineAtLargeFontScale() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                CorrectionNotebookTheme {
                    Box(Modifier.width(320.dp)) {
                        TimerDisplay(TimerState.Stopwatch(600_000, false), compactMode = false)
                    }
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("10000:00")
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        layouts.forEach {
            assertFalse("Timer digits must not clip or wrap", it.hasVisualOverflow)
            assertEquals(1, it.lineCount)
            assertEquals(TextAlign.Center, it.layoutInput.style.textAlign)
        }
    }

    @Test
    fun controlsHideDuringTickingAndShowWhenPaused() {
        val timer = StudyTimerManager(timerScope)
        compose.setContent {
            CorrectionNotebookTheme {
                ImmersiveStudyScreen(timer, onExit = {}, soundEnabled = false, vibrationEnabled = false)
            }
        }
        compose.runOnIdle { timer.startStopwatch() }
        compose.onNodeWithContentDescription("暂停").assertExists()
        compose.waitUntil(7_000) {
            compose.onAllNodesWithContentDescription("暂停").fetchSemanticsNodes().isEmpty()
        }
        compose.runOnIdle { timer.pause() }
        compose.onNodeWithContentDescription("继续").assertExists()
        compose.onNodeWithContentDescription("继续").performClick()
        compose.onNodeWithContentDescription("暂停").assertExists()
    }

    @Test
    fun finishedCountdownHasAnExitActionAndUsesTheSaveResetCallback() {
        var millis = 0L
        val timer = StudyTimerManager(timerScope) { millis }
        var exits = 0
        var resets = 0
        compose.setContent {
            CorrectionNotebookTheme {
                ImmersiveStudyScreen(
                    timer, onExit = {}, onStop = { exits++ }, onReset = { resets++ },
                    soundEnabled = false, vibrationEnabled = false
                )
            }
        }
        compose.runOnIdle {
            timer.startCountdown(1)
            millis = 60_000
            timer.getElapsedSeconds()
        }
        compose.onNodeWithText("倒计时结束").assertExists()
        compose.onNodeWithContentDescription("重置").performClick()
        compose.onNodeWithText("停止提醒").assertDoesNotExist()
        compose.onNodeWithContentDescription("结束").performClick()
        compose.runOnIdle {
            assertEquals(1, resets)
            assertEquals(1, exits)
        }
    }
}
