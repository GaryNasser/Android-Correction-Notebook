package com.github.garynasser.correction_notebook.data.remote.model

import com.github.garynasser.correction_notebook.data.model.appupdate.AppVersionInfo
import com.google.gson.annotations.SerializedName

data class AppVersionDto(
    @SerializedName("latestVersionName") val latestVersionName: String,
    @SerializedName("latestVersionCode") val latestVersionCode: Long,
    @SerializedName("minSupportedVersionCode") val minSupportedVersionCode: Long = 0,
    @SerializedName("downloadUrl") val downloadUrl: String,
    @SerializedName("updateTitle") val updateTitle: String = "发现新版本",
    @SerializedName("updateContent") val updateContent: String = "",
    @SerializedName("forceUpdate") val forceUpdate: Boolean = false,
    @SerializedName("publishTime") val publishTime: Long = 0L
)

fun AppVersionDto.toDomain(): AppVersionInfo {
    return AppVersionInfo(
        latestVersionName = latestVersionName,
        latestVersionCode = latestVersionCode,
        minSupportedVersionCode = minSupportedVersionCode,
        downloadUrl = downloadUrl,
        updateTitle = updateTitle,
        updateContent = updateContent,
        forceUpdate = forceUpdate,
        publishTime = publishTime
    )
}
