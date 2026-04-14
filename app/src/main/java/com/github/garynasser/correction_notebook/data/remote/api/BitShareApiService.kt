package com.github.garynasser.correction_notebook.data.remote.api

import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareFileDetailDto
import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareSearchResponse
import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

interface BitShareApiService {
    @GET("api/public/search")
    suspend fun searchFiles(
        @Query("q") query: String,
        @Query("page") page: Int = 1,
        @Query("page_size") pageSize: Int = 20
    ): BitShareSearchResponse

    @GET("api/public/files/{fileId}")
    suspend fun getFileDetail(
        @Path("fileId") fileId: String
    ): BitShareFileDetailDto

    @Streaming
    @GET("api/public/files/{fileId}/download")
    suspend fun downloadFile(
        @Path("fileId") fileId: String
    ): ResponseBody
}
