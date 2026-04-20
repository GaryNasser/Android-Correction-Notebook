package com.github.garynasser.correction_notebook.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.garynasser.correction_notebook.data.local.AISettingsManager
import com.github.garynasser.correction_notebook.data.model.auth.AuthState
import com.github.garynasser.correction_notebook.data.repository.AuthRepository
import com.github.garynasser.correction_notebook.data.repository.AuthStateManager
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
    private val aiSettingsManager: AISettingsManager
) : ViewModel() {

    // Auth state
    val authState: StateFlow<AuthState> = authStateManager.authState

    // AI Settings - delegate to AISettingsManager
    val aiEnabled: StateFlow<Boolean> = aiSettingsManager.aiEnabled.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), false
    )

    val apiBaseUrl: StateFlow<String> = aiSettingsManager.apiBaseUrl.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), AISettingsManager.DEFAULT_API_URL
    )

    val apiKey: StateFlow<String> = aiSettingsManager.apiKey.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), ""
    )

    val aiModel: StateFlow<String> = aiSettingsManager.aiModel.stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), AISettingsManager.DEFAULT_MODEL
    )

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

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
