package com.github.garynasser.correction_notebook.ui.screens.home

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.model.home.Priority
import com.github.garynasser.correction_notebook.data.model.home.TodoHistoryItem
import com.github.garynasser.correction_notebook.data.repository.TodoHistoryRepository
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate
import java.io.File
import java.util.UUID

class TodoHistoryFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun aRecordCanBeOpenedWithoutDeletingItsFullTitleAndNotes() = withFixture { repository, item, vm ->
        compose.setContent { HistoryTestScreen(vm) }
        compose.onNodeWithText(item.title).performClick()
        compose.onNodeWithText("完成记录").assertExists()
        assertFullDetailText(item.title)
        assertFullDetailText(item.description)
        detailText("截止日期 2026/10/03").performScrollTo().assertIsDisplayed()
        assertEquals(item, repository.historyItems.first().single { it.id == item.id })
    }

    @Test fun deletingARecordRequiresConfirmationAndCancelKeepsIt() = withFixture { repository, item, vm ->
        compose.setContent { HistoryTestScreen(vm) }
        deleteButton(item).performClick()
        compose.onNodeWithText("删除完成记录").assertExists()
        assertEquals(item, repository.historyItems.first().single { it.id == item.id })
        compose.onNodeWithText("取消").performClick()
        assertEquals(item, repository.historyItems.first().single { it.id == item.id })
        deleteButton(item).performClick()
        compose.onNodeWithText("删除").performClick()
        compose.waitUntil(5_000) { runBlocking { repository.historyItems.first().none { it.id == item.id } } }
        compose.onNodeWithText("删除完成记录").assertDoesNotExist()
        assertEquals(1, repository.historyItems.first().count { it.id == "${item.id}-other" })
    }

    @Test fun detailAndDeleteConfirmationSurviveSavedInstanceStateRestoration() = withFixture { repository, item, vm ->
        val restoration = StateRestorationTester(compose)
        restoration.setContent { HistoryTestScreen(vm) }
        compose.onNodeWithText(item.title).performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("完成记录").assertExists()
        assertFullDetailText(item.title)
        compose.onNode(hasText("删除") and hasAnyAncestor(isDialog())).performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("删除完成记录").assertExists()
        compose.onNodeWithText("取消").performClick()
        assertEquals(item, repository.historyItems.first().single { it.id == item.id })
    }

    @Test fun clearHistoryRequiresConfirmationAndCancelKeepsAllRecords() = withFixture { repository, _, vm ->
        val before = repository.historyItems.first()
        compose.setContent { HistoryTestScreen(vm) }
        compose.onNodeWithContentDescription("清除历史").performClick()
        compose.onNodeWithText("取消").performClick()
        assertEquals(before.toSet(), repository.historyItems.first().toSet())
        compose.onNodeWithContentDescription("清除历史").performClick()
        compose.onNodeWithText("清除").performClick()
        compose.waitUntil(5_000) { vm.uiState.value.historyItems.isEmpty() }
        compose.onNodeWithText("暂无完成记录").assertIsDisplayed()
        before.forEach { repository.addHistoryItem(it) }
    }

    @Test fun lightHistoryFitsNarrowScreenWithLargeFont() = checkLayout(dark = false, details = false)
    @Test fun darkHistoryFitsNarrowScreenWithLargeFont() = checkLayout(dark = true, details = false)
    @Test fun lightDetailFitsNarrowScreenWithLargeFont() = checkLayout(dark = false, details = true)
    @Test fun darkDetailFitsNarrowScreenWithLargeFont() = checkLayout(dark = true, details = true)

    private fun checkLayout(dark: Boolean, details: Boolean) = withFixture { _, item, vm ->
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3.375f, 1.3f)) { HistoryTestScreen(vm, dark) }
        }
        if (details) {
            compose.onNodeWithText(item.title).performClick()
            assertFullDetailText(item.title)
            assertFullDetailText(item.description)
            compose.onNodeWithText("关闭").assertIsDisplayed()
            compose.onNode(hasText("删除") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        } else {
            compose.onNodeWithText(item.title).assertIsDisplayed()
            val bounds = deleteButton(item).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
            assertTrue("Delete touch target must be at least 48dp", bounds.width >= 48 * 3.375f - 1)
        }
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        // Window enter animations run outside Compose's test clock.
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(350)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null),
                "qa-history-${if (dark) "dark" else "light"}-${if (details) "details" else "list"}.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    @Composable private fun HistoryTestScreen(vm: TodoHistoryViewModel, dark: Boolean = false) {
        CorrectionNotebookTheme(darkTheme = dark, dynamicColor = false) {
            Box(Modifier.navigationBarsPadding()) { TodoHistoryScreen(viewModel = vm, onBack = {}) }
        }
    }

    private fun deleteButton(item: TodoHistoryItem) = compose.onNode(
        hasContentDescription("删除") and hasAnyAncestor(hasText(item.title)))

    private fun detailText(text: String) = compose.onNode(hasText(text) and hasAnyAncestor(isDialog()), useUnmergedTree = true)

    private fun assertFullDetailText(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        detailText(text).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        layouts.forEach {
            assertFalse("Full record text must not lose lines", it.didOverflowHeight)
            assertEquals(text.length, it.getLineEnd(it.lineCount - 1))
            repeat(it.lineCount) { line ->
                assertFalse(it.isLineEllipsized(line))
                assertTrue(it.getLineRight(line) <= it.size.width + 1)
            }
        }
    }

    private fun withFixture(block: suspend (TodoHistoryRepository, TodoHistoryItem, TodoHistoryViewModel) -> Unit) = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val repository = TodoHistoryRepository(app)
        val original = repository.historyItems.first()
        val item = TodoHistoryItem(UUID.randomUUID().toString(),
            "完成矩阵分析第三章全部习题并整理错题中的证明过程", "文萃楼 M134\n整理例题、证明步骤及课堂笔记。".repeat(8),
            Priority.HIGH, LocalDate.of(2026, 10, 3), 1_790_950_800_000, 1_790_957_400_000, LocalDate.of(2026, 10, 3))
        val store = ViewModelStore()
        try {
            repository.addHistoryItem(item)
            repository.addHistoryItem(item.copy(id = "${item.id}-other", title = "另一项完成记录", description = "", completedAt = item.completedAt - 1))
            val vm = withContext(Dispatchers.Main) { TodoHistoryViewModel(app).also { store.put("history", it) } }
            compose.waitUntil(5_000) { vm.uiState.value.historyItems.any { it.id == item.id } }
            block(repository, item, vm)
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            repository.deleteHistoryItem(item.id)
            repository.deleteHistoryItem("${item.id}-other")
            assertEquals(original.toSet(), repository.historyItems.first().toSet())
        }
    }
}
