package com.github.garynasser.correction_notebook.data.remote.network

import com.github.garynasser.correction_notebook.data.local.TokenManager
import com.github.garynasser.correction_notebook.data.model.auth.AuthState
import com.github.garynasser.correction_notebook.data.remote.api.AuthApiService
import com.github.garynasser.correction_notebook.data.repository.AuthStateManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

class TokenAuthenticator(
    private val tokenManager: TokenManager,
    private val authApi: AuthApiService,
    private val authStateManager: AuthStateManager
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        val currentRefreshToken = runBlocking {
            tokenManager.refreshToken.first()
        }

        if (currentRefreshToken.isNullOrBlank()) {
            authStateManager.updateState(AuthState.Unauthenticated)
            return null
        }

        val refreshResponse = authApi.refreshToken("Bearer $currentRefreshToken").execute()

        if (refreshResponse.isSuccessful) {
            val newAccessToken = refreshResponse.body()?.data?.accessToken?: return null

            runBlocking {
                tokenManager.updateAccessToken(newAccessToken)
            }

            authStateManager.updateState(AuthState.Authenticated)

            return response.request().newBuilder()
                .header("Authorization", newAccessToken)
                .build()
        } else {
            runBlocking {
                tokenManager.removeToken()
            }

            authStateManager.updateState(AuthState.Unauthenticated)

            return null
        }
    }
}