package com.github.garynasser.correction_notebook.data.repository

import com.github.garynasser.correction_notebook.data.local.CredentialManager
import com.github.garynasser.correction_notebook.data.local.TokenManager
import com.github.garynasser.correction_notebook.data.model.auth.CredentialAuthRequest
import com.github.garynasser.correction_notebook.data.model.auth.UserCredential
import com.github.garynasser.correction_notebook.data.remote.api.AuthApiService
import com.github.garynasser.correction_notebook.di.AuthRetrofit
import com.github.garynasser.correction_notebook.utils.RSAUtils
import retrofit2.Retrofit
import javax.inject.Inject

class YanheRepository @Inject constructor(
    private val tokenManager: TokenManager,
    private val credentialManager: CredentialManager,
    private val authService: AuthApiService
) {
    fun saveStudentCredential(credential: UserCredential) {
        credentialManager.saveCredentials(credential)
    }

    fun getStudentCredential(): UserCredential? {
        return credentialManager.getCredentials();
    }

    fun removeStudentCredential() {
        credentialManager.removeCredentials()
    }

    suspend fun getYanheLoginToken(): Result<String> {
        val credential = credentialManager.getCredentials()
            ?: return Result.failure(Exception("Student credential missing"))

        return try {
            val publicKeyResponse = authService.getRSAPublicKey()

            if (publicKeyResponse.code != 200 || publicKeyResponse.data == null) {
                return Result.failure(Exception("Can't get RSA public key"))
            }

            val keyId = publicKeyResponse.data.keyId
            val publicKeyBase64 = publicKeyResponse.data.publicKey

            val encryptStudentPassword = RSAUtils.encrypt(credential.password, publicKeyBase64)

            val request = CredentialAuthRequest(
                keyId = keyId,
                studentId = credential.studentId,
                encryptStudentPassword = encryptStudentPassword
            )

            val response = authService.getYanheToken(request)
            val responseBody = response.data

            if (response.code == 200 && responseBody != null) {
                tokenManager.saveYanheLoginTokens(responseBody)

                Result.success(responseBody)
            } else {
                Result.failure(Exception("Failed to login Yanhe"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Student credential missing"))
        }
    }
}