package com.github.garynasser.correction_notebook.ui.components

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.remote.cas.CasCodeKind
import com.github.garynasser.correction_notebook.data.remote.cas.CasCodePrompt
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class CasChallengeDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun captchaImageAndCodeAreUsableAndDuplicateSubmitIsDisabled() {
        val image = Bitmap.createBitmap(120, 40, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val bytes = ByteArrayOutputStream().also { image.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        image.recycle()
        val submissions = mutableListOf<String>()
        compose.setContent { CorrectionNotebookTheme { CasChallengeDialog(CasCodePrompt(CasCodeKind.CAPTCHA, image = bytes), submissions::add, {}) } }
        compose.onNodeWithContentDescription("学校图形验证码").assertIsDisplayed()
        compose.onNodeWithText("验证").assertIsNotEnabled()
        compose.onNodeWithTag("cas-code-input").performTextInput(" aB42 ")
        compose.onNodeWithText("验证").performClick().assertIsNotEnabled()
        compose.runOnIdle { assertEquals(listOf("aB42"), submissions) }
    }

    @Test fun replacingPromptDiscardsThePreviousCodeAndShowsRetryError() {
        val state = mutableStateOf(CasCodePrompt(CasCodeKind.SMS, maskedPhone = "138****8000"))
        compose.setContent { CorrectionNotebookTheme { CasChallengeDialog(state.value, {}, {}) } }
        compose.onNodeWithTag("cas-code-input").performTextInput("000000")
        compose.runOnIdle { state.value = CasCodePrompt(CasCodeKind.SMS, maskedPhone = "138****8000", error = "验证码错误") }
        compose.onNodeWithText("验证码错误").assertIsDisplayed()
        compose.onNodeWithTag("cas-code-input").assert(SemanticsMatcher.expectValue(SemanticsProperties.EditableText, AnnotatedString("")))
        compose.onNodeWithText("验证").assertIsNotEnabled()
    }

    @Test fun lightDialogKeepsActionsAndLongErrorReachableAtLargeFont() = checkLayout(false)
    @Test fun darkDialogKeepsActionsAndLongErrorReachableAtLargeFont() = checkLayout(true)

    private fun checkLayout(dark: Boolean) {
        val error = "验证码错误或已过期，请确认后重新输入"
        var cancels = 0
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 2f)) {
                CorrectionNotebookTheme(darkTheme = dark, dynamicColor = false) {
                    val prompt = remember { CasCodePrompt(CasCodeKind.SMS, maskedPhone = "138****8000", error = error) }
                    CasChallengeDialog(prompt, {}, { cancels++ })
                }
            }
        }
        compose.onNodeWithTag("cas-code-input").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("验证").assertIsDisplayed()
        compose.onNodeWithText("取消").assertIsDisplayed()
        compose.onNodeWithText(error).performScrollTo().assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(error).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse(layouts.single().hasVisualOverflow)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "qa-cas-dialog-${if (dark) "dark" else "light"}.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle { assertEquals(1, cancels) }
    }
}
