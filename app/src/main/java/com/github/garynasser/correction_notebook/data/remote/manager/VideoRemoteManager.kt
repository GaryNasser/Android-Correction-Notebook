package com.github.garynasser.correction_notebook.data.remote.manager

import android.util.Log
import com.github.garynasser.correction_notebook.data.local.CredentialManager
import com.github.garynasser.correction_notebook.data.local.TokenManager
import com.github.garynasser.correction_notebook.data.remote.api.VideoApiService
import com.github.garynasser.correction_notebook.data.repository.AuthStateManager
import com.github.garynasser.correction_notebook.data.repository.YanheRepository
import com.github.garynasser.correction_notebook.utils.SignatureUtils
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
        Log.i("Token", token.toString())

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

        return block("Bearer $token")
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

    suspend fun getPersonalCourseList(
        semester: Int?,
        page: Int,
        pageSize: Int,
        keyword: String?
    ) = safeApiCall { token ->
        videoApiService.getPersonalCourseList(
            token = token,
            semester = semester,
            page = page,
            pageSize = pageSize,
            keyword = keyword
        )
    }

    suspend fun getCourseSession(courseId: Int) = safeApiCall { token ->
        videoApiService.getCourseSession(
            token = token,
            courseId = courseId
        )
    }

    suspend fun getVideoToken() = safeApiCall { token ->
        videoApiService.getVideoToken(
            token = token
        )
    }

    suspend fun downloadM3U8File(
        url: String,
        videoToken: String,
    ) = safeApiCall { token ->
        val sig = SignatureUtils.getSignature()

        videoApiService.downloadYanheFile(
            url = url,
            xvideoToken = videoToken,
            xclientTimestamp = sig["Xclient-Timestamp"] ?: "",
            xclientSignature = sig["Xclient-Signature"] ?: "",
        )
    }

    suspend fun downloadTsFile(
        url: String,
        videoToken: String
    ) = safeApiCall { token ->
        val sig = SignatureUtils.getSignature()

        videoApiService.downloadYanheFile(
            url = url,
            xvideoToken = videoToken,
            xclientTimestamp = sig["Xclient-Timestamp"] ?: "",
            xclientSignature = sig["Xclient-Signature"] ?: "",
        )
    }
}