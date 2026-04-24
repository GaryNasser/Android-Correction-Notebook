package com.github.garynasser.correction_notebook.data.repository

import com.github.garynasser.correction_notebook.data.model.appupdate.AppVersionInfo
import com.github.garynasser.correction_notebook.data.remote.api.UpdateApiService
import com.github.garynasser.correction_notebook.data.remote.model.toDomain
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AppUpdateRepository @Inject constructor(
    private val updateApiService: UpdateApiService
) {
    suspend fun getLatestVersion(): AppVersionInfo {
        val response = updateApiService.getLatestVersion()
        if (response.code != 200 || response.data == null) {
            throw IllegalStateException(response.message.ifBlank { "检查更新失败" })
        }
        return response.data.toDomain()
    }
}
