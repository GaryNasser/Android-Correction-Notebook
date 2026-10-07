package com.github.garynasser.correction_notebook.ui.screens.home

import android.accessibilityservice.AccessibilityServiceInfo
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.model.home.ScheduleEvent
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import java.io.File
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class AddScheduleKeyboardLayoutTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightEditorKeepsActionsAndFocusedFieldsAboveTheKeyboard() = checkKeyboard(false)
    @Test fun darkEditorKeepsActionsAndFocusedFieldsAboveTheKeyboard() = checkKeyboard(true)

    @Test fun tappingOutsideCannotDismissASavingDraft() {
        val saving = mutableStateOf(true)
        var dismissals = 0
        compose.setContent {
            CorrectionNotebookTheme {
                AddScheduleDialog(isSaving = saving.value, onDismiss = { dismissals++ }, onAdd = {})
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

    private fun checkKeyboard(dark: Boolean) {
        var added: ScheduleEvent? = null
        val date = LocalDate.of(2026, 11, 13)
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                CorrectionNotebookTheme(darkTheme = dark) {
                    AddScheduleDialog(initialDate = date, onDismiss = {}, onAdd = { added = it })
                }
            }
        }
        compose.onNodeWithText("活动标题").performTextReplacement(TITLE)
        compose.onNodeWithText("活动标题").performClick()
        capture(if (dark) "dark-title" else "light-title")
        assertAboveKeyboard("添加日程", "保存", "取消", TITLE)
        compose.onNodeWithText("地点").performScrollTo().performTextReplacement(LOCATION)
        compose.onNodeWithText("地点").performClick()
        capture(if (dark) "dark-location" else "light-location")
        assertAboveKeyboard("添加日程", "保存", "取消", LOCATION)
        compose.onNodeWithText("备注").performScrollTo().performTextReplacement(NOTES)
        compose.onNodeWithText("备注").performClick()
        capture(if (dark) "dark-notes" else "light-notes")
        assertAboveKeyboard("添加日程", "保存", "取消", NOTES)
        compose.onNodeWithContentDescription("开始小时").performScrollTo().performTextReplacement("12")
        compose.onNodeWithContentDescription("结束分钟").performScrollTo().performClick()
        capture(if (dark) "dark-time" else "light-time")
        assertAboveKeyboard("添加日程", "保存", "取消", "结束分钟")
        save().performClick()
        compose.onNodeWithText("结束时间需要晚于开始时间").assertIsDisplayed()
        compose.runOnIdle { assertNull(added) }
        capture(if (dark) "dark-validation" else "light-validation")
        assertAboveKeyboard("添加日程", "保存", "取消", "结束时间需要晚于开始时间")
        compose.onNodeWithContentDescription("结束小时").performScrollTo().performTextReplacement("13")
        compose.onNodeWithText("结束时间需要晚于开始时间").assertDoesNotExist()
        save().performClick()
        compose.runOnIdle {
            val event = checkNotNull(added)
            assertEquals(TITLE, event.title)
            assertEquals(LOCATION, event.location)
            assertEquals(NOTES, event.description)
            assertEquals(date.atTime(12, 0), event.startAt)
            assertEquals(date.atTime(13, 0), event.endAt)
        }
        capture(if (dark) "dark-saved" else "light-saved")
    }

    private fun save() = compose.onNode(hasText("保存") or hasContentDescription("保存"))

    private fun assertAboveKeyboard(vararg labels: String) {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val info = automation.serviceInfo
        val previousFlags = info.flags
        info.flags = info.flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        automation.serviceInfo = info
        try {
            val keyboard = automation.windows.firstOrNull { it.type == AccessibilityWindowInfo.TYPE_INPUT_METHOD }
            assertNotNull("The test requires a visible native keyboard", keyboard)
            val keyboardBounds = Rect().also { keyboard!!.getBoundsInScreen(it) }
            val root = automation.windows.filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION }
                .mapNotNull { it.root }.firstOrNull { findText(it, "添加日程") != null }
            assertNotNull("The native schedule editor must be visible", root)
            labels.forEach { label ->
                val node = findText(root!!, label)
                assertNotNull("$label must be present in the native dialog", node)
                val bounds = Rect().also { node!!.getBoundsInScreen(it) }
                assertTrue("$label must fit above the keyboard: $bounds, keyboard=$keyboardBounds",
                    !bounds.isEmpty && bounds.top >= 0 && bounds.bottom <= keyboardBounds.top)
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
        Thread.sleep(300) // Android keyboard/window animation is outside Compose's clock.
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "qa-add-schedule-$name.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    companion object {
        private const val TITLE = "复习计算理论与算法分析设计"
        private const val LOCATION = "文萃楼 M134"
        private const val NOTES = "整理第三章课堂笔记\n核对例题的证明步骤\n完成课后练习"
    }
}
