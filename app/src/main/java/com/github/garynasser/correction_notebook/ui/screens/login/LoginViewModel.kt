package com.github.garynasser.correction_notebook.ui.screens.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.garynasser.correction_notebook.data.model.auth.AuthState
import com.github.garynasser.correction_notebook.data.repository.AuthRepository
import com.github.garynasser.correction_notebook.data.repository.AuthStateManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor (
    private val authRepository: AuthRepository,
    private val authStateManager: AuthStateManager
) : ViewModel() {
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var isPasswordVisible by mutableStateOf(false)

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)


    val isLoginEnable: Boolean
        get() = password.length >= 6 && !isLoading && username.isNotBlank()

    fun onLoginClick() {
        if (isLoading) return

        errorMessage = null
        val trimmedUsername = username.trim()
        if (trimmedUsername.isBlank() || password.length < 6) return

        viewModelScope.launch {
            isLoading = true

            try {
                authRepository.login(trimmedUsername, password)
                    .onSuccess {
                        authStateManager.updateState(AuthState.Authenticated)
                    }
                    .onFailure { exception ->
                        errorMessage = formatLoginError(exception)
                        authStateManager.updateState(AuthState.Unauthenticated)
                    }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                errorMessage = formatLoginError(exception)
                authStateManager.updateState(AuthState.Unauthenticated)
            } finally {
                isLoading = false
            }
        }
    }

    fun clearError() {
        errorMessage = null
    }

    private fun formatLoginError(error: Throwable): String {
        return error.message
            ?.replace(Regex("^java\\.lang\\.[A-Za-z]+Exception:\\s*"), "")
            ?.takeIf { it.isNotBlank() }
            ?: "登录失败，请检查账号、密码或网络连接"
    }
}
