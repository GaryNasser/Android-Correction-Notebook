package com.github.garynasser.correction_notebook.data.remote.api

import com.github.garynasser.correction_notebook.data.model.common.ApiResponse
import com.google.gson.JsonElement
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface VideoApiService {
    @GET("courseapi/v3/portal-home-setting/get-all-list?tenant=21&type=multi")
    suspend fun getCourseList(
        @Header("Authorization") token: String,
        @Query("term_id") semester: Int?,
        @Query("page") page: Int,
        @Query("per_page") pageSize: Int,
        @Query("search") keyword: String?
    ): ApiResponse<JsonElement>

    @GET("vlabpassportapi/v1/account-profile/course")
    suspend fun getPrivateCourseList(
        @Header("Authorization") token: String,
        @Query("nowpage") page: Int,
        @Query("per-page") pageSize: Int,
        @Query("force_mycourse") type: Int = 1
    ): ApiResponse<JsonElement>

    @GET("courseapi/v3/multi-search/get-course-detail")
    suspend fun getCourseSession(
        @Header("Authorization") token: String,
        @Query("course_id") courseId: Int,
        @Query("with_page") withPage: Boolean? = null,
        @Query("page") page: Int? = null,
        @Query("per_page") pageSize: Int? = null,
        @Query("order_type") orderType: String? = null,
        @Query("order_type_weight") orderTypeWeight: String? = null
    ): ApiResponse<JsonElement>

    @GET("courseapi/v3/portal-home-setting/get-sub-info")
    suspend fun getCourseSessionDetail(
        @Header("Authorization") token: String,
        @Query("sub_id") sessionId: Int,
        @Query("course_id") courseId: Int
    ): ApiResponse<JsonElement>

    @GET("userapi/v1/infosimple")
    suspend fun getYanheUser(@Header("Authorization") token: String): ApiResponse<JsonElement>
}
