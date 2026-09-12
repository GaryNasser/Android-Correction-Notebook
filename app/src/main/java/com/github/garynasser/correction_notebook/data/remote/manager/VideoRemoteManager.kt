package com.github.garynasser.correction_notebook.data.remote.manager

import com.github.garynasser.correction_notebook.data.local.CredentialManager
import com.github.garynasser.correction_notebook.data.local.TokenManager
import com.github.garynasser.correction_notebook.data.remote.api.VideoApiService
import com.github.garynasser.correction_notebook.data.repository.AuthStateManager
import com.github.garynasser.correction_notebook.data.repository.YanheRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import javax.inject.Inject

class VideoRemoteManager @Inject constructor(
    private val videoApiService: VideoApiService,
    private val tokenManager: TokenManager,
    private val credentialManager: CredentialManager,
    private val authStateManager: AuthStateManager,
    private val yanheRepository: YanheRepository
) {
    private val tokenRefreshMutex = Mutex()

    private suspend fun <T> safeApiCall(block: suspend (String) -> T): T? {
        val token = getOrRefreshToken() ?: return null
        return try {
            block("Bearer $token")
        } catch (error: HttpException) {
            if (error.code() != 401 && error.code() != 403) throw error
            val refreshedToken = refreshRejectedToken(token) ?: return null
            block("Bearer $refreshedToken")
        }
    }

    private suspend fun getOrRefreshToken(): String? {
        tokenManager.getYanheLoginToken()?.let { return it }
        return tokenRefreshMutex.withLock {
            tokenManager.getYanheLoginToken() ?: requestNewToken()
        }
    }

    private suspend fun refreshRejectedToken(rejectedToken: String): String? {
        return tokenRefreshMutex.withLock {
            val currentToken = tokenManager.getYanheLoginToken()
            if (currentToken != null && currentToken != rejectedToken) {
                return@withLock currentToken
            }
            tokenManager.removeYanheLoginToken()
            requestNewToken()
        }
    }

    private suspend fun requestNewToken(): String? {
        val credential = credentialManager.getCredentials()

        if (credential == null) {
            authStateManager.onCasLoginRequired()
            return null
        }

        val loginResult = yanheRepository.getYanheLoginToken()
        val token = tokenManager.getYanheLoginToken()

        if (token == null) {
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

    suspend fun getYanheUser() = safeApiCall { token ->
        videoApiService.getYanheUser(token = token)
    }

    suspend fun getVideoToken(id: String) = safeApiCall { token ->
        videoApiService.getVideoToken(
            token = token,
            id = id
        )
    }
}
