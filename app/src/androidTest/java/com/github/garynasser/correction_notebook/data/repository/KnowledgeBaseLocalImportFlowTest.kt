package com.github.garynasser.correction_notebook.data.repository

import android.content.ContentResolver
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
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
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseDatabase
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseFileStorage
import com.github.garynasser.correction_notebook.data.remote.api.BitShareApiService
import com.github.garynasser.correction_notebook.ui.screens.knowledgebase.KnowledgeBaseViewModel
import com.github.garynasser.correction_notebook.ui.screens.knowledgebase.KnowledgeBaseScreen
import com.github.garynasser.correction_notebook.ui.components.LocalAiEnabled
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class KnowledgeBaseLocalImportFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun importingFromMainNeverAccessesTheFileProviderOnMain() = withFixture { f ->
        withContext(Dispatchers.Main) { f.repository.importLocalFile(null, f.uri).getOrThrow() }
        assertTrue(f.resolverCalls.get() >= 2)
        assertFalse("Metadata and file opening must both stay off the UI thread", f.resolverOnMain.get())
        assertArrayEquals(f.payload, File(f.database.knowledgeBaseDao().getAllFiles().single().localPath).readBytes())
    }

    @Test fun aSlowProviderKeepsMainResponsiveAndRejectsDuplicateImportSubmission() = withFixture { f ->
        f.start(this)
        val gate = Gate().also { f.gate = it }
        withContext(Dispatchers.Main) { f.model.importLocalFiles(listOf(f.uri)) }
        assertTrue(gate.entered.await(5, TimeUnit.SECONDS))
        val pulse = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post { pulse.countDown() }
        assertTrue("The UI must remain responsive while a provider is slow", pulse.await(500, TimeUnit.MILLISECONDS))
        f.await { f.model.uiState.value.isImportingLocalFile }
        withContext(Dispatchers.Main) { f.model.importLocalFiles(listOf(f.uri)) }
        assertEquals(1, f.resolverCalls.get())
        gate.release.countDown()
        f.await { !f.model.uiState.value.isImportingLocalFile && f.model.uiState.value.folderContent.files.size == 1 }
        assertEquals("已导入到 知识库根目录", f.model.uiState.value.snackbarMessage)
        assertEquals(1, f.database.knowledgeBaseDao().getAllFiles().size)
        assertArrayEquals(f.payload, f.source.readBytes())
    }

    @Test fun failedMetadataReadEndsTheBusyStateAndCanBeRetried() = withFixture { f ->
        f.start(this)
        f.failResolver = true
        withContext(Dispatchers.Main) { f.model.importLocalFiles(listOf(f.uri)) }
        f.await { !f.model.uiState.value.isImportingLocalFile && f.model.uiState.value.snackbarMessage == "无法读取资料信息，请重试" }
        assertTrue(f.database.knowledgeBaseDao().getAllFiles().isEmpty())
        assertArrayEquals(f.payload, f.source.readBytes())
        f.failResolver = false
        withContext(Dispatchers.Main) {
            f.model.consumeSnackbarMessage()
            f.model.importLocalFiles(listOf(f.uri))
        }
        f.await { !f.model.uiState.value.isImportingLocalFile && f.model.uiState.value.folderContent.files.size == 1 }
        assertEquals("selected.txt", f.model.uiState.value.folderContent.files.single().displayName)
        assertArrayEquals(f.payload, File(f.database.knowledgeBaseDao().getAllFiles().single().localPath).readBytes())
    }

    @Test fun batchImportKeepsItsOriginalFolderAndReportsPartialFailures() = withFixture { f ->
        f.repository.createFolder(null, "矩阵分析").getOrThrow()
        f.repository.createFolder(null, "算法设计").getOrThrow()
        val folders = f.database.knowledgeBaseDao().getAllFolders().associateBy { it.name }
        val original = requireNotNull(folders["矩阵分析"])
        val other = requireNotNull(folders["算法设计"])
        f.start(this)
        withContext(Dispatchers.Main) { f.model.enterFolder(original.id) }
        f.await { f.model.uiState.value.currentFolderId == original.id }
        val gate = Gate().also { f.gate = it }
        withContext(Dispatchers.Main) { f.model.importLocalFiles(listOf(f.uri, Uri.fromFile(File(f.directory, "missing.txt")))) }
        assertTrue(gate.entered.await(5, TimeUnit.SECONDS))
        val pulse = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post { pulse.countDown() }
        assertTrue(pulse.await(500, TimeUnit.MILLISECONDS))
        withContext(Dispatchers.Main) { f.model.enterFolder(other.id) }
        gate.release.countDown()
        f.await { !f.model.uiState.value.isImportingLocalFile && f.model.uiState.value.snackbarMessage == "成功导入 1 个文件，1 个失败" }
        val saved = f.database.knowledgeBaseDao().getAllFiles().single()
        assertEquals(original.id, saved.folderId)
        assertEquals(other.id, f.model.uiState.value.currentFolderId)
        assertTrue(f.model.uiState.value.folderContent.files.isEmpty())
        assertArrayEquals(f.payload, File(saved.localPath).readBytes())
    }

    @Test fun leavingDuringProviderAccessDoesNotCreateAnIndexedOrPartialFile() = withFixture { f ->
        f.start(this)
        val gate = Gate().also { f.gate = it }
        withContext(Dispatchers.Main) { f.model.importLocalFiles(listOf(f.uri)) }
        assertTrue(gate.entered.await(5, TimeUnit.SECONDS))
        val pulse = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post { pulse.countDown() }
        assertTrue(pulse.await(500, TimeUnit.MILLISECONDS))
        withContext(Dispatchers.Main) { f.store.clear() }
        gate.release.countDown()
        f.model.viewModelScope.coroutineContext[Job]?.join()
        assertTrue(f.database.knowledgeBaseDao().getAllFiles().isEmpty())
        assertTrue(File(f.directory, "knowledge_base").walkTopDown().none { it.isFile })
        assertArrayEquals(f.payload, f.source.readBytes())
    }

    @Test fun lightSlowImportKeepsItsCompactStatusAndControlsReadable() = checkStatus(false)
    @Test fun darkSlowImportKeepsItsCompactStatusAndControlsReadable() = checkStatus(true)

    private fun checkStatus(dark: Boolean) = withFixture { f ->
        f.start(this)
        compose.setContent {
            CompositionLocalProvider(LocalAiEnabled provides false,
                LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                CorrectionNotebookTheme(darkTheme = dark, dynamicColor = false) {
                    Box(Modifier.safeDrawingPadding()) {
                        Box(Modifier.size(320.dp, 560.dp)) { KnowledgeBaseScreen(onOpenFile = {}, viewModel = f.model) }
                    }
                }
            }
        }
        val gate = Gate().also { f.gate = it }
        withContext(Dispatchers.Main) { f.model.importLocalFiles(listOf(f.uri)) }
        assertTrue(gate.entered.await(5, TimeUnit.SECONDS))
        f.await { f.model.uiState.value.isImportingLocalFile }
        compose.onNodeWithText("正在导入资料").assertIsDisplayed()
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText("正在导入资料").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        layouts.forEach { layout ->
            assertEquals(1, layout.lineCount)
            assertFalse(layout.didOverflowHeight)
            assertFalse(layout.isLineEllipsized(0))
            assertTrue(layout.getLineRight(0) <= layout.size.width + 1)
        }
        compose.onNodeWithText("导入").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithContentDescription("搜索当前目录").assertIsDisplayed()
        capture(dark)
        gate.release.countDown()
        f.await { !f.model.uiState.value.isImportingLocalFile && f.model.uiState.value.folderContent.files.size == 1 }
        compose.onAllNodesWithText("selected.txt")[0].performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("导入").assertIsEnabled()
    }

    private fun capture(dark: Boolean) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(350)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "qa-local-import-${if (dark) "dark" else "light"}.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    private fun withFixture(test: suspend CoroutineScope.(Fixture) -> Unit) = runBlocking {
        withTimeout(15_000) {
            val fixture = Fixture()
            try { test(fixture) }
            finally {
                withContext(NonCancellable) {
                    fixture.gate?.release?.countDown()
                    fixture.activeGate?.release?.countDown()
                    withContext(Dispatchers.Main) { fixture.store.clear() }
                    if (fixture.hasModel) fixture.model.viewModelScope.coroutineContext[Job]?.join()
                    fixture.collector?.cancelAndJoin()
                    fixture.database.close()
                    fixture.client.dispatcher.executorService.shutdownNow()
                    fixture.client.connectionPool.evictAll()
                    fixture.directory.deleteRecursively()
                }
            }
        }
    }

    private class Gate {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        fun await() {
            entered.countDown()
            check(release.await(5, TimeUnit.SECONDS)) { "Provider gate timed out" }
        }
    }

    private class Fixture {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File.createTempFile("local-import-qa-", "", target.cacheDir).apply { delete(); mkdir() }
        val payload = "矩阵分析课程资料与课堂练习".toByteArray()
        val source = File(directory, "selected.txt").apply { writeBytes(payload) }
        val uri = Uri.fromFile(source)
        val resolverCalls = AtomicInteger()
        val resolverOnMain = AtomicBoolean()
        @Volatile var failResolver = false
        @Volatile var gate: Gate? = null
        @Volatile var activeGate: Gate? = null
        val context = object : ContextWrapper(target) {
            override fun getFilesDir() = directory
            override fun getContentResolver(): ContentResolver {
                resolverCalls.incrementAndGet()
                if (Looper.myLooper() == Looper.getMainLooper()) resolverOnMain.set(true)
                gate?.also { gate = null; activeGate = it }?.await()
                if (failResolver) throw IOException("无法读取资料信息，请重试")
                return super.getContentResolver()
            }
        }
        val database = Room.inMemoryDatabaseBuilder(context, KnowledgeBaseDatabase::class.java).build()
        val repository = KnowledgeBaseRepository(database.knowledgeBaseDao(), KnowledgeBaseFileStorage(context), context)
        val client = OkHttpClient.Builder().addInterceptor { throw AssertionError("Local imports must not request BITShare") }.build()
        val store = ViewModelStore()
        var collector: Job? = null
        lateinit var model: KnowledgeBaseViewModel
        val hasModel get() = ::model.isInitialized
        suspend fun start(scope: CoroutineScope) {
            val service = Retrofit.Builder().baseUrl("http://127.0.0.1/").client(client)
                .addConverterFactory(GsonConverterFactory.create()).build().create(BitShareApiService::class.java)
            val remote = BitShareRepository(service, client)
            withContext(Dispatchers.Main) {
                model = KnowledgeBaseViewModel(repository, remote, StudySetRepository(database.knowledgeBaseDao()))
                store.put("local-import", model)
            }
            collector = scope.launch(Dispatchers.Default) { model.uiState.collect {} }
            await("model startup") { model.uiState.value.folderChoices.isNotEmpty() }
        }
        suspend fun await(label: String = "import completion", predicate: () -> Boolean) {
            val completed = withTimeoutOrNull(5_000) { while (!predicate()) delay(10); true }
            check(completed == true) { "Timed out at $label; state=${model.uiState.value}; collectorActive=${collector?.isActive}" }
        }
    }
}
