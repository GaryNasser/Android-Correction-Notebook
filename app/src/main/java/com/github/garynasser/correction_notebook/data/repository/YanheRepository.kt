package com.github.garynasser.correction_notebook.data.repository

import com.github.garynasser.correction_notebook.data.local.CredentialManager
import com.github.garynasser.correction_notebook.data.local.TokenManager
import com.github.garynasser.correction_notebook.data.model.auth.UserCredential
import com.github.garynasser.correction_notebook.data.remote.cas.BitCasClient
import kotlinx.coroutines.CancellationException
import javax.inject.Inject

class YanheRepository @Inject constructor(
    private val tokenManager: TokenManager,
    private val credentialManager: CredentialManager,
    private val bitCasClient: BitCasClient,
) {
    fun getStudentCredential(): UserCredential? = credentialManager.getCredentials()

    fun removeStudentCredential() {
        credentialManager.removeCredentials()
    }

    suspend fun clearYanheSession() {
        credentialManager.removeCredentials()
        tokenManager.removeYanheLoginToken()
    }

    suspend fun authenticateStudent(credential: UserCredential): Result<String> {
        return authenticateBeforeCommit(
            requestToken = {
                bitCasClient.getYanheToken(
                    studentId = credential.studentId,
                    password = credential.password
                )
            },
            commit = { token ->
                tokenManager.saveYanheLoginTokens(token)
                credentialManager.saveCredentials(credential)
            }
        )
    }

    suspend fun getYanheLoginToken(): Result<String> {
        val credential = credentialManager.getCredentials()
            ?: return Result.failure(Exception("请先登录延河课堂"))

        return authenticateBeforeCommit(
            requestToken = {
                bitCasClient.getYanheToken(
                    studentId = credential.studentId,
                    password = credential.password
                )
            },
            commit = tokenManager::saveYanheLoginTokens
        )
    }
}

internal suspend fun authenticateBeforeCommit(
    requestToken: suspend () -> String,
    commit: suspend (String) -> Unit
): Result<String> {
    return try {
        val token = requestToken()
        commit(token)
        Result.success(token)
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }
}
