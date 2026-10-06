package com.github.garynasser.correction_notebook.ui.screens.knowledgebase

import android.content.ContextWrapper
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseDatabase
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseFileStorage
import com.github.garynasser.correction_notebook.data.model.knowledgebase.*
import com.github.garynasser.correction_notebook.data.remote.api.BitShareApiService
import com.github.garynasser.correction_notebook.data.repository.*
import com.github.garynasser.correction_notebook.ui.components.LocalAiEnabled
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import com.google.gson.Gson
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.MediaType.Companion.toMediaType
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class KnowledgeBaseRemoteFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun restoredFolderPickerDownloadsTheDisplayedFileIntoTheChosenFolder() = checkDownload(restore = true)
    @Test fun clickingAnotherResultDuringLoadingCannotChangeTheDisplayedDownloadTarget() = checkDownload(restore = false)

    private fun checkDownload(restore: Boolean) = withFixture { f ->
        f.local.createFolder(null, "课程资料").getOrThrow()
        f.await { f.model.uiState.value.folderChoices.size == 2 }
        val folderId = f.database.knowledgeBaseDao().getAllFolders().single().id
        val opened = mutableListOf<String>()
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            TestScreen(f.model, dark = !restore, onOpenFile = { opened += it })
        }
        compose.onNodeWithContentDescription("搜索当前目录").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("旧目录中的其他资料")
        search(f)
        capture("remote-search-${if (restore) "light" else "dark"}")
        val gate = if (restore) null else Gate().also { f.gate = it }
        compose.onNodeWithText("矩阵分析讲义").performScrollTo().performClick()
        if (gate != null) {
            assertTrue(gate.entered.await(5, TimeUnit.SECONDS))
            compose.onNodeWithText("算法设计讲义").performScrollTo().performClick()
            gate.release.countDown()
        }
        f.await { f.model.uiState.value.selectedRemoteDetail?.id == "a" }
        compose.onNodeWithText("下载到知识库").performClick()
        if (restore) restoration.emulateSavedInstanceStateRestore()
        capture("remote-picker-${if (restore) "light" else "dark"}")
        compose.onNodeWithText("课程资料").assertIsDisplayed().performClick()
        f.await { f.model.uiState.value.activeDownloadId == null && f.model.uiState.value.selectedTabIndex == 0 }
        val saved = f.database.knowledgeBaseDao().getAllFiles().single()
        assertEquals("a", saved.sourceFileId)
        assertEquals(folderId, saved.folderId)
        assertEquals("矩阵分析.txt", saved.displayName)
        assertArrayEquals("payload-a".toByteArray(), File(saved.localPath).readBytes())
        assertEquals(listOf("a"), f.downloads.toList())
        assertEquals(folderId, f.model.uiState.value.currentFolderId)
        assertEquals("", f.model.uiState.value.localSearchQuery)
        f.await { f.model.uiState.value.folderContent.files.any { it.id == saved.id } }
        capture("remote-saved-${if (restore) "light" else "dark"}")
        compose.onNodeWithText(saved.displayName).performScrollTo().assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(listOf(saved.id), opened) }
    }

    @Test fun leavingDuringFileDetailLoadingDoesNotReopenTheDialog() = checkLateDetail(folder = false, editQuery = false)
    @Test fun leavingDuringFolderDetailLoadingDoesNotReopenTheDialog() = checkLateDetail(folder = true, editQuery = false)
    @Test fun changingTheSearchQueryInvalidatesThePendingFileDetail() = checkLateDetail(folder = false, editQuery = true)
    @Test fun changingTheSearchQueryInvalidatesThePendingFolderDetail() = checkLateDetail(folder = true, editQuery = true)

    @Test fun aBackgroundDownloadDisablesOtherDownloadsAndCanStillBeCancelled() = withFixture { f ->
        compose.setContent { TestScreen(f.model, dark = true) }
        search(f)
        compose.onNodeWithText("矩阵分析讲义").performScrollTo().performClick()
        f.await { f.model.uiState.value.selectedRemoteDetail?.id == "a" }
        compose.onNodeWithText("下载到知识库").performClick()
        val gate = Gate().also { f.downloadGate = it }
        compose.onNodeWithText("知识库根目录").performClick()
        assertTrue(gate.entered.await(5, TimeUnit.SECONDS))
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText("算法设计讲义").performScrollTo().performClick()
        f.await { f.model.uiState.value.selectedRemoteDetail?.id == "b" }
        compose.onNodeWithText("下载到知识库").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText("矩阵分析讲义").performScrollTo().performClick()
        f.await { f.model.uiState.value.selectedRemoteDetail?.id == "a" }
        capture("remote-download-active")
        compose.onNodeWithText("取消下载").assertIsDisplayed().performClick()
        gate.release.countDown()
        f.await { f.model.uiState.value.activeDownloadId == null }
        compose.onNodeWithText("下载到知识库").assertIsEnabled()
        assertTrue(f.database.knowledgeBaseDao().getAllFiles().isEmpty())
    }

    private suspend fun search(f: Fixture) {
        compose.onNodeWithText("BITShare").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("课程")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNode(hasSetTextAction()).assertIsNotFocused()
        f.await { f.model.uiState.value.remoteResults.size == 2 }
    }

    @Composable private fun TestScreen(model: KnowledgeBaseViewModel, dark: Boolean, onOpenFile: (String) -> Unit = {}) {
        CompositionLocalProvider(LocalAiEnabled provides false,
            LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
            CorrectionNotebookTheme(darkTheme = dark, dynamicColor = false) {
                Box(Modifier.safeDrawingPadding()) {
                    Box(Modifier.size(320.dp, 560.dp)) { KnowledgeBaseScreen(onOpenFile, viewModel = model) }
                }
            }
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

    private fun checkLateDetail(folder: Boolean, editQuery: Boolean) = withFixture { f ->
        val gate = Gate().also { f.gate = it }
        withContext(Dispatchers.Main) {
            f.model.selectTab(2)
            f.model.updateRemoteQuery("课程")
            if (folder) f.model.loadRemoteFolderDetail("folder") else f.model.loadRemoteDetail("a")
        }
        assertTrue(gate.entered.await(5, TimeUnit.SECONDS))
        withContext(Dispatchers.Main) {
            if (editQuery) f.model.updateRemoteQuery("新课程") else f.model.selectTab(0)
        }
        gate.release.countDown()
        f.await { !f.model.uiState.value.isRemoteDetailLoading && !f.model.uiState.value.isRemoteFolderLoading }
        assertNull(f.model.uiState.value.selectedRemoteDetail)
        assertNull(f.model.uiState.value.selectedRemoteFolderDetail)
        assertNull(f.model.uiState.value.remoteErrorMessage)
        withContext(Dispatchers.Main) {
            f.model.selectTab(2)
            if (folder) f.model.loadRemoteFolderDetail("folder") else f.model.loadRemoteDetail("a")
        }
        f.await {
            if (folder) f.model.uiState.value.selectedRemoteFolderDetail?.id == "folder"
            else f.model.uiState.value.selectedRemoteDetail?.id == "a"
        }
        assertTrue(f.database.knowledgeBaseDao().getAllFiles().isEmpty())
    }

    private fun withFixture(test: suspend CoroutineScope.(Fixture) -> Unit) = runBlocking {
        withTimeout(20_000) {
            val f = Fixture()
            f.start(this)
            try { test(f) }
            finally {
                withContext(NonCancellable) {
                    f.gate?.release?.countDown()
                    f.activeGate?.release?.countDown()
                    f.downloadGate?.release?.countDown()
                    f.activeDownloadGate?.release?.countDown()
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

    private class Fixture {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File.createTempFile("remote-flow-qa-", "", target.cacheDir).apply { delete(); mkdir() }
        val context = object : ContextWrapper(target) { override fun getFilesDir() = directory }
        val database = Room.inMemoryDatabaseBuilder(context, KnowledgeBaseDatabase::class.java).build()
        val local = KnowledgeBaseRepository(database.knowledgeBaseDao(), KnowledgeBaseFileStorage(context), context)
        val downloads = CopyOnWriteArrayList<String>()
        @Volatile var gate: Gate? = null
        @Volatile var activeGate: Gate? = null
        @Volatile var downloadGate: Gate? = null
        @Volatile var activeDownloadGate: Gate? = null
        private val gson = Gson()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val path = chain.request().url.encodedPath
            val id = path.substringAfterLast('/')
            val download = path.endsWith("/download")
            if (download) downloadGate?.also { downloadGate = null; activeDownloadGate = it }?.await()
            if (path != "/api/public/search" && !download) {
                gate?.also { gate = null; activeGate = it }?.await()
            }
            val body = when {
                download -> path.substringBeforeLast('/').substringAfterLast('/').let { downloads += it; "payload-$it" }
                path == "/api/public/search" -> gson.toJson(BitShareSearchResponse(listOf(
                    BitShareSearchItemDto("file", "a", "矩阵分析讲义", "矩阵分析.txt", "txt", 9, 0, null),
                    BitShareSearchItemDto("file", "b", "算法设计讲义", "算法设计.txt", "txt", 9, 0, null)
                ), 1, 20, 2))
                path.contains("/folders/") -> gson.toJson(BitShareFolderDetailDto(id, "课程目录", "课程资料", null, emptyList(), 2, 0, 18, null))
                else -> gson.toJson(BitShareFileDetailDto(id, if (id == "a") "矩阵分析讲义" else "算法设计讲义", "txt", null, null, null,
                    if (id == "a") "矩阵分析.txt" else "算法设计.txt", "text/plain", 9, null, 0))
            }
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody(if (download) "text/plain".toMediaType() else "application/json".toMediaType())).build()
        }.build()
        val store = ViewModelStore()
        lateinit var model: KnowledgeBaseViewModel
        var collector: Job? = null
        suspend fun start(scope: CoroutineScope) {
            val api = Retrofit.Builder().baseUrl("http://127.0.0.1/").client(client)
                .addConverterFactory(GsonConverterFactory.create()).build().create(BitShareApiService::class.java)
            withContext(Dispatchers.Main) {
                model = KnowledgeBaseViewModel(local, BitShareRepository(api, client) { error("No network fallback expected") }, StudySetRepository(database.knowledgeBaseDao()))
                store.put("remote", model)
            }
            collector = scope.launch(Dispatchers.Default) { model.uiState.collect {} }
            await { model.uiState.value.folderChoices.isNotEmpty() }
        }
        suspend fun await(predicate: () -> Boolean) = withTimeout(5_000) { while (!predicate()) delay(10) }
    }
}
