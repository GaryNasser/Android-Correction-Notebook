package com.github.garynasser.correction_notebook.ui.screens.aitutor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.garynasser.correction_notebook.data.local.AISettingsManager
import com.github.garynasser.correction_notebook.data.model.ai.ChatMessage
import com.github.garynasser.correction_notebook.data.repository.AIRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ChatUiMessage(
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)

@HiltViewModel
class AITutorViewModel @Inject constructor(
    private val aiRepository: AIRepository,
    private val aiSettingsManager: AISettingsManager
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatUiMessage>>(emptyList())
    val messages: StateFlow<List<ChatUiMessage>> = _messages.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    val aiEnabled = aiSettingsManager.aiEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )

    val apiBaseUrl = aiSettingsManager.apiBaseUrl.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), AISettingsManager.DEFAULT_API_URL
    )

    val apiKey = aiSettingsManager.apiKey.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), ""
    )

    val aiModel = aiSettingsManager.aiModel.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), AISettingsManager.DEFAULT_MODEL
    )

    fun sendMessage(content: String) {
        if (content.isBlank()) return

        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            // Add user message
            val userMessage = ChatUiMessage(content = content, isUser = true)
            _messages.value = _messages.value + userMessage

            // Prepare messages for API
            val apiMessages = _messages.value.map { ChatMessage(role = if (it.isUser) "user" else "assistant", content = it.content) }

            // Call API
            val result = aiRepository.sendMessage(apiMessages)

            result.onSuccess { response ->
                val aiMessage = ChatUiMessage(content = response, isUser = false)
                _messages.value = _messages.value + aiMessage
            }.onFailure { exception ->
                _error.value = exception.message ?: "发送失败"
            }

            _isLoading.value = false
        }
    }

    fun clearMessages() {
        _messages.value = emptyList()
    }

    fun clearError() {
        _error.value = null
    }

    fun setAiEnabled(enabled: Boolean) {
        viewModelScope.launch {
            aiSettingsManager.setAiEnabled(enabled)
        }
    }

    fun setApiBaseUrl(url: String) {
        viewModelScope.launch {
            aiSettingsManager.setApiBaseUrl(url)
        }
    }

    fun setApiKey(key: String) {
        viewModelScope.launch {
            aiSettingsManager.setApiKey(key)
        }
    }

    fun setAiModel(model: String) {
        viewModelScope.launch {
            aiSettingsManager.setAiModel(model)
        }
    }
}
