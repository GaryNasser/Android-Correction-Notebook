package com.github.garynasser.correction_notebook.data.datasource

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DataSourceInputStream
import androidx.media3.exoplayer.hls.playlist.HlsMultivariantPlaylist
import androidx.media3.exoplayer.hls.playlist.HlsPlaylistParser
import com.github.garynasser.correction_notebook.data.repository.VideoRepository
import com.github.garynasser.correction_notebook.utils.SignatureUtils
import java.io.IOException
import org.junit.Assert.*
import org.junit.Test

@UnstableApi
class YanheDataSourceTest {
    private fun authorize(url: String) = VideoRepository.YanheAuthData(
        SignatureUtils.encryptURL(url) + "?Xvideo_Token=test-token",
        mapOf("Origin" to "https://www.yanhekt.cn")
    )

    @Test
    fun nestedHlsPlaylistResolvesAgainstOriginalPathNotTheAuthorizationDirectory() {
        val masterUrl = "https://media.example/course/master.m3u8"
        val bytes = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=100000\nlow/playlist.m3u8\n".toByteArray()
        val source = YanheDataSource({ authorize(it) }, ByteArrayDataSource(bytes))
        val stream = DataSourceInputStream(source, DataSpec(Uri.parse(masterUrl)))
        val playlist = stream.use {
            stream.open()
            HlsPlaylistParser().parse(requireNotNull(source.uri), it) as HlsMultivariantPlaylist
        }
        val child = "https://media.example/course/low/playlist.m3u8"
        assertEquals(child, playlist.variants.single().url.toString())
        assertEquals(authorize(child).authenticatedUrl, authorize(playlist.variants.single().url.toString()).authenticatedUrl)
        assertNull(source.uri)
    }

    @Test
    fun authorizationPreservesRangeHeadersAndResponseMetadata() {
        val headers = mapOf("Content-Type" to listOf("video/mp4"))
        val base = RecordingSource(responseHeaders = headers)
        val source = YanheDataSource({ authorize(it) }, base)
        val spec = DataSpec.Builder().setUri("https://media.example/lecture.mp4")
            .setPosition(128).setLength(256).setKey("lecture")
            .setHttpRequestHeaders(mapOf("Accept" to "video/*")).build()
        assertEquals(256L, source.open(spec))
        val sent = requireNotNull(base.opened)
        assertEquals(128L, sent.position)
        assertEquals(256L, sent.length)
        assertEquals("lecture", sent.key)
        assertEquals("video/*", sent.httpRequestHeaders["Accept"])
        assertEquals("https://www.yanhekt.cn", sent.httpRequestHeaders["Origin"])
        assertEquals(headers, source.responseHeaders)
        source.close()
        assertNull(source.uri)
        assertTrue(source.responseHeaders.isEmpty())
    }

    @Test
    fun genuineHttpRedirectIsStillReported() {
        val redirect = Uri.parse("https://cdn.example/redirected/master.m3u8")
        val source = YanheDataSource({ authorize(it) }, RecordingSource(redirect))
        source.open(DataSpec(Uri.parse("https://media.example/master.m3u8")))
        assertEquals(redirect, source.uri)
        source.close()
    }

    @Test
    fun authorizationFailureIsAnIoErrorAndCanBeRetriedAfterClose() {
        var attempts = 0
        val failure = IllegalStateException("Authorization unavailable")
        val base = RecordingSource()
        val source = YanheDataSource({
            if (attempts++ == 0) throw failure
            authorize(it)
        }, base)
        val spec = DataSpec(Uri.parse("https://media.example/lecture.mp4"))
        try {
            source.open(spec)
            fail("Expected an IOException")
        } catch (error: IOException) {
            assertSame(failure, error.cause)
        } finally {
            source.close()
        }
        assertNull(source.uri)
        source.open(spec)
        assertEquals(spec.uri, source.uri)
        assertEquals(2, attempts)
        source.close()
    }

    private class RecordingSource(
        private val redirect: Uri? = null,
        private val responseHeaders: Map<String, List<String>> = emptyMap()
    ) : DataSource {
        var opened: DataSpec? = null
        override fun addTransferListener(listener: androidx.media3.datasource.TransferListener) = Unit
        override fun open(dataSpec: DataSpec): Long {
            opened = dataSpec
            return dataSpec.length
        }
        override fun read(buffer: ByteArray, offset: Int, length: Int) = -1
        override fun getUri(): Uri? = opened?.let { redirect ?: it.uri }
        override fun getResponseHeaders() = if (opened != null) responseHeaders else emptyMap()
        override fun close() { opened = null }
    }
}
