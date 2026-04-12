package com.github.garynasser.correction_notebook.data.repository

import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareFileDetail
import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareSearchResult
import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareSortOption
import com.github.garynasser.correction_notebook.data.remote.api.BitShareApiService
import okhttp3.ResponseBody
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BitShareRepository @Inject constructor(
    private val apiService: BitShareApiService
) {
    suspend fun searchFiles(
        query: String,
        sortOption: BitShareSortOption
    ): Result<List<BitShareSearchResult>> = runCatching {
        if (query.isBlank()) {
            emptyList()
        } else {
            apiService.searchFiles(query = query.trim()).items
                .filter { it.entityType == "file" }
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
                    when (sortOption) {
                        BitShareSortOption.RELEVANCE -> results
                        BitShareSortOption.DOWNLOADS -> results.sortedByDescending { it.downloadCount }
                        BitShareSortOption.LATEST -> results.sortedByDescending {
                            parseInstant(it.uploadedAt)
                        }
                    }
                }
        }
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

    private fun parseInstant(value: String?): Instant {
        return runCatching { Instant.parse(value) }.getOrElse { Instant.EPOCH }
    }
}
