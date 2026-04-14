package com.github.garynasser.correction_notebook.ui.screens.yanhe

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.session.MediaController // 必须是这个
import androidx.media3.session.SessionToken
import androidx.navigation.toRoute
import com.github.garynasser.correction_notebook.service.VideoPlaybackService
import com.github.garynasser.correction_notebook.ui.navigation.VideoPlayer
import com.google.common.util.concurrent.ListenableFuture // 修正包名
import com.google.common.util.concurrent.MoreExecutors // 修正包名
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.core.net.toUri
import androidx.media3.common.C
import androidx.media3.common.Tracks
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val application: Application,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<VideoPlayer>()
    private val videoUrl = args.url

    // 状态管理
    private var browserFuture: ListenableFuture<MediaController>? = null
    var controller = mutableStateOf<MediaController?>(null)
    var playState by mutableStateOf<PlayState>(PlayState.Idle)

    init {
        setupController()
    }

    @OptIn(UnstableApi::class)
    private fun setupController() {
        val sessionToken = SessionToken(
            application,
            ComponentName(application, VideoPlaybackService::class.java)
        )

        // 修正点 1：这里应该是 Builder，不是 equals
        val future = MediaController.Builder(application, sessionToken).buildAsync()
        browserFuture = future

        future.addListener({
            try {
                // 修正点 2：从 future 中获取真正的 controller
                val mediaController = future.get()
                controller.value = mediaController
                startPlay(mediaController)
            } catch (e: Exception) {
                playState = PlayState.Error("控制器连接失败: ${e.message}")
            }
        }, MoreExecutors.directExecutor())
    }

    private fun startPlay(mediaController: MediaController?) {
        mediaController?.addListener(object : Player.Listener {
            @OptIn(UnstableApi::class)
            override fun onTracksChanged(tracks: Tracks) {
                // 检查是否有视频轨道被选中
                val hasVideo = tracks.groups.any { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
                Log.d("PLAYER_DEBUG", "是否有视频轨道: $hasVideo")
                if (!hasVideo) {
                    Log.e("PLAYER_DEBUG", "警告：未检测到视频轨道或视频轨道无法播放")
                }
            }
        })

        mediaController?.let {
            val mediaItem = MediaItem.Builder()
                .setUri(videoUrl.toUri()) // 确保 Uri 转换正确
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build()

            it.setMediaItem(mediaItem)
            it.prepare()
            it.play()
            playState = PlayState.Success(videoUrl)
        }
    }

    override fun onCleared() {
        // 修正点 3：MediaController.releaseFuture 是 Media3 专有的静态方法
        browserFuture?.let {
            MediaController.releaseFuture(it)
        }
        super.onCleared()
    }
}