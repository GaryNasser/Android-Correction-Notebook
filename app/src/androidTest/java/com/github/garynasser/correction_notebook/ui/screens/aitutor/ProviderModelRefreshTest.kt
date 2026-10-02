package com.github.garynasser.correction_notebook.ui.screens.aitutor

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.local.AISettingsManager
import com.github.garynasser.correction_notebook.data.local.CredentialManager
import com.github.garynasser.correction_notebook.data.local.TokenManager
import com.github.garynasser.correction_notebook.data.local.ai.AiCredentialCipher
import com.github.garynasser.correction_notebook.data.local.ai.AiDatabase
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseDatabase
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseFileStorage
import com.github.garynasser.correction_notebook.data.model.ai.AiModelOption
import com.github.garynasser.correction_notebook.data.model.ai.AiProviderForm
import com.github.garynasser.correction_notebook.data.remote.ai.AnthropicCompatibleAdapter
import com.github.garynasser.correction_notebook.data.remote.ai.OpenAiCompatibleAdapter
import com.github.garynasser.correction_notebook.data.remote.api.AIApiService
import com.github.garynasser.correction_notebook.data.remote.api.VideoApiService
import com.github.garynasser.correction_notebook.data.remote.cas.BitCasClient
import com.github.garynasser.correction_notebook.data.remote.manager.VideoRemoteManager
import com.github.garynasser.correction_notebook.data.repository.*
import com.github.garynasser.correction_notebook.domain.usecase.AiStudyUseCase
import com.github.garynasser.correction_notebook.ui.screens.profile.ProfileViewModel
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import com.google.gson.Gson
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import retrofit2.Response
import retrofit2.Retrofit

class ProviderModelRefreshTest {
    @get:Rule val compose = createComposeRule()

    @Test fun profileRefreshDropsOldModelsWhilePendingAndAfterFailure() = checkRefresh(tutor = false)
    @Test fun tutorRefreshDropsOldModelsWhilePendingAndAfterFailure() = checkRefresh(tutor = true)
    @Test fun profileConnectionWithNoModelsDoesNotRetainOldModels() = checkConnection(tutor = false)
    @Test fun tutorConnectionWithNoModelsDoesNotRetainOldModels() = checkConnection(tutor = true)
    @Test fun profileDialogFailedRefreshNeverShowsModelsFromPreviousCredentials() = checkDialogFailure(tutor = false)
    @Test fun tutorDialogFailedRefreshNeverShowsModelsFromPreviousCredentials() = checkDialogFailure(tutor = true)
    @Test fun profileDialogConnectionMakesItsFetchedModelsSelectable() = checkDialogConnection(tutor = false)
    @Test fun tutorDialogConnectionMakesItsFetchedModelsSelectable() = checkDialogConnection(tutor = true)
    @Test fun profileCanFetchAndChooseModelsBeforeFillingTheDefaultModel() = checkEmptyModel(tutor = false)
    @Test fun tutorCanFetchAndChooseModelsBeforeFillingTheDefaultModel() = checkEmptyModel(tutor = true)

    private fun checkRefresh(tutor: Boolean) = withFixture(tutor) { fixture ->
        fixture.fetch(first)
        await { fixture.state().fetchedModels == listOf(AiModelOption(first.model)) && !fixture.state().isProviderBusy }
        val gate = ListGate()
        fixture.service.gate = gate
        fixture.fetch(second)
        gate.entered.await()
        await { fixture.state().isProviderBusy }
        assertTrue("Old models must disappear before the next request completes", fixture.state().fetchedModels.isEmpty())
        gate.release.complete(Unit)
        await { !fixture.state().isProviderBusy }
        assertTrue(fixture.state().providerStatusMessage.orEmpty().isNotBlank())
        assertTrue("A failed refresh must not restore old models", fixture.state().fetchedModels.isEmpty())
        fixture.service.gate = null
        fixture.service.model = second.model
        fixture.fetch(second)
        await { !fixture.state().isProviderBusy && fixture.state().fetchedModels == listOf(AiModelOption(second.model)) }
    }

    private fun checkConnection(tutor: Boolean) = withFixture(tutor) { fixture ->
        fixture.fetch(first)
        await { !fixture.state().isProviderBusy && fixture.state().fetchedModels.isNotEmpty() }
        fixture.service.model = null
        fixture.check(second)
        await { !fixture.state().isProviderBusy && fixture.state().providerStatusMessage.orEmpty().startsWith("连接成功") }
        assertEquals(emptyList<AiModelOption>(), fixture.state().fetchedModels)
        fixture.service.model = second.model
        fixture.check(second)
        await { !fixture.state().isProviderBusy && fixture.state().fetchedModels == listOf(AiModelOption(second.model)) }
    }

