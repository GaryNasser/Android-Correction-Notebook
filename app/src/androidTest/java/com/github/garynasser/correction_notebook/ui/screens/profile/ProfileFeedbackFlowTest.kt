package com.github.garynasser.correction_notebook.ui.screens.profile

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.MainActivity
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ProfileFeedbackFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private val email = "fangmierui@gmail.com"

    @Test fun feedbackCopiesTheCompleteContactAddress() {
        openFeedback()
        compose.onNodeWithContentDescription("复制邮箱").performClick()
        compose.runOnIdle {
            val clipboard = compose.activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            assertEquals(email, clipboard.primaryClip?.getItemAt(0)?.text?.toString())
        }
        compose.onNodeWithContentDescription("已复制邮箱").assertIsDisplayed()
        compose.onNodeWithText("确定").performClick()
        compose.onNodeWithText(email).assertDoesNotExist()
        openFeedback(fromSettings = true)
        compose.onNodeWithContentDescription("复制邮箱").assertIsDisplayed()
        Espresso.pressBack()
        compose.onNodeWithText(email).assertDoesNotExist()
    }

    @Test fun feedbackStaysOpenAfterActivityRecreation() {
        openFeedback()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText(email).assertIsDisplayed()
        compose.onNodeWithText("确定").performClick()
        compose.onNodeWithText(email).assertDoesNotExist()
    }

    @Test fun feedbackKeepsAllTextAndActionsVisible() {
        openFeedback()
        capture()
        listOf("如有 bug 或功能建议，请联系开发者邮箱：", email, "确定").forEach { text ->
            compose.onNodeWithText(text).assertIsDisplayed()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                    val layouts = mutableListOf<TextLayoutResult>()
                    assertTrue(action(layouts))
                    layouts.forEach {
                        assertFalse("$text must fit vertically", it.didOverflowHeight)
                        assertEquals(text.length, it.getLineEnd(it.lineCount - 1))
                        repeat(it.lineCount) { line ->
                            assertFalse(it.isLineEllipsized(line))
                            // Intrinsic CJK text widths can round down by a fraction of a pixel.
                            assertTrue("$text must fit horizontally", it.getLineRight(line) <= it.size.width + 1f)
                        }
                    }
                }
        }
        capture()
    }

    private fun capture() {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        Thread.sleep(250) // Android's dialog window fade is outside the Compose test clock.
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "qa-profile-feedback.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    private fun openFeedback(fromSettings: Boolean = false) {
        if (!fromSettings) compose.onNodeWithContentDescription("设置").performClick()
        compose.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText("帮助与反馈"))
        compose.onNodeWithText("帮助与反馈").performClick()
        compose.onNodeWithText(email).assertIsDisplayed()
    }
}
