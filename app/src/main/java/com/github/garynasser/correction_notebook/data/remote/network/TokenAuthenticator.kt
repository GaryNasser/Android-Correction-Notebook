package com.github.garynasser.correction_notebook.data.remote.network

import com.github.garynasser.correction_notebook.data.local.TokenManager
import com.github.garynasser.correction_notebook.data.model.auth.RefreshRequest
import com.github.garynasser.correction_notebook.data.remote.api.AuthApiService
import kotlinx.coroutines.CancellationException
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
        if (authResponseCount(response) >= MAX_AUTH_ATTEMPTS) return null

        return synchronized(this) {
            runBlocking {
                val requestAccessToken = response.request.header("Authorization")
                val latestAccessToken = tokenManager.getAccessKey()

                // Another request may already have refreshed the shared token.
                if (!latestAccessToken.isNullOrBlank() && latestAccessToken != requestAccessToken) {
                    return@runBlocking response.withAccessToken(latestAccessToken)
                }

                val currentRefreshToken = tokenManager.getRefreshToken()
                    ?.takeIf { it.isNotBlank() }
                    ?: return@runBlocking null

                try {
                    val refreshResponse = authApi.refreshToken(RefreshRequest(currentRefreshToken))
                    val tokenResponse = refreshResponse.data
                        ?.takeIf { refreshResponse.code == 200 }
                    val newAccessToken = tokenResponse?.accessToken
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }

                    if (newAccessToken == null) {
                        if (shouldClearTokensAfterRefresh(refreshResponse.code)) {
                            tokenManager.removeLoginToken()
                        }
                        return@runBlocking null
                    }

                    tokenManager.saveLoginTokens(
                        access = newAccessToken,
                        refresh = selectRefreshToken(
                            currentToken = currentRefreshToken,
                            rotatedToken = tokenResponse.refreshToken
                        )
                    )
                    response.withAccessToken(newAccessToken)
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    // Keep the current tokens on transient network failures.
                    null
                }
            }
        }
    }

    private fun Response.withAccessToken(accessToken: String): Request {
        return request.newBuilder()
            .header("Authorization", accessToken)
            .build()
    }

    private companion object {
        const val MAX_AUTH_ATTEMPTS = 2
    }
}

internal fun authResponseCount(response: Response): Int {
    var count = 1
    var priorResponse = response.priorResponse
    while (priorResponse != null) {
        count++
        priorResponse = priorResponse.priorResponse
    }
    return count
}

internal fun selectRefreshToken(currentToken: String, rotatedToken: String?): String {
    return rotatedToken?.trim()?.takeIf { it.isNotBlank() } ?: currentToken
}

internal fun shouldClearTokensAfterRefresh(responseCode: Int): Boolean {
    return responseCode in 400..499
}
