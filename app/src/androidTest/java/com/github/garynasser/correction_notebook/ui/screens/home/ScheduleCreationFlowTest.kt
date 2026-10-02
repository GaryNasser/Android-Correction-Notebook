package com.github.garynasser.correction_notebook.ui.screens.home

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.printToLog
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import com.github.garynasser.correction_notebook.MainActivity
import com.github.garynasser.correction_notebook.data.repository.ScheduleRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ScheduleCreationFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun selectedDateAndDraftSurviveRecreationAndTheSavedEventCanBeOpenedAndDeleted() {
        val repository = ScheduleRepository(compose.activity.applicationContext)
        val date = LocalDate.now().plusWeeks(1)
        val title = "Schedule QA ${java.util.UUID.randomUUID().toString().take(8)}"
        try {
            compose.onNodeWithContentDescription("下一周").performClick()
            compose.onNodeWithContentDescription("添加日程").performClick()
            compose.onNodeWithText(date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd"))).assertExists()
            compose.onNodeWithText("活动标题").performTextInput(title)
            compose.onNodeWithText("地点").performTextInput("文萃楼 M134")
            compose.activityRule.scenario.recreate()
            compose.onNodeWithText(title).assertExists()
            compose.onNodeWithText("文萃楼 M134").assertExists()
            compose.onNodeWithText("保存").performClick()
            compose.waitUntil(5_000) {
                runBlocking { repository.scheduleEvents.first().any { it.title == title } }
            }
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("活动标题").fetchSemanticsNodes().isEmpty()
            }
            val saved = runBlocking { repository.scheduleEvents.first().single { it.title == title } }
            assertEquals(date.atTime(9, 0), saved.startAt)
            assertEquals(date.atTime(10, 0), saved.endAt)
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText(title, substring = true).fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText(title, substring = true).performClick()
            compose.onNodeWithText("09:00 - 10:00", substring = true).assertExists()
            compose.onNodeWithText("文萃楼 M134").assertExists()
            compose.onNodeWithText("删除").performClick()
            compose.onNodeWithText("确定删除“$title”吗？").assertExists()
            compose.waitUntil(5_000) {
                compose.onAllNodesWithText("删除").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("删除").assertIsEnabled().performClick()
            compose.waitUntil(5_000) {
                runBlocking { repository.scheduleEvents.first().none { it.id == saved.id } }
            }
            compose.onNodeWithText(title, substring = true).assertDoesNotExist()
        } catch (error: Throwable) {
            compose.onAllNodes(isRoot()).printToLog("ScheduleFlowFailure", maxDepth = Int.MAX_VALUE)
            throw error
        } finally {
            runBlocking {
                repository.scheduleEvents.first().filter { it.title == title }.forEach {
                    repository.deleteEvent(it.id)
                }
            }
        }
    }
}
