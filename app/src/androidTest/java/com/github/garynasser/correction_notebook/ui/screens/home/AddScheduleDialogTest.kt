package com.github.garynasser.correction_notebook.ui.screens.home

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import com.github.garynasser.correction_notebook.data.model.home.ScheduleEvent
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class AddScheduleDialogTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun enteringTwoHourDigitsDoesNotInsertAnUnrequestedLeadingZero() {
        var added: ScheduleEvent? = null
        compose.setContent {
            CorrectionNotebookTheme {
                AddScheduleDialog(onDismiss = {}, onAdd = { added = it })
            }
        }
        compose.onNodeWithText("活动标题").performTextInput("Review lecture")
        compose.onAllNodesWithText("时")[1].performTextReplacement("13")
        compose.onAllNodesWithText("时")[0].performTextClearance()
        compose.onAllNodesWithText("时")[0].performTextInput("1")
        compose.waitForIdle()
        compose.onAllNodesWithText("时")[0].performTextInput("2")
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle { assertEquals(12, added?.startAt?.hour) }
    }

    @Test
    fun blankTimeIsRejectedAndAllDayDoesNotRequireHiddenTimeFields() {
        val date = LocalDate.of(2026, 11, 13)
        var added: ScheduleEvent? = null
        compose.setContent {
            CorrectionNotebookTheme {
                AddScheduleDialog(initialDate = date, onDismiss = {}, onAdd = { added = it })
            }
        }
        compose.onNodeWithText("2026/11/13").assertExists()
        compose.onNodeWithText("活动标题").performTextInput("All day workshop")
        compose.onNodeWithContentDescription("开始小时").performScrollTo().performTextClearance()
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("请填写完整的开始和结束时间").assertExists()
        compose.runOnIdle { assertNull(added) }
        compose.onNodeWithContentDescription("全天安排").performScrollTo().performClick()
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle {
            assertEquals(date.atStartOfDay(), added?.startAt)
            assertEquals(date.plusDays(1).atStartOfDay(), added?.endAt)
            assertTrue(added!!.allDay)
        }
    }

    @Test
    fun allDraftFieldsSurviveSavedInstanceStateRestoration() {
        val restoration = StateRestorationTester(compose)
        var added: ScheduleEvent? = null
        restoration.setContent {
            CorrectionNotebookTheme {
                AddScheduleDialog(
                    initialDate = LocalDate.of(2026, 11, 13),
                    onDismiss = {}, onAdd = { added = it }
                )
            }
        }
        compose.onNodeWithText("活动标题").performTextInput("Review lecture")
        compose.onNodeWithText("地点").performTextInput("文萃楼 M134")
        compose.onNodeWithText("备注").performTextInput("Bring notes")
        compose.onNodeWithContentDescription("开始小时").performScrollTo().performTextReplacement("12")
        compose.onNodeWithContentDescription("开始分钟").performTextReplacement("35")
        compose.onNodeWithContentDescription("结束小时").performScrollTo().performTextReplacement("13")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle {
            val event = checkNotNull(added)
            assertEquals("Review lecture", event.title)
            assertEquals("文萃楼 M134", event.location)
            assertEquals("Bring notes", event.description)
            assertEquals(LocalDate.of(2026, 11, 13).atTime(12, 35), event.startAt)
            assertEquals(LocalDate.of(2026, 11, 13).atTime(13, 0), event.endAt)
        }
    }

    @Test
    fun invalidTimeOrderingIsCorrectableWithoutClosingTheDraft() {
        var added: ScheduleEvent? = null
        compose.setContent {
            CorrectionNotebookTheme { AddScheduleDialog(onDismiss = {}, onAdd = { added = it }) }
        }
        compose.onNodeWithText("活动标题").performTextInput("Study group")
        compose.onNodeWithContentDescription("开始小时").performScrollTo().performTextReplacement("12")
        compose.onNodeWithText("保存").performClick()
        compose.onNodeWithText("结束时间需要晚于开始时间").assertExists()
        compose.runOnIdle { assertNull(added) }
        compose.onNodeWithContentDescription("结束小时").performScrollTo().performTextReplacement("13")
        compose.onNodeWithText("结束时间需要晚于开始时间").assertDoesNotExist()
        compose.onNodeWithText("保存").performClick()
        compose.runOnIdle {
            assertEquals(12, added?.startAt?.hour)
            assertEquals(13, added?.endAt?.hour)
        }
    }

    @Test
    fun savingDisablesEveryEditableFieldAndDismissAction() {
        compose.setContent {
            CorrectionNotebookTheme {
                AddScheduleDialog(isSaving = true, onDismiss = {}, onAdd = {})
            }
        }
        listOf("活动标题", "地点", "备注", "取消", "保存中").forEach {
            compose.onNodeWithText(it).assertIsNotEnabled()
        }
        listOf("开始小时", "开始分钟", "结束小时", "结束分钟", "全天安排").forEach {
            compose.onNodeWithContentDescription(it).assertIsNotEnabled()
        }
    }

    @Test
    fun timeValuesAndDateFitTheirAvailableSpace() {
        compose.setContent {
            CorrectionNotebookTheme {
                AddScheduleDialog(initialDate = LocalDate.of(2026, 11, 13), onDismiss = {}, onAdd = {})
            }
        }
        listOf("开始小时", "开始分钟", "结束小时", "结束分钟").forEach { label ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithContentDescription(label).performScrollTo()
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            assertTrue(layouts.isNotEmpty())
            layouts.forEach { assertFalse("$label must not be clipped", it.hasVisualOverflow) }
        }
        val dateLayouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("2026/11/13").performScrollTo()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(dateLayouts) }
        assertTrue(dateLayouts.isNotEmpty())
        dateLayouts.forEach {
            assertFalse("Date must not lose lines", it.multiParagraph.didExceedMaxLines)
            // Button text may use a wider cached paragraph than its intrinsic measured width.
            repeat(it.lineCount) { line ->
                assertTrue("Date text must fit horizontally", it.getLineRight(line) <= it.size.width)
                assertTrue("Date text must fit vertically", it.getLineBottom(line) <= it.size.height)
            }
        }
    }
}
