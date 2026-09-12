package com.github.garynasser.correction_notebook.ui.screens.register

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.garynasser.correction_notebook.data.model.auth.AuthState
import com.github.garynasser.correction_notebook.data.model.auth.UserCredential
import com.github.garynasser.correction_notebook.data.repository.AuthStateManager
import com.github.garynasser.correction_notebook.data.repository.VideoRepository
import com.github.garynasser.correction_notebook.data.repository.YanheRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class RegistrationViewModel @Inject constructor(
    private val yanheRepository: YanheRepository,
    private val authStateManager: AuthStateManager,
    private val videoRepository: VideoRepository,
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
        get() = !isOnNextLoading && username.isNotBlank() && password.length >= 6

    val isCasEnabled: Boolean
        get() = !isCasLoading && casPassword.isNotBlank() && studentId.trim().isNotBlank()

    fun proceedToCasAuth(onSuccess: () -> Unit) {
        if (!isOnNextEnabled) return
        isOnNextLoading = true
        errorMessage = null
        try {
            username = username.trim()
            onSuccess()
        } finally {
            isOnNextLoading = false
        }
    }

    fun submitReauthentication(onConfirmClick: () -> Unit) {
        submitYanheLogin(onConfirmClick)
    }

    fun submit() {
        submitYanheLogin()
    }

    fun submitYanheLogin(onSuccess: () -> Unit = {}) {
        if (isCasLoading) return

        errorMessage = null
        val trimmedStudentId = studentId.trim()
        if (trimmedStudentId.isBlank() || casPassword.isBlank()) return
        isCasLoading = true

        viewModelScope.launch {
            try {
                videoRepository.clearSessionCache()
                yanheRepository.saveStudentCredential(UserCredential(trimmedStudentId, casPassword))
                yanheRepository.getYanheLoginToken().getOrThrow()
                authStateManager.updateState(AuthState.Authenticated)
                onSuccess()
            } catch (exception: CancellationException) {
                clearFailedYanheLogin()
                throw exception
            } catch (exception: Exception) {
                errorMessage = formatCasError(exception)
                clearFailedYanheLogin()
            } finally {
                isCasLoading = false
            }
        }
    }

    fun clearError() {
        errorMessage = null
    }

    private suspend fun clearFailedYanheLogin() {
        withContext(NonCancellable) {
            try {
                yanheRepository.clearYanheSession()
            } catch (_: Exception) {
                // Credentials are removed before the token, so startup cannot restore a failed login.
            }
            videoRepository.clearSessionCache()
            authStateManager.updateState(AuthState.Unauthenticated)
        }
    }

    private fun formatCasError(error: Throwable): String {
        val raw = error.message.orEmpty()
            .replace(Regex("^java\\.lang\\.[A-Za-z]+Exception:\\s*"), "")
            .replace(Regex("^javax\\.net\\.ssl\\.[A-Za-z]+Exception:\\s*"), "")
            .trim()
        return raw.ifBlank { "延河课堂登录失败，请检查学号、密码或网络连接" }
    }
}
