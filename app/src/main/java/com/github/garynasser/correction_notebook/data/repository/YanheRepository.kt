package com.github.garynasser.correction_notebook.data.repository

import com.github.garynasser.correction_notebook.data.local.CredentialManager
import com.github.garynasser.correction_notebook.data.local.TokenManager
import com.github.garynasser.correction_notebook.data.model.auth.UserCredential
import com.github.garynasser.correction_notebook.data.remote.cas.BitCasClient
import javax.inject.Inject

class YanheRepository @Inject constructor(
    private val tokenManager: TokenManager,
    private val credentialManager: CredentialManager,
    private val bitCasClient: BitCasClient,
) {
    fun saveStudentCredential(credential: UserCredential) {
        credentialManager.saveCredentials(credential)
    }

    fun getStudentCredential(): UserCredential? {
        return credentialManager.getCredentials();
    }

    fun removeStudentCredential() {
        credentialManager.removeCredentials()
    }

    suspend fun clearYanheSession() {
        tokenManager.removeYanheLoginToken()
        credentialManager.removeCredentials()
    }

    suspend fun getYanheLoginToken(): Result<String> {
        val credential = credentialManager.getCredentials()
            ?: return Result.failure(Exception("请先登录延河课堂"))

        return try {
            val token = bitCasClient.getYanheToken(
                studentId = credential.studentId,
                password = credential.password
            )
            tokenManager.saveYanheLoginTokens(token)
            Result.success(token)
        } catch (e: Exception) {
            tokenManager.removeYanheLoginToken()
            Result.failure(e)
        }
    }
}
