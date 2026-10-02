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

@OptIn(UnstableApi::class)
class YanheDataSource internal constructor(
    private val authorize: suspend (String) -> VideoRepository.YanheAuthData,
    private val baseDataSource: DataSource,
) : DataSource {
    constructor(repository: VideoRepository, baseDataSource: DataSource) :
        this(repository::getYanheAuthData, baseDataSource)

    private var originalUri: Uri? = null
    private var authenticatedUri: Uri? = null

    override fun addTransferListener(transferListener: TransferListener) {
        baseDataSource.addTransferListener(transferListener)
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        originalUri = null
        authenticatedUri = null
        val authData = try {
            runBlocking(Dispatchers.IO) { authorize(dataSpec.uri.toString()) }
        } catch (error: IOException) {
            throw error
        } catch (error: Exception) {
            throw IOException("视频授权失败", error)
        }

        val requestUri = authData.authenticatedUrl.toUri()
        originalUri = dataSpec.uri
        authenticatedUri = requestUri
        val newDataSpec = dataSpec.buildUpon()
            .setUri(requestUri)
            .setHttpRequestHeaders(dataSpec.httpRequestHeaders + authData.headers)
            .build()

        return baseDataSource.open(newDataSpec)
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return baseDataSource.read(buffer, offset, length)
    }

    override fun getUri(): Uri? {
        val loadedUri = baseDataSource.uri ?: return null
        // Authorization rewrites are not redirects: HLS must resolve relative paths from the original URL.
        return if (loadedUri == authenticatedUri) originalUri else loadedUri
    }

    override fun getResponseHeaders(): Map<String, List<String>> = baseDataSource.responseHeaders

    @Throws(IOException::class)
    override fun close() {
        try {
            baseDataSource.close()
        } finally {
            originalUri = null
            authenticatedUri = null
        }
    }
}
