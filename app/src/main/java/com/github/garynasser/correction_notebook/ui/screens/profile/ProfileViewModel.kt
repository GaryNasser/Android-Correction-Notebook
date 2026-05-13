package com.github.garynasser.correction_notebook.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.garynasser.correction_notebook.data.local.AISettingsManager
import com.github.garynasser.correction_notebook.data.model.ai.AiModelOption
import com.github.garynasser.correction_notebook.data.model.ai.AiProviderForm
import com.github.garynasser.correction_notebook.data.repository.AIRepository
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
    private val providerRepository: ProviderRepository,
    private val aiRepository: AIRepository
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

    private val _isProviderBusy = MutableStateFlow(false)
    val isProviderBusy: StateFlow<Boolean> = _isProviderBusy.asStateFlow()

    private val _providerStatusMessage = MutableStateFlow<String?>(null)
    val providerStatusMessage: StateFlow<String?> = _providerStatusMessage.asStateFlow()

    private val _fetchedModels = MutableStateFlow<List<AiModelOption>>(emptyList())
    val fetchedModels: StateFlow<List<AiModelOption>> = _fetchedModels.asStateFlow()

    fun setAiEnabled(enabled: Boolean) {
        viewModelScope.launch {
            aiSettingsManager.setAiEnabled(enabled)
        }
    }

    fun saveProvider(form: AiProviderForm) {
        viewModelScope.launch {
            aiRepository.validateProviderForm(form)?.let { message ->
                _providerStatusMessage.value = message
                return@launch
            }
            providerRepository.saveProvider(aiRepository.normalizeProviderRecord(form))
            aiSettingsManager.setAiEnabled(true)
            _providerStatusMessage.value = aiRepository.providerConfigWarning(form)
                ?.let { "Provider 已保存。$it" }
                ?: "Provider 已保存"
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

    fun fetchModels(form: AiProviderForm) {
        viewModelScope.launch {
            _isProviderBusy.value = true
            _providerStatusMessage.value = null
            aiRepository.listModels(form)
                .onSuccess { models ->
                    _fetchedModels.value = models
                    _providerStatusMessage.value = if (models.isEmpty()) {
                        "接口可访问，但没有返回模型列表；你仍然可以手动填写模型名"
                    } else {
                        "已获取 ${models.size} 个模型"
                    }
                }
                .onFailure { throwable ->
                    _providerStatusMessage.value = throwable.message ?: "获取模型列表失败"
                }
            _isProviderBusy.value = false
        }
    }

    fun testProvider(form: AiProviderForm) {
        viewModelScope.launch {
            _isProviderBusy.value = true
            _providerStatusMessage.value = null
            aiRepository.testProvider(form)
                .onSuccess { result ->
                    if (result.models.isNotEmpty()) _fetchedModels.value = result.models
                    _providerStatusMessage.value = result.message
                }
                .onFailure { throwable ->
                    _providerStatusMessage.value = throwable.message ?: "连接测试失败"
                }
            _isProviderBusy.value = false
        }
    }

    fun clearProviderStatus() {
        _providerStatusMessage.value = null
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

}
