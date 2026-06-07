package com.github.garynasser.correction_notebook.data.remote.ai

import com.github.garynasser.correction_notebook.data.model.ai.AIProviderConfig
import com.github.garynasser.correction_notebook.data.model.ai.AIProviderType
import com.github.garynasser.correction_notebook.data.model.ai.AnthropicMessage
import com.github.garynasser.correction_notebook.data.model.ai.AnthropicMessageRequest
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
            val parsed = AiResponseParser.requireJsonObject(body, "Anthropic 兼容")
            parsed.get("error")?.takeIf { it.isJsonObject }?.asJsonObject?.let { error ->
                throw IllegalStateException(
                    error.get("message")?.takeIf { it.isJsonPrimitive }?.asString ?: "Anthropic 兼容接口返回错误"
                )
            }

            val content = parsed.get("content")
                ?.takeIf { it.isJsonArray }
                ?.asJsonArray
                ?.mapNotNull { block ->
                    if (block.isJsonObject) {
                        val obj = block.asJsonObject
                        if (obj.get("type")?.takeIf { it.isJsonPrimitive }?.asString == "text") {
                            obj.get("text")?.takeIf { it.isJsonPrimitive }?.asString
                        } else {
                            AiResponseParser.readTextContent(obj.get("text")).takeIf { it.isNotBlank() }
                        }
                    } else {
                        AiResponseParser.readTextContent(block).takeIf { it.isNotBlank() }
                    }
                }
                ?.joinToString("\n")
                ?.trim()
                .orEmpty()

            if (content.isBlank()) {
                throw IllegalStateException("模型返回了空响应")
            }

            NormalizedChatResponse(
                content = content,
                providerMessageId = parsed.get("id")?.takeIf { it.isJsonPrimitive }?.asString,
                finishReason = parsed.get("stop_reason")?.takeIf { it.isJsonPrimitive }?.asString
            )
        }.recoverCatching { throwable ->
            throw IllegalStateException(errorMapper.mapThrowable(throwable), throwable)
        }
    }

    suspend fun listModels(config: AIProviderConfig): Result<List<String>> {
        return runCatching {
            var lastError: Throwable? = null
            resolveAnthropicModelUrls(config.baseUrl).forEach { url ->
                val models = runCatching {
                    val response = aiApiService.getJson(
                        url = url,
                        headers = buildHeaders(config)
                    )
                    val body = if (response.isSuccessful) {
                        response.body()?.string().orEmpty()
                    } else {
                        throw IllegalStateException(
                            errorMapper.mapHttpError(response.code(), response.errorBody()?.string())
                        )
                    }
                    AiResponseParser.extractModelIds(body, "Anthropic 兼容")
                }.onFailure { throwable ->
                    lastError = throwable
                }.getOrNull()

                if (!models.isNullOrEmpty()) {
                    return@runCatching models
                }
            }

            if (shouldUseKnownModelFallback(config.baseUrl, lastError)) {
                return@runCatching knownProviderModels(config.baseUrl).orEmpty()
            }

            throw IllegalStateException(
                lastError?.message ?: "Anthropic 兼容接口未返回可用模型列表，请检查 Base URL、API Key 和服务商是否开放模型列表接口"
            )
        }.recoverCatching { throwable ->
            val fallbackModels = knownProviderModels(config.baseUrl).takeIf {
                shouldUseKnownModelFallback(config.baseUrl, throwable)
            }
            if (fallbackModels != null) {
                fallbackModels
            } else {
                throw IllegalStateException(
                    errorMapper.mapThrowable(throwable),
                    throwable
                )
            }
        }
    }

    private fun buildHeaders(config: AIProviderConfig): Map<String, String> {
        return buildMap {
            if (config.apiKey.isNotBlank()) {
                put("x-api-key", config.apiKey)
                put("Authorization", "Bearer ${config.apiKey}")
            }
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

    companion object {
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private const val DEFAULT_ANTHROPIC_VERSION = "2023-06-01"
        private const val DEFAULT_MAX_TOKENS = 1024
        private val errorMapper = AiErrorMapper
    }
}

internal fun resolveAnthropicModelUrls(baseUrl: String): List<String> {
    val rawBase = baseUrl.trim().trimEnd('/')
    val baseWithoutQuery = rawBase.substringBefore("?")
    val urls = linkedSetOf(resolveAnthropicUrl(baseWithoutQuery, "models"))
    val endpointBase = stripKnownAnthropicEndpointSuffixes(baseWithoutQuery)

    deepSeekProviderRoots(endpointBase).forEach { providerRoot ->
        urls += resolveAnthropicUrl(providerRoot, "models")
    }

    return urls.toList()
}

private fun stripKnownAnthropicEndpointSuffixes(baseUrl: String): String {
    return baseUrl
        .removeSuffix("/v1/messages")
        .removeSuffix("/messages")
        .removeSuffix("/v1/models")
        .removeSuffix("/models")
}

private fun deepSeekProviderRoots(baseUrl: String): List<String> {
    val lowerBase = baseUrl.lowercase()
    if ("api.deepseek.com" !in lowerBase) return emptyList()

    val roots = linkedSetOf<String>()
    when {
        baseUrl.endsWith("/v1/anthropic") -> {
            val v1Root = baseUrl.removeSuffix("/anthropic")
            roots += v1Root
            roots += v1Root.removeSuffix("/v1")
        }
        baseUrl.endsWith("/anthropic/v1") -> {
            roots += baseUrl.removeSuffix("/anthropic/v1")
        }
        baseUrl.endsWith("/anthropic") -> {
            roots += baseUrl.removeSuffix("/anthropic")
        }
        baseUrl.endsWith("/v1") -> {
            roots += baseUrl.removeSuffix("/v1")
        }
    }
    return roots.filter { it != baseUrl }
}

private fun resolveAnthropicUrl(baseUrl: String, suffix: String = "messages"): String {
    val rawBase = baseUrl.trim().trimEnd('/')
    val baseWithoutQuery = rawBase.substringBefore("?")
    if (baseWithoutQuery.endsWith(suffix)) return rawBase

    val normalizedBase = baseWithoutQuery
        .removeSuffix("/messages")
        .removeSuffix("/models")
    return when {
        normalizedBase.endsWith(suffix) -> normalizedBase
        normalizedBase.endsWith("/v1") -> "$normalizedBase/$suffix"
        else -> "$normalizedBase/$suffix"
    }
}

private fun knownProviderModels(baseUrl: String): List<String>? {
    val normalized = baseUrl.lowercase()
    return when {
        "api.deepseek.com" in normalized -> listOf(
            "deepseek-v4-flash",
            "deepseek-v4-pro",
            "deepseek-chat",
            "deepseek-reasoner"
        )
        else -> null
    }
}

private fun shouldUseKnownModelFallback(baseUrl: String, throwable: Throwable?): Boolean {
    if (knownProviderModels(baseUrl).isNullOrEmpty()) return false
    val message = throwable?.message.orEmpty().lowercase()
    val credentialOrQuotaError = listOf(
        "api key",
        "auth",
        "authentication",
        "unauthorized",
        "permission",
        "权限",
        "无效",
        "失效",
        "额度",
        "频繁",
        "quota",
        "rate limit"
    ).any { it in message }
    return !credentialOrQuotaError
}
