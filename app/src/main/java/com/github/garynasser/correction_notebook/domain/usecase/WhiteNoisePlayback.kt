package com.github.garynasser.correction_notebook.domain.usecase

import android.content.ComponentName
import android.content.Context
import androidx.annotation.OptIn
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.github.garynasser.correction_notebook.R
import com.github.garynasser.correction_notebook.data.model.home.WhiteNoise
import com.github.garynasser.correction_notebook.data.model.home.WhiteNoiseState
import com.github.garynasser.correction_notebook.service.WhiteNoisePlaybackService
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@OptIn(UnstableApi::class)
class WhiteNoisePlayback internal constructor(
    context: Context,
    private val mediaItem: (WhiteNoise) -> MediaItem = { it.toMediaItem(context.applicationContext) }
) {
    private val context = context.applicationContext
    private val _state = MutableStateFlow(WhiteNoiseState())
    val state = _state.asStateFlow()
    private var future: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (player !== controller || player.currentMediaItem?.mediaId != _state.value.selectedNoise?.name) return
            _state.value = _state.value.copy(
                isLoading = player.playerError == null && player.playWhenReady && player.playbackState == Player.STATE_BUFFERING,
                isPlaying = player.isPlaying,
                error = if (player.playerError != null) PLAYBACK_ERROR else null
            )
        }

    }

    fun select(noise: WhiteNoise) {
        if (_state.value.selectedNoise == noise && _state.value.error == null) stop()
        else play(noise)
    }

    fun retry() {
        _state.value.selectedNoise?.let(::play)
    }

    private fun play(noise: WhiteNoise) {
        _state.value = WhiteNoiseState(selectedNoise = noise, isLoading = true)
        val connected = controller
        if (connected != null) {
            start(connected, noise)
            return
        }
        if (future != null) return
        try {
            val request = MediaController.Builder(context, SessionToken(context, ComponentName(context, WhiteNoisePlaybackService::class.java)))
                .setListener(object : MediaController.Listener {
                    override fun onDisconnected(disconnected: MediaController) {
                        if (controller !== disconnected) return
                        controller = null
                        val old = future
                        future = null
                        old?.let(MediaController::releaseFuture)
                        if (_state.value.selectedNoise != null) fail("白噪音连接已断开，请重试")
                    }
                }).buildAsync()
            future = request
            request.addListener({
                // A stop or a newer connection must not resurrect an old playback request.
                if (future !== request) return@addListener
                try {
                    val connectedController = request.get()
                    controller = connectedController
                    connectedController.addListener(listener)
                    _state.value.selectedNoise?.let { start(connectedController, it) }
                } catch (error: InterruptedException) {
                    Thread.currentThread().interrupt()
                    connectionFailed(request)
                } catch (_: Exception) {
                    connectionFailed(request)
                }
            }, ContextCompat.getMainExecutor(context))
        } catch (_: Exception) {
            fail("白噪音连接失败，请重试")
        }
    }

    private fun connectionFailed(request: ListenableFuture<MediaController>) {
        if (future !== request) return
        future = null
        MediaController.releaseFuture(request)
        fail("白噪音连接失败，请重试")
    }

    private fun start(connected: MediaController, noise: WhiteNoise) {
        try {
            connected.setMediaItem(mediaItem(noise))
            connected.prepare()
            connected.play()
        } catch (_: Exception) {
            connected.stop()
            fail(PLAYBACK_ERROR)
        }
    }

    private fun fail(message: String) {
        _state.value = _state.value.copy(isLoading = false, isPlaying = false, error = message)
    }

    fun stop() {
        _state.value = WhiteNoiseState()
        val oldController = controller
        val oldFuture = future
        controller = null
        future = null
        oldController?.removeListener(listener)
        oldController?.stop()
        oldController?.clearMediaItems()
        oldFuture?.let(MediaController::releaseFuture)
    }

    companion object {
        private const val PLAYBACK_ERROR = "白噪音播放失败，请重试"
    }
}

private fun WhiteNoise.toMediaItem(context: Context): MediaItem {
    val resource = when (this) {
        WhiteNoise.RAIN -> R.raw.rain
        WhiteNoise.OCEAN -> R.raw.ocean
        WhiteNoise.FOREST -> R.raw.forest
        WhiteNoise.CAFE -> R.raw.cafe
    }
    return MediaItem.Builder()
        .setMediaId(name)
        .setUri("android.resource://${context.packageName}/$resource")
        .setMimeType(MimeTypes.AUDIO_MPEG)
        .setMediaMetadata(MediaMetadata.Builder().setTitle("$displayName · 白噪音").setArtist("BITStudy").build())
        .build()
}
