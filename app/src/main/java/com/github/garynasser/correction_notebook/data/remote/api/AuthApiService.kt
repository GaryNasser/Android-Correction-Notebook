package com.github.garynasser.correction_notebook.data.remote.api

import com.github.garynasser.correction_notebook.data.model.auth.LoginRequest
import com.github.garynasser.correction_notebook.data.model.auth.RSAResponse
import com.github.garynasser.correction_notebook.data.model.auth.CredentialAuthRequest
import com.github.garynasser.correction_notebook.data.model.auth.TokenResponse
import com.github.garynasser.correction_notebook.data.model.common.ApiResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface AuthApiService {
    @POST("/auth/login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<TokenResponse>

    @POST("/auth/refresh")
    fun refreshToken(
        @Header("Authorization") refreshToken: String
    ): Call<ApiResponse<TokenResponse>>

    @GET("/auth/public-key")
    suspend fun getRSAPublicKey(): ApiResponse<RSAResponse>

    @POST("/auth/yanhe-kt")
    suspend fun getYanheToken(@Body request: CredentialAuthRequest): ApiResponse<String>

    @POST("/auth/bit-cas")
    suspend fun casAuth(@Body request: CredentialAuthRequest): ApiResponse<Unit>
}