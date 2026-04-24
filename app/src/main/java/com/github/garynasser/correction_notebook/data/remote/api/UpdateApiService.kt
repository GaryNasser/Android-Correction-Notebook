package com.github.garynasser.correction_notebook.data.remote.api

import com.github.garynasser.correction_notebook.data.model.common.ApiResponse
import com.github.garynasser.correction_notebook.data.remote.model.AppVersionDto
import retrofit2.http.GET

interface UpdateApiService {
    @GET("app/version/latest")
    suspend fun getLatestVersion(): ApiResponse<AppVersionDto>
}
