package com.github.garynasser.correction_notebook.ui.screens.home

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.garynasser.correction_notebook.data.model.home.DeadlineItem
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class DeadlinePageTest {
    @get:Rule val compose = createComposeRule()
    private val pending = DeadlineItem("homework-1", 1, 12, "计算机视觉", "白平衡及伽马矫正", "homework",
        System.currentTimeMillis() + 86_400_000, null, false, false, "<p>提交代码文件</p>")

    @Test fun submittedFilterAndDetailsWorkOnCompactLargeFontScreen() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.5f)) {
                CorrectionNotebookTheme(dynamicColor = false) {
                    Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                        Box {
                            Box(Modifier.width(320.dp).height(520.dp)) {
                                DeadlinePageContent(DeadlineUiState(items = listOf(pending, pending.copy(id = "homework-2", activityId = 2,
                                    title = "第1章思考题", submitted = true)), isSignedIn = true, updatedAt = 1), {})
                            }
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("白平衡及伽马矫正").assertIsDisplayed()
        compose.onNodeWithText("第1章思考题").assertIsDisplayed()
        compose.onNodeWithText("全部").assertIsSelected()
        val all = compose.onNodeWithText("全部").fetchSemanticsNode().boundsInRoot
        val unfinished = compose.onNodeWithText("待完成").fetchSemanticsNode().boundsInRoot
        val submitted = compose.onNodeWithText("已提交").fetchSemanticsNode().boundsInRoot
        assertTrue(all.right <= unfinished.left)
        assertTrue(unfinished.right <= submitted.left)
        assertEquals(all.top, unfinished.top, 1f)
        assertEquals(all.top, submitted.top, 1f)
        compose.onNodeWithText("待完成").performClick()
        compose.onNodeWithText("第1章思考题").assertDoesNotExist()
        capture("compact-large-font")
        compose.onNodeWithText("白平衡及伽马矫正").performClick()
        compose.onNodeWithText("提交代码文件").assertIsDisplayed()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText("已提交").performClick()
        compose.onNodeWithText("第1章思考题").performScrollTo().assertIsDisplayed()
        capture("submitted")
    }

    @Test fun refreshFailureStillDisplaysExistingDeadline() {
        var refreshes = 0
        compose.setContent { CorrectionNotebookTheme {
            DeadlinePageContent(DeadlineUiState(items = listOf(pending), isSignedIn = true,
                updatedAt = 1, error = "同步失败，已保留上次数据"), { refreshes++ })
        } }
        compose.onNodeWithText("同步失败，已保留上次数据").assertIsDisplayed()
        compose.onNodeWithText("白平衡及伽马矫正").assertIsDisplayed()
        compose.onNodeWithContentDescription("同步 DDL").performClick()
        compose.runOnIdle { assertEquals(1, refreshes) }
    }

    @Test fun signedOutPageOffersSchoolLoginInsteadOfStaleTasks() {
        var logins = 0
        compose.setContent { CorrectionNotebookTheme { DeadlinePageContent(DeadlineUiState(), { logins++ }) } }
        compose.onNodeWithText("登录后查看课程作业").assertIsDisplayed()
        compose.onNodeWithText("登录学校账号").performClick()
        compose.runOnIdle { assertEquals(1, logins) }
    }

    @Test fun darkDeadlinePageKeepsTitlesAndErrorReadable() {
        compose.setContent { CorrectionNotebookTheme(darkTheme = true, dynamicColor = false) {
            Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                DeadlinePageContent(DeadlineUiState(items = listOf(pending), isSignedIn = true,
                    updatedAt = 1, error = "同步失败，已保留上次数据"), {})
            }
        } }
        compose.onNodeWithText("白平衡及伽马矫正").assertIsDisplayed()
        compose.onNodeWithText("同步失败，已保留上次数据").assertIsDisplayed()
        capture("dark")
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "qa-ddl-$name.png").outputStream()
                .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }
}
