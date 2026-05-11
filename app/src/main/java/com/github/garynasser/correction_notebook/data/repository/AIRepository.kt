package com.github.garynasser.correction_notebook.data.repository

import com.github.garynasser.correction_notebook.data.local.AISettingsManager
import com.github.garynasser.correction_notebook.data.model.ai.AIProviderConfig
import com.github.garynasser.correction_notebook.data.model.ai.AIProviderType
import com.github.garynasser.correction_notebook.data.model.ai.ChatCompletionRequest
import com.github.garynasser.correction_notebook.data.model.ai.ChatMessage
import com.github.garynasser.correction_notebook.data.model.ai.NormalizedChatMessage
import com.github.garynasser.correction_notebook.data.model.ai.NormalizedChatRequest
import com.github.garynasser.correction_notebook.data.remote.ai.AnthropicCompatibleAdapter
import com.github.garynasser.correction_notebook.data.remote.ai.OpenAiCompatibleAdapter
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIRepository @Inject constructor(
    private val aiSettingsManager: AISettingsManager,
    private val providerRepository: ProviderRepository,
    private val openAiCompatibleAdapter: OpenAiCompatibleAdapter,
    private val anthropicCompatibleAdapter: AnthropicCompatibleAdapter,
    private val gson: Gson
) {
    suspend fun sendMessage(messages: List<ChatMessage>): Result<String> {
        val providerConfig = loadProviderConfig()
            ?: return Result.failure(Exception("API Key 未配置"))

        val request = NormalizedChatRequest(
            model = providerConfig.defaultModel,
            messages = messages.map { NormalizedChatMessage(role = it.role, content = it.content) }
        )

        return sendNormalized(providerConfig, request).map { it.content }
    }

    suspend fun sendChat(
        messages: List<NormalizedChatMessage>,
        systemPrompt: String? = null,
        memorySummary: String? = null,
        modelOverride: String? = null
    ): Result<String> {
        val providerConfig = loadProviderConfig()
            ?: return Result.failure(Exception("API Key 未配置"))
        val request = NormalizedChatRequest(
            model = modelOverride?.takeIf { it.isNotBlank() } ?: providerConfig.defaultModel,
            messages = messages,
            systemPrompt = systemPrompt,
            memorySummary = memorySummary,
            temperature = providerConfig.temperature,
            maxTokens = providerConfig.maxTokens
        )
        return sendNormalized(providerConfig, request).map { it.content }
    }

    suspend fun activeProviderConfig(): AIProviderConfig? = loadProviderConfig()

    private suspend fun sendNormalized(
        providerConfig: AIProviderConfig,
        request: NormalizedChatRequest
    ): Result<com.github.garynasser.correction_notebook.data.model.ai.NormalizedChatResponse> {
        val result = when (providerConfig.type) {
            AIProviderType.OPENAI_COMPATIBLE -> openAiCompatibleAdapter.send(providerConfig, request)
            AIProviderType.ANTHROPIC_COMPATIBLE -> anthropicCompatibleAdapter.send(providerConfig, request)
        }

        return result
    }

    fun isConfigured(): Boolean {
        return false // Will be checked in async manner
    }

    private suspend fun loadProviderConfig(): AIProviderConfig? {
        providerRepository.getActiveProvider()?.let { provider ->
            return AIProviderConfig(
                id = provider.id,
                name = provider.name,
                type = provider.type,
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                defaultModel = provider.defaultModel,
                customHeaders = parseHeaders(provider.customHeadersJson),
                temperature = provider.temperature,
                maxTokens = provider.maxTokens,
                contextMessageLimit = provider.contextMessageLimit
            )
        }

        val apiKey = aiSettingsManager.apiKey.first().trim()
        if (apiKey.isBlank()) return null

        val baseUrl = aiSettingsManager.apiBaseUrl.first().trim()
        val model = aiSettingsManager.aiModel.first().trim().ifBlank { AISettingsManager.DEFAULT_MODEL }

        return AIProviderConfig(
            name = "Legacy AI Provider",
            type = inferProviderType(baseUrl),
            baseUrl = baseUrl.ifBlank { AISettingsManager.DEFAULT_API_URL },
            apiKey = apiKey,
            defaultModel = model,
            contextMessageLimit = 12
        )
    }

    private fun parseHeaders(raw: String): Map<String, String> {
        val trimmed = raw.trim()
        if (trimmed.isBlank() || trimmed == "{}") return emptyMap()

        return runCatching {
            val json = gson.fromJson(trimmed, JsonObject::class.java)
            json.entrySet().associate { it.key to it.value.asString }
        }.getOrElse {
            trimmed.lineSequence()
                .mapNotNull { line ->
                    val separator = if (":" in line) ":" else "="
                    val parts = line.split(separator, limit = 2)
                    if (parts.size == 2 && parts[0].isNotBlank()) {
                        parts[0].trim() to parts[1].trim()
                    } else {
                        null
                    }
                }
                .toMap()
        }
    }

    private fun inferProviderType(baseUrl: String): AIProviderType {
        val normalized = baseUrl.lowercase()
        return if ("anthropic" in normalized || normalized.endsWith("/messages")) {
            AIProviderType.ANTHROPIC_COMPATIBLE
        } else {
            AIProviderType.OPENAI_COMPATIBLE
        }
    }
}
