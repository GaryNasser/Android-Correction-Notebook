package com.github.garynasser.correction_notebook.ui.screens.aitutor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.garynasser.correction_notebook.data.local.AISettingsManager
import com.github.garynasser.correction_notebook.data.local.ai.ChatMessageEntity
import com.github.garynasser.correction_notebook.data.local.ai.ChatSessionEntity
import com.github.garynasser.correction_notebook.data.local.ai.UserMemoryEntity
import com.github.garynasser.correction_notebook.data.model.ai.AiProviderForm
import com.github.garynasser.correction_notebook.data.model.ai.AIProviderType
import com.github.garynasser.correction_notebook.data.model.ai.NormalizedChatMessage
import com.github.garynasser.correction_notebook.data.repository.ChatSessionRepository
import com.github.garynasser.correction_notebook.data.repository.MemoryRepository
import com.github.garynasser.correction_notebook.data.repository.ProviderRecord
import com.github.garynasser.correction_notebook.data.repository.ProviderRepository
import com.github.garynasser.correction_notebook.domain.usecase.AiStudyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiMessage(
    val id: Long = 0L,
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

data class AITutorUiState(
    val activeProvider: ProviderRecord? = null,
    val providers: List<ProviderRecord> = emptyList(),
    val sessions: List<ChatSessionEntity> = emptyList(),
    val selectedSessionId: Long? = null,
    val messages: List<ChatUiMessage> = emptyList(),
    val memories: List<UserMemoryEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isKnowledgeMode: Boolean = false,
    val error: String? = null
) {
    val isConfigured: Boolean get() = activeProvider != null
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class AITutorViewModel @Inject constructor(
    private val aiStudyUseCase: AiStudyUseCase,
    private val providerRepository: ProviderRepository,
    private val chatSessionRepository: ChatSessionRepository,
    private val memoryRepository: MemoryRepository,
    private val aiSettingsManager: AISettingsManager
) : ViewModel() {

    private val selectedSessionId = MutableStateFlow<Long?>(null)
    private val loading = MutableStateFlow(false)
    private val knowledgeMode = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AITutorUiState> = combine(
        providerRepository.observeActiveProvider(),
        providerRepository.observeProviders(),
        chatSessionRepository.observeAllSessions(),
        selectedSessionId,
        selectedSessionId.flatMapLatest { id ->
            if (id == null) flowOf(emptyList()) else chatSessionRepository.observeMessagesForSession(id)
        },
        memoryRepository.observeMemories(),
        loading,
        knowledgeMode,
        error
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val activeProvider = values[0] as ProviderRecord?
        val providers = values[1] as List<ProviderRecord>
        val sessions = values[2] as List<ChatSessionEntity>
        val selectedId = values[3] as Long?
        val messages = values[4] as List<ChatMessageEntity>
        val memories = values[5] as List<UserMemoryEntity>
        val isLoading = values[6] as Boolean
        val isKnowledgeMode = values[7] as Boolean
        val currentError = values[8] as String?

        AITutorUiState(
            activeProvider = activeProvider,
            providers = providers,
            sessions = sessions,
            selectedSessionId = selectedId,
            messages = messages.map {
                ChatUiMessage(
                    id = it.id,
                    content = it.content,
                    isUser = it.role == "user",
                    timestamp = it.createdAt
                )
            },
            memories = memories,
            isLoading = isLoading,
            isKnowledgeMode = isKnowledgeMode,
            error = currentError
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AITutorUiState())

    val aiEnabled = aiSettingsManager.aiEnabled.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        false
    )

    init {
        viewModelScope.launch {
            providerRepository.observeActiveProvider().collect { provider ->
                if (provider != null && selectedSessionId.value == null) {
                    val latest = chatSessionRepository.getLatestSessionForProvider(provider.id)
                    selectedSessionId.value = latest?.id ?: chatSessionRepository.createSession(
                        title = "新的学习对话",
                        providerId = provider.id,
                        model = provider.defaultModel
                    )
                }
            }
        }
    }

    fun sendMessage(content: String) {
        val text = content.trim()
        if (text.isBlank()) return
        viewModelScope.launch {
            val provider = providerRepository.getActiveProvider()
            if (provider == null) {
                error.value = "请先配置 AI Provider"
                return@launch
            }
            val sessionId = selectedSessionId.value ?: chatSessionRepository.createSession(
                title = titleFrom(text),
                providerId = provider.id,
                model = provider.defaultModel
            ).also { selectedSessionId.value = it }

            loading.value = true
            error.value = null
            chatSessionRepository.saveMessage(sessionId, "user", text)

            val recent = chatSessionRepository.getRecentMessages(sessionId, provider.contextMessageLimit)
                .asReversed()
                .map { NormalizedChatMessage(role = it.role, content = it.content) }
            val result = if (knowledgeMode.value) {
                aiStudyUseCase.askKnowledgeBase(text)
            } else {
                aiStudyUseCase.chat(recent)
            }
            result.onSuccess { answer ->
                chatSessionRepository.saveMessage(sessionId, "assistant", answer)
            }.onFailure { throwable ->
                error.value = throwable.message ?: "AI 请求失败"
            }
            loading.value = false
        }
    }

    fun newSession() {
        viewModelScope.launch {
            val provider = providerRepository.getActiveProvider() ?: run {
                error.value = "请先配置 AI Provider"
                return@launch
            }
            selectedSessionId.value = chatSessionRepository.createSession(
                title = "新的学习对话",
                providerId = provider.id,
                model = provider.defaultModel
            )
        }
    }

    fun selectSession(sessionId: Long) {
        selectedSessionId.value = sessionId
    }

    fun clearMessages() {
        selectedSessionId.value?.let { sessionId ->
            viewModelScope.launch { chatSessionRepository.clearSessionMessages(sessionId) }
        }
    }

    fun deleteCurrentSession() {
        selectedSessionId.value?.let { sessionId ->
            viewModelScope.launch {
                chatSessionRepository.deleteSession(sessionId)
                selectedSessionId.value = chatSessionRepository.getLatestSessionForProvider(
                    providerRepository.getActiveProvider()?.id ?: return@launch
                )?.id
            }
        }
    }

    fun renameCurrentSession(title: String) {
        selectedSessionId.value?.let { sessionId ->
            viewModelScope.launch { chatSessionRepository.renameSession(sessionId, title) }
        }
    }

    fun setKnowledgeMode(enabled: Boolean) {
        knowledgeMode.value = enabled
    }

    fun saveProvider(form: AiProviderForm) {
        viewModelScope.launch {
            providerRepository.saveProvider(
                ProviderRecord(
                    id = form.id,
                    name = form.name.trim().ifBlank { "AI Provider" },
                    type = form.type,
                    baseUrl = normalizeBaseUrl(form.baseUrl, form.type),
                    apiKey = form.apiKey.trim(),
                    defaultModel = form.model.trim().ifBlank { AISettingsManager.DEFAULT_MODEL },
                    customHeadersJson = normalizeHeaders(form.customHeaders),
                    temperature = form.temperature.toDoubleOrNull()?.coerceIn(0.0, 2.0),
                    maxTokens = form.maxTokens.toIntOrNull()?.coerceAtLeast(1),
                    contextMessageLimit = form.contextMessageLimit.toIntOrNull()?.coerceIn(1, 60) ?: 12,
                    isActive = form.isActive,
                    createdAt = 0L,
                    updatedAt = 0L
                )
            )
            aiSettingsManager.setAiEnabled(true)
        }
    }

    fun activateProvider(providerId: Long) {
        viewModelScope.launch {
            providerRepository.activateProvider(providerId)
            selectedSessionId.value = null
        }
    }

    fun deleteProvider(providerId: Long) {
        viewModelScope.launch { providerRepository.deleteProvider(providerId) }
    }

    fun saveMemory(category: String, content: String) {
        if (content.isBlank()) return
        viewModelScope.launch {
            aiStudyUseCase.saveMemory(category, content).onFailure {
                error.value = it.message ?: "保存记忆失败"
            }
        }
    }

    fun deleteMemory(memoryId: Long) {
        viewModelScope.launch { memoryRepository.deleteMemory(memoryId) }
    }

    fun clearError() {
        error.value = null
    }

    private fun titleFrom(text: String): String =
        text.take(18).ifBlank { "新的学习对话" }

    private fun normalizeBaseUrl(raw: String, type: AIProviderType): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isNotBlank()) return trimmed
        return when (type) {
            AIProviderType.OPENAI_COMPATIBLE -> AISettingsManager.DEFAULT_API_URL
            AIProviderType.ANTHROPIC_COMPATIBLE -> "https://api.anthropic.com/v1"
        }
    }

    private fun normalizeHeaders(raw: String): String = raw.trim().ifBlank { "{}" }
}
