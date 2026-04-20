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
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIRepository @Inject constructor(
    private val aiSettingsManager: AISettingsManager,
    private val providerRepository: ProviderRepository,
    private val openAiCompatibleAdapter: OpenAiCompatibleAdapter,
    private val anthropicCompatibleAdapter: AnthropicCompatibleAdapter
) {
    suspend fun sendMessage(messages: List<ChatMessage>): Result<String> {
        val providerConfig = loadProviderConfig()
            ?: return Result.failure(Exception("API Key 未配置"))

        val request = NormalizedChatRequest(
            model = providerConfig.defaultModel,
            messages = messages.map { NormalizedChatMessage(role = it.role, content = it.content) }
        )

        val result = when (providerConfig.type) {
            AIProviderType.OPENAI_COMPATIBLE -> openAiCompatibleAdapter.send(providerConfig, request)
            AIProviderType.ANTHROPIC_COMPATIBLE -> anthropicCompatibleAdapter.send(providerConfig, request)
        }

        return result.map { it.content }
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
                customHeaders = emptyMap()
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
            defaultModel = model
        )
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
