package com.github.garynasser.correction_notebook.data.repository

import com.github.garynasser.correction_notebook.data.model.appupdate.AppVersionInfo
import com.github.garynasser.correction_notebook.data.remote.api.UpdateApiService
import com.github.garynasser.correction_notebook.data.remote.model.toDomain
import kotlinx.coroutines.withTimeout
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateRepository @Inject constructor(
    private val updateApiService: UpdateApiService
) {
    suspend fun getLatestVersion(): AppVersionInfo {
        return withTimeout(15_000) { updateApiService.getLatestVersion().toDomain() }
    }
}
