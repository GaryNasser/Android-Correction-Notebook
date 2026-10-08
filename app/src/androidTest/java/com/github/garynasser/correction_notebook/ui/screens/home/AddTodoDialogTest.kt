package com.github.garynasser.correction_notebook.ui.screens.home

import android.graphics.Bitmap
import android.graphics.Rect
import android.accessibilityservice.AccessibilityServiceInfo
import android.view.accessibility.AccessibilityWindowInfo
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.model.home.TodoItem
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AddTodoDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun resubmittingARestoredDraftKeepsItsIdentity() {
        val restoration = StateRestorationTester(compose)
        val submitted = mutableListOf<TodoItem>()
        val visible = mutableStateOf(true)
        restoration.setContent {
            CorrectionNotebookTheme {
                if (visible.value) AddTodoDialog(onDismiss = {}, onAdd = { submitted += it })
            }
        }
        compose.onNodeWithText("标题").performTextReplacement("复习课程")
        compose.onNodeWithContentDescription("添加").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("标题").performTextReplacement("更新后的复习安排")
        compose.onNodeWithContentDescription("添加").performClick()
        compose.runOnIdle {
            assertEquals(2, submitted.size)
            assertEquals(submitted.first().id, submitted.last().id)
            assertEquals("更新后的复习安排", submitted.last().title)
            visible.value = false
        }
        compose.waitForIdle()
        compose.runOnIdle { visible.value = true }
        compose.onNodeWithContentDescription("添加").assertIsNotEnabled()
        compose.onNodeWithText("标题").performTextReplacement("另一条待办")
        compose.onNodeWithContentDescription("添加").performClick()
        compose.runOnIdle {
            assertEquals(3, submitted.size)
            assertNotEquals(submitted.first().id, submitted.last().id)
        }
    }

    @Test fun restoringTheDialogKeepsBothFieldsUntilSubmission() {
        var added: TodoItem? = null
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CorrectionNotebookTheme {
                AddTodoDialog(onDismiss = {}, onAdd = { added = it })
            }
        }
        compose.onNodeWithText("标题").performTextReplacement("  复习矩阵分析  ")
        compose.onNodeWithText("备注（可选）").performTextReplacement("  带上课堂笔记\n核对证明步骤  ")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription("添加").performClick()
        compose.runOnIdle {
            assertEquals("复习矩阵分析", added?.title)
            assertEquals("带上课堂笔记\n核对证明步骤", added?.description)
        }
    }

    @Test fun blankTitlesCannotSubmitThroughTheButtonOrKeyboard() {
        var additions = 0
        compose.setContent {
            CorrectionNotebookTheme { AddTodoDialog(onDismiss = {}, onAdd = { additions++ }) }
        }
        compose.onNodeWithText("标题").performTextReplacement("   ")
        compose.onNodeWithContentDescription("添加").assertIsNotEnabled()
        compose.onNodeWithText("备注（可选）").performImeAction()
        compose.runOnIdle { assertEquals(0, additions) }
    }

    @Test fun savingKeepsTheDraftAndBlocksEditingAndDismissal() {
        val saving = mutableStateOf(false)
        var dismissals = 0
        var additions = 0
        compose.setContent {
            CorrectionNotebookTheme {
                AddTodoDialog(saving.value, onDismiss = { dismissals++ }, onAdd = { additions++ })
            }
        }
        compose.onNodeWithText("标题").performTextReplacement("提交中的待办")
        compose.onNodeWithText("备注（可选）").performTextReplacement("保留这段备注")
        compose.runOnIdle { saving.value = true }
        listOf("标题", "备注（可选）").forEach { compose.onNodeWithText(it).assertIsNotEnabled() }
        compose.onNodeWithContentDescription("取消").assertIsNotEnabled()
        compose.onNodeWithContentDescription("添加中").assertIsNotEnabled()
        Espresso.pressBack()
        compose.runOnIdle {
            assertEquals(0, dismissals)
            assertEquals(0, additions)
            saving.value = false
        }
        compose.onNodeWithText("提交中的待办").assertExists()
        compose.onNodeWithText("保留这段备注").assertExists()
        compose.onNodeWithContentDescription("取消").performClick()
        compose.runOnIdle { assertEquals(1, dismissals) }
    }

    @Test fun titleAndNotesRespectTheirDisplayedLimits() {
        var added: TodoItem? = null
        compose.setContent {
            CorrectionNotebookTheme { AddTodoDialog(onDismiss = {}, onAdd = { added = it }) }
        }
        compose.onNodeWithText("标题").performTextReplacement("课".repeat(61))
        compose.onNodeWithText("备注（可选）").performTextReplacement("复习 ".repeat(61))
        compose.onNodeWithText("60/60").assertExists()
        compose.onNodeWithText("180/180").assertExists()
        compose.onNodeWithText("备注（可选）").performImeAction()
        compose.runOnIdle {
            assertEquals("课".repeat(60), added?.title)
            assertEquals("复习 ".repeat(60).trim(), added?.description)
        }
    }

    @Test fun tappingOutsideOnlyDismissesWhenNotSaving() {
        val saving = mutableStateOf(true)
        var dismissals = 0
        compose.setContent {
            CorrectionNotebookTheme {
                AddTodoDialog(saving.value, onDismiss = { dismissals++ }, onAdd = {})
            }
        }
        compose.onNode(isDialog()).performTouchInput { click(Offset(1f, center.y)) }
        compose.runOnIdle {
            assertEquals(0, dismissals)
            saving.value = false
        }
        compose.onNode(isDialog()).performTouchInput { click(Offset(1f, center.y)) }
        compose.runOnIdle { assertEquals(1, dismissals) }
    }

    @Test fun shortLightDialogKeepsNotesAndActionsReachableWithKeyboard() = checkLayout(false)
    @Test fun shortDarkDialogKeepsNotesAndActionsReachableWithKeyboard() = checkLayout(true)

    private fun checkLayout(dark: Boolean) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                CorrectionNotebookTheme(darkTheme = dark) { AddTodoDialog(onDismiss = {}, onAdd = {}) }
            }
        }
        compose.onNodeWithText("标题").performTextReplacement("复习计算理论与算法分析设计")
        compose.onNodeWithText("标题").performClick()
        capture(if (dark) "dark-keyboard-title" else "light-keyboard-title")
        compose.onNodeWithText("备注（可选）").performScrollTo().performClick().assertIsFocused()
        capture(if (dark) "dark-keyboard-before-scroll" else "light-keyboard-before-scroll")
        assertActionsAboveKeyboard()
        compose.onNodeWithText("输入备注", useUnmergedTree = true).assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                val layouts = mutableListOf<TextLayoutResult>()
                assertTrue(action(layouts))
                layouts.forEach {
                    assertFalse("The notes placeholder must fit vertically", it.didOverflowHeight)
                    assertEquals("输入备注".length, it.getLineEnd(it.lineCount - 1))
                    repeat(it.lineCount) { line ->
                        assertFalse(it.isLineEllipsized(line))
                        // Intrinsic CJK widths can round down by a fraction of a pixel.
                        assertTrue("The notes placeholder must fit horizontally", it.getLineRight(line) <= it.size.width + 1f)
                    }
                }
            }
        compose.onNodeWithText("0/180", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("备注（可选）").performTextReplacement("整理第三章课堂笔记\n核对例题的证明步骤\n完成课后练习")
        compose.onNodeWithContentDescription("添加").assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithContentDescription("取消").assertIsDisplayed()
        capture(if (dark) "dark-keyboard-notes" else "light-keyboard-notes")
        assertActionsAboveKeyboard()
        Espresso.pressBack()
        compose.onNodeWithText("标题").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("备注（可选）").performScrollTo().assertIsDisplayed()
        capture(if (dark) "dark-notes" else "light-notes")
    }

    private fun assertActionsAboveKeyboard() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val info = automation.serviceInfo
        val previousFlags = info.flags
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = info
        try {
            val keyboard = automation.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            assertNotNull("The test must run with a visible native keyboard", keyboard)
            val keyboardBounds = Rect().also { keyboard!!.getBoundsInScreen(it) }
            val root = automation.windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                .mapNotNull { it.root }.firstOrNull { findText(it, "添加待办") != null }
            assertNotNull("The native todo dialog must remain visible", root)
            listOf("添加待办", "添加", "取消").forEach { text ->
                val node = findText(root!!, text)
                assertNotNull("$text must be visible in the native dialog window", node)
                val bounds = Rect().also { node!!.getBoundsInScreen(it) }
                assertTrue("$text must be on screen above the keyboard: $bounds, keyboard=$keyboardBounds",
                    bounds.top >= 0 && bounds.bottom <= keyboardBounds.top && !bounds.isEmpty)
            }
        } finally {
            info.flags = previousFlags
            automation.serviceInfo = info
        }
    }

    private fun findText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.text?.toString() == text || node.contentDescription?.toString() == text) return node
        repeat(node.childCount) { index ->
            node.getChild(index)?.let { child -> findText(child, text)?.let { return it } }
        }
        return null
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        Thread.sleep(300) // Wait for Android's keyboard/window animation, not Compose animation.
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "qa-add-todo-$name.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }
}
