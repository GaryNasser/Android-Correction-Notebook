package com.github.garynasser.correction_notebook.data.model.ai

data class AiModelOption(
    val id: String,
    val displayName: String = id
)

data class AiProviderCheckResult(
    val success: Boolean,
    val message: String,
    val models: List<AiModelOption> = emptyList()
)
