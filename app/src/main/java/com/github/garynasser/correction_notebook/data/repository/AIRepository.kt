package com.github.garynasser.correction_notebook.data.repository

import com.github.garynasser.correction_notebook.data.local.AISettingsManager
import com.github.garynasser.correction_notebook.data.model.ai.ChatCompletionRequest
import com.github.garynasser.correction_notebook.data.model.ai.ChatMessage
import com.github.garynasser.correction_notebook.data.model.ai.ChatCompletionResponse
import com.github.garynasser.correction_notebook.data.remote.api.AIApiService
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIRepository @Inject constructor(
    private val aiSettingsManager: AISettingsManager,
    private val aiApiService: AIApiService
) {
    suspend fun sendMessage(messages: List<ChatMessage>): Result<String> {
        return try {
            val apiKey = aiSettingsManager.apiKey.first()
            val baseUrl = aiSettingsManager.apiBaseUrl.first()
            val model = aiSettingsManager.aiModel.first()

            if (apiKey.isBlank()) {
                return Result.failure(Exception("API Key 未配置"))
            }

            val request = ChatCompletionRequest(
                model = model,
                messages = messages
            )

            val url = "$baseUrl/chat/completions"
            val response = aiApiService.chatCompletion(
                url = url,
                authorization = "Bearer $apiKey",
                request = request
            )

            if (response.error != null) {
                return Result.failure(Exception(response.error.message ?: "API 错误"))
            }

            val content = response.choices?.firstOrNull()?.message?.content
                ?: return Result.failure(Exception("无有效响应"))

            Result.success(content)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun isConfigured(): Boolean {
        return false // Will be checked in async manner
    }
}
