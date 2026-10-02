package com.github.garynasser.correction_notebook.ui.screens.yanhe

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import java.io.File
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import com.github.garynasser.correction_notebook.data.model.yanhe.CourseSection
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun completedSectionMetadataFitsWithoutEllipsisInLightTheme() = assertCompletedCardFits(darkTheme = false)

    @Test
    fun completedSectionMetadataFitsWithoutEllipsisInDarkTheme() = assertCompletedCardFits(darkTheme = true)

    private fun assertCompletedCardFits(darkTheme: Boolean) {
        val text = "已完成 · 第 123 周 · 第 12-14 大节"
        val title = "矩阵分解与数值计算"
        var titleColor = Color.Unspecified
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.3f)) {
                CorrectionNotebookTheme(darkTheme = darkTheme) {
                    titleColor = MaterialTheme.colorScheme.onSurface
                    Box(Modifier.safeDrawingPadding().width(320.dp)) {
                        Column {
                            VideoListTopBar("线性代数", false, {}, {})
                            VideoCard(
                                CourseSection(id = 1, title = title, weekNumber = 123, sectionBigStart = 12, sectionBigEnd = 14),
                                true, false, false, {}, {}, {}, {}
                            )
                        }
                    }
                }
            }
        }
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        layouts.forEach { assertFalse("Week and section metadata must not be truncated", it.hasVisualOverflow) }
        val titleLayouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(title).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(titleLayouts) }
        assertTrue(titleLayouts.isNotEmpty())
        titleLayouts.forEach { assertEquals("Title must use the surface's foreground color", titleColor, it.layoutInput.style.color) }
        compose.onNodeWithContentDescription("章节完成状态").assertIsEnabled()
        compose.onNodeWithContentDescription("课程助手").assertIsEnabled()
        compose.onNodeWithContentDescription("播放摄像头视频").assertIsEnabled()
        compose.onNodeWithContentDescription("播放屏幕录像").assertIsEnabled()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), "qa-video-card-${if (darkTheme) "dark" else "light"}.png").outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
    }

    @Test
    fun headerRefreshIsDisabledWhileLoadingButBackRemainsAvailable() {
        val loading = mutableStateOf(false)
        var refreshes = 0
        var backs = 0
        compose.setContent {
            CorrectionNotebookTheme {
                VideoListTopBar("Algebra", loading.value, { backs++ }, { refreshes++; loading.value = true })
            }
        }
        compose.onNodeWithContentDescription("刷新课程视频").performClick().assertIsNotEnabled()
        compose.onNodeWithContentDescription("返回").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, refreshes); assertEquals(1, backs) }
    }

    @Test
    fun pendingVideoLookupHasAnAccessibleCancelAction() {
        var cancellations = 0
        compose.setContent {
            CorrectionNotebookTheme { VideoResolvingStatus { cancellations++ } }
        }
        compose.onNodeWithContentDescription("取消获取视频地址").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, cancellations) }
    }
}
