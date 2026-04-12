package com.github.garynasser.correction_notebook.data.model.knowledgebase

import com.google.gson.annotations.SerializedName

data class BitShareSearchResponse(
    val items: List<BitShareSearchItemDto>,
    val page: Int,
    @SerializedName("page_size") val pageSize: Int,
    val total: Int
)

data class BitShareSearchItemDto(
    @SerializedName("entity_type") val entityType: String,
    val id: String,
    val name: String,
    @SerializedName("original_name") val originalName: String?,
    val extension: String?,
    val size: Long?,
    @SerializedName("download_count") val downloadCount: Int?,
    @SerializedName("uploaded_at") val uploadedAt: String?
)

data class BitShareFileDetailDto(
    val id: String,
    val title: String,
    val extension: String?,
    @SerializedName("folder_id") val folderId: String?,
    val path: String?,
    val description: String?,
    @SerializedName("original_name") val originalName: String,
    @SerializedName("mime_type") val mimeType: String?,
    val size: Long,
    @SerializedName("uploaded_at") val uploadedAt: String?,
    @SerializedName("download_count") val downloadCount: Int?
)

enum class BitShareSortOption {
    RELEVANCE,
    DOWNLOADS,
    LATEST
}

data class BitShareSearchResult(
    val id: String,
    val title: String,
    val originalName: String,
    val extension: String,
    val sizeBytes: Long,
    val downloadCount: Int,
    val uploadedAt: String?,
    val entityType: String
)

data class BitShareFileDetail(
    val id: String,
    val title: String,
    val originalName: String,
    val extension: String,
    val path: String?,
    val description: String?,
    val mimeType: String,
    val sizeBytes: Long,
    val uploadedAt: String?,
    val downloadCount: Int
)
