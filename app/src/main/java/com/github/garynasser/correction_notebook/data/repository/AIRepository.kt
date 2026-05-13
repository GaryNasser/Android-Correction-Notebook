package com.github.garynasser.correction_notebook.data.repository

import com.github.garynasser.correction_notebook.data.local.AISettingsManager
import com.github.garynasser.correction_notebook.data.model.ai.AIProviderConfig
import com.github.garynasser.correction_notebook.data.model.ai.AiModelOption
import com.github.garynasser.correction_notebook.data.model.ai.AiProviderCheckResult
import com.github.garynasser.correction_notebook.data.model.ai.AiProviderForm
import com.github.garynasser.correction_notebook.data.model.ai.AIProviderType
import com.github.garynasser.correction_notebook.data.model.ai.ChatMessage
import com.github.garynasser.correction_notebook.data.model.ai.NormalizedChatMessage
import com.github.garynasser.correction_notebook.data.model.ai.NormalizedChatRequest
import com.github.garynasser.correction_notebook.data.remote.ai.AnthropicCompatibleAdapter
import com.github.garynasser.correction_notebook.data.remote.ai.OpenAiCompatibleAdapter
import com.google.gson.Gson
import com.google.gson.JsonParser
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
            messages = compactMessages(messages),
            systemPrompt = systemPrompt,
            memorySummary = memorySummary?.take(MAX_MEMORY_CHARS),
            temperature = providerConfig.temperature,
            maxTokens = providerConfig.maxTokens
        )
        return sendNormalized(providerConfig, request).map { it.content }
    }

    suspend fun activeProviderConfig(): AIProviderConfig? = loadProviderConfig()

    suspend fun listModels(config: AIProviderConfig): Result<List<AiModelOption>> {
        return when (config.type) {
            AIProviderType.OPENAI_COMPATIBLE -> openAiCompatibleAdapter.listModels(config)
            AIProviderType.ANTHROPIC_COMPATIBLE -> anthropicCompatibleAdapter.listModels(config)
        }.map { ids -> ids.map { AiModelOption(it) } }
    }

    suspend fun listModels(form: AiProviderForm): Result<List<AiModelOption>> {
        return listModels(form.toProviderConfig())
    }

    suspend fun testProvider(form: AiProviderForm): Result<AiProviderCheckResult> {
        val config = form.toProviderConfig()
        val validationError = validateProviderForm(form)
        if (validationError != null) {
            return Result.failure(IllegalStateException(validationError))
        }

        return runCatching {
            val models = listModels(config).getOrDefault(emptyList())
            val probeModel = form.model.trim().ifBlank { models.firstOrNull()?.id ?: config.defaultModel }
            val modelIds = models.map { it.id }.toSet()
            if (models.isNotEmpty() && probeModel !in modelIds) {
                throw IllegalStateException("模型列表获取成功，但当前模型“$probeModel”不在服务端返回的模型列表中。请点“常用”或“获取模型”后选择列表中的模型，或确认代理允许手动模型名。")
            }

            val response = sendTestMessage(config, probeModel, compact = false).recoverCatching { firstError ->
                val message = firstError.message.orEmpty()
                if ("500" in message || "Internal Server Error" in message) {
                    sendTestMessage(config, probeModel, compact = true).getOrElse {
                        val protocolHint = diagnoseAlternateProtocol(config, probeModel)
                        throw IllegalStateException(
                            "模型列表获取成功，但聊天接口返回 500。已用最小请求重试仍失败。$protocolHint 当前测试地址：${chatEndpointPreview(config)}；模型：$probeModel。原始错误：$message"
                        )
                    }
                } else {
                    throw firstError
                }
            }.getOrThrow()
            AiProviderCheckResult(
                success = true,
                message = "连接成功：${response.content.take(40)}",
                models = models
            )
        }
    }

    private suspend fun sendTestMessage(
        config: AIProviderConfig,
        model: String,
        compact: Boolean
    ): Result<com.github.garynasser.correction_notebook.data.model.ai.NormalizedChatResponse> {
        return sendNormalized(
            config,
            NormalizedChatRequest(
                model = model,
                messages = listOf(
                    NormalizedChatMessage(
                        "user",
                        if (compact) "Reply with OK." else "请只回复：OK"
                    )
                ),
                systemPrompt = if (compact) null else "你是接口连通性测试助手。只回复 OK。",
                maxTokens = if (compact) null else config.maxTokens,
                temperature = if (compact) null else 0.0
            )
        )
    }

    private suspend fun diagnoseAlternateProtocol(config: AIProviderConfig, model: String): String {
        val alternateType = when (config.type) {
            AIProviderType.OPENAI_COMPATIBLE -> AIProviderType.ANTHROPIC_COMPATIBLE
            AIProviderType.ANTHROPIC_COMPATIBLE -> AIProviderType.OPENAI_COMPATIBLE
        }
        val alternateConfig = config.copy(type = alternateType)
        val alternateResult = sendTestMessage(alternateConfig, model, compact = true)
        return if (alternateResult.isSuccess) {
            "同一 Base URL 用“${alternateType.displayName()}”测试可以连通，当前很可能是协议类型选错。请切换协议后再保存。"
        } else {
            "当前协议对应的聊天端点不可用，通常是代理后端异常、Base URL 指向模型列表服务但未转发聊天接口，或服务商要求额外 Headers。"
        }
    }

    private fun chatEndpointPreview(config: AIProviderConfig): String {
        return when (config.type) {
            AIProviderType.OPENAI_COMPATIBLE -> com.github.garynasser.correction_notebook.data.remote.ai.resolveUrl(config.baseUrl, "chat/completions")
            AIProviderType.ANTHROPIC_COMPATIBLE -> {
                val rawBase = config.baseUrl.trim().trimEnd('/')
                val baseWithoutQuery = rawBase.substringBefore("?")
                if (baseWithoutQuery.endsWith("messages")) rawBase else "${baseWithoutQuery.removeSuffix("/messages").removeSuffix("/models")}/messages"
            }
        }
    }

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

    private fun compactMessages(messages: List<NormalizedChatMessage>): List<NormalizedChatMessage> {
        var remaining = MAX_MESSAGE_INPUT_CHARS
        return messages
            .asReversed()
            .mapNotNull { message ->
                if (remaining <= 0) return@mapNotNull null
                val content = message.content.trim()
                if (content.isBlank()) return@mapNotNull null
                val clipped = if (content.length > remaining) {
                    content.takeLast(remaining).let { "（前文已自动截断）\n$it" }
                } else {
                    content
                }
                remaining -= clipped.length
                message.copy(content = clipped)
            }
            .asReversed()
    }

    fun isConfigured(): Boolean {
        return false // Will be checked in async manner
    }

    fun buildProviderConfig(form: AiProviderForm): AIProviderConfig = form.toProviderConfig()

    fun validateProviderForm(form: AiProviderForm): String? {
        validateHeadersText(form.customHeaders)?.let { return it }
        return validateConfig(form.toProviderConfig())
    }

    fun providerConfigWarning(form: AiProviderForm): String? =
        warningForConfig(form.toProviderConfig())

    fun normalizeProviderRecord(form: AiProviderForm): ProviderRecord =
        AiProviderConfigMapper.toRecord(form, gson)

    private suspend fun loadProviderConfig(): AIProviderConfig? {
        providerRepository.getActiveProvider()?.let { provider ->
            return AIProviderConfig(
                id = provider.id,
                name = provider.name,
                type = provider.type,
                baseUrl = provider.baseUrl,
                apiKey = provider.apiKey,
                defaultModel = provider.defaultModel,
                customHeaders = AiProviderConfigMapper.parseHeaders(provider.customHeadersJson, gson),
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

    private fun AiProviderForm.toProviderConfig(): AIProviderConfig {
        return AiProviderConfigMapper.toConfig(this, gson)
    }

    private fun validateConfig(config: AIProviderConfig): String? {
        if (config.baseUrl.isBlank()) return "Base URL 不能为空"
        if (!config.baseUrl.startsWith("http://") && !config.baseUrl.startsWith("https://")) {
            return "Base URL 必须以 http:// 或 https:// 开头"
        }
        if (config.defaultModel.isBlank()) return "模型名称不能为空"
        if (config.type == AIProviderType.ANTHROPIC_COMPATIBLE && config.apiKey.isBlank()) {
            return "Anthropic 兼容接口需要 API Key"
        }
        return null
    }

    private fun warningForConfig(config: AIProviderConfig): String? {
        val lowerUrl = config.baseUrl.lowercase()
        return when {
            config.type == AIProviderType.ANTHROPIC_COMPATIBLE &&
                ("claude.ai" in lowerUrl || "console.anthropic.com" in lowerUrl || "anthropic.com" in lowerUrl && "api.anthropic.com" !in lowerUrl) ->
                "提醒：这个地址看起来像 Anthropic/Claude 网页地址，不一定是 API 地址；如果你的服务商就是这样转发，可以忽略。"
            config.type == AIProviderType.ANTHROPIC_COMPATIBLE &&
                ("openrouter.ai" in lowerUrl || "deepseek" in lowerUrl || "dashscope" in lowerUrl || "ollama" in lowerUrl || "openai" in lowerUrl) ->
                "提醒：这个地址看起来更像 OpenAI 兼容接口；如果你的服务商用 Anthropic 协议挂在这个地址下，可以忽略。"
            config.type == AIProviderType.OPENAI_COMPATIBLE && "anthropic" in lowerUrl ->
                "提醒：这个地址看起来像 Anthropic 接口；如果你的服务商提供 OpenAI 兼容转发，可以忽略。"
            else -> null
        }
    }

    private fun validateHeadersText(raw: String): String? {
        val trimmed = raw.trim()
        if (trimmed.isBlank() || trimmed == "{}") return null
        if (trimmed.startsWith("{")) {
            val element = runCatching { JsonParser.parseString(trimmed) }.getOrNull()
                ?: return "自定义 Headers 不是有效 JSON；也可以改为每行 Key: Value"
            if (!element.isJsonObject) return "自定义 Headers JSON 必须是对象，例如 {\"HTTP-Referer\":\"...\"}"
            val invalidEntry = element.asJsonObject.entrySet().firstOrNull { !it.value.isJsonPrimitive }
            if (invalidEntry != null) return "自定义 Headers 的值必须是字符串或数字：${invalidEntry.key}"
        } else {
            val invalidLine = trimmed.lineSequence()
                .firstOrNull { line -> line.isNotBlank() && ":" !in line && "=" !in line }
            if (invalidLine != null) return "自定义 Headers 每行需要写成 Key: Value 或 Key=Value：$invalidLine"
        }
        return null
    }

    private fun normalizeBaseUrl(raw: String, type: AIProviderType): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isNotBlank()) return trimmed
        return when (type) {
            AIProviderType.OPENAI_COMPATIBLE -> AISettingsManager.DEFAULT_API_URL
            AIProviderType.ANTHROPIC_COMPATIBLE -> "https://api.anthropic.com/v1"
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

    companion object {
        private const val MAX_MESSAGE_INPUT_CHARS = 16_000
        private const val MAX_MEMORY_CHARS = 2_000
    }
}

private fun AIProviderType.displayName(): String =
    when (this) {
        AIProviderType.OPENAI_COMPATIBLE -> "OpenAI 兼容"
        AIProviderType.ANTHROPIC_COMPATIBLE -> "Anthropic 兼容"
    }
