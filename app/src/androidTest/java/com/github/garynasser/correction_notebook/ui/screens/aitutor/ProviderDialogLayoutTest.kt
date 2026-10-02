package com.github.garynasser.correction_notebook.ui.screens.aitutor

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performClick
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class ProviderDialogLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun lightDialogActionsFitAtNarrowWidthAndLargeFont() = checkActions(dark = false)

    @Test
    fun darkDialogActionsFitAtNarrowWidthAndLargeFont() = checkActions(dark = true)

    private fun checkActions(dark: Boolean) {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3.375f, 1.3f)) {
                CorrectionNotebookTheme(darkTheme = dark, dynamicColor = false) {
                    ProviderDialog(AITutorUiState(), onDismiss = {}, onSave = {}, onFetchModels = {},
                        onTestProvider = {}, onClearProviderStatus = {}, onActivate = {}, onDelete = {})
                }
            }
        }
        compose.onNodeWithText("新增").assertIsDisplayed()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("高级参数"))
        capture(dark)
        listOf("获取模型", "测试连接", "保存", "取消", "新增", "高级参数").forEach { text ->
            if (text in listOf("获取模型", "测试连接", "高级参数")) {
                compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(text))
            }
            compose.onNodeWithText(text).assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertFalse(layouts.isEmpty())
            layouts.forEach { layout ->
                assertFalse("Button label must fit its height: $text", layout.didOverflowHeight)
                assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1))
                repeat(layout.lineCount) { line ->
                    assertFalse(layout.isLineEllipsized(line))
                    assertTrue(layout.getLineRight(line) <= layout.size.width + 1f)
                }
            }
        }
        compose.onNodeWithText("高级参数").performClick()
        listOf("自定义 Headers", "温度", "最大输出", "上下文消息数").forEach { label ->
            compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(label))
            compose.onNodeWithText(label).assertIsDisplayed()
        }
        capture(dark, advanced = true)
    }

    private fun capture(dark: Boolean, advanced: Boolean = false) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "qa-provider-${if (dark) "dark" else "light"}${if (advanced) "-advanced" else ""}.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }
}
