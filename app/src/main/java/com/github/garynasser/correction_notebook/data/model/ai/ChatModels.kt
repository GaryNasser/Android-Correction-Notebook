package com.github.garynasser.correction_notebook.data.model.ai

data class ChatMessage(
    val role: String,
    val content: String
)

data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val stream: Boolean = false
)

data class ChatCompletionResponse(
    val id: String?,
    val model: String?,
    val choices: List<Choice>?,
    val error: AIError?
)

data class Choice(
    val index: Int?,
    val message: ResponseMessage?,
    val finish_reason: String?
)

data class ResponseMessage(
    val role: String?,
    val content: String?
)

data class AIError(
    val message: String?,
    val type: String?,
    val code: String?
)
