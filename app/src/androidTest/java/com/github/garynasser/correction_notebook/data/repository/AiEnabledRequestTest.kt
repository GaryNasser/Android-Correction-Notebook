package com.github.garynasser.correction_notebook.data.repository

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.local.AISettingsManager
import com.github.garynasser.correction_notebook.data.local.ai.AiCredentialCipher
import com.github.garynasser.correction_notebook.data.local.ai.AiDatabase
import com.github.garynasser.correction_notebook.data.model.ai.AiProviderForm
import com.github.garynasser.correction_notebook.data.model.ai.ChatMessage
import com.github.garynasser.correction_notebook.data.model.ai.NormalizedChatMessage
import com.github.garynasser.correction_notebook.data.remote.ai.AnthropicCompatibleAdapter
import com.github.garynasser.correction_notebook.data.remote.ai.OpenAiCompatibleAdapter
import com.github.garynasser.correction_notebook.data.remote.api.AIApiService
import com.google.gson.Gson
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Response

class AiEnabledRequestTest {
    private lateinit var database: AiDatabase
    private lateinit var settings: AISettingsManager
    private lateinit var repository: AIRepository
    private lateinit var service: RecordingApiService
    private var previouslyEnabled = false
    private val form = AiProviderForm(name = "Test", baseUrl = "https://example.invalid/v1", model = "test-model")

    @Before
    fun setup() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        settings = AISettingsManager(context)
        previouslyEnabled = settings.aiEnabled.first()
        settings.setAiEnabled(false)
        database = Room.inMemoryDatabaseBuilder(context, AiDatabase::class.java).build()
        val providers = ProviderRepository(database.aiProviderDao(), AiCredentialCipher(context))
        val gson = Gson()
        service = RecordingApiService()
        repository = AIRepository(
            settings,
            providers,
            OpenAiCompatibleAdapter(service, gson),
            AnthropicCompatibleAdapter(service, gson),
            gson
        )
        providers.saveProvider(repository.normalizeProviderRecord(form))
        Unit
    }

    @After
    fun teardown() = runBlocking {
        settings.setAiEnabled(previouslyEnabled)
        database.close()
    }

    @Test
    fun disabledAiDoesNotSendEitherChatRequestFormat() = runBlocking {
        val legacy = repository.sendMessage(listOf(ChatMessage("user", "Hello")))
        val normalized = repository.sendChat(listOf(NormalizedChatMessage("user", "Hello")))
        assertEquals("AI 功能已关闭，请在设置中启用", legacy.exceptionOrNull()?.message)
        assertEquals("AI 功能已关闭，请在设置中启用", normalized.exceptionOrNull()?.message)
        assertEquals(0, service.posts)
    }

    @Test
    fun disablingAiAfterSuccessfulChatStopsTheNextRequest() = runBlocking {
        settings.setAiEnabled(true)
        assertEquals("OK", repository.sendChat(listOf(NormalizedChatMessage("user", "Hello"))).getOrThrow())
        assertEquals(1, service.posts)
        settings.setAiEnabled(false)
        assertTrue(repository.sendChat(listOf(NormalizedChatMessage("user", "Hello again"))).isFailure)
        assertEquals(1, service.posts)
    }

    @Test
    fun explicitProviderConnectionTestStillWorksWhileAiIsDisabled() = runBlocking {
        assertTrue(repository.testProvider(form).getOrThrow().success)
        assertEquals(1, service.gets)
        assertEquals(1, service.posts)
        assertEquals(false, settings.aiEnabled.first())
    }

    private class RecordingApiService : AIApiService {
        var gets = 0
        var posts = 0

        override suspend fun getJson(url: String, headers: Map<String, String>): Response<ResponseBody> {
            gets++
            return Response.success("""{"data":[{"id":"test-model"}]}""".toResponseBody())
        }

        override suspend fun postJson(
            url: String,
            headers: Map<String, String>,
            request: RequestBody
        ): Response<ResponseBody> {
            posts++
            return Response.success("""{"choices":[{"message":{"content":"OK"}}]}""".toResponseBody())
        }
    }
}
