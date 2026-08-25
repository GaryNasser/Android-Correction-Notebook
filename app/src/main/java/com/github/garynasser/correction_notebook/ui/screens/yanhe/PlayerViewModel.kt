package com.github.garynasser.correction_notebook.ui.screens.yanhe

import android.app.Application
import android.content.ComponentName
import androidx.annotation.OptIn
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController // 必须是这个
import androidx.media3.session.SessionToken
import androidx.navigation.toRoute
import com.github.garynasser.correction_notebook.service.VideoPlaybackService
import com.github.garynasser.correction_notebook.ui.navigation.VideoPlayer
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val application: Application,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<VideoPlayer>()
    private val videoUrl = args.url
    val videoTitle = args.videoTitle
    val courseName = args.courseName

    // 状态管理
    private var browserFuture: ListenableFuture<MediaController>? = null
    private var isCleared = false
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

        future.addListener(
            {
                if (shouldIgnoreControllerCallback(future)) return@addListener
                try {
                    val mediaController = future.get()
                    if (shouldIgnoreControllerCallback(future)) return@addListener
                    controller.value = mediaController
                    startPlay(mediaController)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    if (shouldIgnoreControllerCallback(future)) return@addListener
                    playState = PlayState.Error("播放器连接被中断，请返回后重试")
                } catch (e: CancellationException) {
                    if (shouldIgnoreControllerCallback(future)) return@addListener
                    playState = PlayState.Error("播放器连接已取消，请重试")
                } catch (e: ExecutionException) {
                    if (shouldIgnoreControllerCallback(future)) return@addListener
                    playState = PlayState.Error("控制器连接失败: ${e.cause?.message ?: e.message}")
                } catch (e: Exception) {
                    if (shouldIgnoreControllerCallback(future)) return@addListener
                    playState = PlayState.Error("控制器连接失败: ${e.message}")
                }
            },
            ContextCompat.getMainExecutor(application)
        )
    }

    private fun startPlay(mediaController: MediaController) {
        val mediaItem = MediaItem.Builder()
            .setUri(videoUrl.toUri())
            .setMimeType(MimeTypes.APPLICATION_M3U8)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(videoTitle.ifBlank { "延河课堂视频" })
                    .setAlbumTitle(courseName.ifBlank { "BITStudy" })
                    .build()
            )
            .build()

        mediaController.setMediaItem(mediaItem)
        mediaController.prepare()
        mediaController.play()
        playState = PlayState.Success(videoUrl)
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

    private fun shouldIgnoreControllerCallback(future: ListenableFuture<MediaController>): Boolean {
        return isCleared || browserFuture !== future
    }

    override fun onCleared() {
        isCleared = true
        controller.value?.pause()
        controller.value = null
        browserFuture?.let {
            MediaController.releaseFuture(it)
        }
        browserFuture = null
        super.onCleared()
    }
}
