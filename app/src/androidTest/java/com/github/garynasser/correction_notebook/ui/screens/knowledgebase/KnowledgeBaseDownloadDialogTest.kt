package com.github.garynasser.correction_notebook.ui.screens.knowledgebase

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareFileDetail
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class KnowledgeBaseDownloadDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun downloadingLightDialogHasIndependentCancelAndCloseActions() = checkDialog(dark = false, downloading = true)

    @Test
    fun downloadingDarkDialogHasIndependentCancelAndCloseActions() = checkDialog(dark = true, downloading = true)

    @Test
    fun idleLightDialogShowsFullMetadataAndDownloadAction() = checkDialog(dark = false, downloading = false)

    @Test
    fun idleDarkDialogShowsFullMetadataAndDownloadAction() = checkDialog(dark = true, downloading = false)

    private fun checkDialog(dark: Boolean, downloading: Boolean) {
        var cancellations = 0
        var dismissals = 0
        var downloads = 0
        val detail = BitShareFileDetail(
            "file", "计算理论与算法分析设计：第八章课程资料与期末复习讲义",
            "Algorithm_analysis_and_design_revision_notes_2026_complete_version.pdf", "pdf",
            "教学资料 / 计算机学院 / 算法设计与分析 / 第八章完整讲义",
            "包含课程讲义、课堂例题和复习题。".repeat(20), "application/pdf", 12_345L,
            "2026-09-28T12:00:00+08:00", 120
        )
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                CorrectionNotebookTheme(darkTheme = dark, dynamicColor = false) {
                    RemoteDetailDialog(detail, downloading,
                        onDismiss = { dismissals++ }, onCancelClick = { cancellations++ },
                        onDownloadClick = { downloads++ })
                }
            }
        }
        compose.onNodeWithText("文件详情").assertIsDisplayed()
        val action = if (downloading) "取消下载" else "下载到知识库"
        compose.onNodeWithText(action).assertIsDisplayed()
        compose.onNodeWithText("关闭").assertIsDisplayed()
        capture(dark, downloading)
        assertNoOverflow(action)
        assertNoOverflow("关闭")
        listOf(detail.title, detail.originalName, detail.path!!, detail.description!!).forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
            assertNoOverflow(it)
        }
        compose.onNodeWithText(detail.title).performScrollTo()
        compose.onNodeWithText(action).performClick()
        compose.runOnIdle {
            assertEquals(if (downloading) 1 else 0, cancellations)
            assertEquals(if (downloading) 0 else 1, downloads)
            assertEquals(0, dismissals)
        }
        compose.onNodeWithText("关闭").performClick()
        compose.runOnIdle { assertEquals(1, dismissals) }
    }

    private fun capture(dark: Boolean, downloading: Boolean) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val target = File(instrumentation.targetContext.getExternalFilesDir(null), "qa-download-${if (dark) "dark" else "light"}-${if (downloading) "active" else "idle"}.png")
        instrumentation.uiAutomation.takeScreenshot().useBitmap { bitmap ->
            target.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }

    private fun assertNoOverflow(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertFalse(layouts.isEmpty())
        layouts.forEach { layout ->
            // Paragraph widths are fractional, while measured text sizes use integer pixels.
            assertFalse(layout.didOverflowHeight)
            assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1))
            repeat(layout.lineCount) { line ->
                assertFalse(layout.isLineEllipsized(line))
                assertTrue("Text must fit its measured width: $text", layout.getLineRight(line) <= layout.size.width + 1f)
            }
        }
    }

    private inline fun Bitmap.useBitmap(block: (Bitmap) -> Unit) {
        try { block(this) } finally { recycle() }
    }
}
