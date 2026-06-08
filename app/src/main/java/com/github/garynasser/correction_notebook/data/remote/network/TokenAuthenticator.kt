package com.github.garynasser.correction_notebook.data.remote.network

import android.util.Log
import com.github.garynasser.correction_notebook.data.local.TokenManager
import com.github.garynasser.correction_notebook.data.model.auth.RefreshRequest
import com.github.garynasser.correction_notebook.data.remote.api.AuthApiService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(
    private val tokenManager: TokenManager,
    private val authApi: AuthApiService,
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        Log.d("AppLifecycle", "Authenticator act")
        val currentRefreshToken = runBlocking {
            tokenManager.refreshToken.first()
        }

        if (currentRefreshToken.isNullOrBlank()) {
            return null
        }

        runBlocking {
            val refreshResponse = authApi.refreshToken(RefreshRequest(currentRefreshToken))

            if (refreshResponse.code == 200 && refreshResponse.data != null) {
                val newAccessToken = refreshResponse.data.accessToken

                tokenManager.updateAccessToken(newAccessToken)

                return@runBlocking response.request.newBuilder()
                    .header("Authorization", newAccessToken)
                    .build()
            } else {
                tokenManager.removeLoginToken()

                return@runBlocking null
            }
        }

        return null
    }
}
