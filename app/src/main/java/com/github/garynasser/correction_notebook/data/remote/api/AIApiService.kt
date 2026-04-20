package com.github.garynasser.correction_notebook.data.remote.api

import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.HeaderMap
import retrofit2.http.POST
import retrofit2.http.Url

interface AIApiService {
    @POST
    suspend fun postJson(
        @Url url: String,
        @HeaderMap headers: Map<String, String>,
        @Body request: RequestBody
    ): Response<ResponseBody>
}
