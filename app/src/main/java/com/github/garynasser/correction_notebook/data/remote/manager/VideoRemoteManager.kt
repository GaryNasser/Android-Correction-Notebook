package com.github.garynasser.correction_notebook.data.remote.manager

import com.github.garynasser.correction_notebook.data.local.CredentialManager
import com.github.garynasser.correction_notebook.data.local.TokenManager
import com.github.garynasser.correction_notebook.data.model.auth.AuthState
import com.github.garynasser.correction_notebook.data.remote.api.VideoApiService
import com.github.garynasser.correction_notebook.data.remote.cas.CasCredentialException
import com.github.garynasser.correction_notebook.data.repository.AuthStateManager
import com.github.garynasser.correction_notebook.data.repository.YanheRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRemoteManager @Inject constructor(
    private val videoApiService: VideoApiService,
    private val tokenManager: TokenManager,
    private val credentialManager: CredentialManager,
    private val authStateManager: AuthStateManager,
    private val yanheRepository: YanheRepository
) {
    private val tokenRefreshMutex = Mutex()
    internal val sessionVersion: Long get() = yanheRepository.sessionVersion
    internal fun ensureSession(version: Long) = yanheRepository.ensureSession(version)

    private suspend fun <T> safeApiCall(version: Long = sessionVersion, block: suspend (String) -> T): T? {
        val token = getOrRefreshToken(version) ?: return null
        return try {
            ensureSession(version)
            block("Bearer $token").also { ensureSession(version) }
        } catch (error: HttpException) {
            ensureSession(version)
            if (error.code() != 401 && error.code() != 403) throw error
            val refreshedToken = refreshRejectedToken(token, version) ?: return null
            ensureSession(version)
            block("Bearer $refreshedToken").also { ensureSession(version) }
        }
    }

    private suspend fun storedToken(version: Long): String? = yanheRepository.withSession(version) {
        if (yanheRepository.isSignedOut) null else tokenManager.getYanheLoginToken()
    }

    private suspend fun getOrRefreshToken(version: Long): String? {
        storedToken(version)?.let { return it }
        return tokenRefreshMutex.withLock {
            storedToken(version) ?: requestNewToken(version)
        }
    }

    private suspend fun refreshRejectedToken(rejectedToken: String, version: Long): String? {
        return tokenRefreshMutex.withLock {
            val currentToken = storedToken(version)
            if (currentToken != null && currentToken != rejectedToken) {
                return@withLock currentToken
            }
            yanheRepository.withSession(version) { tokenManager.removeYanheLoginToken() }
            requestNewToken(version)
        }
    }

    private suspend fun requestNewToken(version: Long): String? {
        val credential = yanheRepository.withSession(version) { yanheRepository.getStudentCredential() }

        if (credential == null) {
            yanheRepository.withSession(version) { authStateManager.onCasLoginRequired() }
            return null
        }

        val loginResult = yanheRepository.getYanheLoginToken(version)
        val token = storedToken(version)

        if (token == null) {
            if (loginResult.exceptionOrNull() is CasCredentialException) {
                val clearedVersion = yanheRepository.clearYanheSession(version)
                yanheRepository.withSession(clearedVersion) {
                    authStateManager.updateState(AuthState.Unauthenticated)
                    authStateManager.onCasLoginRequired()
                }
            }
            throw IllegalStateException(
                loginResult.exceptionOrNull()?.message ?: "延河课堂登录已失效，请重新登录"
            )
        }

        return token
    }

    suspend fun getCourseList(
        semester: Int?,
        page: Int,
        pageSize: Int,
        keyword: String?
    ) = safeApiCall { token ->
        videoApiService.getCourseList(
            token = token,
            semester = semester,
            page = page,
            pageSize = pageSize,
            keyword = keyword
        )
    }

    suspend fun getPrivateCourseList(
        page: Int,
        pageSize: Int
    ) = safeApiCall { token ->
        videoApiService.getPrivateCourseList(
            token = token,
            page = page,
            pageSize = pageSize
        )
    }

    suspend fun getCourseSession(
        courseId: Int,
        withPage: Boolean?,
        page: Int?,
        pageSize: Int?,
        orderType: String?,
        orderTypeWeight: String?
    ) = safeApiCall { token ->
        videoApiService.getCourseSession(
            token = token,
            courseId = courseId,
            withPage = withPage,
            page = page,
            pageSize = pageSize,
            orderType = orderType,
            orderTypeWeight = orderTypeWeight
        )
    }

    suspend fun getCourseSessionDetail(sessionId: Int) = safeApiCall { token ->
        videoApiService.getCourseSessionDetail(
            token = token,
            sessionId = sessionId
        )
    }

    suspend fun getYanheUser(expectedVersion: Long = sessionVersion) = safeApiCall(expectedVersion) { token ->
        videoApiService.getYanheUser(token = token)
    }

    suspend fun getVideoToken(id: String, expectedVersion: Long = sessionVersion) = safeApiCall(expectedVersion) { token ->
        videoApiService.getVideoToken(
            token = token,
            id = id
        )
    }
}
