package com.github.garynasser.correction_notebook.ui.screens.knowledgebase

import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseDatabase
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseFileStorage
import com.github.garynasser.correction_notebook.data.model.studyset.KnowledgeCardDraft
import com.github.garynasser.correction_notebook.data.model.studyset.StudySetDraft
import com.github.garynasser.correction_notebook.data.remote.api.BitShareApiService
import com.github.garynasser.correction_notebook.data.repository.*
import com.github.garynasser.correction_notebook.ui.components.LocalAiEnabled
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class KnowledgeBaseLearningContextFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun restoredDraftUpdatesOnlyTheSelectedFileAndReachesTheStudySet() = withFixture { f ->
        val restoration = show(f)
        openEditor()
        fillDraft()
        restoration.emulateSavedInstanceStateRestore()
        field("课程名称").assertTextContains("矩阵分析")
        field("课程 ID，可留空").assertTextContains("224")
        field("标签，用逗号分隔").assertTextContains("期末，重点, 重点")
        capture("restored")
        compose.onNodeWithText("保存").assertIsDisplayed().performClick()
        awaitSaved(f)
        assertSaved(f)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("关联课程/标签").assertDoesNotExist()
        val file = requireNotNull(f.local.getFileSummary(f.fileId))
        val setId = f.study.saveDraftFromFile(file, StudySetDraft("课程复习", listOf(
            KnowledgeCardDraft(title = "特征值", front = "什么是特征值？", back = "矩阵的谱参数", tags = file.tags)
        )), createdByAi = false).getOrThrow()
        f.await { f.model.uiState.value.studySets.any { it.id == setId } }
        val set = f.model.uiState.value.studySets.single { it.id == setId }
        assertEquals(224, set.courseId)
        assertEquals("矩阵分析", set.courseName)
        assertEquals(f.fileId, set.sourceRefId)
        f.await { f.model.uiState.value.knowledgeCards.size == 1 }
        assertEquals(listOf("期末", "重点"), f.model.uiState.value.knowledgeCards.single().tags)
    }

    @Test fun invalidCourseIdsNeverSilentlyClearOrChangeTheExistingAssociation() = withFixture { f ->
        show(f)
        openEditor()
        listOf("2147483648", "0", "-12", "abc12").forEach { value ->
            field("课程 ID，可留空").performTextReplacement(value)
            compose.onNodeWithText("保存").assertIsNotEnabled()
            assertEquals(203, f.dao.getFileById(f.fileId)?.courseId)
        }
        field("课程 ID，可留空").performTextReplacement(" 224 ")
        compose.onNodeWithText("保存").assertIsEnabled().performClick()
        f.await { f.dao.getFileById(f.fileId)?.courseId == 224 }
    }

    @Test fun aFailedSaveKeepsTheDraftAndCanRetryAfterRestoration() = withFixture { f ->
        val restoration = show(f, dark = true)
        openEditor()
        fillDraft()
        f.database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_learning_context BEFORE UPDATE ON kb_file BEGIN SELECT RAISE(ABORT, 'test-only save rejected'); END"
        )
        compose.onNodeWithText("保存").performClick()
        f.await { !f.model.uiState.value.isLocalBusy && f.model.uiState.value.learningContextSaveResult?.errorMessage?.contains("test-only save rejected") == true }
        val message = requireNotNull(f.model.uiState.value.learningContextSaveResult?.errorMessage)
        capture("immediate-error-dark")
        compose.onNode(hasText(message) and hasAnyAncestor(isDialog())).assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        field("课程名称").assertIsDisplayed().assertTextContains("矩阵分析")
        assertEquals(203, f.dao.getFileById(f.fileId)?.courseId)
        restoration.emulateSavedInstanceStateRestore()
        field("课程名称").assertTextContains("矩阵分析")
        compose.onNodeWithText("保存").assertIsEnabled()
        compose.onNode(hasText(message) and hasAnyAncestor(isDialog())).performScrollTo().assertIsDisplayed()
        capture("retry-dark")
        f.database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_learning_context")
        compose.onNodeWithText("保存").performClick()
        awaitSaved(f)
        assertSaved(f)
    }

    @Test fun aPendingSaveSurvivesRestorationAndClosesOnlyAfterItFinishes() = withFixture { f ->
        val restoration = show(f)
        openEditor()
        fillDraft()
        val gate = Gate().also { f.saveGate = it }
        compose.onNodeWithText("保存").performClick()
        assertTrue(gate.entered.await(5, TimeUnit.SECONDS))
        assertEquals(f.fileId, f.model.uiState.value.learningContextSaveResult?.fileId)
        assertTrue(f.model.uiState.value.learningContextSaveResult?.isSaving == true)
        compose.onNodeWithText("取消").assertIsNotEnabled()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("取消").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("课程名称").assertTextContains("矩阵分析").assertIsNotEnabled()
        compose.onNodeWithText("保存中").assertIsNotEnabled()
        gate.release.countDown()
        awaitSaved(f)
        assertSaved(f)
        compose.onNodeWithText("关联课程/标签").assertDoesNotExist()
    }

    @Test fun cancellingAFailedSaveDoesNotLeaveAnErrorCoveringTheFileMenu() = withFixture { f ->
        show(f)
        openEditor()
        fillDraft()
        f.database.openHelper.writableDatabase.execSQL(
            "CREATE TRIGGER reject_learning_context BEFORE UPDATE ON kb_file BEGIN SELECT RAISE(ABORT, 'test-only save rejected'); END"
        )
        compose.onNodeWithText("保存").performClick()
        f.await { !f.model.uiState.value.isLocalBusy && f.model.uiState.value.learningContextSaveResult?.errorMessage != null }
        val message = requireNotNull(f.model.uiState.value.learningContextSaveResult?.errorMessage)
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText(message).assertDoesNotExist()
        openEditor()
        field("课程名称").assertTextContains("原课程")
        assertEquals(203, f.dao.getFileById(f.fileId)?.courseId)
        assertNull(f.model.uiState.value.learningContextSaveResult)
    }

    @Test fun anotherFilesPendingRenameDoesNotPreventCancellingTheEditor() = withFixture { f ->
        show(f)
        val gate = Gate().also { f.saveGate = it }
        withContext(Dispatchers.Main) { f.model.renameFile(f.otherFileId, "后台重命名.txt") }
        assertTrue(gate.entered.await(5, TimeUnit.SECONDS))
        openEditor()
        capture("unrelated-busy-cancel")
        compose.onNodeWithText("保存").assertIsNotEnabled()
        compose.onNodeWithText("保存中").assertDoesNotExist()
        compose.onNodeWithText("取消").assertIsEnabled().performClick()
        compose.onNodeWithText("关联课程/标签").assertDoesNotExist()
        assertNull(f.model.uiState.value.learningContextSaveResult)
        gate.release.countDown()
        f.await { !f.model.uiState.value.isLocalBusy }
        assertEquals("原课程", f.dao.getFileById(f.fileId)?.courseName)
        assertEquals("后台重命名.txt", f.dao.getFileById(f.otherFileId)?.displayName)
    }

    @Test fun anotherFilesPendingRenameKeepsTheDraftEditableUntilItCanBeSaved() = withFixture { f ->
        show(f, dark = true)
        val gate = Gate().also { f.saveGate = it }
        withContext(Dispatchers.Main) { f.model.renameFile(f.otherFileId, "后台重命名.txt") }
        assertTrue(gate.entered.await(5, TimeUnit.SECONDS))
        openEditor()
        field("课程名称").assertIsEnabled()
        field("课程名称").performTextReplacement("矩阵分析")
        compose.onNodeWithText("保存").assertIsNotEnabled()
        assertNull(f.model.uiState.value.learningContextSaveResult)
        gate.release.countDown()
        f.await { !f.model.uiState.value.isLocalBusy }
        field("课程名称").assertTextContains("矩阵分析")
        field("课程 ID，可留空").performTextReplacement("224")
        field("标签，用逗号分隔").performTextReplacement("期末，重点, 重点")
        compose.onNodeWithText("保存").assertIsEnabled().performClick()
        awaitSaved(f)
        assertSaved(f)
        assertEquals("后台重命名.txt", f.dao.getFileById(f.otherFileId)?.displayName)
    }

    @Test fun aLongFilenameDoesNotHideSaveErrorsAndTheDraftCanBeRetried() {
        withFixture("矩阵分析课程笔记与复习资料".repeat(4) + ".txt") { f ->
            show(f, dark = true)
            openEditor()
            field("课程 ID，可留空").performScrollTo().performTextReplacement("224")
            field("课程名称").performScrollTo().performTextReplacement("矩阵分析")
            field("标签，用逗号分隔").performScrollTo().performTextReplacement("期末，重点, 重点")
            f.database.openHelper.writableDatabase.execSQL(
                "CREATE TRIGGER reject_learning_context BEFORE UPDATE ON kb_file BEGIN SELECT RAISE(ABORT, 'test-only save rejected'); END"
            )
            compose.onNodeWithText("保存").performClick()
            f.await { !f.model.uiState.value.isLocalBusy && f.model.uiState.value.learningContextSaveResult?.errorMessage != null }
            val message = requireNotNull(f.model.uiState.value.learningContextSaveResult?.errorMessage)
            capture("long-name-error-dark")
            compose.onNode(hasText(message) and hasAnyAncestor(isDialog())).assertIsDisplayed()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            field("课程名称").performScrollTo().assertTextContains("矩阵分析")
            f.database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_learning_context")
            compose.onNodeWithText("保存").performClick()
            awaitSaved(f)
            assertSaved(f)
        }
    }

    @Test fun cancellingTheRestoredDraftLeavesTheOriginalMetadataUntouched() = withFixture { f ->
        val restoration = show(f, dark = true)
        openEditor()
        fillDraft()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("取消").assertIsDisplayed().performClick()
        assertEquals(203, f.dao.getFileById(f.fileId)?.courseId)
        assertEquals("原课程", f.dao.getFileById(f.fileId)?.courseName)
        openEditor()
        field("课程名称").assertTextContains("原课程")
        field("标签，用逗号分隔").assertTextContains("旧标签")
    }

    @Test fun blankValuesCanExplicitlyRemoveTheOriginalAssociationAndTags() = withFixture { f ->
        show(f, dark = true)
        openEditor()
        field("课程 ID，可留空").performTextClearance()
        field("课程名称").performTextClearance()
        field("标签，用逗号分隔").performTextReplacement(" ，, ")
        compose.onNodeWithText("保存").assertIsEnabled().performClick()
        f.await { f.dao.getFileById(f.fileId)?.courseId == null && !f.model.uiState.value.isLocalBusy }
        compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty() }
        val saved = requireNotNull(f.local.getFileSummary(f.fileId))
        assertNull(saved.courseId)
        assertNull(saved.courseName)
        assertTrue(saved.tags.isEmpty())
        assertEquals(203, f.dao.getFileById(f.otherFileId)?.courseId)
        assertArrayEquals(f.payload, File(saved.localPath).readBytes())
    }

    @Test fun repositoryRejectsInvalidCourseIdsWithoutChangingStoredMetadata() = withFixture { f ->
        listOf(0, -1).forEach { courseId ->
            assertTrue(f.local.updateFileLearningContext(f.fileId, courseId, "不应保存", emptyList()).isFailure)
            assertEquals(203, f.dao.getFileById(f.fileId)?.courseId)
            assertEquals("原课程", f.dao.getFileById(f.fileId)?.courseName)
            assertEquals(listOf("旧标签"), f.local.getFileSummary(f.fileId)?.tags)
        }
    }

    @Test fun lightShortEditorKeepsTheFullFilenameAndEveryFieldReachable() = checkLongEditor(dark = false)
    @Test fun darkShortEditorKeepsTheFullFilenameAndEveryFieldReachable() = checkLongEditor(dark = true)

    private fun checkLongEditor(dark: Boolean) {
        val name = "矩阵分析课程笔记与复习资料".repeat(4) + ".txt"
        withFixture(name) { f ->
            show(f, dark)
            openEditor()
            field("标签，用逗号分隔").performScrollTo().assertIsDisplayed()
            field("课程名称").performScrollTo().assertIsDisplayed()
            field("课程 ID，可留空").performScrollTo().assertIsDisplayed()
            val filename = compose.onAllNodesWithText(name, useUnmergedTree = true).onLast()
            filename.performScrollTo().assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            assertTrue(filename.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts))
            assertFalse(layouts.single().hasVisualOverflow)
            compose.onNodeWithText("保存").assertIsDisplayed()
            compose.onNodeWithText("取消").assertIsDisplayed()
            capture("long-name-${if (dark) "dark" else "light"}")
        }
    }

    private fun show(f: Fixture, dark: Boolean = false): StateRestorationTester = StateRestorationTester(compose).also { restoration ->
        restoration.setContent {
            CompositionLocalProvider(LocalAiEnabled provides false,
                LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                CorrectionNotebookTheme(darkTheme = dark, dynamicColor = false) {
                    Box(Modifier.safeDrawingPadding()) {
                        Box(Modifier.size(320.dp, 400.dp)) { KnowledgeBaseScreen({}, viewModel = f.model) }
                    }
                }
            }
        }
    }

    private fun openEditor() {
        compose.onAllNodesWithContentDescription("更多操作").onLast().performClick()
        compose.onNodeWithText("关联课程/标签").performClick()
    }

    private fun field(label: String) = compose.onNode(hasSetTextAction() and hasText(label))

    private fun fillDraft() {
        field("课程 ID，可留空").performTextReplacement("224")
        field("课程名称").performTextReplacement("矩阵分析")
        field("标签，用逗号分隔").performTextReplacement("期末，重点, 重点")
    }

    private suspend fun awaitSaved(f: Fixture) {
        f.await { f.dao.getFileById(f.fileId)?.courseName == "矩阵分析" && !f.model.uiState.value.isLocalBusy }
        compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty() }
        compose.onNode(hasSetTextAction()).assertDoesNotExist()
    }

    private suspend fun assertSaved(f: Fixture) {
        val saved = requireNotNull(f.local.getFileSummary(f.fileId))
        assertEquals(224, saved.courseId)
        assertEquals(listOf("期末", "重点"), saved.tags)
        assertEquals("原课程", f.dao.getFileById(f.otherFileId)?.courseName)
        assertEquals(203, f.dao.getFileById(f.otherFileId)?.courseId)
        assertArrayEquals(f.payload, File(saved.localPath).readBytes())
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(350)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "qa-learning-context-$name.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    private fun withFixture(name: String = "矩阵分析.txt", test: suspend (Fixture) -> Unit) = runBlocking {
        withTimeout(20_000) {
            val f = Fixture(name)
            f.start(this)
            try { test(f) } finally {
                withContext(NonCancellable) {
                    f.saveGate?.release?.countDown()
                    f.activeSaveGate?.release?.countDown()
                    withContext(Dispatchers.Main) { f.store.clear() }
                    f.model.viewModelScope.coroutineContext[Job]?.join()
                    f.collector?.cancelAndJoin()
                    f.database.close()
                    f.client.dispatcher.executorService.shutdownNow()
                    f.client.connectionPool.evictAll()
                    f.directory.deleteRecursively()
                }
            }
        }
    }

    private class Gate {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        fun await() { entered.countDown(); check(release.await(5, TimeUnit.SECONDS)) }
    }

    private class Fixture(private val name: String) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File.createTempFile("learning-context-qa-", "", target.cacheDir).apply { delete(); mkdir() }
        val context = object : ContextWrapper(target) { override fun getFilesDir() = directory }
        @Volatile var saveGate: Gate? = null
        @Volatile var activeSaveGate: Gate? = null
        val database = Room.inMemoryDatabaseBuilder(context, KnowledgeBaseDatabase::class.java)
            .setQueryCallback(object : RoomDatabase.QueryCallback {
                override fun onQuery(sqlQuery: String, bindArgs: List<Any?>) {
                    if (sqlQuery.startsWith("UPDATE", ignoreCase = true) && sqlQuery.contains("kb_file")) {
                        saveGate?.also { saveGate = null; activeSaveGate = it }?.await()
                    }
                }
            }, { it.run() }).build()
        val dao = database.knowledgeBaseDao()
        val local = KnowledgeBaseRepository(dao, KnowledgeBaseFileStorage(context), context)
        val study = StudySetRepository(dao)
        val client = OkHttpClient.Builder().addInterceptor { throw AssertionError("Learning metadata edits must stay offline") }.build()
        val store = ViewModelStore()
        lateinit var model: KnowledgeBaseViewModel
        var collector: Job? = null
        lateinit var fileId: String
        lateinit var otherFileId: String
        val payload = "课程资料原始内容".toByteArray()
        suspend fun start(scope: CoroutineScope) {
            repeat(2) { local.createFolder(null, "课程资料").getOrThrow() }
            val folders = dao.getAllFolders()
            val source = File(directory, name).apply { writeBytes(payload) }
            local.importLocalFile(folders[0].id, Uri.fromFile(source)).getOrThrow()
            local.importLocalFile(folders[1].id, Uri.fromFile(source)).getOrThrow()
            fileId = dao.getFilesByFolder(folders[0].id).single().id
            otherFileId = dao.getFilesByFolder(folders[1].id).single().id
            listOf(fileId, otherFileId).forEach { local.updateFileLearningContext(it, 203, "原课程", listOf("旧标签")).getOrThrow() }
            val service = Retrofit.Builder().baseUrl("http://127.0.0.1/").client(client)
                .addConverterFactory(GsonConverterFactory.create()).build().create(BitShareApiService::class.java)
            withContext(Dispatchers.Main) {
                model = KnowledgeBaseViewModel(local, BitShareRepository(service, client) { throw AssertionError("No network expected") }, study)
                store.put("learning", model)
                model.enterFolder(folders[0].id)
            }
            collector = scope.launch(Dispatchers.Default) { model.uiState.collect {} }
            await { model.uiState.value.folderContent.files.singleOrNull()?.id == fileId }
        }
        suspend fun await(predicate: suspend () -> Boolean) = withTimeout(5_000) { while (!predicate()) delay(10) }
    }
}
