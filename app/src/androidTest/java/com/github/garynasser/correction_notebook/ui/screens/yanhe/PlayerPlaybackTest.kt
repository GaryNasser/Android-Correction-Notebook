package com.github.garynasser.correction_notebook.ui.screens.yanhe

import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.ByteArrayDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.github.garynasser.correction_notebook.data.datasource.YanheDataSource
import com.github.garynasser.correction_notebook.data.repository.VideoRepository
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import com.github.garynasser.correction_notebook.utils.SignatureUtils
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

@UnstableApi
class PlayerPlaybackTest {
    @get:Rule val compose = createComposeRule()
    private lateinit var player: ExoPlayer

    @Test
    fun nativeHlsPlaybackSeeksReplaysFromCacheAndDetachesOnExit() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val rootUrl = "https://media.example/course/master.m3u8"
        val playlistUrl = "https://media.example/course/low/playlist.m3u8"
        val segmentUrl = "https://media.example/course/low/segment.ts"
        val segment = instrumentation.context.assets.open("player-test.ts").use { it.readBytes() }
        val payloads = mapOf(
            rootUrl to "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=100000\nlow/playlist.m3u8\n".toByteArray(),
            playlistUrl to ("#EXTM3U\n#EXT-X-VERSION:3\n#EXT-X-TARGETDURATION:4\n" +
                "#EXT-X-MEDIA-SEQUENCE:0\n#EXTINF:4.0,\nsegment.ts\n#EXT-X-ENDLIST\n").toByteArray(),
            segmentUrl to segment
        ).mapKeys { SignatureUtils.encryptURL(it.key) }
        val opened = ConcurrentLinkedQueue<String>()
        val tokenVersion = AtomicInteger()
        val upstream = DataSource.Factory {
            YanheDataSource({ url ->
                VideoRepository.YanheAuthData(
                    SignatureUtils.encryptURL(url) + "?Xvideo_Token=${tokenVersion.incrementAndGet()}", emptyMap()
                )
            }, FixtureSource(payloads, opened))
        }
        val cacheDirectory = File(context.cacheDir, "player-test-${UUID.randomUUID()}")
        val database = StandaloneDatabaseProvider(context)
        val cache = SimpleCache(cacheDirectory, LeastRecentlyUsedCacheEvictor(1024 * 1024), database)
        val sources = CacheDataSource.Factory().setCache(cache).setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        val state = mutableStateOf<PlayState>(PlayState.Loading)
        val visible = mutableStateOf(true)
        val frames = AtomicInteger()
        var error: PlaybackException? = null
        try {
            compose.runOnIdle {
                player = ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(sources)).build()
                player.addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(playbackState: Int) {
                        if (playbackState == Player.STATE_READY) state.value = PlayState.Success(rootUrl)
                    }
                    override fun onRenderedFirstFrame() { frames.incrementAndGet() }
                    override fun onPlayerError(failure: PlaybackException) { error = failure }
                })
            }
            compose.setContent {
                CorrectionNotebookTheme {
                    Box(Modifier.safeDrawingPadding().width(320.dp).height(200.dp).testTag("playerBounds")) {
                        if (visible.value) {
                            PlayerScreenContent(player, state.value, "HLS test recording", "Playback QA", {
                                visible.value = false
                            }, {})
                        }
                    }
                }
            }
            compose.runOnIdle {
                player.setMediaItem(MediaItem.Builder().setUri(rootUrl).setMimeType(MimeTypes.APPLICATION_M3U8).build())
                player.prepare()
                player.play()
            }
            compose.waitUntil(15_000) { frames.get() > 0 || error != null }
            compose.runOnIdle {
                assertNull("Native HLS playback must not fail", error)
                player.pause()
                assertTrue(player.duration >= 3900L)
                player.seekTo(2000)
            }
            compose.waitUntil(5_000) { cache.getCachedBytes(segmentUrl, 0, segment.size.toLong()) == segment.size.toLong() }
            val root = compose.onNodeWithTag("playerBounds").fetchSemanticsNode().boundsInRoot
            val surface = compose.onNodeWithTag("playerSurface").fetchSemanticsNode().boundsInRoot
            assertEquals(root.width, surface.width, 1f)
            assertEquals(root.height, surface.height, 1f)
            lateinit var nativeView: PlayerView
            compose.runOnIdle {
                val activity = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).single()
                nativeView = requireNotNull(findPlayerView(activity.window.decorView))
                assertSame(player, nativeView.player)
                assertEquals(2000L, player.currentPosition)
                nativeView.hideController()
            }
            val screenshot = instrumentation.uiAutomation.takeScreenshot()
            assertNotNull(screenshot)
            val pixels = IntArray(screenshot.width * screenshot.height)
            screenshot.getPixels(pixels, 0, screenshot.width, 0, 0, screenshot.width, screenshot.height)
            assertTrue("Decoded test pattern must be visible", pixels.count {
                android.graphics.Color.red(it) > 100 && android.graphics.Color.green(it) < 100
            } > 500)
            File(context.getExternalFilesDir(null), "qa-player-playback.png").outputStream().use {
                screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            screenshot.recycle()

            val previousFrames = frames.get()
            compose.runOnIdle {
                player.stop()
                player.seekTo(0)
                player.prepare()
            }
            compose.waitUntil(15_000) { frames.get() > previousFrames || error != null }
            compose.runOnIdle { assertNull(error) }
            assertEquals("Replaying must read the cached segment without a new token request", 1,
                opened.count { it.substringBefore('?') == SignatureUtils.encryptURL(segmentUrl) })
            compose.onNodeWithContentDescription("返回").performClick()
            compose.runOnIdle { assertNull("Leaving the screen must detach the video surface", nativeView.player) }
        } finally {
            compose.runOnIdle {
                visible.value = false
                if (::player.isInitialized) player.release()
            }
            cache.release()
            database.close()
            cacheDirectory.deleteRecursively()
        }
    }

    private fun findPlayerView(view: View): PlayerView? {
        if (view is PlayerView) return view
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) findPlayerView(view.getChildAt(index))?.let { return it }
        }
        return null
    }

    private class FixtureSource(
        private val payloads: Map<String, ByteArray>,
        private val opened: ConcurrentLinkedQueue<String>
    ) : DataSource {
        private var delegate: ByteArrayDataSource? = null
        override fun addTransferListener(listener: TransferListener) = Unit
        override fun open(spec: DataSpec): Long {
            val url = spec.uri.toString()
            opened.add(url)
            val bytes = payloads[url.substringBefore('?')] ?: throw IOException("Unexpected HLS URL: $url")
            return ByteArrayDataSource(bytes).also { delegate = it }.open(spec)
        }
        override fun getUri() = delegate?.uri
        override fun read(buffer: ByteArray, offset: Int, length: Int) = requireNotNull(delegate).read(buffer, offset, length)
        override fun close() { delegate?.close(); delegate = null }
    }
}
