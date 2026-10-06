package com.github.garynasser.correction_notebook.data.repository

import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareFileDetail
import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareSearchResult
import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareSearchPage
import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareFolderDetail
import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareFolderSummary
import com.github.garynasser.correction_notebook.data.remote.api.BitShareApiService
import com.github.garynasser.correction_notebook.data.remote.network.awaitStreamingResponse
import com.github.garynasser.correction_notebook.utils.BitShareNetworkDetector
import com.github.garynasser.correction_notebook.utils.runCatchingCancellable
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BitShareRepository internal constructor(
    private val apiService: BitShareApiService,
    private val okHttpClient: OkHttpClient,
    private val detectEnvironment: suspend () -> BitShareNetworkDetector.NetworkEnvironment
) {
    @Inject
    constructor(apiService: BitShareApiService, okHttpClient: OkHttpClient, networkDetector: BitShareNetworkDetector) :
        this(apiService, okHttpClient, { networkDetector.detectEnvironmentWithRetry(maxRetries = 1) })
    /**
     * 搜索 BITShare 资源（文件或目录）
     * 注意：由于 /api/public/files?folder_id= 接口返回 404，无法按目录获取文件列表，
     * 因此目录只能通过搜索发现，无法浏览目录内的文件
     */
    suspend fun searchFiles(
        query: String,
        page: Int = 1
    ): Result<BitShareSearchPage> = runCatchingCancellable {
        require(page > 0) { "搜索页码无效" }
        if (query.isBlank()) {
            BitShareSearchPage(emptyList(), 0, null)
        } else {
            val response = apiService.searchFiles(query = query.trim(), page = page)
            check(response.page == page && response.pageSize > 0 && response.total >= 0) {
                "搜索分页信息无效，请重试"
            }
            BitShareSearchPage(
                items = response.items.map { item ->
                    BitShareSearchResult(
                        id = item.id,
                        title = item.name,
                        originalName = item.originalName ?: item.name,
                        extension = item.extension.orEmpty(),
                        sizeBytes = item.size ?: 0L,
                        downloadCount = item.downloadCount ?: 0,
                        uploadedAt = item.uploadedAt,
                        entityType = item.entityType
                    )
                },
                total = response.total,
                nextPage = if (response.items.isNotEmpty() && page.toLong() * response.pageSize < response.total) {
                    page + 1
                } else null
            )
        }
    }

    /**
     * 获取目录详情
     */
    suspend fun getFolderDetail(folderId: String): Result<BitShareFolderDetail> = runCatchingCancellable {
        val detail = apiService.getFolderDetail(folderId)
        BitShareFolderDetail(
            id = detail.id,
            name = detail.name,
            description = detail.description,
            parentId = detail.parentId,
            breadcrumbs = detail.breadcrumbs.map {
                com.github.garynasser.correction_notebook.data.model.knowledgebase.KnowledgeBaseBreadcrumb(
                    id = it.id,
                    name = it.name
                )
            },
            fileCount = detail.fileCount,
            downloadCount = detail.downloadCount,
            totalSize = detail.totalSize,
            updatedAt = detail.updatedAt
        )
    }

    /**
     * 获取子目录列表
     */
    suspend fun getSubFolders(parentId: String?): Result<List<BitShareFolderSummary>> = runCatchingCancellable {
        apiService.getFolders(parentId).items.map { folder ->
            BitShareFolderSummary(
                id = folder.id,
                name = folder.name,
                description = folder.description,
                updatedAt = folder.updatedAt,
                fileCount = folder.fileCount,
                downloadCount = folder.downloadCount,
                totalSize = folder.totalSize
            )
        }
    }

    /**
     * 获取根目录列表
     */
    suspend fun getRootFolders(): Result<List<BitShareFolderSummary>> = runCatchingCancellable {
        getSubFolders(null).getOrThrow()
    }

    suspend fun getFileDetail(fileId: String): Result<BitShareFileDetail> = runCatchingCancellable {
        val detail = apiService.getFileDetail(fileId)
        BitShareFileDetail(
            id = detail.id,
            title = detail.title,
            originalName = detail.originalName,
            extension = detail.extension.orEmpty(),
            path = detail.path,
            description = detail.description,
            mimeType = detail.mimeType ?: "application/octet-stream",
            sizeBytes = detail.size,
            uploadedAt = detail.uploadedAt,
            downloadCount = detail.downloadCount ?: 0
        )
    }

    suspend fun <T> downloadFile(fileId: String, consume: suspend (ResponseBody) -> T): Result<T> {
        var consuming = false
        suspend fun download(request: Request): Result<T> = runCatchingCancellable {
            okHttpClient.newCall(request).awaitStreamingResponse { response ->
                check(response.isSuccessful) { "HTTP ${response.code}" }
                val body = checkNotNull(response.body) { "下载响应为空" }
                validateDownloadBody(body)
                consuming = true
                consume(body)
            }
        }
        val primaryAttempt = runCatchingCancellable { download(apiService.downloadFile(fileId).request()).getOrThrow() }
        if (primaryAttempt.isSuccess || consuming) {
            return primaryAttempt
        }

        val fallbackErrorMessages = mutableListOf<String>()
        primaryAttempt.exceptionOrNull()?.message?.let(fallbackErrorMessages::add)

        buildDownloadCandidateUrls(fileId).forEach { url ->
            val attempt = download(Request.Builder().url(url).get().build())
            if (attempt.isSuccess || consuming) {
                return attempt
            }

            attempt.exceptionOrNull()?.message?.let { message ->
                fallbackErrorMessages += "$url -> $message"
            }
        }

        return Result.failure(
            IllegalStateException(
                buildString {
                    append("BITShare 下载失败")
                    if (fallbackErrorMessages.isNotEmpty()) {
                        append("：")
                        append(fallbackErrorMessages.joinToString("；"))
                    }
                }
            )
        )
    }

    private suspend fun buildDownloadCandidateUrls(fileId: String): List<String> {
        val environment = detectEnvironment()
        return bitShareDownloadFallbackUrls(environment, fileId)
    }

    private fun validateDownloadBody(body: ResponseBody) {
        val contentType = body.contentType()?.toString().orEmpty().lowercase()
        val errorMessage = when {
            contentType.contains("application/json") || contentType.contains("text/html") ->
                "下载接口返回了非文件内容"
            body.contentLength() == 0L -> "下载内容为空"
            else -> null
        }
        if (errorMessage != null) {
            body.close()
            error(errorMessage)
        }
    }
}

internal fun bitShareDownloadFallbackUrls(
    environment: BitShareNetworkDetector.NetworkEnvironment,
    fileId: String
): List<String> {
    return if (environment == BitShareNetworkDetector.NetworkEnvironment.INTRANET) {
        listOf("http://10.170.35.57:8890/api/public/files/$fileId/download")
    } else {
        emptyList()
    }
}
