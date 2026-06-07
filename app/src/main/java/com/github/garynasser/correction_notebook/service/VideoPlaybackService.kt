package com.github.garynasser.correction_notebook.service

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.github.garynasser.correction_notebook.data.datasource.YanheDataSource
import com.github.garynasser.correction_notebook.data.repository.VideoRepository
import dagger.hilt.android.AndroidEntryPoint
import java.io.File
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class VideoPlaybackService : MediaSessionService() {

    private var player: ExoPlayer? = null
    private var mediaSession: MediaSession? = null

    @Inject
    lateinit var repository: VideoRepository

    // 静态缓存对象，避免 Service 重启时重复创建导致冲突
    companion object {
        private var cache: SimpleCache? = null
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // 1. 初始化缓存逻辑 (LRU 策略，不保存，只缓存 200MB)
        if (cache == null) {
            val cacheDir = File(cacheDir, "yanhe_video_cache")
            val databaseProvider = StandaloneDatabaseProvider(this)
            cache = SimpleCache(cacheDir, LeastRecentlyUsedCacheEvictor(200 * 1024 * 1024), databaseProvider)
        }
        val videoCache = cache ?: run {
            stopSelf()
            return
        }

        // 2. 构造带逆向授权逻辑的数据源
        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)

        val yanheDataSourceFactory = androidx.media3.datasource.DataSource.Factory {
            YanheDataSource(repository, httpDataSourceFactory.createDataSource())
        }

        // 3. 将缓存和授权数据源整合
        val cacheDataSourceFactory = CacheDataSource.Factory()
            .setCache(videoCache)
            .setUpstreamDataSourceFactory(yanheDataSourceFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)

        // 4. 创建播放器实例
        val createdPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .build()
        player = createdPlayer

        // 5. 创建 MediaSession
        mediaSession = MediaSession.Builder(this, createdPlayer).build()
    }

    // 修复报错：必须实现这个抽象方法
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    // 处理任务被移除时的逻辑
    override fun onTaskRemoved(rootIntent: Intent?) {
        player?.let {
            if (!it.playWhenReady || it.mediaItemCount == 0) {
                stopSelf()
            }
        }
    }

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        super.onDestroy()
    }
}
