package com.github.garynasser.correction_notebook.ui.screens.yanhe

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.ExoPlayer
import androidx.navigation.toRoute
import com.github.garynasser.correction_notebook.data.repository.VideoRepository
import com.github.garynasser.correction_notebook.ui.navigation.VideoPlayer
import com.github.garynasser.correction_notebook.utils.SignatureUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    private val repository: VideoRepository,
    private val application: Application,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val player = ExoPlayer.Builder(application).build()
    var playState by mutableStateOf<PlayState>(PlayState.Idle)
    var downloadProgress by mutableFloatStateOf(0f)

    private val args = savedStateHandle.toRoute<VideoPlayer>()
    private val videoUrl = args.url // 远程 URL 地址

    // 用于管理准备任务，防止重复触发
    private var prepareJob: Job? = null

    init {
        prepareVideo()
    }

    private fun prepareVideo() {
        // 如果任务已经在运行，不再重复启动
        if (prepareJob?.isActive == true) return

        prepareJob = viewModelScope.launch(Dispatchers.IO) {
            playState = PlayState.Loading
            try {
                Log.d("VIDEO", "开始准备视频流程: $videoUrl")

                // 1. MP4 快速通道：如果是 MP4 直接播放远程地址
                if (videoUrl.lowercase().contains(".mp4") && !videoUrl.contains(".m3u8")) {
                    Log.d("VIDEO", "识别为普通视频，直接播放远程地址")
                    withContext(Dispatchers.Main) {
                        playRemoteVideo(videoUrl)
                    }
                    return@launch
                }

                // 2. 检查本地缓存
                val folderName = SignatureUtils.getmd5(videoUrl)
                val workDir = File(application.filesDir, "videos/$folderName")
                val m3u8File = File(workDir, "$folderName.m3u8")

                // 检查索引文件是否存在，且文件夹下有 TS 分片
                val tsExist = workDir.listFiles { _, n -> n.endsWith(".ts") }?.isNotEmpty() == true

                if (m3u8File.exists() && tsExist) {
                    Log.d("VIDEO", "本地已存在完整缓存，直接播放")
                    withContext(Dispatchers.Main) {
                        startPlay(m3u8File.absolutePath)
                    }
                    return@launch
                }

                // 3. 下载原始索引文件 (tempM3u8 是本地 File 对象)
                Log.d("VIDEO", "本地无缓存，开始下载 M3U8 索引...")
                val tempM3u8 = repository.getM3U8File(videoUrl)
                    ?: throw Exception("索引下载失败，请检查网络")

                // 4. 解析任务 (!!! 关键修正：确保第一个参数传入的是远程 videoUrl)
                Log.d("VIDEO", "解析原始 M3U8 文件...")
                val task = repository.processM3U8File(
                    originalUrl = videoUrl, // 必须传原始 URL
                    tempFile = tempM3u8,
                    videoName = folderName
                ) ?: throw Exception("解析索引文件失败")

                // 索引解析重写完成后，立即删除临时文件
                if (tempM3u8.exists()) tempM3u8.delete()

                // 5. 循环下载分片 (带百分比)
                Log.d("VIDEO", "开始下载分片，总数: ${task.tsUrls.size}")
                task.tsUrls.forEachIndexed { index, tsUrl ->
                    ensureActive() // 检查协程是否被取消

                    // 下载分片到 workDir 下的顺序编号文件
                    val success = repository.downloadSingleTs(tsUrl, File(task.workDir, "$index.ts"))

                    if (!success) {
                        Log.e("VIDEO", "分片 $index 下载失败: $tsUrl")
                        throw Exception("分片下载中断")
                    }

                    // 更新进度百分比
                    downloadProgress = (index + 1).toFloat() / task.tsUrls.size
                    if (index % 10 == 0) Log.d("VIDEO", "已完成: $index/${task.tsUrls.size}")
                }

                // 6. 全部下载完成，切换到主线程播放本地文件
                Log.d("VIDEO", "所有分片准备就绪，启动播放")
                withContext(Dispatchers.Main) {
                    startPlay(task.m3u8File.absolutePath)
                }

            } catch (e: Exception) {
                Log.e("VIDEO", "prepareVideo 捕获异常", e)
                playState = PlayState.Error(e.message ?: "资源准备失败")
            }
        }
    }

    /**
     * 播放本地 M3U8
     */
    private fun startPlay(path: String) {
        try {
            val file = File(path)
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.fromFile(file))
                .setMimeType(MimeTypes.APPLICATION_M3U8)
                .build()
            player.setMediaItem(mediaItem)
            player.prepare()
            player.play()
            playState = PlayState.Success(path)
        } catch (e: Exception) {
            playState = PlayState.Error("播放启动失败")
        }
    }

    /**
     * 播放远程普通视频 (MP4)
     */
    private fun playRemoteVideo(url: String) {
        val mediaItem = MediaItem.fromUri(url)
        player.setMediaItem(mediaItem)
        player.prepare()
        player.play()
        playState = PlayState.Success(url)
    }

    override fun onCleared() {
        prepareJob?.cancel() // 确保 ViewModel 销毁时停止下载
        player.release()
        super.onCleared()
    }
}