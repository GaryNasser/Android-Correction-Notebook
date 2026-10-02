package com.github.garynasser.correction_notebook.ui.screens.aitutor

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
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
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("获取模型"))
        capture(dark)
        listOf("获取模型", "测试连接", "保存", "取消", "新增").forEach { text ->
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
        val description = compose.onNodeWithText("Headers、温度、最大输出、上下文长度")
            .fetchSemanticsNode().boundsInRoot
        val toggle = compose.onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ToggleableState))
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Advanced description must not overlap its switch", description.right <= toggle.left + 1f)
    }

    private fun capture(dark: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "qa-provider-${if (dark) "dark" else "light"}.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }
}
