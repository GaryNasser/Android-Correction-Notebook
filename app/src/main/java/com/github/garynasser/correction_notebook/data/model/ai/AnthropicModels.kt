package com.github.garynasser.correction_notebook.data.model.ai

data class AnthropicMessageRequest(
    val model: String,
    val max_tokens: Int = 1024,
    val system: String? = null,
    val messages: List<AnthropicMessage>
)

data class AnthropicMessage(
    val role: String,
    val content: String
)

data class AnthropicMessageResponse(
    val id: String?,
    val type: String?,
    val role: String?,
    val content: List<AnthropicContentBlock>?,
    val stop_reason: String?,
    val error: AnthropicError?
)

data class AnthropicContentBlock(
    val type: String?,
    val text: String?
)

data class AnthropicError(
    val type: String?,
    val message: String?
)
