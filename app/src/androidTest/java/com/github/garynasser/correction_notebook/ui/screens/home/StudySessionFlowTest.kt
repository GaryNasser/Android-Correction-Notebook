package com.github.garynasser.correction_notebook.ui.screens.home

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.github.garynasser.correction_notebook.MainActivity
import com.github.garynasser.correction_notebook.data.model.home.SessionType
import com.github.garynasser.correction_notebook.data.repository.StudySessionRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class StudySessionFlowTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun activityRecreationKeepsFocusFullscreenAndRestoresNavigationOnExit() {
        compose.onNodeWithText("Study").performClick()
        compose.onNodeWithContentDescription("学习模式").performClick()
        compose.onNodeWithText("正计时").performClick()
        compose.onNodeWithContentDescription("暂停").performClick()
        compose.onNodeWithContentDescription("知识库").assertDoesNotExist()

        compose.activityRule.scenario.recreate()
        compose.onNodeWithContentDescription("继续").assertExists()
        compose.onNodeWithContentDescription("知识库").assertDoesNotExist()

        compose.onNodeWithContentDescription("退出").performClick()
        compose.onNodeWithContentDescription("知识库").assertExists()
        compose.onNodeWithContentDescription("统计").performClick()
        compose.onNodeWithContentDescription("知识库").assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        compose.onNodeWithText("学习统计").assertExists()
        compose.onNodeWithContentDescription("知识库").assertDoesNotExist()
    }

    @Test
    fun resetSavesOneRecordAndExitSavesOnlyTheNextSession() {
        val repository = StudySessionRepository(compose.activity.applicationContext)
        val before = runBlocking { repository.getTodaySessions() }
        val beforeMinutes = before.sumOf { it.durationMinutes }

        compose.onNodeWithText("Study").performClick()
        compose.onNodeWithContentDescription("学习模式").performClick()
        compose.onNodeWithText("正计时").performClick()
        waitForElapsedSecond()
        compose.onNodeWithContentDescription("暂停").performClick()
        compose.onNodeWithContentDescription("重置").performClick()
        waitForRecordCount(repository, before.size + 1)
        compose.onNodeWithText("00:00").assertExists()
        compose.onNodeWithContentDescription("继续").assertExists()

        compose.onNodeWithContentDescription("重置").performClick()
        compose.waitForIdle()
        assertEquals(before.size + 1, runBlocking { repository.getTodaySessions().size })

        compose.onNodeWithContentDescription("继续").performClick()
        waitForElapsedSecond()
        compose.onNodeWithContentDescription("暂停").performClick()
        compose.onNodeWithContentDescription("结束").performClick()
        waitForRecordCount(repository, before.size + 2)
        val added = runBlocking { repository.getTodaySessions() }.drop(before.size)
        assertEquals(listOf(SessionType.STOPWATCH, SessionType.STOPWATCH), added.map { it.sessionType })
        assertEquals(listOf(1, 1), added.map { it.durationMinutes })
        compose.onNodeWithText("计时中").assertDoesNotExist()
        compose.onNodeWithText("计时已暂停").assertDoesNotExist()
        compose.onNodeWithContentDescription("统计").performClick()
        compose.onNodeWithText("今日").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("${beforeMinutes + 2}m").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodes(hasScrollAction())[0].performScrollToNode(hasText("正计时"))
        compose.onNodeWithText("正计时").assertExists()
    }

    private fun waitForElapsedSecond() {
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("00:01").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun waitForRecordCount(repository: StudySessionRepository, expected: Int) {
        compose.waitUntil(5_000) {
            runBlocking { repository.getTodaySessions().size } == expected
        }
    }
}
