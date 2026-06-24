package com.github.garynasser.correction_notebook.data.remote.model

import com.github.garynasser.correction_notebook.data.model.appupdate.AppVersionInfo
import com.google.gson.annotations.SerializedName
import java.time.Instant

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

data class GitHubReleaseDto(
    @SerializedName("tag_name") val tagName: String,
    @SerializedName("name") val name: String? = null,
    @SerializedName("body") val body: String? = null,
    @SerializedName("html_url") val htmlUrl: String,
    @SerializedName("published_at") val publishedAt: String? = null,
    @SerializedName("assets") val assets: List<GitHubReleaseAssetDto>? = emptyList()
)

data class GitHubReleaseAssetDto(
    @SerializedName("name") val name: String,
    @SerializedName("browser_download_url") val browserDownloadUrl: String
)

fun GitHubReleaseDto.toDomain(): AppVersionInfo {
    val downloadUrl = assets
        .orEmpty()
        .firstOrNull { it.name.endsWith(".apk", ignoreCase = true) }
        ?.browserDownloadUrl
        ?: htmlUrl

    return AppVersionInfo(
        latestVersionName = tagName,
        latestVersionCode = tagName.toVersionCode(),
        minSupportedVersionCode = 0,
        downloadUrl = downloadUrl,
        updateTitle = name?.takeIf { it.isNotBlank() } ?: "发现新版本",
        updateContent = body.orEmpty(),
        forceUpdate = false,
        publishTime = publishedAt.toEpochMillisOrZero()
    )
}

private fun String?.toEpochMillisOrZero(): Long {
    if (this.isNullOrBlank()) return 0L
    return runCatching { Instant.parse(this).toEpochMilli() }.getOrDefault(0L)
}

private fun String.toVersionCode(): Long {
    val parts = trim()
        .removePrefix("v")
        .removePrefix("V")
        .split('.', '-', '_')
        .mapNotNull { it.takeWhile { char -> char.isDigit() }.toLongOrNull() }
        .take(3)

    return parts.foldIndexed(0L) { index, acc, value ->
        val multiplier = when (index) {
            0 -> 1_000_000L
            1 -> 1_000L
            else -> 1L
        }
        acc + value * multiplier
    }
}
