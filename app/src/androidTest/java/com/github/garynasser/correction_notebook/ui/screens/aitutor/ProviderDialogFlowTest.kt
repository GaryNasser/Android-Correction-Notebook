package com.github.garynasser.correction_notebook.ui.screens.aitutor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.local.AISettingsManager
import com.github.garynasser.correction_notebook.data.local.ai.AiCredentialCipher
import com.github.garynasser.correction_notebook.data.local.ai.AiDatabase
import com.github.garynasser.correction_notebook.data.model.ai.AiModelOption
import com.github.garynasser.correction_notebook.data.model.ai.AiProviderForm
import com.github.garynasser.correction_notebook.data.remote.ai.AnthropicCompatibleAdapter
import com.github.garynasser.correction_notebook.data.remote.ai.OpenAiCompatibleAdapter
import com.github.garynasser.correction_notebook.data.remote.api.AIApiService
import com.github.garynasser.correction_notebook.data.repository.AIRepository
import com.github.garynasser.correction_notebook.data.repository.ProviderRecord
import com.github.garynasser.correction_notebook.data.repository.ProviderRepository
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import com.google.gson.Gson
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import retrofit2.Response

class ProviderDialogFlowTest {
    @get:Rule
    val compose = createComposeRule()
    private lateinit var database: AiDatabase
    private lateinit var providers: ProviderRepository
    private lateinit var ai: AIRepository
    private lateinit var original: ProviderRecord
    private val service = RecordingService()
    private var uiState by mutableStateOf(AITutorUiState())
    private var lastSavedForm: AiProviderForm? = null
    private var completedSaves = 0
    private var failure: Throwable? = null

    @Before
    fun setup() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AiDatabase::class.java).build()
        providers = ProviderRepository(database.aiProviderDao(), AiCredentialCipher(context))
        val gson = Gson()
        ai = AIRepository(AISettingsManager(context), providers, OpenAiCompatibleAdapter(service, gson),
            AnthropicCompatibleAdapter(service, gson), gson)
        val id = providers.saveProvider(ai.normalizeProviderRecord(AiProviderForm(
            name = "First provider", baseUrl = "https://first.example.invalid/v1",
            apiKey = "qa-key-original-abcdef", model = "first-model"
        )))
        original = requireNotNull(providers.getProviderById(id))
        uiState = AITutorUiState(activeProvider = original, providers = listOf(original))
    }

    @After
    fun teardown() { database.close() }

    @Test
    fun addingAnotherProviderDoesNotOverwriteTheFirstAndRepeatedSaveUpdatesTheNewOne() {
        showDialog()
        compose.onNodeWithText("新增").performClick()
        edit("名称", "Second provider")
        edit("Base URL", "https://second.example.invalid/v1")
        edit("API Key", "qa-second-key")
        edit("默认模型", "second-model")
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(5_000) { completedSaves == 1 || failure != null }
        assertNull(failure)
        assertEquals(0L, lastSavedForm!!.id)
        runBlocking {
            assertEquals(2, providers.countProviders())
            assertEquals(original.copy(isActive = false), providers.getProviderById(original.id))
            val active = requireNotNull(providers.getActiveProvider())
            assertEquals("Second provider", active.name)
            assertEquals("qa-second-key", active.apiKey)
            val encrypted = requireNotNull(database.aiProviderDao().getProviderById(active.id)).apiKeyEncrypted
            assertFalse(encrypted.contains("qa-second-key"))
        }
        compose.waitForIdle()
        compose.onNodeWithText("保存").performClick()
        compose.waitUntil(5_000) { completedSaves == 2 || failure != null }
        assertNull(failure)
        assertTrue(lastSavedForm!!.id > 0L)
        runBlocking { assertEquals(2, providers.countProviders()) }
    }

    @Test
    fun changingServicePresetNeverSendsTheOldApiKeyToTheNewEndpoint() {
        showDialog()
        scrollTo("服务商预设")
        compose.onNodeWithText("选择常用服务商预设").performClick()
        compose.onNodeWithText("DeepSeek").performClick()
        scrollTo("测试连接")
        compose.onNodeWithText("测试连接").performClick()
        compose.waitUntil(5_000) { service.posts == 1 || failure != null }
        assertNull(failure)
        assertFalse(service.requests.isEmpty())
        service.requests.forEach { (url, headers) ->
            assertTrue(url.startsWith("https://api.deepseek.com/"))
            assertNull("Credentials from another endpoint must not be forwarded", headers["Authorization"])
        }
        runBlocking { assertEquals(original, providers.getProviderById(original.id)) }
    }

    @Test
    fun changingAnApiKeyWithTheSameSuffixInvalidatesItsFetchedModels() {
        showDialog()
        scrollTo("获取模型")
        compose.onNodeWithText("获取模型").performClick()
        edit("API Key", "qa-key-different-abcdef")
        scrollTo("默认模型")
        compose.onNodeWithText("常用").performClick()
        compose.onNodeWithText("private-old-model").assertDoesNotExist()
    }

    private fun showDialog() {
        compose.setContent {
            val scope = rememberCoroutineScope()
            CorrectionNotebookTheme {
                ProviderDialog(uiState,
                    onDismiss = {},
                    onSave = { form ->
                        lastSavedForm = form
                        uiState = uiState.copy(isProviderBusy = true)
                        scope.launch {
                            try {
                                check(ai.validateProviderForm(form) == null)
                                providers.saveProvider(ai.normalizeProviderRecord(form))
                                uiState = uiState.copy(activeProvider = providers.getActiveProvider(),
                                    providers = providers.observeProviders().first(), isProviderBusy = false)
                                completedSaves++
                            } catch (error: Throwable) { failure = error }
                        }
                    },
                    onFetchModels = { uiState = uiState.copy(fetchedModels = listOf(AiModelOption("private-old-model"))) },
                    onTestProvider = { form ->
                        scope.launch {
                            try { ai.testProvider(form).getOrThrow() }
                            catch (error: Throwable) { failure = error }
                        }
                    },
                    onClearProviderStatus = {}, onActivate = {}, onDelete = {})
            }
        }
    }

    private fun scrollTo(label: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(label))
    }

    private fun edit(label: String, value: String) {
        scrollTo(label)
        compose.onNode(hasSetTextAction().and(hasText(label))).performTextReplacement(value)
    }

    private class RecordingService : AIApiService {
        val requests = CopyOnWriteArrayList<Pair<String, Map<String, String>>>()
        @Volatile var posts = 0
        override suspend fun getJson(url: String, headers: Map<String, String>): Response<ResponseBody> {
            requests += url to headers
            return Response.success("""{"data":[]}""".toResponseBody())
        }
        override suspend fun postJson(url: String, headers: Map<String, String>, request: RequestBody): Response<ResponseBody> {
            requests += url to headers
            posts++
            return Response.success("""{"choices":[{"message":{"content":"OK"}}]}""".toResponseBody())
        }
    }
}
