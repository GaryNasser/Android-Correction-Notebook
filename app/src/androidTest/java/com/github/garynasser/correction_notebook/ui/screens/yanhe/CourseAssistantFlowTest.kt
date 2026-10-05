package com.github.garynasser.correction_notebook.ui.screens.yanhe

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.local.*
import com.github.garynasser.correction_notebook.data.local.ai.AiDatabase
import com.github.garynasser.correction_notebook.data.local.ai.AiCredentialCipher
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseDatabase
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseFileStorage
import com.github.garynasser.correction_notebook.data.model.ai.*
import com.github.garynasser.correction_notebook.data.model.common.ApiResponse
import com.github.garynasser.correction_notebook.data.model.yanhe.*
import com.github.garynasser.correction_notebook.data.remote.ai.*
import com.github.garynasser.correction_notebook.data.remote.api.*
import com.github.garynasser.correction_notebook.data.remote.cas.BitCasClient
import com.github.garynasser.correction_notebook.data.remote.manager.VideoRemoteManager
import com.github.garynasser.correction_notebook.data.repository.*
import com.github.garynasser.correction_notebook.domain.usecase.AiStudyUseCase
import com.github.garynasser.correction_notebook.ui.components.LocalAiEnabled
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import com.google.gson.Gson
import com.google.gson.JsonElement
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.*
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.UUID

class CourseAssistantFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun recreationKeepsSelectedSectionNotesAndGeneratedResult() = withFixture { f ->
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalAiEnabled provides true) {
                CorrectionNotebookTheme { CourseVideoListScreen(f.videos, f.assistant, { _, _, _ -> }, {}) }
            }
        }
        compose.onAllNodesWithContentDescription("课程助手")[0].performClick()
        compose.onNodeWithText("补充课堂笔记，可留空").performTextInput(NOTE)
        compose.onNodeWithText("生成学习包").performClick()
        f.await { f.assistant.uiState.value.result == SUMMARY }
        compose.onNodeWithText(SUMMARY).assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("补充课堂笔记，可留空").assertExists()
        compose.onNodeWithText(NOTE).assertExists()
        compose.onNodeWithText(SUMMARY).assertExists()
        compose.onNodeWithContentDescription("关闭课程助手").performClick()
        compose.onAllNodesWithContentDescription("课程助手")[1].performClick()
        compose.onNodeWithText(SUMMARY).assertDoesNotExist()
        compose.onNodeWithText(NOTE).assertDoesNotExist()
    }

    @Test fun resultCannotBeSavedUnderAnotherSectionAndOldActionsCannotBeReplayed() = withFixture { f ->
        f.generate()
        val oldAction = f.assistant.uiState.value.actions.single()
        withContext(Dispatchers.Main) { f.assistant.saveResultAsNote(12, "算法设计", 35, "另一节课") }
        f.await { !f.assistant.uiState.value.isActionBusy }
        assertTrue("Generated notes must belong to their source section", f.learning.notes.first().isEmpty())
        withContext(Dispatchers.Main) { f.assistant.clear(); f.assistant.applyAction(oldAction, 12, "算法设计", 34, SECTION) }
        f.await { !f.assistant.uiState.value.isActionBusy }
        assertTrue("A closed learning package cannot execute its old actions", f.learning.notes.first().isEmpty())
    }

    @Test fun savingThe201stNoteDoesNotDeleteAnOlderCourseNote() = withFixture { f ->
        val original = List(200) { CourseNote(id = "original-$it", courseId = 12, sectionId = 34,
            sectionTitle = SECTION, content = "原有笔记 $it", createdAt = it.toLong()) }
        f.preferences.updateData { it.toMutablePreferences().apply {
            this[stringPreferencesKey("course_notes")] = CourseLearningPreferenceCodec.serializeNotes(original)
        } }
        f.learning.saveNote(CourseNote(courseId = 99, sectionId = 99, sectionTitle = "新课程", content = "新笔记"))
        assertEquals(201, f.learning.notes.first().size)
        assertEquals(200, f.learning.getNotesForCourse(12).size)
    }

    @Test fun generatedNoteAndTodoSaveOnceAndNotesRemainReadableWithAiDisabled() = withFixture { f ->
        val enabled = mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalAiEnabled provides enabled.value) {
                CorrectionNotebookTheme { CourseVideoListScreen(f.videos, f.assistant, { _, _, _ -> }, {}) }
            }
        }
        compose.onAllNodesWithContentDescription("课程助手")[0].performClick()
        compose.onNodeWithText("生成学习包").performClick()
        f.await { f.assistant.uiState.value.result == SUMMARY }
        compose.onNodeWithText("存笔记").performClick()
        f.await { !f.assistant.uiState.value.isActionBusy && f.assistant.uiState.value.appliedActionKeys.isNotEmpty() }
        compose.onNodeWithText("已保存", substring = false).assertIsNotEnabled()
        compose.onNodeWithText("转待办").performClick()
        f.await { f.assistant.uiState.value.appliedActionKeys.size == 2 }
        compose.onNodeWithText("已添加").assertIsNotEnabled()
        assertEquals(1, f.learning.notes.first().size)
        val todo = f.todos.todoItems.first().single()
        assertEquals("复习：$SECTION", todo.title)
        assertEquals(SUMMARY, todo.description)
        assertEquals("COURSE_ASSISTANT", todo.source.name)
        assertTrue(f.service.request.contains("你没有获得课程录像或转录"))
        compose.onNodeWithText("笔记").performClick()
        compose.onNodeWithText(SUMMARY).assertIsDisplayed()
        compose.onNodeWithContentDescription("关闭课程助手").performClick()
        compose.runOnIdle { enabled.value = false }
        compose.onAllNodesWithContentDescription("课程笔记")[0].performClick()
        compose.onNodeWithText(SUMMARY).assertIsDisplayed()
        compose.onNodeWithText("生成学习包").assertDoesNotExist()
    }

    @Test fun noteDeletionNeedsConfirmationAndNeverDeletesOtherSections() = withFixture { f ->
        val own = CourseNote(courseId = 12, sectionId = 34, sectionTitle = SECTION, content = SUMMARY)
        val other = own.copy(id = "other-section", sectionId = 35, content = "另一个章节的笔记")
        f.learning.saveNote(own); f.learning.saveNote(other)
        show(f, enabled = false)
        compose.onAllNodesWithContentDescription("课程笔记")[0].performClick()
        compose.onNodeWithText(SUMMARY).assertIsDisplayed()
        compose.onNodeWithText(other.content).assertDoesNotExist()
        compose.onNode(hasContentDescription("删除笔记", substring = true)).performClick()
        compose.onNodeWithText("取消").performClick()
        assertEquals(2, f.learning.notes.first().size)
        compose.onNode(hasContentDescription("删除笔记", substring = true)).performClick()
        compose.onNodeWithText("删除").performClick()
        f.await { f.assistant.uiState.value.actionMessage == "已删除课程笔记" || f.assistant.uiState.value.appliedActionKeys.isNotEmpty() }
        assertEquals(listOf(other), f.learning.notes.first())
        compose.onNodeWithText("本节暂无笔记").assertExists()
    }

    @Test fun failedNoteWriteCanBeRetriedAndPendingWriteBlocksDuplicateActions() = withFixture { f ->
        f.generate()
        val gate = Gate()
        f.preferences.writeGate = gate
        f.preferences.failWrite = true
        withContext(Dispatchers.Main) {
            f.assistant.saveResultAsNote(12, "算法设计", 34, SECTION)
            f.assistant.saveResultAsNote(12, "算法设计", 34, SECTION)
        }
        gate.entered.await()
        assertTrue(f.assistant.uiState.value.isActionBusy)
        assertEquals(1, f.preferences.writes)
        gate.release.complete(Unit)
        f.await { !f.assistant.uiState.value.isActionBusy && f.assistant.uiState.value.actionError != null }
        assertTrue(f.learning.notes.first().isEmpty())
        assertTrue(f.assistant.uiState.value.appliedActionKeys.isEmpty())
        f.preferences.failWrite = false
        f.preferences.writeGate = null
        withContext(Dispatchers.Main) { f.assistant.saveResultAsNote(12, "算法设计", 34, SECTION) }
        f.await { f.assistant.uiState.value.appliedActionKeys.isNotEmpty() }
        assertEquals(1, f.learning.notes.first().size)
        assertEquals(2, f.preferences.writes)
    }

    @Test fun failedNoteReadShowsRetryRatherThanAnEmptyNotesClaim() = withFixture { f ->
        f.learning.saveNote(CourseNote(courseId = 12, sectionId = 34, sectionTitle = SECTION, content = SUMMARY))
        f.preferences.failRead = true
        show(f, enabled = false)
        compose.onAllNodesWithContentDescription("课程笔记")[0].performClick()
        compose.waitUntil(5_000) { f.assistant.notesState.value.error != null }
        compose.onNodeWithText("笔记读取失败").assertExists()
        compose.onNodeWithText("本节暂无笔记").assertDoesNotExist()
        f.preferences.failRead = false
        compose.onNodeWithText("重试").performClick()
        compose.waitUntil(5_000) { f.assistant.notesState.value.notes.isNotEmpty() }
        compose.onNodeWithText(SUMMARY).assertIsDisplayed()
    }

    @Test fun closingPendingGenerationCancelsItAndCannotRepopulateAnotherSection() = withFixture { f ->
        val gate = Gate()
        f.service.gate = gate
        withContext(Dispatchers.Main) { f.assistant.summarizeLearningPackage(12, "算法设计", 34, SECTION, NOTE) }
        gate.entered.await()
        withContext(Dispatchers.Main) { f.assistant.clear() }
        gate.cancelled.await()
        f.service.gate = null
        f.service.summary = "另一个章节的学习包"
        withContext(Dispatchers.Main) { f.assistant.summarizeLearningPackage(12, "算法设计", 35, "另一节课", "另一节课的笔记") }
        f.await { f.assistant.uiState.value.result == f.service.summary }
        gate.release.complete(Unit)
        withContext(Dispatchers.Main) { f.assistant.saveResultAsNote(12, "算法设计", 35, "另一节课") }
        f.await { f.assistant.uiState.value.appliedActionKeys.isNotEmpty() }
        assertEquals(35, f.learning.notes.first().single().sectionId)
        assertEquals(f.service.summary, f.learning.notes.first().single().content)
    }

    @Test fun assistantFitsNarrowLightScreenWithKeyboard() = checkLayout(false, true)
    @Test fun assistantFitsNarrowDarkScreen() = checkLayout(true, false)

    @Test fun tappingDialogBackgroundClosesAndCancelsPendingGeneration() = withFixture { f ->
        show(f, enabled = true)
        compose.onAllNodesWithContentDescription("课程助手")[0].performClick()
        val gate = Gate()
        f.service.gate = gate
        compose.onNodeWithText("生成学习包").performClick()
        withTimeout(5_000) { gate.entered.await() }
        compose.onNode(isRoot() and hasAnyDescendant(hasContentDescription("关闭课程助手")))
            .performTouchInput { click(androidx.compose.ui.geometry.Offset(1f, center.y)) }
        compose.waitUntil(5_000) { gate.cancelled.isCompleted }
        compose.onNodeWithContentDescription("关闭课程助手").assertDoesNotExist()
        assertNull(f.assistant.uiState.value.result)
    }

    @Test fun offlineVideoListStillOffersStoredCourseNotesWithoutAi() = withFixture { f ->
        f.learning.saveNote(CourseNote(courseId = 12, sectionId = 34, sectionTitle = SECTION, content = SUMMARY))
        f.listFailure = true
        withContext(Dispatchers.Main) { f.videos.getVideoList(12) }
        f.await { f.videos.uiState is VideoUIState.Error }
        show(f, enabled = false)
        compose.onNodeWithText("视频列表加载失败").assertIsDisplayed()
        compose.onNodeWithContentDescription("查看课程笔记").performClick()
        compose.onNodeWithText(SECTION).assertIsDisplayed()
        compose.onNodeWithText(SUMMARY).assertIsDisplayed()
        compose.onNodeWithText("生成学习包").assertDoesNotExist()
    }

    @Test fun longStoredNotesExpandFullyAndSurviveScreenRestoration() = withFixture { f ->
        val content = SUMMARY.repeat(20)
        f.learning.saveNote(CourseNote(courseId = 12, sectionId = 34, sectionTitle = SECTION, content = content))
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            CompositionLocalProvider(LocalAiEnabled provides false, LocalDensity provides Density(3.375f, 1.3f)) {
                CorrectionNotebookTheme(darkTheme = true, dynamicColor = false) {
                    CourseAssistantDialog(12, "算法设计", CourseSection(id = 34, title = SECTION), "", {}, f.assistant, {})
                }
            }
        }
        compose.onNodeWithText("展开笔记").performClick()
        assertFullText(content)
        restoration.emulateSavedInstanceStateRestore()
        assertFullText(content)
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("收起笔记"))
        compose.onNodeWithText("收起笔记").performScrollTo().performClick()
        compose.onNodeWithText("展开笔记").assertExists()
    }

    private fun checkLayout(dark: Boolean, keyboard: Boolean) = withFixture { f ->
        f.service.summary = SUMMARY.repeat(8)
        val longSection = CourseSection(id = 34, courseId = 12, title = "$SECTION：Dijkstra 算法、优先队列与课后习题复习")
        compose.setContent {
            CompositionLocalProvider(LocalAiEnabled provides true, LocalDensity provides Density(3.375f, 1.3f)) {
                CorrectionNotebookTheme(darkTheme = dark, dynamicColor = false) {
                    CourseAssistantDialog(12, "算法设计", longSection, NOTE, {}, f.assistant, {})
                }
            }
        }
        assertFullText(longSection.title)
        if (keyboard) {
            compose.onNodeWithText("补充课堂笔记，可留空").performClick()
            compose.waitForIdle()
            android.os.SystemClock.sleep(400)
        }
        compose.onNodeWithContentDescription("关闭课程助手").assertIsDisplayed()
        compose.onNodeWithText("生成学习包").assertIsDisplayed().performClick()
        f.await { f.assistant.uiState.value.result == f.service.summary }
        compose.onNodeWithText("存笔记").assertIsDisplayed()
        compose.onNodeWithText("转待办").assertIsDisplayed()
        compose.onNodeWithText("重新生成").assertIsDisplayed()
        if (keyboard) {
            val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
            val previousFlags = automation.serviceInfo.flags
            automation.serviceInfo = automation.serviceInfo.apply {
                flags = flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
            }
            try {
                compose.waitUntil(5_000) { automation.windows.any { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD } }
                val ime = automation.windows.first { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
                val keyboardBounds = android.graphics.Rect()
                ime.getBoundsInScreen(keyboardBounds)
                listOf("存笔记", "转待办", "重新生成").forEach { label ->
                    val bounds = compose.onNodeWithText(label).fetchSemanticsNode().boundsInWindow
                    assertTrue("$label is covered by the keyboard", bounds.bottom <= keyboardBounds.top)
                }
            } finally {
                automation.serviceInfo = automation.serviceInfo.apply { flags = previousFlags }
            }
        }
        compose.onNodeWithText(f.service.summary).performScrollTo()
        assertFullText(f.service.summary)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(350)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try { File(instrumentation.targetContext.getExternalFilesDir(null), "qa-course-assistant-${if (dark) "dark" else "light"}.png")
            .outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) } }
        finally { bitmap.recycle() }
    }

    private fun show(f: Fixture, enabled: Boolean) {
        compose.setContent {
            CompositionLocalProvider(LocalAiEnabled provides enabled) {
                CorrectionNotebookTheme { CourseVideoListScreen(f.videos, f.assistant, { _, _, _ -> }, {}) }
            }
        }
    }

    private fun assertFullText(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        layouts.forEach { assertFalse(it.didOverflowHeight); assertEquals(text.length, it.getLineEnd(it.lineCount - 1)) }
    }

    private fun withFixture(test: suspend (Fixture) -> Unit) = runBlocking {
        val f = Fixture()
        val enabled = f.settings.aiEnabled.first()
        val token = f.tokens.getYanheLoginToken()
        try {
            f.settings.setAiEnabled(true)
            f.tokens.saveYanheLoginTokens("course-assistant-qa")
            f.providers.saveProvider(f.ai.normalizeProviderRecord(AiProviderForm(name = "Course assistant QA",
                baseUrl = "https://unused.invalid/v1", apiKey = "qa-key", model = "qa-model")))
            withContext(Dispatchers.Main) { f.create() }
            withTimeout(25_000) { f.await { f.videos.uiState is VideoUIState.Success }; test(f) }
        } finally {
            withContext(Dispatchers.Main) { f.store.clear() }
            f.settings.setAiEnabled(enabled)
            if (token == null) f.tokens.removeYanheLoginToken() else f.tokens.saveYanheLoginTokens(token)
            f.database.close()
            f.knowledge.close()
            f.network.dispatcher.executorService.shutdownNow()
            f.network.connectionPool.evictAll()
            f.dataScope.cancel()
            f.dataScope.coroutineContext[Job]?.join()
            f.courseFile.delete()
            f.todoFile.delete()
        }
    }

    private class Fixture {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val settings = AISettingsManager(context)
        val tokens = TokenManager(context)
        val database = Room.inMemoryDatabaseBuilder(context, AiDatabase::class.java).build()
        val knowledge = Room.inMemoryDatabaseBuilder(context, KnowledgeBaseDatabase::class.java).build()
        val providers = ProviderRepository(database.aiProviderDao(), AiCredentialCipher(context))
        val service = FakeAi()
        private val gson = Gson()
        val ai = AIRepository(settings, providers, OpenAiCompatibleAdapter(service, gson), AnthropicCompatibleAdapter(service, gson), gson)
        val dataScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val courseFile = File(context.cacheDir, "qa-course-${UUID.randomUUID()}.preferences_pb")
        val todoFile = File(context.cacheDir, "qa-course-todos-${UUID.randomUUID()}.preferences_pb")
        val preferences = ControlledPreferences(PreferenceDataStoreFactory.create(scope = dataScope, produceFile = { courseFile }))
        val learning = CourseLearningRepository(preferences)
        val todos = TodoRepository(PreferenceDataStoreFactory.create(scope = dataScope, produceFile = { todoFile }))
        val network = OkHttpClient.Builder().addInterceptor { error("Unexpected external network request") }.build()
        val store = ViewModelStore()
        lateinit var videos: VideoListViewModel
        lateinit var assistant: CourseAssistantViewModel
        @Volatile var listFailure = false
        fun create() {
            val unused = Retrofit.Builder().baseUrl("https://unused.invalid/").client(network)
                .addConverterFactory(GsonConverterFactory.create()).build().create(VideoApiService::class.java)
            val api = object : VideoApiService by unused {
                override suspend fun getCourseSession(token: String, courseId: Int, withPage: Boolean?, page: Int?, pageSize: Int?,
                    orderType: String?, orderTypeWeight: String?): ApiResponse<JsonElement> {
                    if (listFailure) throw java.io.IOException("离线")
                    return ApiResponse(0, data = gson.toJsonTree(
                        listOf(CourseSection(id = 34, courseId = 12, title = SECTION), CourseSection(id = 35, courseId = 12, title = "另一节课"))))
                }
            }
            val credentials = CredentialManager(context)
            val remote = VideoRemoteManager(api, tokens, credentials, AuthStateManager(), YanheRepository(tokens, credentials, BitCasClient(network)))
            videos = VideoListViewModel(SavedStateHandle(mapOf("courseId" to 12, "courseName" to "算法设计")), VideoRepository(remote), learning)
            assistant = CourseAssistantViewModel(AiStudyUseCase(ai, KnowledgeBaseAiRepository(knowledge.knowledgeBaseDao()),
                MemoryRepository(database.userMemoryDao()), todos, ScheduleRepository(context), StudySessionRepository(context), learning,
                KnowledgeBaseRepository(knowledge.knowledgeBaseDao(), KnowledgeBaseFileStorage(context), context)), learning, todos)
            store.put("videos", videos)
            store.put("assistant", assistant)
        }
        suspend fun generate() {
            withContext(Dispatchers.Main) { assistant.summarizeLearningPackage(12, "算法设计", 34, SECTION, NOTE) }
            await { assistant.uiState.value.result == SUMMARY }
        }
        suspend fun await(predicate: () -> Boolean) = withTimeout(5_000) { while (!predicate()) delay(10) }
    }

    private class ControlledPreferences(private val delegate: DataStore<Preferences>) : DataStore<Preferences> {
        @Volatile var failRead = false
        @Volatile var failWrite = false
        @Volatile var writeGate: Gate? = null
        var writes = 0
        override val data: Flow<Preferences> = flow {
            if (failRead) throw java.io.IOException("笔记读取失败")
            emitAll(delegate.data)
        }
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            writes++
            writeGate?.await()
            if (failWrite) throw java.io.IOException("笔记保存失败")
            return delegate.updateData(transform)
        }
    }

    private class Gate {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        suspend fun await() {
            entered.complete(Unit)
            try { release.await() } catch (error: CancellationException) { cancelled.complete(Unit); throw error }
        }
    }

    private class FakeAi : AIApiService {
        var request = ""
        @Volatile var gate: Gate? = null
        var summary = SUMMARY
        override suspend fun getJson(url: String, headers: Map<String, String>): Response<ResponseBody> = error("Unexpected models request")
        override suspend fun postJson(url: String, headers: Map<String, String>, request: RequestBody): Response<ResponseBody> {
            val buffer = Buffer(); request.writeTo(buffer); this.request = buffer.readUtf8()
            gate?.await()
            val reply = Gson().toJson(mapOf("summary" to summary, "actions" to listOf(mapOf("type" to "SAVE_COURSE_NOTE",
                "title" to "保存本节笔记", "payload" to mapOf("content" to summary)))))
            return Response.success(Gson().toJson(mapOf("choices" to listOf(mapOf("message" to mapOf("content" to reply))))).toResponseBody())
        }
    }

    companion object {
        private const val SECTION = "最短路径与松弛操作的正确性证明"
        private const val NOTE = "候选路径更短时才更新当前距离。"
        private const val SUMMARY = "本节复习：比较原有距离与候选路径的长度。"
    }
}
