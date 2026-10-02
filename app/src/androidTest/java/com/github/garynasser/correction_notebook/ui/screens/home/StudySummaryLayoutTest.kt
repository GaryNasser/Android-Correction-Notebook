package com.github.garynasser.correction_notebook.ui.screens.home

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class StudySummaryLayoutTest {
    @get:Rule
    val compose = createComposeRule()

    private lateinit var density: Density

    @Test
    fun summaryFitsInOneCompactRowAndBothMetricsOpenStatistics() {
        var opened = 0
        compose.setContent {
            density = LocalDensity.current
            CorrectionNotebookTheme {
                Box(Modifier.width(320.dp).testTag("summary")) {
                    QuickStatsPreview(
                        todayMinutes = 75,
                        completedPomodoros = 3,
                        onClick = { opened++ }
                    )
                }
            }
        }

        assertTrue(
            compose.onNodeWithTag("summary").fetchSemanticsNode().boundsInRoot.height <=
                with(density) { 80.dp.toPx() } + 1f
        )
        compose.onNodeWithText("今日学习").performClick()
        compose.onNodeWithText("番茄钟").performClick()
        compose.runOnIdle { assertEquals(2, opened) }
    }

    @Test
    fun emptyTodoStateStaysCompactAndCanAddATodo() {
        var added = 0
        compose.setContent {
            density = LocalDensity.current
            CorrectionNotebookTheme {
                Box(Modifier.width(320.dp).testTag("emptyTodo")) {
                    EmptyTodoState(onAddClick = { added++ })
                }
            }
        }

        assertTrue(
            compose.onNodeWithTag("emptyTodo").fetchSemanticsNode().boundsInRoot.height <=
                with(density) { 64.dp.toPx() } + 1f
        )
        compose.onNodeWithText("暂无待办事项").assertExists()
        compose.onNodeWithText("添加待办").performClick()
        compose.runOnIdle { assertEquals(1, added) }
    }
}
