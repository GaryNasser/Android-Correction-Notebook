package com.github.garynasser.correction_notebook.ui.screens.home

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
}
