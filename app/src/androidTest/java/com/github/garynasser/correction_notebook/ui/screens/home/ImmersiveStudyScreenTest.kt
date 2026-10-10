package com.github.garynasser.correction_notebook.ui.screens.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
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
import java.io.File

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
    fun uploadedLightBackgroundKeepsTimerTextReadableAndControlsUsable() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val image = File.createTempFile("qa-light-background-", ".png", instrumentation.targetContext.cacheDir)
        val bitmap = Bitmap.createBitmap(400, 600, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(android.graphics.Color.WHITE)
            Canvas(bitmap).drawRect(200f, 0f, 400f, 600f, Paint().apply { color = android.graphics.Color.rgb(200, 200, 200) })
            image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
        var millis = 0L
        var exits = 0
        val timer = StudyTimerManager(timerScope) { millis }
        try {
            compose.setContent {
                CorrectionNotebookTheme(darkTheme = InstrumentationRegistry.getArguments().getString("qaDark") == "true",
                    dynamicColor = false) {
                    ImmersiveStudyScreen(timer, onExit = {}, onStop = { exits++ }, backgroundImageUri = image.toURI().toString(),
                        soundEnabled = false, vibrationEnabled = false)
                }
            }
            compose.runOnIdle {
                timer.startStopwatch()
                millis = 12_000
                timer.pause()
            }
            // Two different light regions prove Coil loaded the image rather than showing the black fallback.
            compose.waitUntil(5_000) {
                val pixels = compose.onRoot().captureToImage().toPixelMap()
                val left = pixels[2, pixels.height / 3].red
                val right = pixels[pixels.width - 3, pixels.height / 3].red
                left > 0.1f && right > 0.08f && left - right > 0.04f
            }
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            try {
                File(instrumentation.targetContext.getExternalFilesDir(null), "qa-immersive-light-background.png")
                    .outputStream().use { screenshot.compress(Bitmap.CompressFormat.PNG, 100, it) }
            } finally { screenshot.recycle() }
            val pixels = compose.onRoot().captureToImage().toPixelMap()
            val background = pixels[2, pixels.height / 3]
            listOf("00:12", "正计时", "已记录 00:12").forEach { text ->
                val layouts = mutableListOf<TextLayoutResult>()
                val nodes = compose.onAllNodesWithText(text)
                assertTrue(nodes.fetchSemanticsNodes().isNotEmpty())
                repeat(nodes.fetchSemanticsNodes().size) { index ->
                    nodes[index].assertIsDisplayed()
                        .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                }
                assertTrue(layouts.isNotEmpty())
                layouts.forEach { layout ->
                    assertFalse("Timer text must not clip", layout.hasVisualOverflow)
                    val foreground = layout.layoutInput.style.color.compositeOver(background)
                    val ratio = (foreground.luminance() + 0.05f) / (background.luminance() + 0.05f)
                    assertTrue("$text needs readable contrast on a light photo, got $ratio", ratio >= 4.5f)
                }
            }
            compose.onNodeWithContentDescription("继续").assertIsDisplayed().performClick()
            compose.runOnIdle { assertTrue((timer.timerState.value as TimerState.Stopwatch).isRunning) }
            compose.onNodeWithContentDescription("暂停").performClick()
            compose.runOnIdle { assertFalse((timer.timerState.value as TimerState.Stopwatch).isRunning) }
            compose.onNodeWithContentDescription("结束").assertIsDisplayed().performClick()
            compose.runOnIdle { assertEquals(1, exits) }
        } finally { image.delete() }
    }

    @Test
    fun controlsHideDuringTickingAndShowWhenPaused() {
        var millis = 0L
        val timer = StudyTimerManager(timerScope) { millis }
        compose.setContent {
            CorrectionNotebookTheme {
                ImmersiveStudyScreen(timer, onExit = {}, soundEnabled = false, vibrationEnabled = false)
            }
        }
        compose.runOnIdle { timer.startStopwatch() }
        compose.onNodeWithContentDescription("暂停").assertExists()
        repeat(3) { second ->
            compose.mainClock.advanceTimeBy(1_000)
            compose.runOnIdle {
                millis += 1_000
                assertEquals(second + 1, timer.getElapsedSeconds())
            }
            compose.onNodeWithContentDescription("暂停").assertExists()
        }
        compose.mainClock.advanceTimeBy(600)
        compose.onNodeWithContentDescription("暂停").assertDoesNotExist()
        compose.runOnIdle { timer.pause() }
        compose.onNodeWithContentDescription("继续").assertExists()
        compose.onNodeWithContentDescription("继续").performClick()
        compose.onNodeWithContentDescription("暂停").assertExists()
        compose.mainClock.advanceTimeBy(3_600)
        compose.onNodeWithContentDescription("暂停").assertDoesNotExist()
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
