package com.github.garynasser.correction_notebook.data.repository

import com.github.garynasser.correction_notebook.data.model.auth.AuthEvent
import com.github.garynasser.correction_notebook.data.model.auth.AuthState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthStateManager @Inject constructor() {
    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    private val _authEvents = MutableSharedFlow<AuthEvent>()
    val authState = _authState.asStateFlow()
    val authEvents = _authEvents.asSharedFlow()

    fun updateState(state: AuthState) {
        _authState.value = state
    }

    suspend fun onCasLoginRequired() {
        _authEvents.emit(AuthEvent.NEEDS_LOGIN)
    }


}