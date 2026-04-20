package com.github.garynasser.correction_notebook.data.remote.network

import com.github.garynasser.correction_notebook.data.local.TokenManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response

class AuthInterceptor(private val tokenManager: TokenManager) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val token = runBlocking { tokenManager.accessToken.first() }

        val request = chain.request().newBuilder()
            .addHeader("Authorization", token ?: "")
            .build()

        return chain.proceed(request)
    }

}