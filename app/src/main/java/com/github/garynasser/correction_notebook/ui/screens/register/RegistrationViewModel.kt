package com.github.garynasser.correction_notebook.ui.screens.register

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.garynasser.correction_notebook.data.model.auth.AuthState
import com.github.garynasser.correction_notebook.data.model.auth.UserCredential
import com.github.garynasser.correction_notebook.data.repository.AuthRepository
import com.github.garynasser.correction_notebook.data.repository.AuthStateManager
import com.github.garynasser.correction_notebook.data.repository.YanheRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class RegistrationViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val authStateManager: AuthStateManager,
    private val yanheRepository: YanheRepository
): ViewModel() {
    var username by mutableStateOf("")
    var password by mutableStateOf("")
    var studentId by mutableStateOf("")
    var casPassword by mutableStateOf("")

    var isPasswordVisible by mutableStateOf(false)

    var isCasPasswordVisible by mutableStateOf(false)

    var isOnNextLoading by mutableStateOf(false)
        private set

    var isCasLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)

    val isOnNextEnabled: Boolean
        get() = !isOnNextLoading && !username.isEmpty() && password.length >= 6

    val isCasEnabled: Boolean
        get() = !isCasLoading && !casPassword.isEmpty() && !studentId.isEmpty()

    fun proceedToCasAuth(onSuccess: () -> Unit) {
        isOnNextLoading = true
        try {
            // TODO: 添加检查用户名是否被占用
            onSuccess()
        } finally {
            isOnNextLoading = false
        }
    }

    fun submitReauthentication() {
        yanheRepository.saveStudentCredential(UserCredential(studentId, casPassword))
    }

    fun submit() {
        if (isCasLoading) return

        errorMessage = null

        viewModelScope.launch {
            isCasLoading = true
            authStateManager.updateState(AuthState.Loading)

            val result = authRepository.casAuth(
                studentId,
                casPassword,
                username,
                password
            )

            result.onSuccess {
                isCasLoading = false
                authStateManager.updateState(AuthState.Authenticated)
                yanheRepository.saveStudentCredential(UserCredential(studentId, casPassword))
            } .onFailure { exception ->
                errorMessage = exception.message ?: "登录失败，请检查网络"
                isCasLoading = false
                authStateManager.updateState(AuthState.Unauthenticated)
                yanheRepository.removeStudentCredential()
            }
        }
    }
}