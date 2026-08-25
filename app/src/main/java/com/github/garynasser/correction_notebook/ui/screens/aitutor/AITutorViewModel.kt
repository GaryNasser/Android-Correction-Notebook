package com.github.garynasser.correction_notebook.ui.screens.aitutor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.garynasser.correction_notebook.data.local.AISettingsManager
import com.github.garynasser.correction_notebook.data.local.ai.ChatMessageEntity
import com.github.garynasser.correction_notebook.data.local.ai.ChatSessionEntity
import com.github.garynasser.correction_notebook.data.local.ai.UserMemoryEntity
import com.github.garynasser.correction_notebook.data.model.ai.AiProviderForm
import com.github.garynasser.correction_notebook.data.model.ai.AiModelOption
import com.github.garynasser.correction_notebook.data.model.ai.NormalizedChatMessage
import com.github.garynasser.correction_notebook.data.repository.AIRepository
import com.github.garynasser.correction_notebook.data.repository.ChatSessionRepository
import com.github.garynasser.correction_notebook.data.repository.MemoryRepository
import com.github.garynasser.correction_notebook.data.repository.ProviderRecord
import com.github.garynasser.correction_notebook.data.repository.ProviderRepository
import com.github.garynasser.correction_notebook.domain.usecase.AiStudyUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
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
    val fetchedModels: List<AiModelOption> = emptyList(),
    val isProviderBusy: Boolean = false,
    val isChatActionBusy: Boolean = false,
    val isMemoryBusy: Boolean = false,
    val providerStatusMessage: String? = null,
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
    private val aiRepository: AIRepository,
    private val providerRepository: ProviderRepository,
    private val chatSessionRepository: ChatSessionRepository,
    private val memoryRepository: MemoryRepository,
    private val aiSettingsManager: AISettingsManager
) : ViewModel() {

    private val selectedSessionId = MutableStateFlow<Long?>(null)
    private val loading = MutableStateFlow(false)
    private val providerBusy = MutableStateFlow(false)
    private val chatActionBusy = MutableStateFlow(false)
    private val memoryBusy = MutableStateFlow(false)
    private val providerStatus = MutableStateFlow<String?>(null)
    private val fetchedModels = MutableStateFlow<List<AiModelOption>>(emptyList())
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
        fetchedModels,
        providerBusy,
        chatActionBusy,
        memoryBusy,
        providerStatus,
        loading,
        knowledgeMode,
        error
    ) { values ->
        val activeProvider = values.typed<ProviderRecord?>(0)
        val providers = values.typed<List<ProviderRecord>>(1)
        val sessions = values.typed<List<ChatSessionEntity>>(2)
        val selectedId = values.typed<Long?>(3)
        val messages = values.typed<List<ChatMessageEntity>>(4)
        val memories = values.typed<List<UserMemoryEntity>>(5)
        val currentFetchedModels = values.typed<List<AiModelOption>>(6)
        val isProviderBusy = values.typed<Boolean>(7)
        val isChatActionBusy = values.typed<Boolean>(8)
        val isMemoryBusy = values.typed<Boolean>(9)
        val currentProviderStatus = values.typed<String?>(10)
        val isLoading = values.typed<Boolean>(11)
        val isKnowledgeMode = values.typed<Boolean>(12)
        val currentError = values.typed<String?>(13)

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
            fetchedModels = currentFetchedModels,
            isProviderBusy = isProviderBusy,
            isChatActionBusy = isChatActionBusy,
            isMemoryBusy = isMemoryBusy,
            providerStatusMessage = currentProviderStatus,
            isLoading = isLoading,
            isKnowledgeMode = isKnowledgeMode,
            error = currentError
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AITutorUiState())

    @Suppress("UNCHECKED_CAST")
    private fun <T> Array<Any?>.typed(index: Int): T = this[index] as T

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
        if (loading.value) return
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
            try {
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error.value = e.message ?: "AI 请求失败"
            } finally {
                loading.value = false
            }
        }
    }

    fun newSession() {
        runChatAction("新建对话失败") {
            val provider = providerRepository.getActiveProvider() ?: run {
                error.value = "请先配置 AI Provider"
                return@runChatAction
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
        val sessionId = selectedSessionId.value ?: return
        runChatAction("清空对话失败") {
            chatSessionRepository.clearSessionMessages(sessionId)
        }
    }

    fun deleteCurrentSession() {
        val sessionId = selectedSessionId.value ?: return
        runChatAction("删除对话失败") {
            chatSessionRepository.deleteSession(sessionId)
            val activeProvider = providerRepository.getActiveProvider()
            selectedSessionId.value = activeProvider?.let {
                chatSessionRepository.getLatestSessionForProvider(it.id)?.id
            }
        }
    }

    fun renameCurrentSession(title: String) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isBlank()) return
        val sessionId = selectedSessionId.value ?: return
        runChatAction("重命名对话失败") {
            chatSessionRepository.renameSession(sessionId, trimmedTitle)
        }
    }

    fun setKnowledgeMode(enabled: Boolean) {
        knowledgeMode.value = enabled
    }

    fun saveProvider(form: AiProviderForm) {
        runProviderAction("保存 Provider 失败") {
            aiRepository.validateProviderForm(form)?.let { message ->
                providerStatus.value = message
                return@runProviderAction
            }
            providerRepository.saveProvider(aiRepository.normalizeProviderRecord(form))
            aiSettingsManager.setAiEnabled(true)
            providerStatus.value = aiRepository.providerConfigWarning(form)
                ?.let { "Provider 已保存。$it" }
                ?: "Provider 已保存"
        }
    }

    fun fetchModels(form: AiProviderForm) {
        if (providerBusy.value) return
        providerBusy.value = true
        viewModelScope.launch {
            try {
                providerStatus.value = null
                aiRepository.listModels(form)
                    .onSuccess { models ->
                        fetchedModels.value = models
                        providerStatus.value = if (models.isEmpty()) {
                            "接口可访问，但没有返回模型列表；你仍然可以手动填写模型名"
                        } else {
                            "已获取 ${models.size} 个模型"
                        }
                    }
                    .onFailure { throwable ->
                        if (throwable is CancellationException) throw throwable
                        providerStatus.value = throwable.message ?: "获取模型列表失败"
                    }
            } finally {
                providerBusy.value = false
            }
        }
    }

    fun testProvider(form: AiProviderForm) {
        if (providerBusy.value) return
        providerBusy.value = true
        viewModelScope.launch {
            try {
                providerStatus.value = null
                aiRepository.testProvider(form)
                    .onSuccess { result ->
                        if (result.models.isNotEmpty()) fetchedModels.value = result.models
                        providerStatus.value = result.message
                    }
                    .onFailure { throwable ->
                        if (throwable is CancellationException) throw throwable
                        providerStatus.value = throwable.message ?: "连接测试失败"
                    }
            } finally {
                providerBusy.value = false
            }
        }
    }

    fun clearProviderStatus() {
        providerStatus.value = null
    }

    fun activateProvider(providerId: Long) {
        runProviderAction("切换 Provider 失败") {
            providerRepository.activateProvider(providerId)
            selectedSessionId.value = null
            providerStatus.value = "已切换默认 Provider"
        }
    }

    fun deleteProvider(providerId: Long) {
        runProviderAction("删除 Provider 失败") {
            val wasActive = providerRepository.getProviderById(providerId)?.isActive == true
            providerRepository.deleteProvider(providerId)
            if (wasActive) {
                selectedSessionId.value = null
            }
            if (providerRepository.countProviders() == 0) {
                aiSettingsManager.setAiEnabled(false)
                providerStatus.value = "Provider 已删除，AI 导师已关闭"
            } else {
                providerStatus.value = "Provider 已删除"
            }
        }
    }

    fun saveMemory(category: String, content: String) {
        val trimmedContent = content.trim()
        if (trimmedContent.isBlank()) return
        runMemoryAction("保存记忆失败") {
            aiStudyUseCase.saveMemory(category, trimmedContent).getOrThrow()
        }
    }

    fun deleteMemory(memoryId: Long) {
        runMemoryAction("删除记忆失败") {
            memoryRepository.deleteMemory(memoryId)
        }
    }

    fun clearError() {
        error.value = null
    }

    private fun titleFrom(text: String): String =
        text.take(18).ifBlank { "新的学习对话" }

    private fun runProviderAction(
        failureMessage: String,
        action: suspend () -> Unit
    ) {
        if (providerBusy.value) return
        providerBusy.value = true
        viewModelScope.launch {
            try {
                providerStatus.value = null
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                providerStatus.value = error.message ?: failureMessage
            } finally {
                providerBusy.value = false
            }
        }
    }

    private fun runChatAction(
        failureMessage: String,
        action: suspend () -> Unit
    ) {
        runBusyAction(chatActionBusy, failureMessage, action)
    }

    private fun runMemoryAction(
        failureMessage: String,
        action: suspend () -> Unit
    ) {
        runBusyAction(memoryBusy, failureMessage, action)
    }

    private fun runBusyAction(
        busyState: MutableStateFlow<Boolean>,
        failureMessage: String,
        action: suspend () -> Unit
    ) {
        if (busyState.value) return
        busyState.value = true
        viewModelScope.launch {
            try {
                error.value = null
                action()
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                this@AITutorViewModel.error.value = error.message ?: failureMessage
            } finally {
                busyState.value = false
            }
        }
    }

}
