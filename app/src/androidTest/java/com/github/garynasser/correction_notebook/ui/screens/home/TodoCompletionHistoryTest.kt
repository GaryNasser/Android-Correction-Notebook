package com.github.garynasser.correction_notebook.ui.screens.home

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import com.github.garynasser.correction_notebook.MainActivity
import com.github.garynasser.correction_notebook.data.model.home.Priority
import com.github.garynasser.correction_notebook.data.model.home.TodoItem
import com.github.garynasser.correction_notebook.data.repository.TodoHistoryRepository
import com.github.garynasser.correction_notebook.data.repository.TodoRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.util.UUID

class TodoCompletionHistoryTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun addingATodoAfterActivityRecreationSavesTheOriginalDraft() {
        val todos = TodoRepository(compose.activity.applicationContext)
        val title = "待办草稿 QA ${UUID.randomUUID().toString().take(8)}"
        val notes = "矩阵分析第三章\n复查证明步骤和课堂笔记。"
        try {
            compose.onNodeWithText("Study").performClick()
            compose.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
                .performScrollToNode(hasContentDescription("添加待办"))
            compose.onNodeWithContentDescription("添加待办").performClick()
            compose.onNodeWithText("标题").performTextReplacement(title)
            compose.onNodeWithText("备注（可选）").performTextReplacement(notes)
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText(title).assertExists()
            compose.onNodeWithText(notes).assertExists()
            compose.onNodeWithContentDescription("添加").performClick()
            compose.waitUntil(5_000) { runBlocking { todos.todoItems.first().any { it.title == title } } }
            val saved = runBlocking { todos.todoItems.first().single { it.title == title } }
            assertEquals(notes, saved.description)
            compose.onNodeWithText("备注（可选）").assertDoesNotExist()
        } finally {
            runBlocking { todos.todoItems.first().filter { it.title == title }.forEach { todos.deleteTodo(it.id) } }
        }
    }

    @Test fun completingATodoKeepsItsFullRecordAndHistoryReopensAfterRecreation() {
        val context = compose.activity.applicationContext
        val todos = TodoRepository(context)
        val history = TodoHistoryRepository(context)
        val item = TodoItem(title = "History QA ${UUID.randomUUID().toString().take(8)}",
            description = "矩阵分析第三章\n复查证明步骤和课堂笔记。", priority = Priority.HIGH, dueDate = LocalDate.now())
        try {
            runBlocking { todos.addTodo(item) }
            compose.onNodeWithText("Study").performClick()
            compose.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
                .performScrollToNode(hasContentDescription("完成待办：${item.title}"))
            compose.onNodeWithContentDescription("完成待办：${item.title}").performClick()
            compose.waitUntil(5_000) { runBlocking { history.historyItems.first().any { it.title == item.title } } }
            val saved = runBlocking { history.historyItems.first().single { it.title == item.title } }
            assertEquals(item.description, saved.description)
            assertEquals(item.priority, saved.priority)
            assertEquals(item.dueDate, saved.dueDate)
            assertEquals(item.createdAt, saved.createdAt)
            compose.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
                .performScrollToNode(hasContentDescription("完成历史"))
            compose.onNodeWithContentDescription("完成历史").performClick()
            compose.onNodeWithText(item.title).performClick()
            compose.onNodeWithText("完成记录").assertIsDisplayed()
            compose.onNode(hasText(item.description) and hasAnyAncestor(isDialog())).assertExists()
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText("完成记录").assertIsDisplayed()
            compose.onNode(hasText(item.description) and hasAnyAncestor(isDialog())).assertExists()
            compose.onNodeWithText("关闭").performClick()
            compose.onNodeWithContentDescription("返回").performClick()
            compose.onNodeWithText("Study").assertIsDisplayed()
            assertEquals(saved, runBlocking { history.historyItems.first().single { it.id == saved.id } })
        } finally {
            runBlocking {
                todos.deleteTodo(item.id)
                history.historyItems.first().filter { it.title == item.title }.forEach { history.deleteHistoryItem(it.id) }
            }
        }
    }
}
