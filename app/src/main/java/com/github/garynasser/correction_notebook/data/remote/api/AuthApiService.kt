package com.github.garynasser.correction_notebook.data.remote.api

import com.github.garynasser.correction_notebook.data.model.auth.LoginRequest
import com.github.garynasser.correction_notebook.data.model.auth.TokenResponse
import com.github.garynasser.correction_notebook.data.model.common.ApiResponse
import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.HEAD
import retrofit2.http.Header
import retrofit2.http.POST

interface AuthApiService {
    @POST("/auth/login")
    suspend fun login(@Body request: LoginRequest): ApiResponse<TokenResponse>

    @POST("/auth/refresh")
    fun refreshToken(
        @Header("Authorization") refreshToken: String
    ): Call<ApiResponse<TokenResponse>>
}