    private fun checkDialogFailure(tutor: Boolean) = withFixture(tutor) { fixture ->
        showDialog(fixture)
        click("获取模型")
        compose.waitUntil(5_000) { !fixture.state().isProviderBusy && fixture.state().fetchedModels.isNotEmpty() }
        edit("API Key", second.apiKey)
        val gate = ListGate()
        fixture.service.gate = gate
        click("获取模型")
        gate.entered.await()
        gate.release.complete(Unit)
        compose.waitUntil(5_000) { !fixture.state().isProviderBusy && fixture.state().providerStatusMessage.orEmpty().isNotBlank() }
        openModels()
        compose.onNode(hasText(first.model).and(hasSetTextAction().not())).assertDoesNotExist()
    }

    private fun checkDialogConnection(tutor: Boolean) = withFixture(tutor) { fixture ->
        showDialog(fixture)
        edit("Base URL", second.baseUrl)
        edit("API Key", second.apiKey)
        edit("默认模型", second.model)
        fixture.service.model = second.model
        click("测试连接")
        compose.waitUntil(5_000) { !fixture.state().isProviderBusy && fixture.state().fetchedModels == listOf(AiModelOption(second.model)) }
        openModels()
        compose.onNode(hasText(second.model).and(hasSetTextAction().not())).assertIsDisplayed()
    }

