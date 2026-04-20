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

        return block(token)
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

    suspend fun downloadFile(
        url: String,
        videoToken: String,
    ) = safeApiCall { token ->
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            Log.e("VIDEO_MANAGER", "拒绝非网络地址的请求: $url")
            return@safeApiCall null
        }

        if (url.contains(".m3u8") && url.contains("/cache/")) {
            Log.e("VIDEO_MANAGER", "拒绝缓存文件路径: $url")
            return@safeApiCall null
        }

        Log.d("VIDEO_MANAGER", "downloadFile 被调用，URL: $url")
        Log.d("VIDEO_MANAGER", "videoToken: ${videoToken.take(20)}...")

        val sig = SignatureUtils.getSignature()
        Log.d("VIDEO_MANAGER", "Xclient-Timestamp: ${sig["Xclient-Timestamp"]}")
        Log.d("VIDEO_MANAGER", "Xclient-Signature: ${sig["Xclient-Signature"]}")

        val response = videoApiService.downloadYanheFile(
            url = url,
            xvideoToken = videoToken,
            xclientTimestamp = sig["Xclient-Timestamp"] ?: "",
            xclientSignature = sig["Xclient-Signature"] ?: "",
        )

        Log.d("VIDEO_MANAGER", "响应码: ${response.code()}")
        Log.d("VIDEO_MANAGER", "响应头: ${response.headers()}")

        response
    }
}