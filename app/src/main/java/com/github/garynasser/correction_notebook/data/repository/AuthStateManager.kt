package com.github.garynasser.correction_notebook.data.repository

import com.github.garynasser.correction_notebook.data.model.auth.AuthState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthStateManager @Inject constructor() {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState = _authState.asStateFlow()

    fun updateState(state: AuthState) {
        _authState.value = state
    }
}