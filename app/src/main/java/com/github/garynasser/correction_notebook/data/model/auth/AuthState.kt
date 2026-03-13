package com.github.garynasser.correction_notebook.data.model.auth

sealed class AuthState {
    object Loading: AuthState()
    object Authenticated: AuthState()
    object Unauthenticated: AuthState()
}