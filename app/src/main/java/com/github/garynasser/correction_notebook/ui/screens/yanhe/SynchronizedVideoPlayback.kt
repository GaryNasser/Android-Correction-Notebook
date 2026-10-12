package com.github.garynasser.correction_notebook.ui.screens.yanhe

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.PlaybackParameters
import kotlin.math.abs

internal enum class VideoDisplayMode(val label: String) {
    BOTH("双画面"), SCREEN("PPT"), CAMERA("教室")
}

internal class SynchronizedVideoPlayback(private val primary: Player, private val screen: Player) {
    private var active = true
    private val listener = object : Player.Listener {
        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) { synchronize() }
        override fun onIsPlayingChanged(isPlaying: Boolean) { synchronize() }
        override fun onEvents(player: Player, events: Player.Events) { synchronize() }
        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int
        ) { synchronize(forceSeek = true) }
    }

    init { primary.addListener(listener) }

    fun setActive(value: Boolean) {
        active = value
        synchronize(forceSeek = value)
    }

    fun synchronize(forceSeek: Boolean = false) {
        val duration = screen.duration
        val target = primary.currentPosition.coerceAtLeast(0).let {
            if (duration != C.TIME_UNSET && duration > 0) it.coerceAtMost(duration) else it
        }
        // Separate video decoders can drift; only the silent view is corrected.
        if (forceSeek || abs(screen.currentPosition - target) > 750) screen.seekTo(target)
        if (screen.playbackParameters != primary.playbackParameters) {
            screen.playbackParameters = primary.playbackParameters
        }
        screen.playWhenReady = active && primary.isPlaying
    }

    fun release() {
        primary.removeListener(listener)
        screen.pause()
    }
}
