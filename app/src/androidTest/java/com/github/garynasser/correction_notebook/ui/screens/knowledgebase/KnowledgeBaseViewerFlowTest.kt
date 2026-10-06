package com.github.garynasser.correction_notebook.ui.screens.knowledgebase

import android.graphics.Bitmap
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
import com.github.garynasser.correction_notebook.data.remote.ai.AnthropicCompatibleAdapter
import com.github.garynasser.correction_notebook.data.remote.ai.OpenAiCompatibleAdapter
import com.github.garynasser.correction_notebook.data.remote.api.AIApiService
import com.github.garynasser.correction_notebook.data.repository.*
import com.github.garynasser.correction_notebook.domain.usecase.AiStudyUseCase
import com.github.garynasser.correction_notebook.ui.components.LocalAiEnabled
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import com.google.gson.Gson
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.RequestBody
import okhttp3.ResponseBody
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

    private fun show(f: Fixture, dark: Boolean = false, height: Int = 180) {
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalAiEnabled provides false, LocalDensity provides Density(density, 1.3f)) {
                CorrectionNotebookTheme(darkTheme = dark, dynamicColor = false) {
                    Box(Modifier.safeDrawingPadding()) {
                        Box(Modifier.size(width = 320.dp, height = height.dp)) {
                            KnowledgeBaseFileViewerScreen({}, {}, f.vm)
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
            withContext(Dispatchers.Main) { f.store.clear() }
            if (f.hasViewModel) f.vm.viewModelScope.coroutineContext[Job]?.join()
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
        @Volatile var failure: String? = null
        private val dao = object : KnowledgeBaseDao by database.knowledgeBaseDao() {
            override suspend fun getFileById(fileId: String): KnowledgeBaseFileEntity? {
                failure?.let { throw IOException(it) }
                return database.knowledgeBaseDao().getFileById(fileId)
            }
        }
        suspend fun start() {
            val settings = AISettingsManager(context)
            val service = object : AIApiService {
                override suspend fun getJson(url: String, headers: Map<String, String>): Response<ResponseBody> =
                    throw AssertionError("Viewer recovery must not request AI models")
                override suspend fun postJson(url: String, headers: Map<String, String>, request: RequestBody): Response<ResponseBody> =
                    throw AssertionError("Viewer recovery must not send an AI request")
            }
            val gson = Gson()
            val ai = AIRepository(settings, ProviderRepository(aiDatabase.aiProviderDao(), AiCredentialCipher(context)),
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
        suspend fun addTextFile(createLocalFile: Boolean = true, mimeType: String = "text/plain", displayName: String = "QA 资料.txt") {
            if (createLocalFile) localFile.writeText(CONTENT)
            val now = System.currentTimeMillis()
            database.knowledgeBaseDao().insertFile(KnowledgeBaseFileEntity(FILE_ID, null, displayName, localFile.name,
                localFile.absolutePath, mimeType, localFile.length(), "LOCAL", null, null, null, null, null, "", null, now, now))
        }
        suspend fun awaitPreview() = await { !vm.uiState.value.isLoading && vm.uiState.value.textPreview == CONTENT }
        private suspend fun await(predicate: () -> Boolean) = withTimeout(5_000) { while (!predicate()) delay(10) }
    }

    private companion object {
        const val FILE_ID = "viewer-flow-qa"
        const val CONTENT = "修复后的资料内容"
    }
}
