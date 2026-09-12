package com.github.garynasser.correction_notebook.ui.update

import com.github.garynasser.correction_notebook.data.model.appupdate.AppVersionInfo
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdatePolicyTest {
    @Test
    fun detectsNewerBuildWhenVersionNameIsUnchanged() {
        val update = resolveAvailableUpdate(
            latest = versionInfo(versionName = "1.3.0", versionCode = 14),
            currentVersionName = "1.3.0",
            currentVersionCode = 13
        )

        assertNotNull(update)
        assertFalse(update!!.forceUpdate)
    }

    @Test
    fun ignoresSameBuildAndVersionName() {
        val update = resolveAvailableUpdate(
            latest = versionInfo(versionName = "1.3.0", versionCode = 13),
            currentVersionName = "1.3.0",
            currentVersionCode = 13
        )

        assertNull(update)
    }

    @Test
    fun marksUnsupportedBuildAsForcedUpdate() {
        val update = resolveAvailableUpdate(
            latest = versionInfo(
                versionName = "1.3.0",
                versionCode = 13,
                minSupportedVersionCode = 12
            ),
            currentVersionName = "1.3.0",
            currentVersionCode = 11
        )

        assertNotNull(update)
        assertTrue(update!!.forceUpdate)
    }

    @Test
    fun fallsBackToVersionNameWhenBuildCodeIsMissing() {
        val update = resolveAvailableUpdate(
            latest = versionInfo(versionName = "1.3.1", versionCode = 0),
            currentVersionName = "1.3.0",
            currentVersionCode = 13
        )

        assertNotNull(update)
    }

    private fun versionInfo(
        versionName: String,
        versionCode: Long,
        minSupportedVersionCode: Long = 0
    ) = AppVersionInfo(
        latestVersionName = versionName,
        latestVersionCode = versionCode,
        minSupportedVersionCode = minSupportedVersionCode,
        downloadUrl = "https://example.com/app.apk",
        updateTitle = "发现新版本",
        updateContent = "",
        forceUpdate = false,
        publishTime = 0
    )
}
