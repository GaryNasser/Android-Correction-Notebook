package com.github.garynasser.correction_notebook.ui.screens.knowledgebase

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.local.AISettingsManager
import com.github.garynasser.correction_notebook.data.local.ai.AiCredentialCipher
import com.github.garynasser.correction_notebook.data.local.ai.AiDatabase
import com.github.garynasser.correction_notebook.data.local.knowledgebase.*
import com.github.garynasser.correction_notebook.data.model.ai.AiProviderForm
import com.github.garynasser.correction_notebook.data.remote.ai.AnthropicCompatibleAdapter
import com.github.garynasser.correction_notebook.data.remote.ai.OpenAiCompatibleAdapter
import com.github.garynasser.correction_notebook.data.remote.api.AIApiService
import com.github.garynasser.correction_notebook.data.repository.*
import com.github.garynasser.correction_notebook.domain.usecase.AiStudyUseCase
import com.github.garynasser.correction_notebook.domain.usecase.KnowledgeAiMode
import com.github.garynasser.correction_notebook.ui.components.LocalAiEnabled
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import com.google.gson.Gson
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.first
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import retrofit2.Response

class KnowledgeBaseViewerFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightShortViewerErrorKeepsRetryReachable() = checkLongError(false)
    @Test fun darkShortViewerErrorKeepsRetryReachable() = checkLongError(true)

    private fun checkLongError(dark: Boolean) = withFixture { f ->
        f.failure = (1..12).joinToString("\n") { "暂时无法读取资料记录，请稍后重试（$it）。" }
        f.start()
        val message = requireNotNull(f.vm.uiState.value.errorMessage)
        show(f, dark)
        compose.onNodeWithText("重试").performScrollTo().assertIsDisplayed().assertIsEnabled()
        assertFullText(message)
        saveScreenshot("error-${if (dark) "dark" else "light"}")
        f.failure = null
        f.addTextFile()
        compose.onNodeWithText("重试").performClick()
        f.awaitPreview()
        compose.onNodeWithText(CONTENT).assertIsDisplayed()
        compose.onNodeWithText("文件不可用").assertDoesNotExist()
    }

    @Test fun retryLoadsAFileRecordRestoredAfterTheViewerOpened() = withFixture { f ->
        f.start()
        show(f)
        compose.onNodeWithText("文件不存在或已被删除").assertIsDisplayed()
        f.addTextFile()
        compose.onNodeWithText("重试").performScrollTo().performClick()
        f.awaitPreview()
        compose.onNodeWithText(CONTENT).assertIsDisplayed()
    }

    @Test fun missingLocalFileIsNotMisreportedAsAnUnsupportedFormatAndCanRecover() = withFixture { f ->
        f.addTextFile(createLocalFile = false)
        f.start()
        show(f)
        compose.onNodeWithText("文件不可用").assertIsDisplayed()
        compose.onNodeWithText("本地文件不存在，可能已被移除").assertIsDisplayed()
        compose.onNodeWithText("当前格式暂不支持应用内深度预览").assertDoesNotExist()
        f.localFile.writeText(CONTENT)
        compose.onNodeWithText("重试").performScrollTo().performClick()
        f.awaitPreview()
        compose.onNodeWithText(CONTENT).assertIsDisplayed()
    }

    @Test fun lightFailedPreviewCanBeRetriedAfterTheFileIsRepaired() = checkFailedPreview(false)
    @Test fun darkFailedPreviewCanBeRetriedAfterTheFileIsRepaired() = checkFailedPreview(true)

    @Test fun lightLongFileInfoKeepsEveryFieldReadable() = checkFileInfo(false)
    @Test fun darkLongFileInfoKeepsEveryFieldReadable() = checkFileInfo(true)

    private fun checkFileInfo(dark: Boolean) = withFixture { f ->
        val name = "矩阵分析期末复习资料与课堂讨论记录 ".repeat(12) + ".txt"
        val source = "课程资料来源与版本说明 ".repeat(24)
        f.addTextFile(displayName = name, sourceTitle = source)
        f.start()
        show(f, dark, height = 340)
        menuAction("文件信息")
        compose.onNodeWithText("来源: $source").performScrollTo().assertIsDisplayed()
        assertFullText("名称: $name")
        assertFullText("来源: $source")
        compose.onNodeWithText("类型: text/plain").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("关闭").assertIsDisplayed().performClick()
        compose.onNodeWithText("文件信息").assertDoesNotExist()
        assertTrue(f.localFile.exists())
    }

    @Test fun lightLongDeleteConfirmationKeepsTheFullNameAndAllowsCancellation() = checkDeleteConfirmation(false)
    @Test fun darkLongDeleteConfirmationKeepsTheFullNameAndAllowsCancellation() = checkDeleteConfirmation(true)

    private fun checkDeleteConfirmation(dark: Boolean) = withFixture { f ->
        val name = "矩阵分析期末复习资料与课堂讨论记录 ".repeat(20) + ".txt"
        f.addTextFile(displayName = name)
        f.start()
        show(f, dark, height = 340)
        menuAction("删除")
        val message = "确定删除“$name”吗？"
        compose.onNodeWithText(message).performScrollTo().assertIsDisplayed()
        assertFullText(message)
        saveScreenshot("delete-confirm-${if (dark) "dark" else "light"}")
        compose.onNodeWithText("取消").assertIsDisplayed().performClick()
        assertTrue(f.localFile.exists())
        assertEquals(0, f.deleteCalls)
        assertFalse(f.vm.uiState.value.isDeleted)
    }

    @Test fun previewRecoveryActionsAreDisabledWhileTheFileIsBeingDeleted() = withFixture { f ->
        f.addTextFile(mimeType = "application/octet-stream")
        f.start()
        val gate = Gate().also { f.deleteGate = it }
        var exits = 0
        show(f, height = 340, onDeleted = { exits++ })
        menuAction("删除")
        compose.onNodeWithText("删除").performClick()
        gate.entered.await()
        compose.onNodeWithText("重试").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithText("其他应用打开").performScrollTo().assertIsNotEnabled()
        gate.release.complete(Unit)
        compose.waitUntil(5_000) { exits == 1 }
        assertTrue(f.vm.uiState.value.isDeleted)
        assertFalse(f.localFile.exists())
    }

    @Test fun refreshCannotReplaceAnActiveOrCompletedDeletion() = withFixture { f ->
        f.addTextFile()
        f.start()
        val gate = Gate().also { f.deleteGate = it }
        withContext(Dispatchers.Main) { f.vm.deleteCurrentFile() }
        gate.entered.await()
        val reads = f.recordReads
        withContext(Dispatchers.Main) {
            f.vm.refresh()
            f.vm.runAiAction(KnowledgeAiMode.SUMMARY)
            f.vm.generateStudySet()
            f.vm.rebuildIndex()
            f.vm.exportFile(FILE_ID, Uri.fromFile(File(f.localFile.absolutePath + ".export")))
            assertTrue(f.vm.uiState.value.isDeletingFile)
            assertFalse(f.vm.uiState.value.isLoading)
            assertTrue(f.vm.isExportError)
        }
        assertEquals(reads, f.recordReads)
        gate.release.complete(Unit)
        f.await { f.vm.uiState.value.isDeleted }
        withContext(Dispatchers.Main) { f.vm.refresh() }
        assertTrue(f.vm.uiState.value.isDeleted)
        assertEquals(reads, f.recordReads)
    }

    @Test fun failedDeletionRestoresTheFileAndCanBeRetriedFromTheScreen() = withFixture { f ->
        f.addTextFile()
        f.start()
        f.deleteFailure = true
        var exits = 0
        show(f, height = 340, onDeleted = { exits++ })
        menuAction("删除")
        compose.onNodeWithText("删除").performClick()
        f.await { !f.vm.uiState.value.isDeletingFile && f.vm.uiState.value.errorMessage != null }
        assertEquals(CONTENT, f.localFile.readText())
        assertEquals(0, exits)
        f.deleteFailure = false
        menuAction("删除")
        compose.onNodeWithText("删除").performClick()
        compose.waitUntil(5_000) { exits == 1 }
        assertTrue(f.vm.uiState.value.isDeleted)
        assertFalse(f.localFile.exists())
        assertEquals(2, f.deleteCalls)
    }

    @Test fun deletingAFileCancelsItsPendingAiActionAndDoesNotResurrectItsCache() = checkPendingAiDeletion(false)
    @Test fun failedDeletionAlsoStopsItsPendingAiActionAndAllowsRecovery() = checkPendingAiDeletion(true)

    private fun checkPendingAiDeletion(failDeletion: Boolean) = withFixture { f ->
        f.addTextFile()
        f.start()
        f.enableAi()
        val aiGate = Gate().also { f.aiGate = it }
        val deleteGate = Gate().also { f.deleteGate = it }
        f.deleteFailure = failDeletion
        withContext(Dispatchers.Main) { f.vm.runAiAction(KnowledgeAiMode.SUMMARY) }
        aiGate.entered.await()
        withContext(Dispatchers.Main) { f.vm.deleteCurrentFile() }
        deleteGate.entered.await()
        assertTrue("Deletion must cancel the pending AI request", aiGate.cancelled.isCompleted)
        assertFalse(f.vm.uiState.value.isAiLoading)
        deleteGate.release.complete(Unit)
        f.await { if (failDeletion) !f.vm.uiState.value.isDeletingFile && f.vm.uiState.value.errorMessage != null else f.vm.uiState.value.isDeleted }
        assertNull(f.database.knowledgeBaseDao().getAiResultCache(FILE_ID, KnowledgeAiMode.SUMMARY.name))
        assertNull(f.vm.uiState.value.aiResult)
        if (failDeletion) {
            assertEquals(CONTENT, f.localFile.readText())
            f.deleteGate = null
            f.deleteFailure = false
            withContext(Dispatchers.Main) { f.vm.refresh() }
            f.awaitPreview()
        } else {
            assertTrue(f.database.knowledgeBaseDao().getChunksForFile(FILE_ID).isEmpty())
        }
    }

    @Test fun exportFromTheViewerReportsFailureAndRetriesWithTheCurrentSavedFilePath() = withFixture { f ->
        f.addTextFile()
        f.start()
        val registry = RecordingRegistry()
        show(f, height = 340, registry = registry)
        val target = File.createTempFile("viewer-export-", ".txt", f.context.cacheDir)
        val moved = File.createTempFile("viewer-moved-", ".txt", f.context.cacheDir).apply { delete() }
        try {
            menuAction("导出副本")
            compose.runOnIdle { registry.dispatchResult(registry.requestCode, Uri.fromFile(File(target, "missing.txt"))) }
            f.await { f.vm.isExportError }
            assertTrue(f.localFile.exists())
            assertTrue(f.localFile.renameTo(moved))
            val record = requireNotNull(f.database.knowledgeBaseDao().getFileById(FILE_ID))
            f.database.knowledgeBaseDao().insertFile(record.copy(localPath = moved.absolutePath))
            menuAction("导出副本")
            compose.runOnIdle { registry.dispatchResult(registry.requestCode, Uri.fromFile(target)) }
            f.await { f.vm.exportMessage == "已导出文件副本" }
            compose.onNodeWithText("已导出文件副本").assertIsDisplayed()
            assertFalse(f.vm.isExportError)
            assertEquals(CONTENT, target.readText())
            assertEquals(2, registry.launches)
        } finally { target.delete(); moved.delete() }
    }

    @Test fun unsupportedFileKeepsItsFullNameAndBothRecoveryActionsOnAShortScreen() = withFixture { f ->
        val name = "矩阵分析期末复习资料与课堂讨论记录 ".repeat(5) + ".bin"
        f.addTextFile(mimeType = "application/octet-stream", displayName = name)
        f.start()
        show(f, dark = true, height = 240)
        val nameNode = compose.onNode(hasText(name) and hasAnyAncestor(hasScrollAction()), useUnmergedTree = true)
        nameNode.performScrollTo()
        val layouts = mutableListOf<TextLayoutResult>()
        nameNode.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        layouts.forEach { assertFalse(it.hasVisualOverflow); assertEquals(name.length, it.getLineEnd(it.lineCount - 1)) }
        compose.onNodeWithText("其他应用打开").performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("重试").performScrollTo().assertIsDisplayed().performClick()
        compose.waitUntil(5_000) { !f.vm.uiState.value.isLoading }
        assertEquals(KnowledgeBasePreviewType.FALLBACK, f.vm.uiState.value.previewType)
    }

    private fun checkFailedPreview(dark: Boolean) = withFixture { f ->
        f.addTextFile(mimeType = "application/pdf")
        f.start()
        assertEquals(KnowledgeBasePreviewType.FALLBACK, f.vm.uiState.value.previewType)
        show(f, dark, height = 280)
        compose.onNodeWithText("暂无可用预览").assertIsDisplayed()
        compose.onNodeWithText("其他应用打开").assertIsEnabled()
        compose.onNodeWithText("重试").performScrollTo().assertIsDisplayed()
        saveScreenshot("fallback-${if (dark) "dark" else "light"}")
        f.addTextFile()
        compose.onNodeWithText("重试").performClick()
        f.awaitPreview()
        compose.onNodeWithText(CONTENT).assertIsDisplayed()
    }

    private fun menuAction(label: String) {
        compose.onNodeWithContentDescription("更多操作").performClick()
        compose.onNodeWithText(label).performScrollTo().performClick()
    }

    private fun show(f: Fixture, dark: Boolean = false, height: Int = 180, onDeleted: () -> Unit = {}, registry: ActivityResultRegistry? = null) {
        val owner = registry?.let { object : ActivityResultRegistryOwner { override val activityResultRegistry = it } }
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalAiEnabled provides false, LocalDensity provides Density(density, 1.3f),
                LocalActivityResultRegistryOwner provides (owner ?: requireNotNull(LocalActivityResultRegistryOwner.current))) {
                CorrectionNotebookTheme(darkTheme = dark, dynamicColor = false) {
                    Box(Modifier.safeDrawingPadding()) {
                        Box(Modifier.size(width = 320.dp, height = height.dp)) {
                            KnowledgeBaseFileViewerScreen({}, onDeleted, f.vm)
                        }
                    }
                }
            }
        }
    }

    private fun assertFullText(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        layouts.forEach {
            assertFalse(it.didOverflowHeight)
            assertEquals(text.length, it.getLineEnd(it.lineCount - 1))
            repeat(it.lineCount) { line ->
                assertFalse(it.isLineEllipsized(line))
                assertTrue(it.getLineRight(line) <= it.size.width + 1)
            }
        }
    }

    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(350)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "qa-viewer-$name.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    private fun withFixture(test: suspend (Fixture) -> Unit) = runBlocking {
        val f = Fixture()
        try { withTimeout(15_000) { test(f) } }
        finally {
            f.deleteGate?.release?.complete(Unit)
            f.aiGate?.release?.complete(Unit)
            withContext(Dispatchers.Main) { f.store.clear() }
            if (f.hasViewModel) f.vm.viewModelScope.coroutineContext[Job]?.join()
            f.aiEnabledBefore?.let { f.settings.setAiEnabled(it) }
            f.database.close()
            f.aiDatabase.close()
            f.localFile.delete()
        }
    }

    private class Fixture {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, KnowledgeBaseDatabase::class.java).build()
        val aiDatabase = Room.inMemoryDatabaseBuilder(context, AiDatabase::class.java).build()
        val localFile = File.createTempFile("viewer-qa-", ".bin", context.cacheDir)
            .apply { delete() }
        val store = ViewModelStore()
        lateinit var vm: KnowledgeBaseFileViewerViewModel
        val hasViewModel get() = ::vm.isInitialized
        val settings = AISettingsManager(context)
        private val providers = ProviderRepository(aiDatabase.aiProviderDao(), AiCredentialCipher(context))
        private lateinit var ai: AIRepository
        var aiEnabledBefore: Boolean? = null
        @Volatile var failure: String? = null
        @Volatile var deleteFailure = false
        @Volatile var deleteGate: Gate? = null
        @Volatile var aiGate: Gate? = null
        @Volatile var recordReads = 0
        @Volatile var deleteCalls = 0
        private val dao = object : KnowledgeBaseDao by database.knowledgeBaseDao() {
            override suspend fun getFileById(fileId: String): KnowledgeBaseFileEntity? {
                recordReads++
                failure?.let { throw IOException(it) }
                return database.knowledgeBaseDao().getFileById(fileId)
            }
            override suspend fun deleteFileWithDerivedData(file: KnowledgeBaseFileEntity) {
                deleteCalls++
                deleteGate?.await()
                if (deleteFailure) throw IOException("删除失败，请稍后再试")
                database.knowledgeBaseDao().deleteFileWithDerivedData(file)
            }
        }
        suspend fun start() {
            val service = object : AIApiService {
                override suspend fun getJson(url: String, headers: Map<String, String>): Response<ResponseBody> =
                    throw AssertionError("Viewer recovery must not request AI models")
                override suspend fun postJson(url: String, headers: Map<String, String>, request: RequestBody): Response<ResponseBody> {
                    val gate = aiGate ?: throw AssertionError("Viewer recovery must not send an AI request")
                    gate.await()
                    return Response.success("""{"choices":[{"message":{"content":"QA summary"}}]}""".toResponseBody())
                }
            }
            val gson = Gson()
            ai = AIRepository(settings, providers,
                OpenAiCompatibleAdapter(service, gson), AnthropicCompatibleAdapter(service, gson), gson)
            val repository = KnowledgeBaseRepository(dao, KnowledgeBaseFileStorage(context), context)
            val index = KnowledgeBaseAiRepository(dao)
            val useCase = AiStudyUseCase(ai, index, MemoryRepository(aiDatabase.userMemoryDao()),
                TodoRepository(context), ScheduleRepository(context), StudySessionRepository(context), CourseLearningRepository(context), repository)
            withContext(Dispatchers.Main) {
                vm = KnowledgeBaseFileViewerViewModel(repository, index, KnowledgeBasePreviewRenderer(context), useCase,
                    StudySetRepository(dao), SavedStateHandle(mapOf("fileId" to FILE_ID)))
                store.put("viewer", vm)
            }
            await { !vm.uiState.value.isLoading }
        }
        suspend fun addTextFile(createLocalFile: Boolean = true, mimeType: String = "text/plain", displayName: String = "QA 资料.txt", sourceTitle: String? = null) {
            if (createLocalFile) localFile.writeText(CONTENT)
            val now = System.currentTimeMillis()
            database.knowledgeBaseDao().insertFile(KnowledgeBaseFileEntity(FILE_ID, null, displayName, localFile.name,
                localFile.absolutePath, mimeType, localFile.length(), "LOCAL", null, sourceTitle, null, null, null, "", null, now, now))
        }
        suspend fun enableAi() {
            aiEnabledBefore = settings.aiEnabled.first()
            settings.setAiEnabled(true)
            providers.saveProvider(ai.normalizeProviderRecord(AiProviderForm(name = "Viewer QA", baseUrl = "https://unused.invalid/v1",
                apiKey = "qa-key", model = "qa-model")))
            database.knowledgeBaseDao().insertChunks(listOf(KnowledgeBaseChunkEntity(fileId = FILE_ID, chunkIndex = 0,
                title = "QA 资料", path = localFile.absolutePath, content = CONTENT, keywords = "QA", updatedAt = System.currentTimeMillis())))
        }
        suspend fun awaitPreview() = await { !vm.uiState.value.isLoading && vm.uiState.value.textPreview == CONTENT }
        suspend fun await(predicate: () -> Boolean) = withTimeout(5_000) { while (!predicate()) delay(10) }
    }

    private class Gate {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        suspend fun await() {
            entered.complete(Unit)
            try { release.await() }
            catch (error: CancellationException) { cancelled.complete(Unit); throw error }
        }
    }

    private class RecordingRegistry : ActivityResultRegistry() {
        var requestCode = 0
        var launches = 0
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            this.requestCode = requestCode
            launches++
        }
    }

    private companion object {
        const val FILE_ID = "viewer-flow-qa"
        const val CONTENT = "修复后的资料内容"
    }
}
