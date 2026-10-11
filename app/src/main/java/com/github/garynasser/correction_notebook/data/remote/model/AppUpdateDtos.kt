package com.github.garynasser.correction_notebook.data.remote.model

import com.github.garynasser.correction_notebook.data.model.appupdate.AppVersionInfo
import com.github.garynasser.correction_notebook.data.remote.api.UpdateApiService
import com.google.gson.annotations.SerializedName
import java.time.Instant

data class WebsiteReleaseDto(
    @SerializedName("tag") val tag: String? = null,
    @SerializedName("notes") val notes: String? = null,
    @SerializedName("published_at") val publishedAt: String? = null
)

fun WebsiteReleaseDto.toDomain(): AppVersionInfo {
    val versionName = tag.orEmpty().trim()
    require(versionName.matches(Regex("[vV]?\\d+(\\.\\d+)*"))) { "Invalid website release version" }

    return AppVersionInfo(
        latestVersionName = versionName,
        // The mirror exposes the release tag, not Android's versionCode.
        latestVersionCode = 0,
        minSupportedVersionCode = 0,
        downloadUrl = UpdateApiService.DOWNLOAD_PAGE_URL,
        updateTitle = "发现新版本",
        updateContent = notes.orEmpty(),
        forceUpdate = false,
        publishTime = publishedAt.toEpochMillisOrZero()
    )
}

private fun String?.toEpochMillisOrZero(): Long {
    if (this.isNullOrBlank()) return 0L
    return runCatching { Instant.parse(this).toEpochMilli() }.getOrDefault(0L)
}
