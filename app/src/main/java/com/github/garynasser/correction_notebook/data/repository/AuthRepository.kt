package com.github.garynasser.correction_notebook.data.repository

import android.util.Log
import com.github.garynasser.correction_notebook.data.local.TokenManager
import com.github.garynasser.correction_notebook.data.model.auth.AuthState
import com.github.garynasser.correction_notebook.data.model.auth.CredentialAuthRequest
import com.github.garynasser.correction_notebook.data.model.auth.LoginRequest
import com.github.garynasser.correction_notebook.data.model.auth.RefreshRequest
import com.github.garynasser.correction_notebook.data.model.auth.RegisterRequest
import com.github.garynasser.correction_notebook.data.model.auth.UserCredential
import com.github.garynasser.correction_notebook.data.remote.api.AuthApiService
import com.github.garynasser.correction_notebook.utils.RSAUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(
    private val tokenManager: TokenManager,
    private val apiService: AuthApiService
) {
    suspend fun validateSession(): AuthState {
        Log.d("AppLifecycle", "validate session called")
        val refreshToken = tokenManager.getRefreshToken()
        Log.d("AppLifecycle", "refreshToken: $refreshToken")
        if (refreshToken == null) return AuthState.Unauthenticated

        return try {
            val response = apiService.refreshToken(RefreshRequest(refreshToken))
            Log.d("AppLifecycle", response.toString())

            if (response.code == 200 && response.data != null) {
                val responseBody = response.data

                val accessToken = responseBody.accessToken
                val refreshToken = responseBody.refreshToken

                if (refreshToken.isNullOrBlank()) {
                    tokenManager.updateAccessToken(accessToken)
                } else {
                    tokenManager.saveLoginTokens(accessToken, refreshToken)
                    Log.d("AppLifecycle", "refresh success")
                }
                AuthState.Authenticated
            } else {
                Log.d("AppLifecycle", "refresh failed")
                AuthState.Unauthenticated
            }
        } catch (e: Exception) {
            Log.e("AppLifecycle", "error happened while refresh", e)
            AuthState.Unauthenticated
        }
    }

    suspend fun login(username: String, password: String): Result<Unit> {
        return try {
            val response = apiService.login(LoginRequest(username, password))

            if (response.code == 200 && response.data != null) {
                tokenManager.saveLoginTokens(
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

    /*
    * 新用户注册时调用，认证为本校学生，需要带上用户名和密码
    * */
    suspend fun casAuth(
        studentId: String,
        casPassword: String,
        username: String,
        password: String
        ): Result<Unit> {
        return try {
            val publicKeyResponse = apiService.getRSAPublicKey()

            if (publicKeyResponse.code != 200 || publicKeyResponse.data == null) {
                return Result.failure(Exception("Can't get RSA public key"))
            }

            val credential = RSAUtils.sendEncryptCredential(
                userCredential = UserCredential(studentId, casPassword),
                keyId = publicKeyResponse.data.keyId,
                publicKeyBase64 = publicKeyResponse.data.publicKey
            )

            val request = RegisterRequest(
                username = username,
                password = password,
                credentialAuthRequest = credential
            )

            val response = apiService.register(request)

            if (response.code == 200 && response.data != null) {
                Result.success(Unit)
            } else {
                Result.failure(Exception("BIT cas failed"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("BIT cas failed"))
        }
    }

    suspend fun logout() {
        tokenManager.removeLoginToken()
    }
}