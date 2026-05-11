package com.github.garynasser.correction_notebook.data.remote.ai

import com.github.garynasser.correction_notebook.data.model.ai.AIProviderConfig
import com.github.garynasser.correction_notebook.data.model.ai.AIProviderType
import com.github.garynasser.correction_notebook.data.model.ai.ChatCompletionRequest
import com.github.garynasser.correction_notebook.data.model.ai.ChatCompletionResponse
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
            val parsed = gson.fromJson(body, ChatCompletionResponse::class.java)
            if (parsed.error != null) {
                throw IllegalStateException(parsed.error.message ?: "OpenAI 兼容接口返回错误")
            }

            val message = parsed.choices?.firstOrNull()?.message?.content?.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("模型返回了空响应")

            NormalizedChatResponse(
                content = message,
                providerMessageId = parsed.id,
                finishReason = parsed.choices.firstOrNull()?.finish_reason
            )
        }.recoverCatching { throwable ->
            throw IllegalStateException(errorMapper.mapThrowable(throwable))
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
            put("Authorization", "Bearer ${config.apiKey}")
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
    val normalizedBase = baseUrl.trim().trimEnd('/')
    return when {
        normalizedBase.endsWith(pathSuffix) -> normalizedBase
        normalizedBase.endsWith("/v1") -> "$normalizedBase/$pathSuffix"
        else -> "$normalizedBase/$pathSuffix"
    }
}
