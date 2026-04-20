package com.github.garynasser.correction_notebook.data.repository

import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareFileDetail
import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareSearchResult
import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareSortOption
import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareFolderDetail
import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareFolderSummary
import com.github.garynasser.correction_notebook.data.remote.api.BitShareApiService
import okhttp3.ResponseBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BitShareRepository @Inject constructor(
    private val apiService: BitShareApiService
) {
    /**
     * 搜索 BITShare 资源（文件或目录）
     * 注意：由于 /api/public/files?folder_id= 接口返回 404，无法按目录获取文件列表，
     * 因此目录只能通过搜索发现，无法浏览目录内的文件
     */
    suspend fun searchFiles(
        query: String,
        sortOption: BitShareSortOption
    ): Result<List<BitShareSearchResult>> = runCatching {
        if (query.isBlank()) {
            emptyList()
        } else {
            apiService.searchFiles(query = query.trim()).items
                .map { item ->
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
                }
                .let { results ->
                    // 文件和文件夹分开处理
                    val folders = results.filter { it.entityType == "folder" }
                    val files = results.filter { it.entityType == "file" }

                    when (sortOption) {
                        BitShareSortOption.RELEVANCE -> folders + files
                        BitShareSortOption.DOWNLOADS -> folders + files.sortedByDescending { it.downloadCount }
                        BitShareSortOption.LATEST -> folders + files.sortedByDescending {
                            // ISO 8601 格式字符串可直接按字典序比较
                            it.uploadedAt ?: ""
                        }
                    }
                }
        }
    }

    /**
     * 获取目录详情
     */
    suspend fun getFolderDetail(folderId: String): Result<BitShareFolderDetail> = runCatching {
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
    suspend fun getSubFolders(parentId: String?): Result<List<BitShareFolderSummary>> = runCatching {
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
    suspend fun getRootFolders(): Result<List<BitShareFolderSummary>> = runCatching {
        getSubFolders(null).getOrThrow()
    }

    suspend fun getFileDetail(fileId: String): Result<BitShareFileDetail> = runCatching {
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

    suspend fun downloadFile(fileId: String): Result<ResponseBody> = runCatching {
        apiService.downloadFile(fileId)
    }
}
