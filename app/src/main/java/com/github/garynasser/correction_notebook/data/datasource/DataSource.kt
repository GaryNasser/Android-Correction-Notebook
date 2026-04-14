package com.github.garynasser.correction_notebook.data.datasource

import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import com.github.garynasser.correction_notebook.data.repository.VideoRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.IOException
import androidx.core.net.toUri
import androidx.media3.common.util.Log

@OptIn(UnstableApi::class)
class YanheDataSource(
    private val repository: VideoRepository,
    private val baseDataSource: DataSource,
) : DataSource {

    override fun addTransferListener(transferListener: TransferListener) {
        baseDataSource.addTransferListener(transferListener)
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        val originalUrl = dataSpec.uri.toString()

        // 在这里执行你的逆向授权逻辑
        // 使用 runBlocking 阻塞当前加载线程（ExoPlayer 的后台线程）
        val authData = runBlocking(Dispatchers.IO) {
            repository.getYanheAuthData(originalUrl)
        }

        // 构造新的请求
        // 注意：setHttpRequestHeaders 是在 DataSpec.Builder 上调用的
        val newDataSpec = dataSpec.buildUpon()
            .setUri(authData.authenticatedUrl.toUri())
            .setHttpRequestHeaders(authData.headers)
            .build()

        Log.d("VIDEO", newDataSpec.toString())

        return baseDataSource.open(newDataSpec)
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return baseDataSource.read(buffer, offset, length)
    }

    override fun getUri(): Uri? {
        return baseDataSource.uri
    }

    @Throws(IOException::class)
    override fun close() {
        baseDataSource.close()
    }
}