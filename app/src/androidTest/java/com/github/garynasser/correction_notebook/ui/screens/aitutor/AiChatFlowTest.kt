package com.github.garynasser.correction_notebook.ui.screens.aitutor

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.local.AISettingsManager
import com.github.garynasser.correction_notebook.data.local.ai.AiCredentialCipher
import com.github.garynasser.correction_notebook.data.local.ai.AiDatabase
import com.github.garynasser.correction_notebook.data.local.ai.UserMemoryDao
import com.github.garynasser.correction_notebook.data.local.ai.UserMemoryEntity
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseChunkEntity
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseDatabase
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseFileEntity
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseFileStorage
import com.github.garynasser.correction_notebook.data.model.ai.AIProviderType
import com.github.garynasser.correction_notebook.data.model.ai.AiProviderForm
import com.github.garynasser.correction_notebook.data.remote.ai.AnthropicCompatibleAdapter
import com.github.garynasser.correction_notebook.data.remote.ai.OpenAiCompatibleAdapter
import com.github.garynasser.correction_notebook.data.remote.api.AIApiService
import com.github.garynasser.correction_notebook.data.repository.*
import com.github.garynasser.correction_notebook.domain.usecase.AiStudyUseCase
import com.github.garynasser.correction_notebook.domain.usecase.KnowledgeAiMode
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import com.google.gson.Gson
import com.google.gson.JsonParser
import java.time.LocalDate
import java.io.File
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Rule
import retrofit2.Response

class AiChatFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun chatCannotSendItsHistoryToAProviderActivatedDuringPreparation() = checkProviderSwitch(knowledgeMode = false)
    @Test fun knowledgeChatCannotSendItsContextToAProviderActivatedDuringPreparation() = checkProviderSwitch(knowledgeMode = true)

    private fun checkProviderSwitch(knowledgeMode: Boolean) = withFixture { fixture ->
        val gate = Gate().also { fixture.memoryGate = it }
        withContext(Dispatchers.Main) {
            fixture.vm.setKnowledgeMode(knowledgeMode)
            fixture.vm.sendMessage("physics QA first question")
        }
        gate.entered.await()
        fixture.providers.activateProvider(fixture.secondId)
        await { fixture.vm.uiState.value.activeProvider?.id == fixture.secondId &&
            fixture.vm.uiState.value.selectedSessionId == fixture.secondSession }
        gate.release.complete(Unit)
        await { !fixture.vm.uiState.value.isLoading }
        assertEquals(1, fixture.service.requests.size)
        assertFirstProvider(fixture.service.requests.single())
        val request = fixture.service.requests.single().body
        assertTrue(request.contains(if (knowledgeMode) "physics conservation QA private document" else FIRST_HISTORY))
        assertFalse(request.contains(SECOND_HISTORY))
        assertEquals(REPLY, fixture.messages(fixture.firstSession).last().content)
        assertEquals(listOf(SECOND_HISTORY), fixture.messages(fixture.secondSession).map { it.content })
        assertEquals(listOf(SECOND_HISTORY), fixture.vm.uiState.value.messages.map { it.content })
    }

    @Test fun editingAProviderDuringPreparationCannotChangeTheCapturedCredentialsOrModel() = withFixture { fixture ->
        val gate = Gate().also { fixture.memoryGate = it }
        withContext(Dispatchers.Main) { fixture.vm.sendMessage("QA edit question") }
        gate.entered.await()
        val provider = requireNotNull(fixture.providers.getProviderById(fixture.firstId))
        fixture.providers.saveProvider(provider.copy(baseUrl = "https://edited.invalid/v1",
            apiKey = "qa-edited-key", defaultModel = "edited-model", customHeadersJson = """{"X-QA-Route":"edited"}"""))
        gate.release.complete(Unit)
        await { !fixture.vm.uiState.value.isLoading }
        assertFirstProvider(fixture.service.requests.single())
        assertEquals("first-model", fixture.chats.getSessionById(fixture.firstSession)?.model)
        assertEquals(REPLY, fixture.messages(fixture.firstSession).last().content)
    }

    @Test fun everyStudyActionKeepsItsProviderWhileBuildingContext() = withFixture { fixture ->
        fixture.service.reply = """{"summary":"QA reply","title":"Physics","cards":[{"type":"QA_FLASHCARD","title":"Energy","front":"What is conserved?","back":"Energy."}]}"""
        val today = LocalDate.of(2026, 10, 3)
        val actions: List<Pair<String, suspend () -> Result<*>>> = listOf(
            "file summary" to { fixture.useCase.summarizeKnowledgeFile(FILE_ID, KnowledgeAiMode.SUMMARY) },
            "structured file summary" to { fixture.useCase.summarizeKnowledgeFileStructured(FILE_ID, KnowledgeAiMode.KEY_POINTS) },
            "study set" to { fixture.useCase.generateStudySetFromKnowledgeFile(FILE_ID) },
            "today plan" to { fixture.useCase.generateTodayPlan(today) },
            "today advice" to { fixture.useCase.generateTodayAdvice(today) },
            "todo breakdown" to { fixture.useCase.breakDownTodo("Physics", "QA task") },
            "structured todo" to { fixture.useCase.breakDownTodoStructured("Physics", "QA task") },
            "course summary" to { fixture.useCase.summarizeCourseSection("Physics", "QA note") },
            "structured course summary" to { fixture.useCase.summarizeCourseSectionStructured(1, "Physics", 2, "Conservation", "QA note") },
            "stats insight" to { fixture.useCase.generateStatsInsight(today.minusDays(7), today, "QA period") }
        )
        for ((name, action) in actions) {
            fixture.providers.activateProvider(fixture.firstId)
            val gate = Gate().also { fixture.memoryGate = it }
            val operation = async { action() }
            gate.entered.await()
            fixture.providers.activateProvider(fixture.secondId)
            gate.release.complete(Unit)
            val result = operation.await()
            assertTrue("Study action must complete: $name: ${result.exceptionOrNull()}", result.isSuccess)
            assertFirstProvider(fixture.service.requests.last(), name)
        }
        assertEquals(actions.size, fixture.service.requests.size)
    }

    @Test fun disablingAiDuringPreparationStillPreventsTheCapturedRequest() = withFixture { fixture ->
        val gate = Gate().also { fixture.memoryGate = it }
        withContext(Dispatchers.Main) { fixture.vm.sendMessage("QA disabled question") }
        gate.entered.await()
        fixture.settings.setAiEnabled(false)
        gate.release.complete(Unit)
        await { !fixture.vm.uiState.value.isLoading && fixture.vm.uiState.value.error != null }
        assertTrue(fixture.vm.uiState.value.error.orEmpty().contains("AI 功能已关闭"))
        assertTrue(fixture.service.requests.isEmpty())
        assertEquals("QA disabled question", fixture.messages(fixture.firstSession).last().content)
    }

    @Test fun deletingAProviderDuringPreparationPreventsAnyFurtherRequestUsingItsCredentials() = withFixture { fixture ->
        val gate = Gate().also { fixture.memoryGate = it }
        withContext(Dispatchers.Main) { fixture.vm.sendMessage("QA deleted question") }
        gate.entered.await()
        fixture.providers.deleteProvider(fixture.firstId)
        gate.release.complete(Unit)
        await { !fixture.vm.uiState.value.isLoading && fixture.vm.uiState.value.error != null }
        assertTrue("Deleted credentials must not start another request", fixture.service.requests.isEmpty())
        assertEquals(null, fixture.chats.getSessionById(fixture.firstSession))
        assertEquals(listOf(SECOND_HISTORY), fixture.messages(fixture.secondSession).map { it.content })
    }

    @Test fun duplicateSendAndDestructiveActionsAreIgnoredWhileAReplyIsPending() = withFixture { fixture ->
        val gate = Gate().also { fixture.service.gate = it }
        withContext(Dispatchers.Main) { fixture.vm.sendMessage("QA pending question") }
        gate.entered.await()
        withContext(Dispatchers.Main) {
            fixture.vm.sendMessage("QA duplicate question")
            fixture.vm.clearMessages()
            fixture.vm.deleteCurrentSession()
            fixture.vm.newSession()
            fixture.vm.selectSession(fixture.secondSession)
        }
        assertEquals(1, fixture.service.requests.size)
        assertEquals(fixture.firstSession, fixture.vm.uiState.value.selectedSessionId)
        assertEquals(2, fixture.chats.observeAllSessions().first().size)
        assertEquals(listOf(FIRST_HISTORY, "QA pending question"), fixture.messages(fixture.firstSession).map { it.content })
        gate.release.complete(Unit)
        await { !fixture.vm.uiState.value.isLoading && fixture.vm.uiState.value.messages.lastOrNull()?.content == REPLY }
        assertEquals(listOf(FIRST_HISTORY, "QA pending question", REPLY), fixture.messages(fixture.firstSession).map { it.content })
    }

    @Test fun aFailedRequestPreservesTheQuestionAndAllowsTheNextRequest() = withFixture { fixture ->
        fixture.service.reject = true
        withContext(Dispatchers.Main) { fixture.vm.sendMessage("QA failed question") }
        await { !fixture.vm.uiState.value.isLoading && fixture.vm.uiState.value.error != null }
        assertEquals(listOf(FIRST_HISTORY, "QA failed question"), fixture.messages(fixture.firstSession).map { it.content })
        fixture.service.reject = false
        withContext(Dispatchers.Main) { fixture.vm.sendMessage("QA next question") }
        await { !fixture.vm.uiState.value.isLoading && fixture.vm.uiState.value.messages.lastOrNull()?.content == REPLY }
        assertEquals(null, fixture.vm.uiState.value.error)
        assertEquals(2, fixture.service.requests.size)
        assertTrue(fixture.service.requests.last().body.contains("QA failed question"))
        assertEquals(listOf(FIRST_HISTORY, "QA failed question", "QA next question", REPLY), fixture.messages(fixture.firstSession).map { it.content })
    }

    @Test fun clearingTheViewModelCancelsTheReplyWithoutDeletingTheSavedQuestion() = withFixture { fixture ->
        val gate = Gate().also { fixture.service.gate = it }
        withContext(Dispatchers.Main) { fixture.vm.sendMessage("QA cancelled question") }
        gate.entered.await()
        withContext(Dispatchers.Main) { fixture.store.clear() }
        gate.cancelled.await()
        assertEquals(listOf(FIRST_HISTORY, "QA cancelled question"), fixture.messages(fixture.firstSession).map { it.content })
        assertEquals(1, fixture.service.requests.size)
    }

    @Test fun stoppingAReplyFromTheScreenCancelsTheRequestAndReenablesTheComposer() = withFixture { fixture ->
        val gate = Gate().also { fixture.service.gate = it }
        compose.setContent { CorrectionNotebookTheme { AITutorScreen(viewModel = fixture.vm) } }
        compose.onNode(hasSetTextAction()).performTextReplacement("QA stopped question")
        compose.onNodeWithContentDescription("发送").performClick()
        gate.entered.await()
        compose.onNodeWithContentDescription("停止回复").assertIsEnabled().performClick()
        gate.cancelled.await()
        await { !fixture.vm.uiState.value.isLoading }
        assertEquals(listOf(FIRST_HISTORY, "QA stopped question"), fixture.messages(fixture.firstSession).map { it.content })
        fixture.service.gate = null
        compose.onNode(hasSetTextAction()).assertIsEnabled().performTextReplacement("QA next question")
        compose.onNodeWithContentDescription("发送").performClick()
        compose.waitUntil(5_000) { !fixture.vm.uiState.value.isLoading && fixture.vm.uiState.value.messages.lastOrNull()?.content == REPLY }
        assertEquals(2, fixture.service.requests.size)
        assertEquals(REPLY, fixture.messages(fixture.firstSession).last().content)
    }

    @Test fun lightChatControlsFitNarrowScreenWithLargeFont() = checkLayout(dark = false, empty = false)
    @Test fun darkChatControlsFitNarrowScreenWithLargeFont() = checkLayout(dark = true, empty = false)
    @Test fun lightEmptyChatFitsNarrowScreenWithLargeFont() = checkLayout(dark = false, empty = true)
    @Test fun darkEmptyChatFitsNarrowScreenWithLargeFont() = checkLayout(dark = true, empty = true)

    private fun checkLayout(dark: Boolean, empty: Boolean) = withFixture { fixture ->
        if (empty) {
            fixture.chats.clearSessionMessages(fixture.firstSession)
            await { fixture.vm.uiState.value.messages.isEmpty() }
        }
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3.375f, 1.3f)) {
                CorrectionNotebookTheme(darkTheme = dark, dynamicColor = false) {
                    AITutorScreen(viewModel = fixture.vm)
                }
            }
        }
        compose.onNodeWithContentDescription("发送").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText("普通").assertIsDisplayed()
        if (empty) compose.onNodeWithText("制定学习安排").assertIsDisplayed()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null),
                "qa-chat-${if (dark) "dark" else "light"}-${if (empty) "empty" else "history"}.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    private fun assertFirstProvider(request: RecordedRequest, action: String = "chat") {
        assertEquals("$action must use its original endpoint", "https://first.invalid/v1/chat/completions", request.url)
        assertEquals("Bearer qa-first-key", request.headers["Authorization"])
        assertEquals("first", request.headers["X-QA-Route"])
        val payload = JsonParser.parseString(request.body).asJsonObject
        assertEquals("first-model", payload.get("model").asString)
        assertEquals(0.4, payload.get("temperature").asDouble, 0.001)
        assertEquals(512, payload.get("max_tokens").asInt)
    }

    private suspend fun await(predicate: () -> Boolean) = withTimeout(5_000) {
        while (!predicate()) delay(10)
    }

    private fun withFixture(test: suspend CoroutineScope.(Fixture) -> Unit) = runBlocking {
        val fixture = Fixture()
        val previouslyEnabled = fixture.settings.aiEnabled.first()
        try {
            fixture.settings.setAiEnabled(true)
            fixture.seed()
            withTimeout(30_000) {
                withContext(Dispatchers.Main) { fixture.createViewModel() }
                val observer = launch { fixture.vm.uiState.collect {} }
                try {
                    await { fixture.vm.uiState.value.selectedSessionId == fixture.firstSession &&
                        fixture.vm.uiState.value.messages.isNotEmpty() }
                    test(fixture)
                } finally { observer.cancel() }
            }
        } finally {
            withContext(Dispatchers.Main) { fixture.store.clear() }
            fixture.memoryGate?.release?.complete(Unit)
            fixture.service.gate?.release?.complete(Unit)
            fixture.settings.setAiEnabled(previouslyEnabled)
            fixture.database.close()
            fixture.knowledge.close()
        }
    }

    private class Fixture {
        private val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AiDatabase::class.java).build()
        val knowledge = Room.inMemoryDatabaseBuilder(context, KnowledgeBaseDatabase::class.java).build()
        val settings = AISettingsManager(context)
        val providers = ProviderRepository(database.aiProviderDao(), AiCredentialCipher(context))
        val chats = ChatSessionRepository(database.chatSessionDao(), database.chatMessageDao())
        val service = ChatService()
        private val gson = Gson()
        val ai = AIRepository(settings, providers, OpenAiCompatibleAdapter(service, gson), AnthropicCompatibleAdapter(service, gson), gson)
        private val memories = MemoryRepository(database.userMemoryDao())
        @Volatile var memoryGate: Gate? = null
        private val gatedMemories = MemoryRepository(object : UserMemoryDao by database.userMemoryDao() {
            override fun observeMemories(): Flow<List<UserMemoryEntity>> = flow {
                memoryGate?.await()
                emitAll(database.userMemoryDao().observeMemories())
            }
        })
        val useCase = AiStudyUseCase(ai, KnowledgeBaseAiRepository(knowledge.knowledgeBaseDao()), gatedMemories,
            TodoRepository(context), ScheduleRepository(context), StudySessionRepository(context), CourseLearningRepository(context),
            KnowledgeBaseRepository(knowledge.knowledgeBaseDao(), KnowledgeBaseFileStorage(context), context))
        val store = ViewModelStore()
        lateinit var vm: AITutorViewModel
        var firstId = 0L
        var secondId = 0L
        var firstSession = 0L
        var secondSession = 0L

        suspend fun seed() {
            firstId = providers.saveProvider(ai.normalizeProviderRecord(AiProviderForm(name = "First", baseUrl = "https://first.invalid/v1",
                apiKey = "qa-first-key", model = "first-model", customHeaders = """{"X-QA-Route":"first"}""", temperature = "0.4", maxTokens = "512")))
            secondId = providers.saveProvider(ai.normalizeProviderRecord(AiProviderForm(name = "Second", baseUrl = "https://second.invalid/v1",
                type = AIProviderType.ANTHROPIC_COMPATIBLE, apiKey = "qa-second-key", model = "second-model", isActive = false)))
            firstSession = chats.createSession("First conversation", firstId, "first-model")
            secondSession = chats.createSession("Second conversation", secondId, "second-model")
            chats.saveMessage(firstSession, "user", FIRST_HISTORY)
            chats.saveMessage(secondSession, "user", SECOND_HISTORY)
            val now = System.currentTimeMillis()
            knowledge.knowledgeBaseDao().insertFile(KnowledgeBaseFileEntity(FILE_ID, null, "Physics.txt", "physics.txt", "/unused/physics.txt",
                "text/plain", 64L, "LOCAL", null, null, null, null, null, "", null, now, now))
            knowledge.knowledgeBaseDao().insertChunks(listOf(KnowledgeBaseChunkEntity(fileId = FILE_ID, chunkIndex = 0,
                title = "Physics", path = "Physics.txt", content = "physics conservation QA private document",
                keywords = "physics conservation", updatedAt = now)))
        }

        fun createViewModel() {
            vm = AITutorViewModel(useCase, ai, providers, chats, memories, settings)
            store.put("chat-flow", vm)
        }

        suspend fun messages(sessionId: Long) = chats.observeMessagesForSession(sessionId).first()
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

    private data class RecordedRequest(val url: String, val headers: Map<String, String>, val body: String)

    private class ChatService : AIApiService {
        val requests = CopyOnWriteArrayList<RecordedRequest>()
        @Volatile var reply = REPLY
        @Volatile var reject = false
        @Volatile var gate: Gate? = null
        private val gson = Gson()
        override suspend fun getJson(url: String, headers: Map<String, String>): Response<ResponseBody> =
            throw AssertionError("Unexpected model list request")
        override suspend fun postJson(url: String, headers: Map<String, String>, request: RequestBody): Response<ResponseBody> {
            val buffer = Buffer()
            request.writeTo(buffer)
            requests += RecordedRequest(url, headers, buffer.readUtf8())
            gate?.await()
            if (reject) return Response.error(401, """{"error":{"message":"QA rejected request"}}""".toResponseBody())
            val body = if (url.endsWith("/messages")) {
                gson.toJson(mapOf("content" to listOf(mapOf("type" to "text", "text" to reply))))
            } else {
                gson.toJson(mapOf("choices" to listOf(mapOf("message" to mapOf("content" to reply)))))
            }
            return Response.success(body.toResponseBody())
        }
    }

    companion object {
        private const val FIRST_HISTORY = "QA first provider private history"
        private const val SECOND_HISTORY = "QA second provider private history"
        private const val FILE_ID = "qa-chat-physics"
        private const val REPLY = "QA reply"
    }
}
