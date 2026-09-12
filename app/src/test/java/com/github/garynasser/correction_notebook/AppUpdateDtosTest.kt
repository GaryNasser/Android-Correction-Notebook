package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.remote.model.GitHubReleaseDto
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
}
