package com.github.garynasser.correction_notebook.service

import android.content.Context
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

    companion object {
        private var cache: SimpleCache? = null

        internal fun mediaSourceFactory(context: Context, repository: VideoRepository): DefaultMediaSourceFactory {
            val appContext = context.applicationContext
            val videoCache = cache ?: SimpleCache(
                File(appContext.cacheDir, "yanhe_video_cache"),
                LeastRecentlyUsedCacheEvictor(200L * 1024 * 1024),
                StandaloneDatabaseProvider(appContext)
            ).also { cache = it }
            val http = DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true)
            val upstream = androidx.media3.datasource.DataSource.Factory {
                YanheDataSource(repository, http.createDataSource())
            }
            return DefaultMediaSourceFactory(CacheDataSource.Factory()
                .setCache(videoCache)
                .setUpstreamDataSourceFactory(upstream)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR))
        }
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        // 4. 创建播放器实例
        val createdPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory(this, repository))
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
        mediaSession?.release()
        mediaSession = null
        player?.release()
        player = null
        super.onDestroy()
    }
}