    private fun checkEmptyModel(tutor: Boolean) = withFixture(tutor) { fixture ->
        showDialog(fixture)
        edit("默认模型", "")
        compose.onNodeWithText("保存").assertIsNotEnabled()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("测试连接"))
        compose.onNodeWithText("测试连接").assertIsNotEnabled()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("获取模型"))
        compose.onNodeWithText("获取模型").assertIsEnabled().performClick()
        compose.waitUntil(5_000) { !fixture.state().isProviderBusy && fixture.state().fetchedModels.isNotEmpty() }
        openModels()
        compose.onNode(hasText(first.model).and(hasSetTextAction().not())).performClick()
        compose.onNode(hasSetTextAction().and(hasText(first.model))).assertIsDisplayed()
        compose.onNodeWithText("保存").assertIsEnabled()
    }

    private fun showDialog(fixture: Fixture) {
        compose.setContent {
            val state = fixture.tutor?.uiState?.collectAsState()?.value ?: run {
                val profile = requireNotNull(fixture.profile)
                val models by profile.fetchedModels.collectAsState()
                val busy by profile.isProviderBusy.collectAsState()
                val message by profile.providerStatusMessage.collectAsState()
                AITutorUiState(fetchedModels = models, isProviderBusy = busy, providerStatusMessage = message)
            }
            CorrectionNotebookTheme {
                ProviderDialog(state.copy(activeProvider = fixture.original, providers = listOf(fixture.original)),
                    onDismiss = {}, onSave = {}, onFetchModels = fixture::fetchOnMain,
                    onTestProvider = fixture::checkOnMain, onClearProviderStatus = fixture::clearStatus,
                    onActivate = {}, onDelete = {})
            }
        }
    }

    private fun click(scrollLabel: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(scrollLabel))
        compose.onNodeWithText(scrollLabel).performClick()
    }

    private fun openModels() {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("默认模型"))
        compose.onNodeWithContentDescription("选择模型").performClick()
    }

    private fun edit(label: String, value: String) {
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(label))
        compose.onNode(hasSetTextAction().and(hasText(label))).performTextReplacement(value)
    }

    private suspend fun await(predicate: () -> Boolean) = withTimeout(5_000) {
        while (!predicate()) delay(10)
    }

    private fun withFixture(tutor: Boolean, test: suspend (Fixture) -> Unit) = runBlocking {
        val fixture = Fixture()
        var observer: kotlinx.coroutines.Job? = null
        try {
            withTimeout(20_000) {
                fixture.original = fixture.providers.getProviderById(
                    fixture.providers.saveProvider(fixture.ai.normalizeProviderRecord(first)))!!
                withContext(Dispatchers.Main) { fixture.create(tutor) }
                observer = fixture.tutor?.let { vm -> launch { vm.uiState.collect {} } }
                await { fixture.state().activeProvider != null || !tutor }
                try {
                    test(fixture)
                } finally {
                    observer?.cancel()
                }
            }
        } finally {
            fixture.service.gate?.release?.complete(Unit)
            observer?.cancel()
            withContext(Dispatchers.Main) { fixture.store.clear() }
            fixture.database.close()
            fixture.knowledge.close()
            fixture.network.dispatcher.executorService.shutdownNow()
            fixture.network.connectionPool.evictAll()
        }
    }

    private class Fixture {
        private val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AiDatabase::class.java).build()
        val knowledge = Room.inMemoryDatabaseBuilder(context, KnowledgeBaseDatabase::class.java).build()
        val providers = ProviderRepository(database.aiProviderDao(), AiCredentialCipher(context))
        val service = ModelService()
        private val settings = AISettingsManager(context)
        private val gson = Gson()
        val ai = AIRepository(settings, providers, OpenAiCompatibleAdapter(service, gson), AnthropicCompatibleAdapter(service, gson), gson)
        val store = ViewModelStore()
        val network = OkHttpClient.Builder().addInterceptor { throw AssertionError("Unexpected school request") }.build()
        lateinit var original: ProviderRecord
        var profile: ProfileViewModel? = null
        var tutor: AITutorViewModel? = null

        fun create(useTutor: Boolean) {
            if (useTutor) {
                val memory = MemoryRepository(database.userMemoryDao())
                val dao = knowledge.knowledgeBaseDao()
                val useCase = AiStudyUseCase(ai, KnowledgeBaseAiRepository(dao), memory,
                    TodoRepository(context), ScheduleRepository(context), StudySessionRepository(context),
                    CourseLearningRepository(context), KnowledgeBaseRepository(dao, KnowledgeBaseFileStorage(context), context))
                tutor = AITutorViewModel(useCase, ai, providers,
                    ChatSessionRepository(database.chatSessionDao(), database.chatMessageDao()), memory, settings)
                store.put("provider-tutor", tutor!!)
            } else {
                val tokens = TokenManager(context)
                val credentials = CredentialManager(context)
                val auth = AuthStateManager()
                val yanhe = YanheRepository(tokens, credentials, BitCasClient(network))
                val api = Retrofit.Builder().baseUrl("https://unused.invalid/").client(network).build().create(VideoApiService::class.java)
                profile = ProfileViewModel(auth, settings, providers, ai, yanhe,
                    VideoRepository(VideoRemoteManager(api, tokens, credentials, auth, yanhe)))
                store.put("provider-profile", profile!!)
            }
        }

        fun state(): AITutorUiState = tutor?.uiState?.value ?: AITutorUiState(
            fetchedModels = profile!!.fetchedModels.value, isProviderBusy = profile!!.isProviderBusy.value,
            providerStatusMessage = profile!!.providerStatusMessage.value)
        suspend fun fetch(form: AiProviderForm) = withContext(Dispatchers.Main) { fetchOnMain(form) }
        suspend fun check(form: AiProviderForm) = withContext(Dispatchers.Main) { checkOnMain(form) }
        fun fetchOnMain(form: AiProviderForm) { if (tutor != null) tutor!!.fetchModels(form) else profile!!.fetchModels(form) }
        fun checkOnMain(form: AiProviderForm) { if (tutor != null) tutor!!.testProvider(form) else profile!!.testProvider(form) }
        fun clearStatus() { if (tutor != null) tutor!!.clearProviderStatus() else profile!!.clearProviderStatus() }
    }

    private class ListGate {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
    }

    private class ModelService : AIApiService {
        @Volatile var model: String? = first.model
        @Volatile var gate: ListGate? = null
        override suspend fun getJson(url: String, headers: Map<String, String>): Response<ResponseBody> {
            gate?.let {
                it.entered.complete(Unit)
                it.release.await()
                return Response.error(503, "temporarily unavailable".toResponseBody())
            }
            val body = model?.let { """{"data":[{"id":"$it"}]}""" } ?: """{"data":[]}"""
            return Response.success(body.toResponseBody())
        }
        override suspend fun postJson(url: String, headers: Map<String, String>, request: RequestBody): Response<ResponseBody> =
            Response.success("""{"choices":[{"message":{"content":"OK"}}]}""".toResponseBody())
    }

    companion object {
        private val first = AiProviderForm(name = "First", baseUrl = "https://first.invalid/v1", apiKey = "qa-first", model = "private-first-model")
        private val second = AiProviderForm(name = "Second", baseUrl = "https://second.invalid/v1", apiKey = "qa-second", model = "private-second-model")
    }
}
