package com.github.garynasser.correction_notebook.ui.screens.main

import androidx.compose.animation.core.rememberTransition
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.github.garynasser.correction_notebook.data.local.AISettingsManager
import com.github.garynasser.correction_notebook.data.model.auth.AuthState
import com.github.garynasser.correction_notebook.data.repository.AuthRepository
import com.github.garynasser.correction_notebook.data.repository.AuthStateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.WhileSubscribed
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    val authStateManager: AuthStateManager,
    val aiSettingsManager: AISettingsManager
) : ViewModel() {
    val authState = authStateManager.authState

    init {
        checkUserAuth()
    }

    private fun checkUserAuth() {
        viewModelScope.launch {
            val result = authRepository.validateSession()

            authStateManager.updateState(result)
        }
    }
}