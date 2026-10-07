package com.github.garynasser.correction_notebook.ui.screens.home

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.model.home.PomodoroSettings
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class PomodoroSettingsDialogTest {
    @get:Rule val compose = createComposeRule()
    private val labels = listOf("学习时长", "短休息", "长休息", "循环轮数")
    private val edited = PomodoroSettings(45, 8, 22, 6)
    private val sliders = SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo)

    @Test fun restoringTheDialogPreservesEveryChangedValueUntilSaving() {
        var saved: PomodoroSettings? = null
        val restoration = show(onSave = { saved = it })
        changeValues()
        restoration.emulateSavedInstanceStateRestore()
        capture("restored")
        listOf("45 分钟", "8 分钟", "22 分钟", "6 轮").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle { assertEquals(edited, saved) }
    }

    @Test fun everySliderNamesTheSettingAndAnnouncesItsActualValue() {
        show()
        labels.zip(listOf("25 分钟", "5 分钟", "15 分钟", "4 轮")).forEach { (label, value) ->
            // Slider semantics expose its 44dp thumb, not the surrounding 48dp layout.
            compose.onNodeWithContentDescription(label).performScrollTo().assertIsDisplayed()
                .assertHeightIsAtLeast(44.dp)
                .assert(SemanticsMatcher.keyIsDefined(SemanticsActions.SetProgress))
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, value))
            val bounds = compose.onNodeWithContentDescription(label).getBoundsInRoot()
            assertTrue("$label must be fully visible after scrolling", bounds.bottom - bounds.top >= 43.5.dp)
        }
    }

    @Test fun cancellingAChangedDraftDoesNotSaveIt() {
        var saves = 0
        var dismissals = 0
        show(onSave = { saves++ }, onDismiss = { dismissals++ })
        changeValues()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle {
            assertEquals(0, saves)
            assertEquals(1, dismissals)
        }
    }

    @Test fun lightShortDialogKeepsEverySettingAndActionReachable() = checkLayout(false)
    @Test fun darkShortDialogKeepsEverySettingAndActionReachable() = checkLayout(true)

    private fun checkLayout(dark: Boolean) {
        show(dark = dark)
        labels.zip(listOf("25 分钟", "5 分钟", "15 分钟", "4 轮")).forEach { (label, value) ->
            compose.onNodeWithText(label).performScrollTo().assertIsDisplayed()
            listOf(label, value).forEach { text ->
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertTrue(layouts.isNotEmpty())
                val layout = layouts.single()
                assertFalse("$text must fit: ${layout.size}, width overflow=${layout.didOverflowWidth}, height overflow=${layout.didOverflowHeight}", layout.hasVisualOverflow)
            }
        }
        compose.onNodeWithText("保存").assertIsDisplayed()
        compose.onNodeWithText("取消").assertIsDisplayed()
        compose.onNodeWithContentDescription(labels.last()).performScrollTo().assertIsDisplayed()
        capture(if (dark) "short-dark-bottom" else "short-light-bottom")
        compose.onNodeWithText("学习时长").performScrollTo()
        capture(if (dark) "short-dark-top" else "short-light-top")
    }

    private fun changeValues() {
        listOf(45f, 8f, 22f, 6f).forEachIndexed { index, value ->
            compose.onAllNodes(sliders)[index].performScrollTo()
                .performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(value)) }
        }
    }

    private fun show(
        dark: Boolean = false,
        onSave: (PomodoroSettings) -> Unit = {},
        onDismiss: () -> Unit = {}
    ): StateRestorationTester = StateRestorationTester(compose).also { restoration ->
        restoration.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                CorrectionNotebookTheme(darkTheme = dark, dynamicColor = false) {
                    PomodoroSettingsDialog(PomodoroSettings(), onDismiss, onSave)
                }
            }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "qa-pomodoro-settings-$name.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }
}
