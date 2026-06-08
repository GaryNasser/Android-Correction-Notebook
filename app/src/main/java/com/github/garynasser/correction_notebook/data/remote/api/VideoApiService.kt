package com.github.garynasser.correction_notebook.data.remote.api

import com.github.garynasser.correction_notebook.data.model.common.ApiResponse
import com.github.garynasser.correction_notebook.data.model.yanhe.CourseSection
import com.github.garynasser.correction_notebook.data.model.yanhe.VideoTokenResponse
import com.google.gson.JsonElement
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Query
import retrofit2.http.Streaming
import retrofit2.http.Url

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
        @Query("page_size") pageSize: Int,
        @Query("keyword") keyword: String?
    ): ApiResponse<JsonElement>


    @Headers(
        "Origin: https://www.yanhekt.cn",
        "Referer: https://www.yanhekt.cn/",
        "xdomain-client: web_user",
        "Xdomain-Client: web_user",
        "Xclient-Version: v1",
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    )
    @GET("https://cbiz.yanhekt.cn/v2/course/private/list")
    suspend fun getPrivateCourseList(
        @Header("Authorization") token: String,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
        @Query("user_relationship_type") type: Int = 1
    ): ApiResponse<JsonElement>

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
        @Query("course_id") courseId: Int,
        @Query("with_page") withPage: Boolean? = null,
        @Query("page") page: Int? = null,
        @Query("page_size") pageSize: Int? = null,
        @Query("order_type") orderType: String? = null,
        @Query("order_type_weight") orderTypeWeight: String? = null
    ): ApiResponse<JsonElement>

    @Headers(
        "Origin: https://www.yanhekt.cn",
        "Referer: https://www.yanhekt.cn/",
        "xdomain-client: web_user",
        "Xdomain-Client: web_user",
        "Xclient-Version: v1",
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    )
    @GET("https://cbiz.yanhekt.cn/v1/course/session")
    suspend fun getCourseSessionDetail(
        @Header("Authorization") token: String,
        @Query("session_id") sessionId: Int,
        @Query("with_video") withVideo: Boolean = true
    ): ApiResponse<JsonElement>

    @Headers(
        "Origin: https://www.yanhekt.cn",
        "Referer: https://www.yanhekt.cn/",
        "xdomain-client: web_user",
        "Xdomain-Client: web_user",
        "Xclient-Version: v1",
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    )
    @GET("https://cbiz.yanhekt.cn/v1/user")
    suspend fun getYanheUser(
        @Header("Authorization") token: String
    ): ApiResponse<JsonElement>

    @Headers(
        "Origin: https://www.yanhekt.cn",
        "Referer: https://www.yanhekt.cn/",
        "xdomain-client: web_user",
        "Xdomain-Client: web_user",
        "Xclient-Version: v1",
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    )
    @GET("https://cbiz.yanhekt.cn/v1/auth/video/token")
    suspend fun getVideoToken(
        @Header("Authorization") token: String,
        @Query("id") id: String
    ): ApiResponse<VideoTokenResponse>

    @Headers(
        "Origin: https://www.yanhekt.cn",
        "Referer: https://www.yanhekt.cn/",
        "xdomain-client: web_user",
        "Xdomain-Client: web_user",
        "Xclient-Version: v1",
        "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    )
    @GET
    @Streaming
    suspend fun downloadYanheFile(
        @Url url: String,
        @Query("Xvideo_Token") xvideoToken: String,
        @Query("Xclient_Timestamp") xclientTimestamp: String,
        @Query("Xclient_Signature") xclientSignature: String,
        @Query("Platform") platform: String = "yhkt_user",
        @Query("Xclient_Version") xclientVersion: String = "v1"
    ): Response<ResponseBody>
}
