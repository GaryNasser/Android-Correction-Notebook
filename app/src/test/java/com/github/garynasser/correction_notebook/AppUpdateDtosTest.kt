package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.remote.api.UpdateApiService
import com.github.garynasser.correction_notebook.data.remote.model.WebsiteReleaseDto
import com.github.garynasser.correction_notebook.data.remote.model.toDomain
import com.github.garynasser.correction_notebook.data.repository.AppUpdateRepository
import com.github.garynasser.correction_notebook.di.NetworkModule
import com.github.garynasser.correction_notebook.ui.update.resolveAvailableUpdate
import com.google.gson.Gson
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class AppUpdateDtosTest {
    @Test
    fun officialWebsiteContractChecksVersionAndOpensTheDownloadSection() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            assertEquals("https://bitstudy.fangwenky.dpdns.org/api/release.json", request.url.toString())
            assertEquals("GET", request.method)
            assertEquals("application/json", request.header("Accept"))
            assertEquals("no-cache", request.header("Cache-Control"))
            assertNull(request.header("Authorization"))
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body("""{"release_id":409538936,"tag":"v1.0.5","notes":"更新内容", "published_at":"2026-10-11T08:33:30Z","download_url":"/downloads/409538936/630223572-BITStudy-v1.0.5.apk","assets":[]}"""
                    .toResponseBody("application/json".toMediaType())).build()
        }.build()
        try {
            val version = AppUpdateRepository(NetworkModule.provideUpdateApiService(client)).getLatestVersion()
            assertEquals("v1.0.5", version.latestVersionName)
            assertEquals(0L, version.latestVersionCode)
            assertEquals("更新内容", version.updateContent)
            assertEquals(1791707610000L, version.publishTime)
            assertEquals("https://bitstudy.fangwenky.dpdns.org/#download", version.downloadUrl)
            assertFalse(version.forceUpdate)
            assertNull(resolveAvailableUpdate(version, "1.0.5", 6))
            assertNotNull(resolveAvailableUpdate(version, "1.0.4", 5))
            assertNull(resolveAvailableUpdate(version, "1.0.6", 7))
        } finally {
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
    }

    @Test
    fun absentNotesOrInvalidDateDoNotPreventCheckingAValidVersion() {
        val version = WebsiteReleaseDto(tag = "v1.0.6", publishedAt = "invalid").toDomain()
        assertEquals("", version.updateContent)
        assertEquals(0L, version.publishTime)
        assertEquals(UpdateApiService.DOWNLOAD_PAGE_URL, version.downloadUrl)
    }

    @Test
    fun missingOrMalformedVersionFailsInsteadOfReportingUpToDate() {
        listOf("{}", "{\"tag\":null}", "{\"tag\":\" \"}", "{\"tag\":\"not-a-version\"}").forEach { json ->
            val release = Gson().fromJson(json, WebsiteReleaseDto::class.java)
            assertThrows(IllegalArgumentException::class.java) { release.toDomain() }
        }
    }

    @Test
    fun remoteDownloadLinksAndForceFlagsCannotChangeTheWebsiteDestination() {
        val release = Gson().fromJson(
            """{"tag":"v1.0.6","download_url":"https://example.com/app.apk","release_url":"https://github.com/example/release","forceUpdate":true}""",
            WebsiteReleaseDto::class.java
        ).toDomain()
        assertEquals(UpdateApiService.DOWNLOAD_PAGE_URL, release.downloadUrl)
        assertFalse(release.forceUpdate)
    }
}
