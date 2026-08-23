package com.github.garynasser.correction_notebook.ui.screens.yanhe

import android.app.Application
import android.content.ComponentName
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.session.MediaController // 必须是这个
import androidx.media3.session.SessionToken
import androidx.navigation.toRoute
import com.github.garynasser.correction_notebook.service.VideoPlaybackService
import com.github.garynasser.correction_notebook.ui.navigation.VideoPlayer
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import androidx.core.net.toUri
import androidx.media3.common.util.UnstableApi
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException

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
        private set

    init {
        setupController()
    }

    @OptIn(UnstableApi::class)
    private fun setupController() {
        if (videoUrl.isBlank()) {
            playState = PlayState.Error("视频地址为空，请返回后重新选择课程视频")
            return
        }
        playState = PlayState.Loading
        val sessionToken = SessionToken(
            application,
            ComponentName(application, VideoPlaybackService::class.java)
        )

        browserFuture?.let(MediaController::releaseFuture)
        val future = MediaController.Builder(application, sessionToken).buildAsync()
        browserFuture = future

        future.addListener({
            try {
                val mediaController = future.get()
                controller.value = mediaController
                startPlay(mediaController)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                playState = PlayState.Error("播放器连接被中断，请返回后重试")
            } catch (e: CancellationException) {
                playState = PlayState.Error("播放器连接已取消，请重试")
            } catch (e: ExecutionException) {
                playState = PlayState.Error("控制器连接失败: ${e.cause?.message ?: e.message}")
            } catch (e: Exception) {
                playState = PlayState.Error("控制器连接失败: ${e.message}")
            }
        }, MoreExecutors.directExecutor())
    }

    private fun startPlay(mediaController: MediaController?) {
        mediaController?.let {
            val mediaItem = MediaItem.Builder()
                .setUri(videoUrl.toUri())
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build()

            it.setMediaItem(mediaItem)
            it.prepare()
            it.play()
            playState = PlayState.Success(videoUrl)
        }
    }

    fun retryPlayback() {
        val currentController = controller.value
        if (currentController != null) {
            playState = PlayState.Loading
            runCatching { startPlay(currentController) }
                .onFailure { playState = PlayState.Error("播放失败: ${it.message ?: "视频流加载失败"}") }
        } else {
            setupController()
        }
    }

    override fun onCleared() {
        controller.value?.pause()
        browserFuture?.let {
            MediaController.releaseFuture(it)
        }
        super.onCleared()
    }
}
