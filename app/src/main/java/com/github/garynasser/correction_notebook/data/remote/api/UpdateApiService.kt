package com.github.garynasser.correction_notebook.data.remote.api

import com.github.garynasser.correction_notebook.data.remote.model.WebsiteReleaseDto
import retrofit2.http.GET
import retrofit2.http.Headers

interface UpdateApiService {
    @Headers(
        "Accept: application/json",
        "Cache-Control: no-cache"
    )
    @GET("api/release.json")
    suspend fun getLatestVersion(): WebsiteReleaseDto

    companion object {
        const val BASE_URL = "https://bitstudy.fangwenky.dpdns.org/"
        const val DOWNLOAD_PAGE_URL = "${BASE_URL}#download"
    }
}
