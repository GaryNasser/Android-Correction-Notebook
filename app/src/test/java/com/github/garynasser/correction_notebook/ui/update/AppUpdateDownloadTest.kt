package com.github.garynasser.correction_notebook.ui.update

import org.junit.Assert.*
import org.junit.Test

class AppUpdateDownloadTest {
    @Test fun rejectsIncompleteUrlsAndNonWebSchemes() {
        listOf("", "https:", "http:/app.apk", "https:///app.apk", "https:example.com/app.apk",
            "https://", "https://example.com:99999/app.apk", "https://example.com:0/app.apk",
            "https://exa mple.com/app.apk", "https://example.com/app .apk",
            "file:///storage/app.apk", "javascript:alert(1)", "intent://example.com/app.apk").forEach {
            assertNull(it, validatedUpdateUrl(it))
        }
    }

    @Test fun allowsUppercaseSchemeAndPreservesEncodedSignedDownloadParameters() {
        val url = "HTTPS://example.com/app%20release.apk?signature=a%2Fb%3D&download=1#release"
        assertEquals(url, validatedUpdateUrl("  $url\n"))
    }

    @Test fun releasePagesAndHttpUrlsAreValidDestinations() {
        assertEquals("https://github.com/example/app/releases/tag/v2", validatedUpdateUrl("https://github.com/example/app/releases/tag/v2"))
        assertEquals("http://example.com:8080/app.apk", validatedUpdateUrl("http://example.com:8080/app.apk"))
    }
}
