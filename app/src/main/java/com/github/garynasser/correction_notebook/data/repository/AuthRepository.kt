package com.github.garynasser.correction_notebook.data.repository

import com.github.garynasser.correction_notebook.data.local.TokenManager
import com.github.garynasser.correction_notebook.data.model.auth.AuthState
import com.github.garynasser.correction_notebook.data.model.auth.LoginRequest
import com.github.garynasser.correction_notebook.data.remote.api.AuthApiService
import com.github.garynasser.correction_notebook.di.AuthRetrofit
import com.github.garynasser.correction_notebook.di.NetworkModule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import retrofit2.Retrofit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    @AuthRetrofit private val retrofit: Retrofit,
    private val tokenManager: TokenManager,
    private val apiService: AuthApiService
) {
    private val authApi = retrofit.create(AuthApiService::class.java)

    suspend fun validateSession(): AuthState {
        val refreshToken = tokenManager.getRefreshToken()
        if (refreshToken == null) return AuthState.Unauthenticated

        return try {
            val response = apiService.refreshToken("Bearer $refreshToken").execute()
            val responseBody = response.body()?.data
            if (response.isSuccessful && responseBody != null) {
                val accessToken = responseBody.accessToken
                val refreshToken = responseBody.refreshToken

                tokenManager.saveTokens(accessToken, refreshToken)
                AuthState.Authenticated
            } else {
                AuthState.Unauthenticated
            }
        } catch (e: Exception) {
            AuthState.Unauthenticated
        }
    }

    suspend fun login(username: String, password: String): Result<Unit> {
        return try {
            val response = authApi.login(LoginRequest(username, password))

            if (response.code == 200 && response.data != null) {
                tokenManager.saveTokens(
                    access = response.data.accessToken,
                    refresh = response.data.refreshToken ?: ""
                )

                Result.success(Unit)
            } else {
                Result.failure(Exception(response.message))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun logout() {
        tokenManager.removeToken()
    }
}