package com.github.garynasser.correction_notebook.data.remote.api

import com.github.garynasser.correction_notebook.data.model.common.ApiResponse
import com.github.garynasser.correction_notebook.data.model.yanhe.CourseSection
import com.github.garynasser.correction_notebook.data.model.yanhe.PaginatedData
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Query

interface VideoApiService {
    @Headers(
        "Origin: https://www.yanhekt.cn",
        "Referer: https://www.yanhekt.cn/",
        "xdomain-client: web_user",
        "Xdomain-Client: web_user",
        "Xclient-Version: v1",
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    )
    @GET("https://cbiz.yanhekt.cn/v2/course/list")
    suspend fun getCourseList(
        @Header("Authorization") token: String,
        @Query("semesters[]") semester: Int?,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
        @Query("keyword") keyword: String?
    ): ApiResponse<PaginatedData>


    @Headers(
        "Origin: https://www.yanhekt.cn",
        "Referer: https://www.yanhekt.cn/",
        "xdomain-client: web_user",
        "Xdomain-Client: web_user",
        "Xclient-Version: v1",
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    )
    @GET("https://cbiz.yanhekt.cn/v2/course/list")
    suspend fun getPersonalCourseList(
        @Header("Authorization") token: String,
        @Query("semesters[]") semester: Int?,
        @Query("page") page: Int,
        @Query("pageSize") pageSize: Int,
        @Query("keyword") keyword: String?,
        @Query("user_relationship_type") type: Int = 1,
        @Query("with_introduction") introduction: Boolean = true
    ): ApiResponse<PaginatedData>

    @Headers(
        "Origin: https://www.yanhekt.cn",
        "Referer: https://www.yanhekt.cn/",
        "xdomain-client: web_user",
        "Xdomain-Client: web_user",
        "Xclient-Version: v1",
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    )
    @GET("https://cbiz.yanhekt.cn/v2/course/session/list")
    suspend fun getCourseSession(
        @Header("Authorization") token: String,
        @Query("course_id") courseId: Int
    ): ApiResponse<List<CourseSection>>
}