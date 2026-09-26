package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.repository.bitShareDownloadFallbackUrls
import com.github.garynasser.correction_notebook.utils.BitShareNetworkDetector.NetworkEnvironment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BitShareFallbackTest {
    @Test
    fun intranetDownloadGetsSingleCampusFallback() {
        assertEquals(
            listOf("http://10.170.35.57:8890/api/public/files/file-1/download"),
            bitShareDownloadFallbackUrls(NetworkEnvironment.INTRANET, "file-1")
        )
    }

    @Test
    fun internetDownloadDoesNotRepeatFailedExternalRequest() {
        assertTrue(bitShareDownloadFallbackUrls(NetworkEnvironment.INTERNET, "file-1").isEmpty())
        assertTrue(bitShareDownloadFallbackUrls(NetworkEnvironment.UNKNOWN, "file-1").isEmpty())
    }
}
