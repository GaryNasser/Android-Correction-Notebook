package com.github.garynasser.correction_notebook.data.remote.manager

import com.github.garynasser.correction_notebook.data.local.CredentialManager
import com.github.garynasser.correction_notebook.data.local.TokenManager
import com.github.garynasser.correction_notebook.data.model.auth.CredentialAuthRequest
import com.github.garynasser.correction_notebook.data.remote.api.AuthApiService
import com.github.garynasser.correction_notebook.data.remote.api.VideoApiService
import com.github.garynasser.correction_notebook.data.repository.AuthStateManager
import com.github.garynasser.correction_notebook.data.repository.YanheRepository
import com.github.garynasser.correction_notebook.utils.RSAUtils
import javax.inject.Inject

class VideoRemoteManager @Inject constructor(
    private val videoApiService: VideoApiService,
    private val tokenManager: TokenManager,
    private val credentialManager: CredentialManager,
    private val authStateManager: AuthStateManager,
    private val yanheRepository: YanheRepository
) {
    private suspend fun <T> safeApiCall(block: suspend (String) -> T): T? {
        var token = tokenManager.getYanheLoginToken()

        if (token == null) {
            val credential = credentialManager.getCredentials()

            if (credential == null) {
                authStateManager.onCasLoginRequired()
                return null
            }

            yanheRepository.getYanheLoginToken()
            token = tokenManager.getYanheLoginToken()

            if (token == null) {
                throw RuntimeException()
            }
        }

        return block(token)
    }

    suspend fun getCourseList(
        semester: String,
        page: String,
        pageSize: String,
        keyword: String
    ) = safeApiCall { token ->
        videoApiService.getCourseList(
            token = token,
            semester = semester,
            page = page,
            pageSize = pageSize,
            keyword = keyword
        )
    }

    suspend fun getPersonalCourseList(
        semester: String,
        page: String,
        pageSize: String,
        keyword: String
    ) = safeApiCall { token ->
        videoApiService.getCourseList(
            token = token,
            semester = semester,
            page = page,
            pageSize = pageSize,
            keyword = keyword
        )
    }
}