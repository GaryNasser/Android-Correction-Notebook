package com.github.garynasser.correction_notebook.data.remote.ai

import com.github.garynasser.correction_notebook.data.model.ai.AIProviderConfig
import com.github.garynasser.correction_notebook.data.model.ai.AIProviderType
import com.github.garynasser.correction_notebook.data.model.ai.AnthropicMessage
import com.github.garynasser.correction_notebook.data.model.ai.AnthropicMessageRequest
import com.github.garynasser.correction_notebook.data.model.ai.AnthropicMessageResponse
import com.github.garynasser.correction_notebook.data.model.ai.NormalizedChatRequest
import com.github.garynasser.correction_notebook.data.model.ai.NormalizedChatResponse
import com.github.garynasser.correction_notebook.data.remote.api.AIApiService
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class AnthropicCompatibleAdapter @Inject constructor(
    private val aiApiService: AIApiService,
    private val gson: Gson
) : AiProviderAdapter {
    override fun supports(type: AIProviderType): Boolean = type == AIProviderType.ANTHROPIC_COMPATIBLE

    override suspend fun send(
        config: AIProviderConfig,
        request: NormalizedChatRequest
    ): Result<NormalizedChatResponse> {
        return runCatching {
            val payload = AnthropicMessageRequest(
                model = request.model,
                max_tokens = request.maxTokens ?: config.maxTokens ?: DEFAULT_MAX_TOKENS,
                temperature = request.temperature ?: config.temperature,
                system = buildSystemPrompt(request),
                messages = request.messages
                    .filter { it.role != "system" }
                    .map { AnthropicMessage(role = normalizeRole(it.role), content = it.content) }
            )
            val response = aiApiService.postJson(
                url = resolveAnthropicUrl(config.baseUrl),
                headers = buildHeaders(config),
                request = gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE)
            )

            if (!response.isSuccessful) {
                throw IllegalStateException(
                    errorMapper.mapHttpError(response.code(), response.errorBody()?.string())
                )
            }

            val body = response.body()?.string().orEmpty()
            val parsed = gson.fromJson(body, AnthropicMessageResponse::class.java)
            if (parsed.error != null) {
                throw IllegalStateException(parsed.error.message ?: "Anthropic 兼容接口返回错误")
            }

            val content = parsed.content
                ?.filter { it.type == "text" }
                ?.joinToString("\n") { it.text.orEmpty() }
                ?.trim()
                .orEmpty()

            if (content.isBlank()) {
                throw IllegalStateException("模型返回了空响应")
            }

            NormalizedChatResponse(
                content = content,
                providerMessageId = parsed.id,
                finishReason = parsed.stop_reason
            )
        }.recoverCatching { throwable ->
            throw IllegalStateException(errorMapper.mapThrowable(throwable))
        }
    }

    private fun buildHeaders(config: AIProviderConfig): Map<String, String> {
        return buildMap {
            put("x-api-key", config.apiKey)
            put("anthropic-version", DEFAULT_ANTHROPIC_VERSION)
            put("Content-Type", "application/json")
            putAll(config.customHeaders)
        }
    }

    private fun buildSystemPrompt(request: NormalizedChatRequest): String? {
        val parts = listOfNotNull(
            request.systemPrompt?.takeIf { it.isNotBlank() },
            request.memorySummary?.takeIf { it.isNotBlank() }?.let { "用户长期记忆摘要：\n$it" }
        )
        return parts.takeIf { it.isNotEmpty() }?.joinToString("\n\n")
    }

    private fun normalizeRole(role: String): String {
        return when (role) {
            "assistant" -> "assistant"
            else -> "user"
        }
    }

    private fun resolveAnthropicUrl(baseUrl: String): String {
        val normalizedBase = baseUrl.trim().trimEnd('/')
        return when {
            normalizedBase.endsWith("messages") -> normalizedBase
            normalizedBase.endsWith("/v1") -> "$normalizedBase/messages"
            else -> "$normalizedBase/messages"
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val DEFAULT_ANTHROPIC_VERSION = "2023-06-01"
        private const val DEFAULT_MAX_TOKENS = 1024
        private val errorMapper = AiErrorMapper
    }
}
