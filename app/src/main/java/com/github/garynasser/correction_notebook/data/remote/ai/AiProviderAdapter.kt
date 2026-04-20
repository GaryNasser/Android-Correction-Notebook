package com.github.garynasser.correction_notebook.data.remote.ai

import com.github.garynasser.correction_notebook.data.model.ai.AIProviderConfig
import com.github.garynasser.correction_notebook.data.model.ai.AIProviderType
import com.github.garynasser.correction_notebook.data.model.ai.NormalizedChatRequest
import com.github.garynasser.correction_notebook.data.model.ai.NormalizedChatResponse

interface AiProviderAdapter {
    fun supports(type: AIProviderType): Boolean

    suspend fun send(
        config: AIProviderConfig,
        request: NormalizedChatRequest
    ): Result<NormalizedChatResponse>
}
