package com.github.garynasser.correction_notebook.data.repository

import com.github.garynasser.correction_notebook.data.local.CredentialManager
import com.github.garynasser.correction_notebook.data.local.TokenManager
import com.github.garynasser.correction_notebook.data.model.auth.UserCredential
import com.github.garynasser.correction_notebook.data.remote.cas.BitCasClient
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YanheRepository @Inject constructor(
    private val tokenManager: TokenManager,
    private val credentialManager: CredentialManager,
    private val bitCasClient: BitCasClient,
) {
    private val sessionMutex = Mutex()
    private val _sessionRevision = MutableStateFlow(0L)
    internal val sessionRevision = _sessionRevision.asStateFlow()
    internal val sessionVersion: Long get() = _sessionRevision.value
    @Volatile internal var isSignedOut = false
        private set
    private var loginAttempt = 0L

    fun getStudentCredential(): UserCredential? = if (isSignedOut) null else credentialManager.getCredentials()

    suspend fun clearYanheSession(expectedVersion: Long? = null): Long = sessionMutex.withLock {
        expectedVersion?.let(::ensureSession)
        isSignedOut = true
        _sessionRevision.value++
        loginAttempt++
        try {
            credentialManager.removeCredentials()
        } finally {
            tokenManager.removeYanheLoginToken()
        }
        sessionVersion
    }

    suspend fun authenticateStudent(credential: UserCredential): Result<String> {
        val (version, attempt) = sessionMutex.withLock { sessionVersion to ++loginAttempt }
        return authenticateBeforeCommit(
            requestToken = {
                bitCasClient.getYanheToken(
                    studentId = credential.studentId,
                    password = credential.password
                )
            },
            commit = { token ->
                withSession(version) {
                    if (attempt != loginAttempt) throw CancellationException("登录请求已被替换")
                    currentCoroutineContext().ensureActive()
                    val oldToken = tokenManager.getYanheLoginToken()
                    val oldCredential = credentialManager.getCredentials()
                    try {
                        tokenManager.saveYanheLoginTokens(token)
                        currentCoroutineContext().ensureActive()
                        credentialManager.saveCredentials(credential)
                        isSignedOut = false
                        _sessionRevision.value++
                    } catch (error: Exception) {
                        withContext(NonCancellable) {
                            if (oldToken == null) tokenManager.removeYanheLoginToken() else tokenManager.saveYanheLoginTokens(oldToken)
                            if (oldCredential == null) credentialManager.removeCredentials() else credentialManager.saveCredentials(oldCredential)
                        }
                        throw error
                    }
                }
            }
        )
    }

    suspend fun getYanheLoginToken(expectedVersion: Long? = null): Result<String> {
        val (version, credential) = sessionMutex.withLock {
            expectedVersion?.let(::ensureSession)
            sessionVersion to getStudentCredential()
        }
        credential
            ?: return Result.failure(Exception("请先登录延河课堂"))

        return authenticateBeforeCommit(
            requestToken = {
                bitCasClient.getYanheToken(
                    studentId = credential.studentId,
                    password = credential.password
                )
            },
            commit = { token -> withSession(version) { tokenManager.saveYanheLoginTokens(token) } }
        )
    }

    internal fun ensureSession(version: Long) {
        if (version != sessionVersion) throw CancellationException("登录会话已变更")
    }

    internal suspend fun <T> withSession(version: Long, action: suspend () -> T): T = sessionMutex.withLock {
        ensureSession(version)
        action()
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
