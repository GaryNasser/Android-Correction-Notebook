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
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
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

    @Test fun searchCanContinueBeyondTheFirstTwentyResults() = withFixture { f ->
        f.searchItems = pagedItems()
        compose.setContent { TestScreen(f.model, dark = false) }
        search(f, expectedCount = 20)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("加载更多"))
        compose.onNodeWithText("加载更多").assertIsDisplayed().performClick()
        f.await { f.model.uiState.value.remoteResults.size == 25 }
        assertEquals(listOf(1, 2), f.searchCalls.map { it.second })
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("课程资料 25"))
        compose.onNodeWithText("课程资料 25").assertIsDisplayed()
        capture("remote-pagination-light")
    }

    @Test fun failedNextPageKeepsResultsAndRestoredRetryLoadsTheSamePage() = withFixture { f ->
        f.searchItems = pagedItems()
        val restoration = StateRestorationTester(compose)
        restoration.setContent { TestScreen(f.model, dark = true) }
        search(f, expectedCount = 20)
        f.failSearchPage = 2
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("加载更多"))
        compose.onNodeWithText("加载更多").performClick()
        f.await { f.model.uiState.value.remoteLoadMoreError != null && !f.model.uiState.value.isLoadingMoreRemote }
        assertEquals(20, f.model.uiState.value.remoteResults.size)
        assertTrue(f.model.uiState.value.canLoadMoreRemote)
        assertNull(f.model.uiState.value.remoteErrorMessage)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("重试加载"))
        compose.onNodeWithText("重试加载").assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("重试加载").assertIsDisplayed()
        compose.onNodeWithText("已加载 20 / 25 项").assertIsDisplayed()
        compose.onNodeWithText("重试加载").assertIsDisplayed()
        capture("remote-pagination-error-dark")
        f.failSearchPage = null
        compose.onNodeWithText("重试加载").performClick()
        f.await { f.model.uiState.value.remoteResults.size == 25 && !f.model.uiState.value.isLoadingMoreRemote }
        assertEquals(listOf(1, 2, 2), f.searchCalls.map { it.second })
        assertFalse(f.model.uiState.value.canLoadMoreRemote)
        assertNull(f.model.uiState.value.remoteLoadMoreError)
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("课程资料 25"))
        compose.onNodeWithText("课程资料 25").assertIsDisplayed()
        capture("remote-pagination-recovered-dark")
    }

    @Test fun repeatedContinuationIsIgnoredAndEditingRejectsTheOldPage() = withFixture { f ->
        f.searchItems = pagedItems()
        compose.setContent { TestScreen(f.model, dark = false) }
        search(f, expectedCount = 20)
        val gate = Gate().also { f.searchGate = it }
        withContext(Dispatchers.Main) { repeat(3) { f.model.loadMoreRemoteResources() } }
        assertTrue(gate.entered.await(5, TimeUnit.SECONDS))
        compose.onNode(hasSetTextAction()).performTextClearance()
        compose.onNode(hasSetTextAction()).performTextInput("新课程")
        f.searchItems = pagedItems(3).map { it.copy(name = "新课程 ${it.id}") }
        compose.onNode(hasSetTextAction()).performImeAction()
        f.await { f.model.uiState.value.remoteResults.size == 3 && !f.model.uiState.value.isRemoteSearching }
        gate.release.countDown()
        assertTrue(gate.returned.await(5, TimeUnit.SECONDS))
        compose.waitForIdle()
        assertEquals(listOf("课程" to 1, "课程" to 2, "新课程" to 1), f.searchCalls.toList())
        assertTrue(f.model.uiState.value.remoteResults.all { it.title.startsWith("新课程") })
        assertFalse(f.model.uiState.value.canLoadMoreRemote)
        assertFalse(f.model.uiState.value.isLoadingMoreRemote)
        assertNull(f.model.uiState.value.remoteLoadMoreError)
    }

    @Test fun refreshingLoadedPagesReplacesResultsAndResetsTheContinuation() = withFixture { f ->
        f.searchItems = pagedItems()
        compose.setContent { TestScreen(f.model, dark = false) }
        search(f, expectedCount = 20)
        withContext(Dispatchers.Main) { f.model.loadMoreRemoteResources() }
        f.await { f.model.uiState.value.remoteResults.size == 25 }
        f.searchItems = pagedItems(26).map { it.copy(name = "更新 ${it.name}") }
        compose.onNodeWithContentDescription("刷新搜索").performClick()
        f.await { !f.model.uiState.value.isRemoteSearching && f.model.uiState.value.remoteResults.size == 20 }
        assertTrue(f.model.uiState.value.remoteResults.all { it.title.startsWith("更新") })
        withContext(Dispatchers.Main) { f.model.loadMoreRemoteResources() }
        f.await { f.model.uiState.value.remoteResults.size == 26 }
        assertEquals(listOf(1, 2, 1, 2), f.searchCalls.map { it.second })
        assertFalse(f.model.uiState.value.canLoadMoreRemote)
    }

    @Test fun sortingLoadedResultsKeepsEveryPageWithoutAnotherRequest() = withFixture { f ->
        f.searchItems = pagedItems().map { item ->
            item.copy(uploadedAt = when (item.id) {
                "file-22" -> "invalid timestamp"
                "file-23" -> "2026-10-25T10:00:00.200+02:00"
                "file-24" -> "2026-10-25T08:00:00.100Z"
                "file-25" -> "2026-10-25T08:00:00Z"
                else -> item.uploadedAt
            })
        }
        compose.setContent { TestScreen(f.model, dark = false) }
        search(f, expectedCount = 20)
        withContext(Dispatchers.Main) { f.model.loadMoreRemoteResources() }
        f.await { f.model.uiState.value.remoteResults.size == 25 }
        compose.onNodeWithText("下载量").performClick()
        f.await { f.model.uiState.value.remoteSort == BitShareSortOption.DOWNLOADS }
        assertEquals((25 downTo 1).map { "file-$it" }, f.model.uiState.value.remoteResults.map { it.id })
        compose.onNodeWithText("课程资料 25").assertIsDisplayed()
        compose.onNodeWithText("最新").performClick()
        f.await { f.model.uiState.value.remoteSort == BitShareSortOption.LATEST }
        assertEquals(listOf("file-23", "file-24", "file-25"), f.model.uiState.value.remoteResults.take(3).map { it.id })
        assertEquals("file-22", f.model.uiState.value.remoteResults.last().id)
        compose.onNodeWithText("综合").performClick()
        f.await { f.model.uiState.value.remoteSort == BitShareSortOption.RELEVANCE }
        assertEquals((1..25).map { "file-$it" }, f.model.uiState.value.remoteResults.map { it.id })
        assertEquals(listOf(1, 2), f.searchCalls.map { it.second })
        assertFalse(f.model.uiState.value.canLoadMoreRemote)
    }

    @Test fun overlappingPagesDeduplicateFilesButKeepSameIdFoldersUsable() = withFixture { f ->
        f.searchItems = pagedItems()
        val folder = f.searchItems.first().copy(entityType = "folder", name = "课程目录")
        f.searchResponses = mapOf(2 to BitShareSearchResponse(
            listOf(f.searchItems.first(), folder) + f.searchItems.drop(20), 2, 20, 27
        ))
        compose.setContent { TestScreen(f.model, dark = false) }
        search(f, expectedCount = 20)
        withContext(Dispatchers.Main) { f.model.loadMoreRemoteResources() }
        f.await { f.model.uiState.value.remoteResults.size == 26 }
        assertEquals(1, f.model.uiState.value.remoteResults.count { it.id == "file-1" && it.entityType == "file" })
        assertEquals(1, f.model.uiState.value.remoteResults.count { it.id == "file-1" && it.entityType == "folder" })
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText("课程目录"))
        compose.onNodeWithText("课程目录").assertIsDisplayed().performClick()
        f.await { f.model.uiState.value.selectedRemoteFolderDetail?.id == "file-1" }
    }

    @Test fun invalidContinuationMetadataKeepsThePageAvailableForRetry() = withFixture { f ->
        f.searchItems = pagedItems()
        compose.setContent { TestScreen(f.model, dark = false) }
        search(f, expectedCount = 20)
        f.searchResponses = mapOf(2 to BitShareSearchResponse(f.searchItems.drop(20), 1, 20, 25))
        withContext(Dispatchers.Main) { f.model.loadMoreRemoteResources() }
        f.await { f.model.uiState.value.remoteLoadMoreError != null && !f.model.uiState.value.isLoadingMoreRemote }
        assertEquals(20, f.model.uiState.value.remoteResults.size)
        assertTrue(f.model.uiState.value.canLoadMoreRemote)
        f.searchResponses = emptyMap()
        withContext(Dispatchers.Main) { f.model.loadMoreRemoteResources() }
        f.await { f.model.uiState.value.remoteResults.size == 25 }
        assertEquals(listOf(1, 2, 2), f.searchCalls.map { it.second })
    }

    @Test fun emptyContinuationEndsLoadingWithoutAnExtraRequest() = withFixture { f ->
        f.searchItems = pagedItems()
        f.searchResponses = mapOf(2 to BitShareSearchResponse(emptyList(), 2, 20, 25))
        compose.setContent { TestScreen(f.model, dark = false) }
        search(f, expectedCount = 20)
        withContext(Dispatchers.Main) { f.model.loadMoreRemoteResources() }
        f.await { !f.model.uiState.value.isLoadingMoreRemote && !f.model.uiState.value.canLoadMoreRemote }
        withContext(Dispatchers.Main) { f.model.loadMoreRemoteResources() }
        assertEquals(20, f.model.uiState.value.remoteResults.size)
        assertEquals(listOf(1, 2), f.searchCalls.map { it.second })
    }

    private fun pagedItems(count: Int = 25) = (1..count).map { index ->
        BitShareSearchItemDto("file", "file-$index", "课程资料 $index", "资料$index.txt", "txt", 9, index,
            "2026-10-${index.toString().padStart(2, '0')}T08:00:00Z")
    }

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

    @Test fun longFolderDetailsRemainReadableOnAShortLightScreen() = checkLongFolder(dark = false)
    @Test fun longFolderDetailsRemainReadableOnAShortDarkScreen() = checkLongFolder(dark = true)

    private fun checkLongFolder(dark: Boolean) = withFixture { f ->
        val name = "矩阵分析与计算理论课程资料目录".repeat(4)
        val description = "本目录收录矩阵分析课程的讲义、课堂笔记与复习资料。".repeat(6)
        val breadcrumbs = listOf("北京理工大学", "数学与统计学院", "矩阵分析课程资料", name)
            .mapIndexed { i, title -> BitShareBreadcrumbDto("parent-$i", title) }
        val path = breadcrumbs.joinToString(" > ") { it.name }
        f.folderDetail = BitShareFolderDetailDto("folder", name, description, "parent-2", breadcrumbs, 128, 325, 4096, "2026-10-07T08:00:00Z")
        f.searchItems = listOf(BitShareSearchItemDto("folder", "folder", "课程目录", null, null, null, null, null))
        val restoration = StateRestorationTester(compose)
        restoration.setContent { TestScreen(f.model, dark) }
        search(f, expectedCount = 1)
        compose.onNodeWithText("课程目录").performClick()
        f.await { f.model.uiState.value.selectedRemoteFolderDetail?.id == "folder" }
        restoration.emulateSavedInstanceStateRestore()
        listOf(name, description, "128", "325", "4.0 KB", path).forEach { text ->
            val node = compose.onNodeWithText(text, useUnmergedTree = true)
            node.performScrollTo().assertIsDisplayed()
            val layouts = mutableListOf<TextLayoutResult>()
            assertTrue(node.fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action!!.invoke(layouts))
            val layout = layouts.single()
            assertFalse(layout.didOverflowHeight)
            assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1))
            repeat(layout.lineCount) { line ->
                assertFalse(layout.isLineEllipsized(line))
                // Paragraph widths are fractional; measured sizes use integer pixels.
                assertTrue("Text must fit its measured width: $text", layout.getLineRight(line) <= layout.size.width + 1f)
            }
        }
        capture("remote-folder-path-${if (dark) "dark" else "light"}")
        compose.onNodeWithText(name, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        capture("remote-folder-name-${if (dark) "dark" else "light"}")
        compose.onNodeWithText("关闭").assertIsDisplayed().performClick()
        compose.onNodeWithText("课程目录").assertIsDisplayed()
        assertNull(f.model.uiState.value.selectedRemoteFolderDetail)
        assertTrue(f.database.knowledgeBaseDao().getAllFiles().isEmpty())
    }

    @Test fun failedFolderDetailsKeepSearchResultsAndTheSameFolderCanBeRetried() = withFixture { f ->
        f.searchItems = listOf(BitShareSearchItemDto("folder", "folder", "课程目录", null, null, null, null, null))
        f.failFolderDetail = true
        compose.setContent { TestScreen(f.model, dark = true) }
        search(f, expectedCount = 1)
        compose.onNodeWithText("课程目录").performClick()
        f.await { f.model.uiState.value.remoteErrorMessage != null && !f.model.uiState.value.isRemoteFolderLoading }
        assertNull(f.model.uiState.value.selectedRemoteFolderDetail)
        assertEquals("folder", f.model.uiState.value.remoteResults.single().id)
        f.failFolderDetail = false
        compose.onNodeWithText("课程目录").performClick()
        f.await { f.model.uiState.value.selectedRemoteFolderDetail?.id == "folder" }
        assertNull(f.model.uiState.value.remoteErrorMessage)
        assertEquals(listOf(1), f.searchCalls.map { it.second })
        compose.onNodeWithText("关闭").assertIsDisplayed().performClick()
        compose.onNodeWithText("课程目录").assertIsDisplayed()
    }

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

    private suspend fun search(f: Fixture, expectedCount: Int = 2) {
        compose.onNodeWithText("BITShare").performClick()
        compose.onNode(hasSetTextAction()).performTextInput("课程")
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.onNode(hasSetTextAction()).assertIsNotFocused()
        f.await { f.model.uiState.value.remoteResults.size == expectedCount && !f.model.uiState.value.isRemoteSearching }
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
                    f.searchGate?.release?.countDown()
                    f.activeSearchGate?.release?.countDown()
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
        val returned = CountDownLatch(1)
        fun await() {
            entered.countDown()
            try { check(release.await(5, TimeUnit.SECONDS)) } finally { returned.countDown() }
        }
    }

    private class Fixture {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File.createTempFile("remote-flow-qa-", "", target.cacheDir).apply { delete(); mkdir() }
        val context = object : ContextWrapper(target) { override fun getFilesDir() = directory }
        val database = Room.inMemoryDatabaseBuilder(context, KnowledgeBaseDatabase::class.java).build()
        val local = KnowledgeBaseRepository(database.knowledgeBaseDao(), KnowledgeBaseFileStorage(context), context)
        val downloads = CopyOnWriteArrayList<String>()
        val searchCalls = CopyOnWriteArrayList<Pair<String, Int>>()
        var searchItems = listOf(
            BitShareSearchItemDto("file", "a", "矩阵分析讲义", "矩阵分析.txt", "txt", 9, 0, null),
            BitShareSearchItemDto("file", "b", "算法设计讲义", "算法设计.txt", "txt", 9, 0, null)
        )
        @Volatile var failSearchPage: Int? = null
        @Volatile var failFolderDetail = false
        var folderDetail: BitShareFolderDetailDto? = null
        var searchResponses = emptyMap<Int, BitShareSearchResponse>()
        @Volatile var searchGate: Gate? = null
        @Volatile var activeSearchGate: Gate? = null
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
                path == "/api/public/search" -> {
                    val page = chain.request().url.queryParameter("page")!!.toInt()
                    val size = chain.request().url.queryParameter("page_size")!!.toInt()
                    searchCalls += chain.request().url.queryParameter("q")!! to page
                    val response = searchResponses[page] ?: BitShareSearchResponse(searchItems.drop((page - 1) * size).take(size), page, size, searchItems.size)
                    searchGate?.also { searchGate = null; activeSearchGate = it }?.await()
                    gson.toJson(response)
                }
                path.contains("/folders/") -> gson.toJson(folderDetail ?: BitShareFolderDetailDto(id, "课程目录", "课程资料", null, emptyList(), 2, 0, 18, null))
                else -> gson.toJson(BitShareFileDetailDto(id, if (id == "a") "矩阵分析讲义" else "算法设计讲义", "txt", null, null, null,
                    if (id == "a") "矩阵分析.txt" else "算法设计.txt", "text/plain", 9, null, 0))
            }
            val failed = (path == "/api/public/search" && chain.request().url.queryParameter("page")?.toInt() == failSearchPage) ||
                (path.contains("/folders/") && failFolderDetail)
            Response.Builder().request(chain.request()).protocol(Protocol.HTTP_1_1).code(if (failed) 500 else 200).message(if (failed) "Search unavailable" else "OK")
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
