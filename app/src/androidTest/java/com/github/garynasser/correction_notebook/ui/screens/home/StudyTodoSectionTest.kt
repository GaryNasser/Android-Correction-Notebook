package com.github.garynasser.correction_notebook.ui.screens.home

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.github.garynasser.correction_notebook.data.model.home.TodoItem
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StudyTodoSectionTest {
    @get:Rule
    val compose = createComposeRule()

    private val first = TodoItem(id = "first", title = "Review lecture")
    private val second = TodoItem(id = "second", title = "Finish homework")
    private val todos = mutableStateOf(listOf(first, second))
    private val busyIds = mutableStateOf(emptySet<String>())
    private val deletedIds = mutableListOf<String>()

    private fun showSection() {
        compose.setContent {
            MaterialTheme {
                StudyTodoSection(
                    todos = todos.value,
                    mutatingTodoIds = busyIds.value,
                    isAiLoading = false,
                    breakingDownTodoId = null,
                    onAddTodo = {},
                    onShowHistory = {},
                    onToggleTodo = { id ->
                        todos.value = todos.value.map {
                            if (it.id == id) it.copy(isCompleted = !it.isCompleted) else it
                        }
                    },
                    onBreakDownTodo = {},
                    onDeleteTodo = { deletedIds += it }
                )
            }
        }
    }

    @Test
    fun deleteConfirmationStaysWithTheSameTodoAfterReordering() {
        showSection()
        compose.onAllNodesWithContentDescription("删除")[0].performClick()
        compose.runOnIdle { todos.value = listOf(second, first) }

        compose.onNodeWithText("确定删除“${first.title}”吗？").assertExists()
        compose.onNodeWithText("删除").performClick()
        compose.runOnIdle { assertEquals(listOf(first.id), deletedIds) }
    }

    @Test
    fun removingTheSelectedTodoDoesNotOpenConfirmationForItsNeighbor() {
        showSection()
        compose.onAllNodesWithContentDescription("删除")[0].performClick()
        compose.runOnIdle { todos.value = listOf(second) }

        compose.onNodeWithText("删除待办").assertDoesNotExist()
        compose.onNodeWithText(second.title).assertExists()
        compose.runOnIdle { assertEquals(emptyList<String>(), deletedIds) }
    }

    @Test
    fun completionControlExposesItsStateAndTogglesInBothDirections() {
        showSection()
        val checkbox = compose.onNodeWithContentDescription("完成待办：${first.title}")
        checkbox.assertIsOff().assertIsEnabled().performClick()
        checkbox.assertIsOn().performClick()
        checkbox.assertIsOff()
    }

    @Test
    fun aTodoBeingSavedCannotBeToggledOrDeleted() {
        busyIds.value = setOf(first.id)
        showSection()
        compose.onNodeWithContentDescription("完成待办：${first.title}")
            .assertIsOff().assertIsNotEnabled()
        compose.onAllNodesWithContentDescription("删除")[0].assertIsNotEnabled()
        compose.onNodeWithContentDescription("完成待办：${second.title}").assertIsEnabled()
    }
}
