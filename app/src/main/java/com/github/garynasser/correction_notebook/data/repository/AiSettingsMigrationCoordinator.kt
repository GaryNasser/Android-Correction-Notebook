package com.github.garynasser.correction_notebook.data.repository

import com.github.garynasser.correction_notebook.data.local.AISettingsManager
import com.github.garynasser.correction_notebook.data.model.ai.AIProviderType
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiSettingsMigrationCoordinator @Inject constructor(
    private val aiSettingsManager: AISettingsManager,
    private val providerRepository: ProviderRepository
) {
    suspend fun migrateIfNeeded() {
        if (providerRepository.countProviders() > 0) return

        val apiKey = aiSettingsManager.apiKey.first().trim()
        if (apiKey.isBlank()) return

        val baseUrl = aiSettingsManager.apiBaseUrl.first().trim()
            .ifBlank { AISettingsManager.DEFAULT_API_URL }
        val model = aiSettingsManager.aiModel.first().trim()
            .ifBlank { AISettingsManager.DEFAULT_MODEL }
        val enabled = aiSettingsManager.aiEnabled.first()

        providerRepository.saveProvider(
            ProviderRecord(
                id = 0L,
                name = buildDefaultProviderName(baseUrl),
                type = AIProviderType.OPENAI_COMPATIBLE,
                baseUrl = baseUrl,
                apiKey = apiKey,
                defaultModel = model,
                customHeadersJson = "{}",
                temperature = null,
                maxTokens = null,
                contextMessageLimit = 12,
                isActive = enabled,
                createdAt = System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    private fun buildDefaultProviderName(baseUrl: String): String {
        return when {
            "anthropic" in baseUrl.lowercase() -> "默认 Anthropic Provider"
            "openai" in baseUrl.lowercase() -> "默认 OpenAI Provider"
            else -> "默认 AI Provider"
        }
    }
}
