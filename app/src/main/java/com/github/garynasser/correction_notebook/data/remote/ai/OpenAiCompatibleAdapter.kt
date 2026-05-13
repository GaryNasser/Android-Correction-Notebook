package com.github.garynasser.correction_notebook.data.remote.ai

import com.github.garynasser.correction_notebook.data.model.ai.AIProviderConfig
import com.github.garynasser.correction_notebook.data.model.ai.AIProviderType
import com.github.garynasser.correction_notebook.data.model.ai.ChatCompletionRequest
import com.github.garynasser.correction_notebook.data.model.ai.ChatMessage
import com.github.garynasser.correction_notebook.data.model.ai.NormalizedChatRequest
import com.github.garynasser.correction_notebook.data.model.ai.NormalizedChatResponse
import com.github.garynasser.correction_notebook.data.remote.api.AIApiService
import com.google.gson.Gson
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class OpenAiCompatibleAdapter @Inject constructor(
    private val aiApiService: AIApiService,
    private val gson: Gson
) : AiProviderAdapter {
    override fun supports(type: AIProviderType): Boolean = type == AIProviderType.OPENAI_COMPATIBLE

    override suspend fun send(
        config: AIProviderConfig,
        request: NormalizedChatRequest
    ): Result<NormalizedChatResponse> {
        return runCatching {
            val payload = ChatCompletionRequest(
                model = request.model,
                messages = buildMessages(request),
                temperature = request.temperature,
                max_tokens = request.maxTokens
            )
            val response = aiApiService.postJson(
                url = resolveUrl(config.baseUrl, "chat/completions"),
                headers = buildHeaders(config),
                request = gson.toJson(payload).toRequestBody(JSON_MEDIA_TYPE)
            )

            if (!response.isSuccessful) {
                throw IllegalStateException(
                    errorMapper.mapHttpError(response.code(), response.errorBody()?.string())
                )
            }

            val body = response.body()?.string().orEmpty()
            val parsed = AiResponseParser.requireJsonObject(body, "OpenAI 兼容")
            parsed.get("error")?.takeIf { it.isJsonObject }?.asJsonObject?.let { error ->
                throw IllegalStateException(
                    error.get("message")?.takeIf { it.isJsonPrimitive }?.asString ?: "OpenAI 兼容接口返回错误"
                )
            }

            val firstChoice = parsed.get("choices")
                ?.takeIf { it.isJsonArray }
                ?.asJsonArray
                ?.firstOrNull()
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
            val messageObject = firstChoice?.get("message")
                ?.takeIf { it.isJsonObject }
                ?.asJsonObject
            val message = AiResponseParser.readTextContent(messageObject?.get("content"))
                .ifBlank { AiResponseParser.readTextContent(firstChoice?.get("text")) }
                .takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("模型返回了空响应")

            NormalizedChatResponse(
                content = message,
                providerMessageId = parsed.get("id")?.takeIf { it.isJsonPrimitive }?.asString,
                finishReason = firstChoice?.get("finish_reason")?.takeIf { it.isJsonPrimitive }?.asString
            )
        }.recoverCatching { throwable ->
            throw IllegalStateException(errorMapper.mapThrowable(throwable), throwable)
        }
    }

    suspend fun listModels(config: AIProviderConfig): Result<List<String>> {
        return runCatching {
            val response = aiApiService.getJson(
                url = resolveUrl(config.baseUrl, "models"),
                headers = buildHeaders(config)
            )
            val body = if (response.isSuccessful) {
                response.body()?.string().orEmpty()
            } else {
                throw IllegalStateException(
                    errorMapper.mapHttpError(response.code(), response.errorBody()?.string())
                )
            }
            AiResponseParser.extractModelIds(body, "OpenAI 兼容")
        }.recoverCatching { throwable ->
            throw IllegalStateException(errorMapper.mapThrowable(throwable), throwable)
        }
    }

    private fun buildMessages(request: NormalizedChatRequest): List<ChatMessage> {
        return buildList {
            request.systemPrompt?.takeIf { it.isNotBlank() }?.let {
                add(ChatMessage(role = "system", content = it))
            }
            request.memorySummary?.takeIf { it.isNotBlank() }?.let {
                add(ChatMessage(role = "system", content = "用户长期记忆摘要：\n$it"))
            }
            addAll(request.messages.map { ChatMessage(role = it.role, content = it.content) })
        }
    }

    private fun buildHeaders(config: AIProviderConfig): Map<String, String> {
        return buildMap {
            if (config.apiKey.isNotBlank()) {
                put("Authorization", "Bearer ${config.apiKey}")
            }
            put("Content-Type", "application/json")
            putAll(config.customHeaders)
        }
    }

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private val errorMapper = AiErrorMapper
    }
}

internal fun resolveUrl(baseUrl: String, pathSuffix: String): String {
    val rawBase = baseUrl.trim().trimEnd('/')
    val baseWithoutQuery = rawBase.substringBefore("?")
    if (baseWithoutQuery.endsWith(pathSuffix)) return rawBase

    val normalizedBase = baseWithoutQuery
        .removeSuffix("/chat/completions")
        .removeSuffix("/completions")
        .removeSuffix("/models")
    return when {
        normalizedBase.endsWith(pathSuffix) -> normalizedBase
        normalizedBase.endsWith("/v1") -> "$normalizedBase/$pathSuffix"
        else -> "$normalizedBase/$pathSuffix"
    }
}
