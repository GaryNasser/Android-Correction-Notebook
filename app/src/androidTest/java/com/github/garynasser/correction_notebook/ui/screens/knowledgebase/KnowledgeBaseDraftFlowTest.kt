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
import androidx.room.RoomDatabase
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
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
    @Test fun failedCardCreationKeepsTheDraftAndRetriesIntoTheChosenSet() = checkDraft("manual", failSave = true, light = true)
    @Test fun failedCardEditKeepsTheDraftAndRetriesTheOriginalCard() = checkDraft("edit", failSave = true)
    @Test fun failedNewStudySetCreationKeepsTheDraftAndRollsBackTheEmptySet() = checkDraft("new", failSave = true)
    @Test fun failedKnowledgePointCreationKeepsItsExplanationAndTags() = checkDraft("manual", failSave = true, knowledgePoint = true)
    @Test fun failedCardCreationCanBeCancelledWithoutChangingTheStudySets() = checkDraft("new", failSave = true, cancelAfterFailure = true)
    @Test fun pendingCardCreationSurvivesRestorationWithoutDuplicatingTheCard() = checkDraft("manual", pendingSave = true)
    @Test fun pendingCardEditSurvivesRestorationWithoutChangingAnotherCard() = checkDraft("edit", pendingSave = true)
    @Test fun pendingNewStudySetCreationSurvivesRestorationWithoutDuplicatingTheSet() = checkDraft("new", pendingSave = true)

    private fun checkDraft(kind: String, failSave: Boolean = false, pendingSave: Boolean = false,
        knowledgePoint: Boolean = false, cancelAfterFailure: Boolean = false, light: Boolean = false) = runBlocking {
        withTimeout(20_000) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val target = instrumentation.targetContext
            val directory = File.createTempFile("rename-qa-", "", target.cacheDir).apply { delete(); mkdir() }
            val context = object : ContextWrapper(target) { override fun getFilesDir() = directory }
            val blockWrites = AtomicBoolean(false)
            val writeEntered = CountDownLatch(1)
            val writeRelease = CountDownLatch(1)
            val database = Room.inMemoryDatabaseBuilder(context, KnowledgeBaseDatabase::class.java)
                .setQueryCallback(object : RoomDatabase.QueryCallback {
                    override fun onQuery(sqlQuery: String, bindArgs: List<Any?>) {
                        if (sqlQuery.contains("flashcard", ignoreCase = true) &&
                            (sqlQuery.startsWith("INSERT", ignoreCase = true) || sqlQuery.startsWith("UPDATE", ignoreCase = true)) &&
                            blockWrites.compareAndSet(true, false)) {
                            writeEntered.countDown()
                            check(writeRelease.await(10, TimeUnit.SECONDS))
                        }
                    }
                }, { it.run() }).build()
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
                if (kind == "edit" && pendingSave) StudySetRepository(dao).saveManualCard(
                    title = "另一张卡片", type = KnowledgeCardType.QA_FLASHCARD, front = "另一个问题", back = "另一张卡片的答案",
                    hint = "", courseName = null, studySetId = "set-0"
                ).getOrThrow()
                await {
                    when (kind) {
                        "folder" -> model.uiState.value.folderContent.folders.size == 2
                        "file" -> model.uiState.value.folderContent.files.size == 1
                        "edit" -> model.uiState.value.studySets.size == 2 && model.uiState.value.knowledgeCards.size == if (pendingSave) 2 else 1
                        else -> model.uiState.value.studySets.size == 2
                    }
                }
                val selectedId = when (kind) {
                    "folder" -> model.uiState.value.folderContent.folders[1].id
                    "file" -> model.uiState.value.folderContent.files.single().id
                    "edit" -> "set-1"
                    else -> model.uiState.value.studySets[1].id
                }
                val originalCardId = model.uiState.value.knowledgeCards.firstOrNull { it.studySetId == selectedId }?.flashcardId
                val restoration = StateRestorationTester(compose)
                restoration.setContent {
                    CompositionLocalProvider(LocalAiEnabled provides false,
                        LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                        CorrectionNotebookTheme(darkTheme = !light && kind != "folder" && kind != "file", dynamicColor = false) {
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
                        compose.onNode(hasScrollToIndexAction()).performScrollToKey(selectedId)
                        val titles = compose.onAllNodesWithText(original)
                        (if (index == 0) titles.onFirst() else titles.onLast()).assertIsDisplayed().performClick()
                    }
                    if (kind == "manual") {
                        compose.onNodeWithContentDescription("添加知识卡片").performClick()
                    } else if (kind == "edit") {
                        compose.onNodeWithContentDescription("知识卡片操作").performScrollTo().assertIsDisplayed().performClick()
                        compose.onNodeWithText("编辑").performClick()
                    }
                    if (knowledgePoint) {
                        compose.onNodeWithText("知识点卡").performScrollTo().performClick()
                        cardField("知识点标题").performTextReplacement(draft)
                        cardField("核心解释").performTextReplacement("特征值与特征向量")
                    } else {
                        cardField("问题").performTextReplacement(draft)
                        cardField("答案").performTextReplacement("特征值与特征向量")
                    }
                    cardField("标签，用逗号分隔").performTextReplacement("期末，重点")
                } else {
                    val action = if (kind == "studySet") "学习集操作" else "更多操作"
                    compose.onNode(hasScrollToIndexAction()).performScrollToKey(selectedId)
                    compose.onAllNodesWithContentDescription(action).onLast().performScrollTo().assertIsDisplayed().performClick()
                    compose.onNodeWithText("重命名").performClick()
                    compose.onNode(hasSetTextAction()).performTextReplacement(draft)
                }
                restoration.emulateSavedInstanceStateRestore()
                if (kind == "manual" || kind == "edit" || kind == "new") {
                    cardField(if (knowledgePoint) "知识点标题" else "问题").assertTextContains(draft)
                    cardField(if (knowledgePoint) "核心解释" else "答案").assertTextContains("特征值与特征向量")
                } else {
                    compose.onNode(hasSetTextAction()).assertIsDisplayed().assertTextContains(draft)
                }
                compose.onNodeWithText("取消").assertIsDisplayed()
                compose.onNodeWithText("保存").assertIsDisplayed().assertIsEnabled()
                capture(kind)
                if (failSave) database.openHelper.writableDatabase.execSQL(
                    "CREATE TRIGGER reject_card_save BEFORE ${if (kind == "edit") "UPDATE" else "INSERT"} ON flashcard BEGIN SELECT RAISE(ABORT, 'test-only card save rejected'); END"
                )
                blockWrites.set(pendingSave)
                compose.onNodeWithText("保存").performClick()
                if (pendingSave) {
                    assertTrue(writeEntered.await(5, TimeUnit.SECONDS))
                    compose.onNodeWithText("取消").assertIsNotEnabled()
                    restoration.emulateSavedInstanceStateRestore()
                    compose.onNodeWithText("保存中").assertIsDisplayed().assertIsNotEnabled()
                    compose.onNodeWithText("取消").assertIsNotEnabled()
                    cardField("答案", editable = false).assertTextContains("特征值与特征向量").assertIsNotEnabled()
                    writeRelease.countDown()
                }
                if (failSave) {
                    await { !model.uiState.value.isLocalBusy && model.uiState.value.snackbarMessage?.contains("test-only card save rejected") == true }
                    val message = requireNotNull(model.uiState.value.knowledgeCardSaveResult?.errorMessage)
                    compose.onNode(hasText(message) and hasAnyAncestor(isDialog())).assertIsDisplayed()
                    restoration.emulateSavedInstanceStateRestore()
                    cardField(if (knowledgePoint) "核心解释" else "答案").assertTextContains("特征值与特征向量")
                    cardField("标签，用逗号分隔").assertTextContains("期末，重点")
                    compose.onNodeWithText("保存").assertIsDisplayed().assertIsEnabled()
                    assertEquals(2, model.uiState.value.studySets.size)
                    assertEquals(if (kind == "edit") 1 else 0, model.uiState.value.knowledgeCards.size)
                    assertEquals(2, StudySetRepository(dao).observeStudySets().first().size)
                    assertEquals(if (kind == "edit") 1 else 0, StudySetRepository(dao).observeKnowledgeCards().first().size)
                    if (kind == "edit") assertEquals("原答案", dao.getFlashcardById(originalCardId!!)?.back)
                    compose.onNode(hasScrollToIndexAction() and hasAnyAncestor(isDialog())).performScrollToNode(hasText(message))
                    compose.onNode(hasText(message) and hasAnyAncestor(isDialog())).assertIsDisplayed()
                    capture("card-failed-$kind-${if (knowledgePoint) "point" else "qa"}")
                    if (cancelAfterFailure) {
                        compose.onNodeWithText("取消").performClick()
                        compose.onNode(hasSetTextAction()).assertDoesNotExist()
                        assertEquals(2, model.uiState.value.studySets.size)
                        assertTrue(model.uiState.value.knowledgeCards.isEmpty())
                        assertEquals(2, StudySetRepository(dao).observeStudySets().first().size)
                        assertTrue(StudySetRepository(dao).observeKnowledgeCards().first().isEmpty())
                        return@withTimeout
                    }
                    database.openHelper.writableDatabase.execSQL("DROP TRIGGER reject_card_save")
                    compose.onNodeWithText("保存").performClick()
                }
                await {
                    when (kind) {
                        "folder" -> model.uiState.value.folderContent.folders.any { it.id == selectedId && it.name == draft }
                        "file" -> model.uiState.value.folderContent.files.any { it.id == selectedId && it.displayName == draft }
                        "manual", "edit" -> model.uiState.value.knowledgeCards.any { it.front == draft }
                        "new" -> model.uiState.value.studySets.size == 3 && model.uiState.value.knowledgeCards.any { it.front == draft }
                        else -> model.uiState.value.studySets.any { it.id == selectedId && it.title == draft }
                    }
                }
                compose.waitUntil(5_000) { compose.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isEmpty() }
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
                        val card = model.uiState.value.knowledgeCards.single { it.studySetId == selectedId }
                        assertEquals(selectedId, card.studySetId)
                        assertEquals("特征值与特征向量", card.back)
                        assertEquals(listOf("期末", "重点"), card.tags)
                        assertEquals(if (knowledgePoint) KnowledgeCardType.KNOWLEDGE_CARD else KnowledgeCardType.QA_FLASHCARD, card.type)
                        if (kind == "edit") assertEquals(originalCardId, card.flashcardId)
                        assertEquals(if (kind == "edit" && pendingSave) 2 else 1, model.uiState.value.knowledgeCards.size)
                        if (kind == "edit" && pendingSave) {
                            assertEquals("另一张卡片的答案", model.uiState.value.knowledgeCards.single { it.studySetId == "set-0" }.back)
                        }
                    }
                    "new" -> {
                        assertEquals(3, model.uiState.value.studySets.size)
                        val card = model.uiState.value.knowledgeCards.single()
                        assertFalse(card.studySetId in listOf("set-0", "set-1"))
                        assertEquals("特征值与特征向量", card.back)
                        assertEquals(listOf("期末", "重点"), card.tags)
                    }
                    else -> assertEquals(original, model.uiState.value.studySets.single { it.id != selectedId }.title)
                }
                restoration.emulateSavedInstanceStateRestore()
                compose.onNode(hasSetTextAction()).assertDoesNotExist()
            } finally {
                withContext(NonCancellable) {
                    writeRelease.countDown()
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

    private fun cardField(label: String, editable: Boolean = true): SemanticsNodeInteraction {
        val matcher = if (editable) hasSetTextAction() and hasText(label) else hasText(label)
        compose.onNode(hasScrollToIndexAction() and hasAnyAncestor(isDialog())).performScrollToNode(matcher)
        return compose.onNode(matcher)
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
