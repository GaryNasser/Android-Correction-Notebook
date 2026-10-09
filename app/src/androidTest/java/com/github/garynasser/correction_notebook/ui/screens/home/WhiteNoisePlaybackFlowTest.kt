package com.github.garynasser.correction_notebook.ui.screens.home

import android.content.ComponentName
import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.R
import com.github.garynasser.correction_notebook.data.model.home.WhiteNoise
import com.github.garynasser.correction_notebook.data.model.home.WhiteNoiseState
import com.github.garynasser.correction_notebook.domain.usecase.WhiteNoisePlayback
import com.github.garynasser.correction_notebook.service.WhiteNoisePlaybackService
import com.github.garynasser.correction_notebook.ui.components.LocalAiEnabled
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.util.concurrent.TimeUnit

@OptIn(UnstableApi::class)
class WhiteNoisePlaybackFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allFourBundledTracksPlayThroughTheLoopingMediaSession() = withFixture { f ->
        render(f)
        compose.onNodeWithContentDescription("更多").performClick()
        withProbe(f) { probe ->
            WhiteNoise.entries.forEach { noise ->
                compose.onNodeWithContentDescription(noise.displayName).performScrollTo().performClick()
                f.await { f.home.whiteNoisePlayback.state.value.isPlaying && f.home.whiteNoisePlayback.state.value.selectedNoise == noise }
                awaitPlayer(probe) { it.isPlaying && it.currentMediaItem?.mediaId == noise.name }
                compose.onNodeWithText("正在播放：${noise.displayName}").performScrollTo().assertIsDisplayed()
                compose.onNodeWithContentDescription(noise.displayName).assertIsSelected().assertHeightIsAtLeast(48.dp)
                withContext(Dispatchers.Main) {
                    assertEquals(Player.REPEAT_MODE_ONE, probe.repeatMode)
                    assertEquals(0.5f, probe.volume, 0.001f)
                }
            }
            compose.onNodeWithContentDescription("无白噪音").performClick()
            awaitPlayer(probe) { !it.isPlaying && it.mediaItemCount == 0 }
            assertEquals(WhiteNoiseState(), f.home.whiteNoisePlayback.state.value)
            compose.onNodeWithContentDescription("无白噪音").assertIsSelected()
        }
    }

    @Test fun theNewestSelectionWinsWhileTheServiceIsConnecting() = withFixture { f ->
        render(f)
        withContext(Dispatchers.Main) {
            f.home.whiteNoisePlayback.select(WhiteNoise.RAIN)
            f.home.whiteNoisePlayback.select(WhiteNoise.OCEAN)
            f.home.whiteNoisePlayback.select(WhiteNoise.CAFE)
        }
        f.await { f.home.whiteNoisePlayback.state.value.isPlaying }
        assertEquals(WhiteNoise.CAFE, f.home.whiteNoisePlayback.state.value.selectedNoise)
        withProbe(f) { probe -> awaitPlayer(probe) { it.isPlaying && it.currentMediaItem?.mediaId == WhiteNoise.CAFE.name } }
    }

    @Test fun stoppingAPendingConnectionCannotStartAudioLater() = withFixture { f ->
        render(f)
        withContext(Dispatchers.Main) {
            f.home.whiteNoisePlayback.select(WhiteNoise.RAIN)
            f.home.whiteNoisePlayback.stop()
        }
        withProbe(f) { probe ->
            awaitPlayer(probe) { !it.isPlaying && it.mediaItemCount == 0 }
            assertEquals(WhiteNoiseState(), f.home.whiteNoisePlayback.state.value)
            withContext(Dispatchers.Main) { f.home.whiteNoisePlayback.select(WhiteNoise.FOREST) }
            f.await { f.home.whiteNoisePlayback.state.value.isPlaying }
            awaitPlayer(probe) { it.currentMediaItem?.mediaId == WhiteNoise.FOREST.name && it.isPlaying }
        }
    }

    @Test fun externalMediaControlsUpdateTheSheetWithoutChangingTheTimer() = withFixture { f ->
        render(f)
        compose.onNodeWithContentDescription("更多").performClick()
        val timer = f.home.timerManager.timerState.value
        compose.onNodeWithContentDescription("雨声").performScrollTo().performClick()
        f.await { f.home.whiteNoisePlayback.state.value.isPlaying }
        withProbe(f) { probe ->
            withContext(Dispatchers.Main) { probe.pause() }
            f.await { !f.home.whiteNoisePlayback.state.value.isPlaying && !f.home.whiteNoisePlayback.state.value.isLoading }
            compose.onNodeWithText("已暂停：雨声").performScrollTo().assertIsDisplayed()
            withContext(Dispatchers.Main) { probe.play() }
            f.await { f.home.whiteNoisePlayback.state.value.isPlaying }
            compose.onNodeWithText("正在播放：雨声").assertIsDisplayed()
            withContext(Dispatchers.Main) { probe.stop() }
            f.await("Stopped media must not remain loading") {
                !f.home.whiteNoisePlayback.state.value.isPlaying && !f.home.whiteNoisePlayback.state.value.isLoading
            }
            compose.onNodeWithText("已暂停：雨声").assertIsDisplayed()
        }
        assertEquals(timer, f.home.timerManager.timerState.value)
    }

    @Test fun rebuildingTheCompositionDoesNotRestartOrReleaseTheTrack() = withFixture { f ->
        val restoration = render(f)
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithContentDescription("雨声").performScrollTo().performClick()
        f.await { f.home.whiteNoisePlayback.state.value.isPlaying }
        withProbe(f) { probe ->
            awaitPlayer(probe) { it.isPlaying && it.currentPosition > 250 }
            val before = withContext(Dispatchers.Main) { probe.currentPosition }
            restoration.emulateSavedInstanceStateRestore()
            compose.onNodeWithContentDescription("更多").performClick()
            compose.onNodeWithContentDescription("雨声").assertIsSelected()
            compose.onNodeWithText("正在播放：雨声").performScrollTo().assertIsDisplayed()
            withContext(Dispatchers.Main) { assertTrue(probe.currentPosition >= before) }
            assertTrue(f.home.whiteNoisePlayback.state.value.isPlaying)
        }
    }

    @Test fun exitingTheSessionStopsPlaybackAndClearsTheSelection() = withFixture { f ->
        render(f)
        withContext(Dispatchers.Main) { f.home.whiteNoisePlayback.select(WhiteNoise.RAIN) }
        f.await { f.home.whiteNoisePlayback.state.value.isPlaying }
        withProbe(f) { probe ->
            withContext(Dispatchers.Main) { f.home.finishCurrentSessionAndExit() }
            f.await { f.home.uiState.value.selectedMode == null && !f.home.uiState.value.isSavingStudySession }
            awaitPlayer(probe) { !it.isPlaying && it.mediaItemCount == 0 }
            assertEquals(WhiteNoiseState(), f.home.whiteNoisePlayback.state.value)
        }
    }

    @Test fun clearingTheOwnerReleasesPlaybackRatherThanLeavingAudioBehind() = withFixture { f ->
        render(f)
        val playback = f.home.whiteNoisePlayback
        withContext(Dispatchers.Main) { playback.select(WhiteNoise.OCEAN) }
        f.await { playback.state.value.isPlaying }
        withProbe(f) { probe ->
            withContext(Dispatchers.Main) { f.store.clear() }
            awaitPlayer(probe) { !it.isPlaying && it.mediaItemCount == 0 }
            assertEquals(WhiteNoiseState(), playback.state.value)
        }
    }

    @Test fun lightPlaybackErrorsStayVisibleAndCanBeRetried() = checkError(false)
    @Test fun darkPlaybackErrorsStayVisibleAndCanBeRetried() = checkError(true)

    private fun checkError(dark: Boolean) = withFixture { f ->
        var fail = true
        val playback = WhiteNoisePlayback(f.context) { noise ->
            MediaItem.Builder().setMediaId(noise.name)
                .setUri(if (fail) "file://${f.context.cacheDir}/missing-noise-${System.nanoTime()}.mp3"
                    else "android.resource://${f.context.packageName}/${R.raw.rain}")
                .build()
        }
        try {
            compose.setContent {
                val state by playback.state.collectAsState()
                CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                    CorrectionNotebookTheme(darkTheme = dark, dynamicColor = false) {
                        Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                            ImmersiveStudyScreen(f.home.timerManager, onExit = {}, soundEnabled = false, vibrationEnabled = false,
                                noiseState = state, onNoiseSelect = playback::select, onNoiseNone = playback::stop, onNoiseRetry = playback::retry)
                        }
                    }
                }
            }
            compose.onNodeWithContentDescription("更多").performClick()
            compose.onNodeWithContentDescription("雨声").performScrollTo().performClick()
            assertNotNull("Noise button must dispatch its selection", playback.state.value.selectedNoise)
            awaitNoiseState(playback) { it.error != null }
            assertFalse(playback.state.value.isLoading)
            assertFalse(playback.state.value.isPlaying)
            compose.onNodeWithText("白噪音播放失败，请重试").performScrollTo().assertIsDisplayed()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            compose.onNodeWithContentDescription("重试白噪音").assertIsDisplayed()
            listOf("无白噪音", "雨声", "海浪", "森林", "咖啡馆").forEach {
                compose.onNodeWithContentDescription(it).performScrollTo().assertIsDisplayed()
                    .assertHeightIsAtLeast(48.dp).assertWidthIsAtLeast(48.dp)
            }
            val bounds = listOf("无白噪音", "雨声", "海浪", "森林", "咖啡馆").map {
                compose.onNodeWithContentDescription(it).fetchSemanticsNode().boundsInRoot
            }
            assertTrue(bounds.first().left >= 0)
            assertTrue(bounds.last().right <= f.context.resources.displayMetrics.widthPixels)
            bounds.zipWithNext().forEach { (left, right) -> assertTrue(left.right <= right.left) }
            capture(if (dark) "dark-error" else "light-error")
            fail = false
            compose.onNodeWithContentDescription("重试白噪音").performClick()
            awaitNoiseState(playback) { it.isPlaying }
            compose.onNodeWithText("白噪音播放失败，请重试").assertDoesNotExist()
            compose.onNodeWithText("正在播放：雨声").performScrollTo().assertIsDisplayed()
            capture(if (dark) "dark-playing" else "light-playing")
            compose.onNodeWithContentDescription("雨声").performClick()
            assertEquals(WhiteNoiseState(), playback.state.value)
            compose.onNodeWithContentDescription("无白噪音").assertIsSelected()
        } finally { withContext(NonCancellable + Dispatchers.Main) { playback.stop() } }
    }

    private fun render(f: HomeFormSaveFailureTest.Fixture) = StateRestorationTester(compose).also { it.setContent {
        CompositionLocalProvider(LocalAiEnabled provides false, LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
            CorrectionNotebookTheme(dynamicColor = false) {
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) { HomeScreen(f.home, f.statistics) }
            }
        }
    } }

    private suspend fun withProbe(f: HomeFormSaveFailureTest.Fixture, test: suspend (MediaController) -> Unit) {
        val future = withContext(Dispatchers.Main) {
            MediaController.Builder(f.context, SessionToken(f.context, ComponentName(f.context, WhiteNoisePlaybackService::class.java))).buildAsync()
        }
        try { test(withContext(Dispatchers.IO) { future.get(10, TimeUnit.SECONDS) }) }
        finally { withContext(NonCancellable + Dispatchers.Main) { MediaController.releaseFuture(future) } }
    }

    private suspend fun awaitPlayer(player: Player, predicate: (Player) -> Boolean) {
        withTimeout(10_000) { while (!withContext(Dispatchers.Main) { predicate(player) }) delay(10) }
    }

    private suspend fun awaitNoiseState(playback: WhiteNoisePlayback, predicate: (WhiteNoiseState) -> Boolean) {
        try {
            withTimeout(10_000) { while (!predicate(playback.state.value)) delay(10) }
        } catch (error: TimeoutCancellationException) {
            throw AssertionError("White noise state did not settle: ${playback.state.value}", error)
        }
    }

    private fun withFixture(test: suspend (HomeFormSaveFailureTest.Fixture) -> Unit) = runBlocking {
        val f = HomeFormSaveFailureTest.Fixture()
        try {
            withContext(Dispatchers.Main) {
                f.create()
                f.home.startPomodoro()
                f.home.selectMode(StudyMode.IMMERSIVE)
                f.home.timerManager.pause()
            }
            f.await { f.home.uiState.value.scheduleSections.size == 7 }
            withTimeout(40_000) { test(f) }
        } finally {
            withContext(NonCancellable) {
                withContext(Dispatchers.Main) { f.store.clear() }
                f.scope.cancel()
                f.scope.coroutineContext[Job]?.join()
                f.files.forEach { it.delete() }
                f.database.close()
                f.knowledge.close()
                f.network.dispatcher.executorService.shutdownNow()
                f.network.connectionPool.evictAll()
            }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(350)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "qa-white-noise-$name.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }
}
