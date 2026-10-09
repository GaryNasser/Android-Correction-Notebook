package com.github.garynasser.correction_notebook.ui.screens.home

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.model.home.IcsDiffItem
import com.github.garynasser.correction_notebook.data.model.home.IcsDiffType
import com.github.garynasser.correction_notebook.data.model.home.IcsImportPreview
import com.github.garynasser.correction_notebook.data.model.home.ImportDecision
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDateTime
import java.io.File

class IcsImportPreviewDialogTest {
    @get:Rule
    val compose = createComposeRule()

    private val date = LocalDateTime.of(2026, 10, 2, 8, 0)
    private val preview = IcsImportPreview(
        fileName = "北京理工大学_2026年秋季学期_课程与日程更新.ics", sourceCalendarId = "test",
        incomingEvents = emptyList(),
        added = (1..7).map { IcsDiffItem(IcsDiffType.ADDED, "课程 $it", date, "新增日程") },
        updated = emptyList(), conflicts = emptyList(),
        deleted = (1..5).map { IcsDiffItem(IcsDiffType.DELETED, "删除项 $it", date, "覆盖时删除") }
    )

    @Test
    fun everyChangedEventCanBeReviewedBeforeApplyingTheImport() {
        compose.setContent {
            CorrectionNotebookTheme { IcsImportPreviewDialog(preview, onDismiss = {}, onApply = {}) }
        }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("课程 7"))
        compose.onNodeWithText("课程 7").assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("删除项 5"))
        compose.onNodeWithText("删除项 5").assertExists()
    }

    @Test
    fun fileNameAndLongCourseNameWrapWithoutTruncation() {
        val longTitle = "毛泽东思想和中国特色社会主义理论体系概论（课程安排调整）"
        val longPreview = preview.copy(added = listOf(preview.added.first().copy(title = longTitle)))
        compose.setContent {
            CorrectionNotebookTheme { IcsImportPreviewDialog(longPreview, onDismiss = {}, onApply = {}) }
        }
        assertFullText(preview.fileName)
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(longTitle))
        assertFullText(longTitle)
        listOf("合并", "覆盖", "取消").forEach { compose.onNodeWithText(it).assertIsDisplayed() }
    }

    @Test
    fun unchangedImportHasAnExplicitEmptyState() {
        compose.setContent {
            CorrectionNotebookTheme {
                IcsImportPreviewDialog(preview.copy(added = emptyList(), deleted = emptyList()), onDismiss = {}, onApply = {})
            }
        }
        compose.onNodeWithText("没有变更").assertIsDisplayed()
    }

    private fun assertFullText(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).assertIsDisplayed()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        layouts.forEach { assertFalse("$text must not be truncated", it.hasVisualOverflow) }
    }

    @Test
    fun mergeAndOverwriteKeepTheirDistinctActionsAndCancelDoesNotImport() {
        val decisions = mutableListOf<ImportDecision>()
        var dismissed = 0
        compose.setContent {
            CorrectionNotebookTheme {
                IcsImportPreviewDialog(preview, onDismiss = { dismissed++ }, onApply = { decisions += it })
            }
        }
        compose.onNodeWithText("合并").performClick()
        compose.onNodeWithText("覆盖").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle {
            assertEquals(listOf(ImportDecision.MERGE, ImportDecision.OVERWRITE), decisions)
            assertEquals(1, dismissed)
        }
    }

    @Test
    fun applyingAnImportDisablesAllThreeActions() {
        compose.setContent {
            CorrectionNotebookTheme {
                IcsImportPreviewDialog(preview, isApplying = true, onDismiss = {}, onApply = {})
            }
        }
        listOf("导入中", "覆盖", "取消").forEach { compose.onNodeWithText(it).assertIsNotEnabled() }
    }

    @Test fun lightSaveErrorStaysVisibleWhileReviewingEveryChange() = checkSaveError(false)
    @Test fun darkSaveErrorStaysVisibleWhileReviewingEveryChange() = checkSaveError(true)

    private fun checkSaveError(dark: Boolean) {
        val error = "课表导入失败：暂时无法保存课程与日程，请稍后重试"
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                CorrectionNotebookTheme(darkTheme = dark, dynamicColor = false) {
                    IcsImportPreviewDialog(preview, saveError = error, onDismiss = {}, onApply = {})
                }
            }
        }
        assertFullText(error)
        compose.onNodeWithText(error).assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("删除项 5"))
        compose.onNodeWithText("删除项 5").assertIsDisplayed()
        assertFullText(error)
        listOf("合并", "覆盖", "取消").forEach {
            compose.onNodeWithText(it).assertIsEnabled().assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        }
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "qa-ics-${if (dark) "dark" else "light"}-error.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }
}
