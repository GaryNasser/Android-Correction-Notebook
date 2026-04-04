package com.github.garynasser.correction_notebook.ui.screens.login

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.garynasser.correction_notebook.data.model.auth.AuthState
import com.github.garynasser.correction_notebook.data.repository.AuthRepository
import com.github.garynasser.correction_notebook.data.repository.AuthStateManager
import com.github.garynasser.correction_notebook.ui.screens.main.MainViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
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
        get() = password.length >= 6 && !isLoading && !username.isEmpty()

    fun onLoginClick() {
        if (isLoading) return

        errorMessage = null

        viewModelScope.launch {
            isLoading = true

            val result = authRepository.login(username, password)

            result.onSuccess {
                isLoading = false
                authStateManager.updateState(AuthState.Authenticated)
            } .onFailure { exception ->
                errorMessage = exception.message ?: "登录失败，请检查网络"
                isLoading = false
            }
        }
    }
}