package com.github.garynasser.correction_notebook.ui.screens.knowledgebase

import android.annotation.SuppressLint
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Parcel
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseDatabase
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseFileStorage
import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareSearchResponse
import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareSortOption
import com.github.garynasser.correction_notebook.data.remote.api.BitShareApiService
import com.github.garynasser.correction_notebook.data.repository.BitShareRepository
import com.github.garynasser.correction_notebook.data.repository.KnowledgeBaseRepository
import com.github.garynasser.correction_notebook.data.repository.StudySetRepository
import com.github.garynasser.correction_notebook.ui.components.LocalAiEnabled
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class KnowledgeBaseNavigationRestoreTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightFolderRestorationKeepsThePathAndCreatesInsideThatFolder() = checkFolder(false)
    @Test fun darkFolderRestorationKeepsThePathAndCreatesInsideThatFolder() = checkFolder(true)

    private fun checkFolder(dark: Boolean) = withFixture { f ->
        f.local.createFolder(null, "课程资料").getOrThrow()
        val parent = f.dao.getAllFolders().single().id
        f.local.createFolder(parent, "矩阵分析").getOrThrow()
        val child = f.dao.getAllFolders().single { it.parentId == parent }.id
        withContext(Dispatchers.Main) { f.model.enterFolder(child) }
        await { f.model.uiState.value.folderContent.breadcrumbs.lastOrNull()?.id == child }
        val currentModel = mutableStateOf(f.model)
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalAiEnabled provides false,
                LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                CorrectionNotebookTheme(darkTheme = dark, dynamicColor = false) {
                    Box(Modifier.safeDrawingPadding()) {
                        Box(Modifier.size(320.dp, 560.dp)) {
                            KnowledgeBaseScreen({}, viewModel = currentModel.value)
                        }
                    }
                }
            }
        }
        compose.onNodeWithText("知识库 / 课程资料 / 矩阵分析").assertIsDisplayed()
        f.recreate()
        withContext(Dispatchers.Main) { currentModel.value = f.model }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("知识库 / 课程资料 / 矩阵分析").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回上一级").assertIsDisplayed()
        capture(if (dark) "dark-folder-restored" else "light-folder-restored")
        compose.onNodeWithContentDescription("新建文件夹").performClick()
        compose.onNode(hasSetTextAction()).performTextReplacement("复习笔记")
        compose.onNodeWithText("创建").assertIsDisplayed().performClick()
        await { f.model.uiState.value.folderContent.folders.singleOrNull()?.name == "复习笔记" }
        assertEquals(child, f.dao.getAllFolders().single { it.name == "复习笔记" }.parentId)
        compose.onNodeWithContentDescription("返回上一级").performClick()
        await { f.model.uiState.value.currentFolderId == parent }
        compose.onNodeWithText("知识库 / 课程资料").assertIsDisplayed()
        compose.onNodeWithContentDescription("返回上一级").performClick()
        await { f.model.uiState.value.currentFolderId == null }
        f.recreate()
        assertNull(f.model.uiState.value.currentFolderId)
        assertEquals(0, f.searchCalls.get())
    }

    @Test fun everyTabAndItsSearchConditionsSurviveARecreatedOwnerWithoutRequests() = withFixture { f ->
        f.local.createFolder(null, "课程资料").getOrThrow()
        val folder = f.dao.getAllFolders().single().id
        for (tab in 0..2) {
            withContext(Dispatchers.Main) {
                f.model.enterFolder(folder)
                f.model.selectTab(tab)
                f.model.updateLocalSearchQuery("期末复习")
                f.model.updateRemoteQuery("矩阵分析")
                f.model.updateRemoteSort(BitShareSortOption.LATEST)
            }
            f.recreate()
            val state = f.model.uiState.value
            assertEquals(tab, state.selectedTabIndex)
            assertEquals(folder, state.currentFolderId)
            assertEquals("期末复习", state.localSearchQuery)
            assertEquals("矩阵分析", state.remoteQuery)
            assertEquals(BitShareSortOption.LATEST, state.remoteSort)
            assertTrue(state.remoteResults.isEmpty())
            assertFalse(state.isRemoteSearching)
            assertFalse(state.isLocalBusy)
            assertNull(state.activeDownloadId)
        }
        assertEquals(0, f.searchCalls.get())
        assertTrue(f.dao.getAllFiles().isEmpty())
        assertEquals(1, f.dao.getAllFolders().size)
    }

    @Test fun aDeletedRestoredFolderReturnsToRootAndDoesNotCreateInTheOldLocation() = withFixture { f ->
        f.local.createFolder(null, "课程资料").getOrThrow()
        val folder = f.dao.getAllFolders().single().id
        withContext(Dispatchers.Main) { f.model.enterFolder(folder) }
        await { f.model.uiState.value.folderContent.breadcrumbs.lastOrNull()?.id == folder }
        val saved = f.savedHandle()
        f.local.deleteFolder(folder).getOrThrow()
        f.recreate(saved)
        await { f.model.uiState.value.currentFolderId == null && f.model.uiState.value.folderContent.breadcrumbs.size == 1 }
        withContext(Dispatchers.Main) { f.model.createFolder("新资料") }
        await { !f.model.uiState.value.isLocalBusy && f.model.uiState.value.folderContent.folders.size == 1 }
        assertNull(f.dao.getAllFolders().single().parentId)
        f.recreate()
        assertNull(f.model.uiState.value.currentFolderId)
    }

    @Test fun removedTabAndSortValuesFallBackToValidDefaults() = withFixture { f ->
        f.recreate(SavedStateHandle(mapOf("knowledgeNavigation" to Bundle().apply {
            putInt("tab", 99)
            putString("folder", " ")
            putString("remoteSort", "removed-sort")
        })))
        assertEquals(0, f.model.uiState.value.selectedTabIndex)
        assertNull(f.model.uiState.value.currentFolderId)
        assertEquals(BitShareSortOption.RELEVANCE, f.model.uiState.value.remoteSort)
        assertEquals(0, f.searchCalls.get())
    }

    @Test fun anInterruptedSearchRestoresItsQueryButOnlyRunsAgainWhenRequested() = withFixture { f ->
        f.searchGate = CompletableDeferred()
        withContext(Dispatchers.Main) {
            f.model.selectTab(2)
            f.model.updateRemoteQuery("矩阵分析")
            f.model.searchRemoteResources()
        }
        await { f.searchCalls.get() == 1 && f.model.uiState.value.isRemoteSearching }
        f.recreate()
        assertEquals(1, f.searchCancellations.get())
        assertEquals(1, f.searchCalls.get())
        assertEquals("矩阵分析", f.model.uiState.value.remoteQuery)
        assertFalse(f.model.uiState.value.isRemoteSearching)
        assertNull(f.model.uiState.value.remoteErrorMessage)
        f.searchGate?.complete(Unit)
        withContext(Dispatchers.Main) { f.model.searchRemoteResources() }
        await { f.searchCalls.get() == 2 && !f.model.uiState.value.isRemoteSearching }
        assertTrue(f.model.uiState.value.remoteResults.isEmpty())
    }

    private fun withFixture(test: suspend (Fixture) -> Unit) = runBlocking {
        val f = Fixture(this)
        try {
            f.start()
            withTimeout(25_000) { test(f) }
        } finally {
            withContext(NonCancellable) {
                f.searchGate?.complete(Unit)
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

    private class Fixture(private val scope: CoroutineScope) {
        val target = InstrumentationRegistry.getInstrumentation().targetContext
        val directory = File.createTempFile("navigation-qa-", "", target.cacheDir).apply { delete(); mkdir() }
        val context = object : ContextWrapper(target) { override fun getFilesDir() = directory }
        val database = Room.inMemoryDatabaseBuilder(context, KnowledgeBaseDatabase::class.java).build()
        val dao = database.knowledgeBaseDao()
        val local = KnowledgeBaseRepository(dao, KnowledgeBaseFileStorage(context), context)
        val searchCalls = AtomicInteger()
        val searchCancellations = AtomicInteger()
        var searchGate: CompletableDeferred<Unit>? = null
        val client = OkHttpClient.Builder().addInterceptor { error("No external request expected") }.build()
        private val api = object : BitShareApiService by Retrofit.Builder().baseUrl("http://127.0.0.1/")
            .client(client).addConverterFactory(GsonConverterFactory.create()).build().create(BitShareApiService::class.java) {
            override suspend fun searchFiles(query: String, page: Int, pageSize: Int): BitShareSearchResponse {
                searchCalls.incrementAndGet()
                try { searchGate?.await() }
                catch (error: CancellationException) { searchCancellations.incrementAndGet(); throw error }
                return BitShareSearchResponse(emptyList(), page, pageSize, 0)
            }
        }
        val store = ViewModelStore()
        lateinit var model: KnowledgeBaseViewModel
        private var handle = SavedStateHandle()
        var collector: Job? = null

        suspend fun start() {
            withContext(Dispatchers.Main) {
                model = KnowledgeBaseViewModel(local, BitShareRepository(api, client),
                    StudySetRepository(dao), handle).also { store.put("navigation", it) }
            }
            collector = scope.launch(Dispatchers.Default) { model.uiState.collect {} }
            await { model.uiState.value.folderChoices.isNotEmpty() }
        }

        @SuppressLint("RestrictedApi")
        suspend fun savedHandle(): SavedStateHandle = withContext(Dispatchers.Main) {
            val parcel = Parcel.obtain()
            try {
                parcel.writeBundle(handle.savedStateProvider().saveState())
                parcel.setDataPosition(0)
                SavedStateHandle.createHandle(parcel.readBundle(javaClass.classLoader), null)
            } finally { parcel.recycle() }
        }

        suspend fun recreate(saved: SavedStateHandle? = null) {
            val restored = saved ?: savedHandle()
            val previous = model
            withContext(Dispatchers.Main) { store.clear() }
            previous.viewModelScope.coroutineContext[Job]?.join()
            collector?.cancelAndJoin()
            handle = restored
            start()
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "qa-navigation-$name.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    companion object {
        private suspend fun await(predicate: () -> Boolean) = withTimeout(5_000) { while (!predicate()) delay(10) }
    }
}
