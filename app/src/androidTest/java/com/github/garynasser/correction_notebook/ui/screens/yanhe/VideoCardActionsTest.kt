package com.github.garynasser.correction_notebook.ui.screens.yanhe

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.github.garynasser.correction_notebook.data.model.yanhe.CourseSection
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class VideoCardActionsTest {
    @get:Rule
    val compose = createComposeRule()

    @Test
    fun removingAiAssistantLeavesBothPlaybackActionsAvailable() {
        var cameraPlays = 0
        var screenPlays = 0
        compose.setContent {
            CorrectionNotebookTheme {
                VideoCard(
                    section = CourseSection(id = 1, title = "Lecture"),
                    isCompleted = false,
                    isResolvingVideo = false,
                    isUpdatingCompletion = false,
                    onCompletedChange = {},
                    onAiAssistantClick = null,
                    onCameraPlayClick = { cameraPlays++ },
                    onScreenPlayClick = { screenPlays++ }
                )
            }
        }
        compose.onNodeWithContentDescription("课程助手").assertDoesNotExist()
        compose.onNodeWithContentDescription("播放摄像头视频").assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("播放屏幕录像").assertIsEnabled().performClick()
        compose.runOnIdle {
            assertEquals(1, cameraPlays)
            assertEquals(1, screenPlays)
        }
    }
}
