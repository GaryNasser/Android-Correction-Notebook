package com.github.garynasser.correction_notebook.data.model.ai

import com.github.garynasser.correction_notebook.data.local.AISettingsManager

data class AiProviderForm(
    val id: Long = 0L,
    val name: String = "我的 AI Provider",
    val type: AIProviderType = AIProviderType.OPENAI_COMPATIBLE,
    val baseUrl: String = AISettingsManager.DEFAULT_API_URL,
    val apiKey: String = "",
    val model: String = AISettingsManager.DEFAULT_MODEL,
    val customHeaders: String = "{}",
    val temperature: String = "",
    val maxTokens: String = "",
    val contextMessageLimit: String = "12",
    val isActive: Boolean = true
)
