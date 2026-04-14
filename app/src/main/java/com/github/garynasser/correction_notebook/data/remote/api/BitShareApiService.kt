package com.github.garynasser.correction_notebook.data.remote.api

import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareFileDetailDto
import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareSearchResponse
import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareFolderListResponse
import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareFolderDetailDto
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface BitShareApiService {
    // ============ Search ============
    @GET("api/public/search")
    suspend fun searchFiles(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20
    ): BitShareSearchResponse

    // ============ File Operations ============
    @GET("api/public/files/{fileId}")
    suspend fun getFileDetail(
        @Path("fileId") fileId: String
    ): BitShareFileDetailDto

    @Streaming
    @GET("api/public/files/{fileId}/download")
    suspend fun downloadFile(
        @Path("fileId") fileId: String
    ): ResponseBody

    /**
     * 批量下载文件
     * 注意：经实测可用，返回 ZIP 文件流
     */
    @Streaming
    @GET("api/public/files/batch-download")
    suspend fun batchDownloadFiles(
        @Query("file_ids") fileIds: String
    ): ResponseBody

    // ============ Folder Operations ============
    /**
     * 获取目录列表
     * @param parentId 可选，查询某目录下的直接子目录；不传时查询根目录
     */
    @GET("api/public/folders")
    suspend fun getFolders(
        @Query("parent_id") parentId: String? = null
    ): BitShareFolderListResponse

    /**
     * 获取目录详情
     */
    @GET("api/public/folders/{folderId}")
    suspend fun getFolderDetail(
        @Path("folderId") folderId: String
    ): BitShareFolderDetailDto
}
