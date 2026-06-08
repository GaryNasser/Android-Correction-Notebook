package com.github.garynasser.correction_notebook.ui.screens.main

import androidx.lifecycle.ViewModel
import com.github.garynasser.correction_notebook.data.local.AISettingsManager
import com.github.garynasser.correction_notebook.data.model.auth.AuthState
import com.github.garynasser.correction_notebook.data.repository.AuthStateManager
import com.github.garynasser.correction_notebook.data.repository.YanheRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    val authStateManager: AuthStateManager,
    val aiSettingsManager: AISettingsManager,
    private val yanheRepository: YanheRepository,
) : ViewModel() {
    val authState = authStateManager.authState

    init {
        val state = if (yanheRepository.getStudentCredential() != null) {
            AuthState.Authenticated
        } else {
            AuthState.Unauthenticated
        }
        authStateManager.updateState(state)
    }
}
