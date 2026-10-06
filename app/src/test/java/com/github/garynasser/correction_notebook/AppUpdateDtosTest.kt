package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.remote.model.GitHubReleaseDto
import com.github.garynasser.correction_notebook.data.remote.model.GitHubReleaseAssetDto
import com.github.garynasser.correction_notebook.data.remote.model.toDomain
import org.junit.Assert.assertEquals
import org.junit.Test

class AppUpdateDtosTest {
    @Test
    fun githubReleaseDoesNotInventAndroidVersionCode() {
        val version = GitHubReleaseDto(
            tagName = "v1.3.0",
            htmlUrl = "https://example.com/release"
        ).toDomain()

        assertEquals(0L, version.latestVersionCode)
        assertEquals("v1.3.0", version.latestVersionName)
    }

    @Test fun emptyApkUrlFallsBackToTheReleasePage() {
        val version = GitHubReleaseDto(tagName = "v1.3.0", htmlUrl = "https://example.com/release",
            assets = listOf(GitHubReleaseAssetDto("app.apk", "  "))).toDomain()
        assertEquals("https://example.com/release", version.downloadUrl)
    }

    @Test fun choosesAnApkInsteadOfSourceArchivesAndKeepsReleaseNotes() {
        val version = GitHubReleaseDto(tagName = "v1.3.0", name = "BITStudy 1.3.0", body = "更新内容",
            htmlUrl = "https://example.com/release", publishedAt = "2026-06-25T07:39:12Z",
            assets = listOf(GitHubReleaseAssetDto("source.zip", "https://example.com/source.zip"),
                GitHubReleaseAssetDto("BITStudy.APK", "https://example.com/app.apk"))).toDomain()
        assertEquals("https://example.com/app.apk", version.downloadUrl)
        assertEquals("更新内容", version.updateContent)
        assertEquals("BITStudy 1.3.0", version.updateTitle)
        assertEquals(1782373152000L, version.publishTime)
    }
}
