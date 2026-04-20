package com.github.garynasser.correction_notebook.data.remote.api

import com.github.garynasser.correction_notebook.data.model.auth.LoginRequest
import com.github.garynasser.correction_notebook.data.model.auth.RSAResponse
import com.github.garynasser.correction_notebook.data.model.auth.CredentialAuthRequest
import com.github.garynasser.correction_notebook.data.model.auth.RefreshRequest
import com.github.garynasser.correction_notebook.data.model.auth.RegisterRequest
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
    suspend fun refreshToken(
        @Body request: RefreshRequest
    ): ApiResponse<TokenResponse>

    @GET("/auth/public-key")
    suspend fun getRSAPublicKey(): ApiResponse<RSAResponse>

    @POST("/auth/yanhe-token")
    suspend fun getYanheToken(@Body request: CredentialAuthRequest): ApiResponse<String>

    @POST("/auth/register")
    suspend fun register(@Body request: RegisterRequest): ApiResponse<TokenResponse>
}