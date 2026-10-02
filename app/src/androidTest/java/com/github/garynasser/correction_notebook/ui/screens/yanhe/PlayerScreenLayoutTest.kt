package com.github.garynasser.correction_notebook.ui.screens.yanhe

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File

class PlayerScreenLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun longErrorInShortLandscapeKeepsBackAndRetryInsideTheScreen() {
        var retries = 0
        var backs = 0
        val message = "The video server is temporarily unavailable. ".repeat(20)
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.3f)) {
                CorrectionNotebookTheme {
                    Box(Modifier.safeDrawingPadding().width(320.dp).height(240.dp).testTag("player")) {
                        PlayerScreenContent(
                            null, PlayState.Error(message),
                            "Lecture recording", "Linear algebra", { backs++ }, { retries++ }
                        )
                    }
                }
            }
        }
        val bounds = compose.onNodeWithTag("player").fetchSemanticsNode().boundsInRoot
        val retry = compose.onNodeWithText("重试").fetchSemanticsNode().boundsInRoot
        val back = compose.onNodeWithContentDescription("返回").fetchSemanticsNode().boundsInRoot
        assertTrue("Retry must have a visible label", retry.width > 0 && retry.height > 0)
        assertTrue("Retry must not be clipped off screen", bounds.contains(retry.topLeft) && bounds.contains(retry.bottomRight))
        assertTrue("Retry must stay below the header", retry.top >= back.bottom)
        val scroll = compose.onNodeWithText(message).fetchSemanticsNode().config[SemanticsProperties.VerticalScrollAxisRange]
        assertTrue("Long error details must remain scrollable", scroll.maxValue() > 0)
        compose.onNodeWithText(message).performTouchInput { swipeUp() }
        compose.runOnIdle { assertTrue(scroll.value() > 0) }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), "qa-player-error.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
        compose.onNodeWithText("重试").performClick()
        compose.onNodeWithContentDescription("返回").performClick()
        compose.runOnIdle {
            assertEquals(1, retries)
            assertEquals(1, backs)
        }
    }
}
