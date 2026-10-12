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
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController // 必须是这个
import androidx.media3.session.SessionToken
import androidx.navigation.toRoute
import com.github.garynasser.correction_notebook.data.repository.VideoRepository
import com.github.garynasser.correction_notebook.service.VideoPlaybackService
import com.github.garynasser.correction_notebook.ui.navigation.VideoPlayer
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val application: Application,
    private val savedStateHandle: SavedStateHandle,
    private val repository: VideoRepository,
) : ViewModel() {

    private val args = savedStateHandle.toRoute<VideoPlayer>()
    private val videoUrl = args.url
    private val screenUrl = args.screenUrl
    private var currentScreenUrl = screenUrl
    private val screenFallbackUrl = args.screenFallbackUrl
    val primaryIsScreen = args.primaryIsScreen
    val videoTitle = args.videoTitle
    val courseName = args.courseName

    // 状态管理
    private var browserFuture: ListenableFuture<MediaController>? = null
    private var isCleared = false
    var controller = mutableStateOf<MediaController?>(null)
    var playState by mutableStateOf<PlayState>(PlayState.Idle)
        private set

    var screenPlayer by mutableStateOf<ExoPlayer?>(null)
        private set
    var screenPlayState by mutableStateOf<PlayState>(PlayState.Idle)
        private set
    internal var displayMode by mutableStateOf(
        savedStateHandle.get<String>("displayMode")?.let { name -> VideoDisplayMode.entries.find { it.name == name } }
            ?: if (screenUrl.isNotBlank()) VideoDisplayMode.BOTH
            else if (primaryIsScreen) VideoDisplayMode.SCREEN else VideoDisplayMode.CAMERA
    )
        private set
    private var synchronizedPlayback: SynchronizedVideoPlayback? = null
    private var synchronizationJob: Job? = null
    private var screenActive = true

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            playState = when (playbackState) {
                Player.STATE_BUFFERING -> PlayState.Loading
                Player.STATE_READY, Player.STATE_ENDED -> PlayState.Success(videoUrl)
                else -> playState
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            val message = when (error.errorCode) {
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "视频读取超时，请重试"
                PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> "无法连接视频服务器，请检查网络后重试"
                PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> if (videoHttpStatus(error) == 404)
                    "延河课堂未找到这一路录像，请返回课程重新选择节次" else "视频服务器拒绝请求，请返回课程重新选择节次"
                else -> error.message?.takeIf { it.isNotBlank() } ?: "视频流加载失败"
            }
            playState = PlayState.Error("播放失败: $message")
        }
    }

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
                    mediaController.addListener(playerListener)
                    controller.value = mediaController
                    if (mediaController.currentMediaItem?.localConfiguration?.uri?.toString() == videoUrl &&
                        mediaController.playerError == null && mediaController.playbackState != Player.STATE_IDLE) {
                        mediaController.play()
                        playState = PlayState.Success(videoUrl)
                    } else {
                        startPlay(mediaController, savedStateHandle.get<Long>("positionMs") ?: 0L)
                    }
                    setupScreenPlayer(mediaController)
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

    private fun startPlay(mediaController: MediaController, positionMs: Long = mediaController.currentPosition) {
        val mediaItemBuilder = MediaItem.Builder()
            .setUri(videoUrl.toUri())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(videoTitle.ifBlank { "延河课堂视频" })
                    .setAlbumTitle(courseName.ifBlank { "BITStudy" })
                    .build()
            )
        inferCourseVideoMimeType(videoUrl)?.let(mediaItemBuilder::setMimeType)
        val mediaItem = mediaItemBuilder.build()

        mediaController.setMediaItem(mediaItem, positionMs.coerceAtLeast(0))
        mediaController.prepare()
        mediaController.play()
    }

    @OptIn(UnstableApi::class)
    private fun setupScreenPlayer(primary: Player) {
        synchronizationJob?.cancel()
        synchronizedPlayback?.release()
        synchronizedPlayback = null
        screenPlayer?.release()
        screenPlayer = null
        if (screenUrl.isNotBlank()) {
            screenPlayState = PlayState.Loading
            val secondary = ExoPlayer.Builder(application)
                .setMediaSourceFactory(VideoPlaybackService.mediaSourceFactory(application, repository)).build()
            secondary.volume = 0f
            secondary.trackSelectionParameters = secondary.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_AUDIO, true).build()
            secondary.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    screenPlayState = when (playbackState) {
                        Player.STATE_BUFFERING -> PlayState.Loading
                        Player.STATE_READY, Player.STATE_ENDED -> PlayState.Success(currentScreenUrl)
                        else -> screenPlayState
                    }
                    if (playbackState == Player.STATE_READY) synchronizedPlayback?.synchronize()
                }
                override fun onPlayerError(error: PlaybackException) {
                    if (nextScreenVideoUrl(error, currentScreenUrl, screenFallbackUrl) != null) {
                        // Some generated PPT MP4s are missing while the recorder's original VGA stream is available.
                        currentScreenUrl = screenFallbackUrl
                        screenPlayState = PlayState.Loading
                        secondary.setMediaItem(screenMediaItem(), primary.currentPosition.coerceAtLeast(0))
                        secondary.prepare()
                        synchronizedPlayback?.synchronize(forceSeek = true)
                    } else {
                        screenPlayState = PlayState.Error(if (videoHttpStatus(error) == 404)
                            "延河课堂暂未提供可用的 PPT 录像；可继续观看教室视角。"
                            else "PPT 画面加载失败，请重试；教室视频仍可观看。")
                    }
                }
            })
            screenPlayer = secondary
            synchronizedPlayback = SynchronizedVideoPlayback(primary, secondary).also { it.setActive(screenActive) }
            secondary.setMediaItem(screenMediaItem(), primary.currentPosition.coerceAtLeast(0))
            secondary.prepare()
        }
        synchronizationJob = viewModelScope.launch {
            while (isActive) {
                savedStateHandle["positionMs"] = primary.currentPosition.coerceAtLeast(0)
                if (screenActive) synchronizedPlayback?.synchronize()
                delay(500)
            }
        }
    }

    private fun screenMediaItem(): MediaItem = MediaItem.Builder().setUri(currentScreenUrl).apply {
        inferCourseVideoMimeType(currentScreenUrl)?.let(::setMimeType)
    }.build()

    fun setScreenActive(active: Boolean) {
        screenActive = active
        synchronizedPlayback?.setActive(active)
    }

    internal fun setDisplayMode(mode: VideoDisplayMode) {
        if (screenUrl.isBlank() && mode != if (primaryIsScreen) VideoDisplayMode.SCREEN else VideoDisplayMode.CAMERA) return
        displayMode = mode
        savedStateHandle["displayMode"] = mode.name
    }

    fun retryScreenPlayback() {
        screenPlayState = PlayState.Loading
        screenPlayer?.let {
            it.seekTo(controller.value?.currentPosition ?: 0L)
            it.prepare()
            synchronizedPlayback?.synchronize(forceSeek = true)
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

    private fun shouldIgnoreControllerCallback(future: ListenableFuture<MediaController>): Boolean {
        return isCleared || browserFuture !== future
    }

    override fun onCleared() {
        isCleared = true
        controller.value?.let { savedStateHandle["positionMs"] = it.currentPosition.coerceAtLeast(0) }
        synchronizationJob?.cancel()
        synchronizedPlayback?.release()
        synchronizedPlayback = null
        screenPlayer?.release()
        screenPlayer = null
        controller.value?.removeListener(playerListener)
        controller.value?.pause()
        controller.value = null
        browserFuture?.let {
            MediaController.releaseFuture(it)
        }
        browserFuture = null
        super.onCleared()
    }
}

internal fun inferCourseVideoMimeType(url: String): String? {
    val path = url.substringBefore('#').substringBefore('?').lowercase()
    return when {
        path.endsWith(".m3u8") -> MimeTypes.APPLICATION_M3U8
        path.endsWith(".mp4") || path.endsWith(".m4v") -> MimeTypes.VIDEO_MP4
        else -> null
    }
}

@OptIn(UnstableApi::class)
internal fun videoHttpStatus(error: Throwable): Int? = generateSequence(error) { it.cause }
    .filterIsInstance<androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException>()
    .firstOrNull()?.responseCode

internal fun nextScreenVideoUrl(error: Throwable, currentUrl: String, fallbackUrl: String): String? =
    fallbackUrl.takeIf { videoHttpStatus(error) == 404 && it.isNotBlank() && currentUrl != it }
