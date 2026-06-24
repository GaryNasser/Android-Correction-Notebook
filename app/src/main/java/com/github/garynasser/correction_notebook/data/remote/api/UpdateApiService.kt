package com.github.garynasser.correction_notebook.data.remote.api

import com.github.garynasser.correction_notebook.data.remote.model.GitHubReleaseDto
import retrofit2.http.GET
import retrofit2.http.Headers

interface UpdateApiService {
    @Headers(
        "Accept: application/vnd.github+json",
        "X-GitHub-Api-Version: 2022-11-28",
        "User-Agent: Android-Correction-Notebook"
    )
    @GET("repos/GaryNasser/Android-Correction-Notebook/releases/latest")
    suspend fun getLatestVersion(): GitHubReleaseDto
}
