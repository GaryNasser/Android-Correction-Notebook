package com.github.garynasser.correction_notebook.ui.screens.yanhe

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

@androidx.annotation.OptIn(UnstableApi::class)
class CourseVideoFallbackTest {
    private val generated = "https://aita.yanhekt.cn/play/frim_files/ppt2026/preview.mp4"
    private val original = "https://aita.yanhekt.cn/play/2/vod3/2026/09/22/123/0/VGA/VGA.m3u8"

    private fun httpError(code: Int) = HttpDataSource.InvalidResponseCodeException(
        code, "HTTP error", null, emptyMap(), DataSpec(Uri.parse(generated)), byteArrayOf())

    @Test fun missingGeneratedPptFallsBackOnceAndNestedCausesAreRecognized() {
        val error = IOException("Source error", httpError(404))
        assertEquals(original, nextScreenVideoUrl(error, generated, original))
        assertNull(nextScreenVideoUrl(error, original, original))
        assertNull(nextScreenVideoUrl(error, generated, ""))
    }

    @Test fun networkingOrAuthorizationErrorsDoNotMasqueradeAsMissingPpt() {
        assertNull(nextScreenVideoUrl(IOException("offline"), generated, original))
        listOf(401, 403, 500).forEach { assertNull(nextScreenVideoUrl(httpError(it), generated, original)) }
    }
}
