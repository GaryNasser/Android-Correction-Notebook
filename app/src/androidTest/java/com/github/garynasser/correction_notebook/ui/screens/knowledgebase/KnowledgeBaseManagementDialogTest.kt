package com.github.garynasser.correction_notebook.ui.screens.knowledgebase

import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseDatabase
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseFileStorage
import com.github.garynasser.correction_notebook.data.model.knowledgebase.KnowledgeBaseFolderChoice
import com.github.garynasser.correction_notebook.data.repository.KnowledgeBaseRepository
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class KnowledgeBaseManagementDialogTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightSameNamedFoldersRemainIndividuallySelectable() = checkFolders(false)
    @Test fun darkSameNamedFoldersRemainIndividuallySelectable() = checkFolders(true)

    private fun checkFolders(dark: Boolean) {
        val folders = createSameNamedFolders()
        assertEquals(folders[1].path, folders[2].path)
        val selected = mutableListOf<String?>()
        var dismissals = 0
        compose.setContent {
            themed(dark) { FolderPickerDialog("移动到", folders, { dismissals++ }, { selected += it }) }
        }
        compose.onAllNodesWithText("矩阵分析")[0].performClick()
        compose.onAllNodesWithText("矩阵分析")[1].performClick()
        compose.onNodeWithText("知识库根目录").performClick()
        compose.onNodeWithText("取消").performClick()
        compose.runOnIdle {
            assertEquals(listOf(folders[1].id, folders[2].id, null), selected)
            assertEquals(1, dismissals)
        }
        capture("folder-picker-${if (dark) "dark" else "light"}")
    }

    @Test fun aBlankNameCannotDismissTheFormOrSubmit() {
        val submissions = mutableListOf<String>()
        compose.setContent { themed { NameInputDialog("新建文件夹", "", "创建", {}, { submissions += it }) } }
        compose.onNodeWithText("创建").assertIsNotEnabled()
        compose.onNode(hasSetTextAction()).performTextInput("   ")
        compose.onNodeWithText("创建").assertIsNotEnabled()
        compose.runOnIdle { assertTrue(submissions.isEmpty()) }
        compose.onNode(hasSetTextAction()).performTextReplacement("  矩阵分析  ")
        compose.onNodeWithText("创建").assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(listOf("矩阵分析"), submissions) }
    }

    @Test fun keyboardDoneSubmitsTheTrimmedName() {
        val submissions = mutableListOf<String>()
        compose.setContent { themed { NameInputDialog("重命名文件", "原名称.txt", "保存", {}, { submissions += it }) } }
        compose.onNode(hasSetTextAction()).performTextReplacement("  新名称.txt  ")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.runOnIdle { assertEquals(listOf("新名称.txt"), submissions) }
    }

    @Test fun anUnsubmittedNameSurvivesSavedStateRestoration() {
        val restoration = StateRestorationTester(compose)
        restoration.setContent { themed { NameInputDialog("新建文件夹", "", "创建", {}, {}) } }
        compose.onNode(hasSetTextAction()).performTextInput("矩阵分析复习")
        restoration.emulateSavedInstanceStateRestore()
        compose.onNode(hasSetTextAction()).assertTextContains("矩阵分析复习")
        compose.onNodeWithText("创建").assertIsEnabled()
    }

    @Test fun lightLongDeleteConfirmationKeepsTheNameAndActionsReadable() = checkConfirmation(false)
    @Test fun darkLongDeleteConfirmationKeepsTheNameAndActionsReadable() = checkConfirmation(true)

    private fun checkConfirmation(dark: Boolean) {
        val message = "仅支持删除空文件夹。确定删除“${"矩阵分析讲义与课后练习 ".repeat(60)}”吗？"
        var deletions = 0
        var dismissals = 0
        compose.setContent { themed(dark) { ConfirmDialog("删除文件夹", message, "删除", { dismissals++ }, { deletions++ }) } }
        compose.onNodeWithText(message).performScrollTo().assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(message).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        layouts.forEach { layout ->
            assertFalse(layout.didOverflowHeight)
            assertEquals(message.length, layout.getLineEnd(layout.lineCount - 1))
            repeat(layout.lineCount) { line ->
                assertFalse(layout.isLineEllipsized(line))
                assertTrue(layout.getLineRight(line) <= layout.size.width + 1)
            }
        }
        compose.onNodeWithText("取消").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, dismissals); assertEquals(0, deletions) }
        compose.onNodeWithText("删除").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, deletions) }
        capture("management-confirm-${if (dark) "dark" else "light"}")
    }

    private fun createSameNamedFolders(): List<KnowledgeBaseFolderChoice> = runBlocking {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File.createTempFile("folders-qa-", "", target.cacheDir).apply { delete(); mkdir() }
        val context = object : ContextWrapper(target) { override fun getFilesDir() = directory }
        val database = Room.inMemoryDatabaseBuilder(context, KnowledgeBaseDatabase::class.java).build()
        try {
            val repository = KnowledgeBaseRepository(database.knowledgeBaseDao(), KnowledgeBaseFileStorage(context), context)
            repeat(2) { repository.createFolder(null, "矩阵分析").getOrThrow() }
            repository.observeFolderChoices().first()
        } finally { database.close(); directory.deleteRecursively() }
    }

    @Composable private fun themed(dark: Boolean = false, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
            CorrectionNotebookTheme(darkTheme = dark, dynamicColor = false, content = content)
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(350)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "qa-$name.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }
}
