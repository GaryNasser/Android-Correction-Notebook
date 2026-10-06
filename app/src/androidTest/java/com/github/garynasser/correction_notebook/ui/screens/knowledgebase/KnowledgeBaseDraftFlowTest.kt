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
import com.github.garynasser.correction_notebook.data.local.knowledgebase.StudySetEntity
import com.github.garynasser.correction_notebook.data.model.studyset.KnowledgeCardType
import com.github.garynasser.correction_notebook.data.remote.api.BitShareApiService
import com.github.garynasser.correction_notebook.data.repository.BitShareRepository
import com.github.garynasser.correction_notebook.data.repository.KnowledgeBaseRepository
import com.github.garynasser.correction_notebook.data.repository.StudySetRepository
import com.github.garynasser.correction_notebook.ui.components.LocalAiEnabled
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import java.io.File
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class KnowledgeBaseDraftFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun folderRenameRestoresItsDraftAndSavesOnlyTheSelectedId() = checkDraft("folder")
    @Test fun fileRenameRestoresItsDraftAndPreservesFileContents() = checkDraft("file")
    @Test fun studySetRenameRestoresItsDraftAndSavesOnlyTheSelectedId() = checkDraft("studySet")
    @Test fun addingACardRestoresItsDraftAndKeepsTheSelectedStudySet() = checkDraft("manual")
    @Test fun editingACardRestoresItsDraftAndUpdatesTheOriginalCard() = checkDraft("edit")
    @Test fun aNewStudySetRestoresItsDraftWithoutAttachingToAnExistingSet() = checkDraft("new")

    private fun checkDraft(kind: String) = runBlocking {
        withTimeout(20_000) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val target = instrumentation.targetContext
            val directory = File.createTempFile("rename-qa-", "", target.cacheDir).apply { delete(); mkdir() }
            val context = object : ContextWrapper(target) { override fun getFilesDir() = directory }
            val database = Room.inMemoryDatabaseBuilder(context, KnowledgeBaseDatabase::class.java).build()
            val dao = database.knowledgeBaseDao()
            val repository = KnowledgeBaseRepository(dao, KnowledgeBaseFileStorage(context), context)
            val client = OkHttpClient.Builder().addInterceptor { throw AssertionError("Renaming must stay offline") }.build()
            val service = Retrofit.Builder().baseUrl("http://127.0.0.1/").client(client)
                .addConverterFactory(GsonConverterFactory.create()).build().create(BitShareApiService::class.java)
            val store = ViewModelStore()
            val model = withContext(Dispatchers.Main) {
                KnowledgeBaseViewModel(repository, BitShareRepository(service, client) { throw AssertionError("Renaming must not probe the network") }, StudySetRepository(dao))
                    .also { store.put("rename", it) }
            }
            val collector = launch(Dispatchers.Default) { model.uiState.collect {} }
            try {
                val original = "矩阵分析"
                val payload = "课程资料原始内容".toByteArray()
                when (kind) {
                    "folder" -> repeat(2) { repository.createFolder(null, original).getOrThrow() }
                    "file" -> {
                        val source = File(directory, "$original.txt").apply { writeBytes(payload) }
                        repository.importLocalFile(null, Uri.fromFile(source)).getOrThrow()
                    }
                    else -> repeat(2) { index ->
                        dao.insertStudySet(StudySetEntity("set-$index", null, null, original, "manual", null, false, 1, 1))
                    }
                }
                if (kind == "edit") StudySetRepository(dao).saveManualCard(
                    title = original, type = KnowledgeCardType.QA_FLASHCARD, front = "原问题", back = "原答案",
                    hint = "", courseName = null, studySetId = "set-1"
                ).getOrThrow()
                await {
                    when (kind) {
                        "folder" -> model.uiState.value.folderContent.folders.size == 2
                        "file" -> model.uiState.value.folderContent.files.size == 1
                        "edit" -> model.uiState.value.studySets.size == 2 && model.uiState.value.knowledgeCards.size == 1
                        else -> model.uiState.value.studySets.size == 2
                    }
                }
                val selectedId = when (kind) {
                    "folder" -> model.uiState.value.folderContent.folders[1].id
                    "file" -> model.uiState.value.folderContent.files.single().id
                    "edit" -> "set-1"
                    else -> model.uiState.value.studySets[1].id
                }
                val originalCardId = model.uiState.value.knowledgeCards.singleOrNull()?.flashcardId
                val restoration = StateRestorationTester(compose)
                restoration.setContent {
                    CompositionLocalProvider(LocalAiEnabled provides false,
                        LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                        CorrectionNotebookTheme(darkTheme = kind != "folder" && kind != "file", dynamicColor = false) {
                            Box(Modifier.safeDrawingPadding()) {
                                Box(Modifier.size(320.dp, 560.dp)) { KnowledgeBaseScreen({}, viewModel = model) }
                            }
                        }
                    }
                }
                val draft = if (kind == "file") "矩阵分析课程讲义.txt" else "矩阵分析课程复习"
                if (kind != "folder" && kind != "file") compose.onNodeWithText("知识空间").performClick()
                if (kind == "manual" || kind == "edit" || kind == "new") {
                    if (kind == "new") {
                        compose.onNodeWithContentDescription("新建学习集卡片").assertIsDisplayed().performClick()
                    } else {
                        val index = model.uiState.value.studySets.indexOfFirst { it.id == selectedId }
                        compose.onAllNodesWithText(original)[index].performClick()
                    }
                    if (kind == "manual") {
                        compose.onNodeWithContentDescription("添加知识卡片").performClick()
                    } else if (kind == "edit") {
                        compose.onNodeWithContentDescription("知识卡片操作").performClick()
                        compose.onNodeWithText("编辑").performClick()
                    }
                    compose.onNode(hasSetTextAction() and hasText("问题")).performScrollTo().performTextReplacement(draft)
                    compose.onNode(hasSetTextAction() and hasText("答案")).performScrollTo().performTextReplacement("特征值与特征向量")
                } else {
                    val action = if (kind == "studySet") "学习集操作" else "更多操作"
                    val actionIndex = when (kind) { "file" -> 1; "folder" -> 2; else -> 1 }
                    compose.onAllNodesWithContentDescription(action)[actionIndex].performClick()
                    compose.onNodeWithText("重命名").performClick()
                    compose.onNode(hasSetTextAction()).performTextReplacement(draft)
                }
                restoration.emulateSavedInstanceStateRestore()
                if (kind == "manual" || kind == "edit" || kind == "new") {
                    compose.onNode(hasSetTextAction() and hasText("问题")).performScrollTo().assertTextContains(draft)
                    compose.onNode(hasSetTextAction() and hasText("答案")).performScrollTo().assertTextContains("特征值与特征向量")
                } else {
                    compose.onNode(hasSetTextAction()).assertIsDisplayed().assertTextContains(draft)
                }
                compose.onNodeWithText("取消").assertIsDisplayed()
                compose.onNodeWithText("保存").assertIsDisplayed().assertIsEnabled()
                capture(kind)
                compose.onNodeWithText("保存").performClick()
                await {
                    when (kind) {
                        "folder" -> model.uiState.value.folderContent.folders.any { it.id == selectedId && it.name == draft }
                        "file" -> model.uiState.value.folderContent.files.any { it.id == selectedId && it.displayName == draft }
                        "manual", "edit" -> model.uiState.value.knowledgeCards.any { it.front == draft }
                        "new" -> model.uiState.value.studySets.size == 3 && model.uiState.value.knowledgeCards.any { it.front == draft }
                        else -> model.uiState.value.studySets.any { it.id == selectedId && it.title == draft }
                    }
                }
                compose.onNode(hasSetTextAction()).assertDoesNotExist()
                when (kind) {
                    "folder" -> {
                        val folders = dao.getAllFolders()
                        assertEquals(draft, folders.single { it.id == selectedId }.name)
                        assertEquals(original, folders.single { it.id != selectedId }.name)
                    }
                    "file" -> {
                        val file = requireNotNull(dao.getFileById(selectedId))
                        assertEquals(draft, file.displayName)
                        assertArrayEquals(payload, File(file.localPath).readBytes())
                    }
                    "manual", "edit" -> {
                        assertEquals(2, model.uiState.value.studySets.size)
                        val card = model.uiState.value.knowledgeCards.single()
                        assertEquals(selectedId, card.studySetId)
                        assertEquals("特征值与特征向量", card.back)
                        if (kind == "edit") assertEquals(originalCardId, card.flashcardId)
                    }
                    "new" -> {
                        assertEquals(3, model.uiState.value.studySets.size)
                        val card = model.uiState.value.knowledgeCards.single()
                        assertFalse(card.studySetId in listOf("set-0", "set-1"))
                        assertEquals("特征值与特征向量", card.back)
                    }
                    else -> assertEquals(original, model.uiState.value.studySets.single { it.id != selectedId }.title)
                }
                restoration.emulateSavedInstanceStateRestore()
                compose.onNode(hasSetTextAction()).assertDoesNotExist()
            } finally {
                withContext(NonCancellable) {
                    withContext(Dispatchers.Main) { store.clear() }
                    model.viewModelScope.coroutineContext[Job]?.join()
                    collector.cancelAndJoin()
                    database.close()
                    client.dispatcher.executorService.shutdownNow()
                    client.connectionPool.evictAll()
                    directory.deleteRecursively()
                }
            }
        }
    }

    private suspend fun await(predicate: () -> Boolean) = withTimeout(5_000) {
        while (!predicate()) delay(10)
    }

    private fun capture(kind: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(350)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "qa-rename-$kind.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }
}
