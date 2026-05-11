package com.github.garynasser.correction_notebook.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.garynasser.correction_notebook.data.local.AISettingsManager
import com.github.garynasser.correction_notebook.data.model.ai.AIProviderType
import com.github.garynasser.correction_notebook.data.model.ai.AiProviderForm
import com.github.garynasser.correction_notebook.data.model.auth.AuthState
import com.github.garynasser.correction_notebook.data.repository.AuthRepository
import com.github.garynasser.correction_notebook.data.repository.AuthStateManager
import com.github.garynasser.correction_notebook.data.repository.ProviderRecord
import com.github.garynasser.correction_notebook.data.repository.ProviderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val authStateManager: AuthStateManager,
    private val aiSettingsManager: AISettingsManager,
    private val providerRepository: ProviderRepository
) : ViewModel() {

    // Auth state
    val authState: StateFlow<AuthState> = authStateManager.authState

    // AI Settings - delegate to AISettingsManager
    val aiEnabled: StateFlow<Boolean> = aiSettingsManager.aiEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )

    val activeProvider: StateFlow<ProviderRecord?> = providerRepository.observeActiveProvider()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val providers: StateFlow<List<ProviderRecord>> = providerRepository.observeProviders()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun setAiEnabled(enabled: Boolean) {
        viewModelScope.launch {
            aiSettingsManager.setAiEnabled(enabled)
        }
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
                    customHeadersJson = form.customHeaders.trim().ifBlank { "{}" },
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
            aiSettingsManager.setAiEnabled(true)
        }
    }

    fun deleteProvider(providerId: Long) {
        viewModelScope.launch {
            providerRepository.deleteProvider(providerId)
        }
    }

    fun logout() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                authRepository.logout()
                authStateManager.updateState(AuthState.Unauthenticated)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun normalizeBaseUrl(raw: String, type: AIProviderType): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isNotBlank()) return trimmed
        return when (type) {
            AIProviderType.OPENAI_COMPATIBLE -> AISettingsManager.DEFAULT_API_URL
            AIProviderType.ANTHROPIC_COMPATIBLE -> "https://api.anthropic.com/v1"
        }
    }
}
