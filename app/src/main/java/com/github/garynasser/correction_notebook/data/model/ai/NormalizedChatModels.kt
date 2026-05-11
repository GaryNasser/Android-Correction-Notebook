package com.github.garynasser.correction_notebook.data.model.ai

data class AIProviderConfig(
    val id: Long? = null,
    val name: String,
    val type: AIProviderType,
    val baseUrl: String,
    val apiKey: String,
    val defaultModel: String,
    val customHeaders: Map<String, String> = emptyMap(),
    val temperature: Double? = null,
    val maxTokens: Int? = null,
    val contextMessageLimit: Int = 12
)

data class NormalizedChatMessage(
    val role: String,
    val content: String
)

data class NormalizedChatRequest(
    val model: String,
    val messages: List<NormalizedChatMessage>,
    val systemPrompt: String? = null,
    val memorySummary: String? = null,
    val temperature: Double? = null,
    val maxTokens: Int? = null
)

data class NormalizedChatResponse(
    val content: String,
    val providerMessageId: String? = null,
    val finishReason: String? = null
)
