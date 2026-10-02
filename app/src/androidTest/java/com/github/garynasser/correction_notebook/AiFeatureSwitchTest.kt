package com.github.garynasser.correction_notebook

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.github.garynasser.correction_notebook.data.local.AISettingsManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test

class AiFeatureSwitchTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private lateinit var settings: AISettingsManager
    private var previouslyEnabled = false

    @Before
    fun setup() = runBlocking {
        settings = AISettingsManager(compose.activity.applicationContext)
        previouslyEnabled = settings.aiEnabled.first()
        settings.setAiEnabled(false)
    }

    @After
    fun restoreSetting() = runBlocking {
        settings.setAiEnabled(previouslyEnabled)
    }

    @Test
    fun settingChangesReachNavigationHomeAndNestedStatisticsImmediately() {
        compose.onNodeWithText("Study").performClick()
        runBlocking { settings.setAiEnabled(true) }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("AI").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodes(hasScrollAction())[0]
            .performScrollToNode(hasText("AI 学习建议"))
        compose.onNodeWithText("AI 学习建议").assertExists()

        runBlocking { settings.setAiEnabled(false) }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("AI").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("AI 学习建议").assertDoesNotExist()
        compose.onNodeWithContentDescription("统计").performClick()
        compose.onNodeWithText("学习统计").assertExists()
        compose.onNodeWithText("AI 解读").assertDoesNotExist()

        runBlocking { settings.setAiEnabled(true) }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("AI 解读").fetchSemanticsNodes().isNotEmpty()
        }
        runBlocking { settings.setAiEnabled(false) }
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText("AI 解读").fetchSemanticsNodes().isEmpty()
        }
        compose.onNodeWithText("学习统计").assertExists()
    }
}
