package com.github.garynasser.correction_notebook.data.repository

import android.content.Context
import android.util.Log
import com.github.garynasser.correction_notebook.data.model.common.ApiResponse
import com.github.garynasser.correction_notebook.data.model.yanhe.Course
import com.github.garynasser.correction_notebook.data.model.yanhe.CourseSection
import com.github.garynasser.correction_notebook.data.remote.manager.VideoRemoteManager
import com.github.garynasser.correction_notebook.utils.SignatureUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File
import javax.inject.Inject
import kotlin.math.log

class VideoRepository @Inject constructor(
    private val videoRemoteManager: VideoRemoteManager
) {
    suspend fun getCourse(
        semester: Int?,
        page: Int,
        pageSize: Int,
        keyword: String? = null
    ): List<Course> {
        val response = videoRemoteManager.getCourseList(
            semester = semester,
            page = page,
            pageSize = pageSize,
            keyword = keyword
        )

        return response?.data?.courses ?: emptyList()
    }

    suspend fun getPersonalCourse(
        semester: Int?,
        page: Int,
        pageSize: Int,
        keyword: String? = null
    ): List<Course> {
        val response = videoRemoteManager.getPersonalCourseList(
            semester = semester,
            page = page,
            pageSize = pageSize,
            keyword = keyword
        )

        return response?.data?.courses ?: emptyList()
    }

    suspend fun getCourseSession(courseId: Int): List<CourseSection> {
        val response = videoRemoteManager.getCourseSession(courseId)

        return response?.data ?: emptyList()
    }

    suspend fun getM3U8File(url: String, context: Context): File? {
        Log.d("VIDEO", "尝试获取 M3U8 文件")
        return withContext(Dispatchers.IO) {
            try {

                val videoTokenResponse = videoRemoteManager.getVideoToken()

                Log.d("VIDEO", videoTokenResponse.toString())
                if (videoTokenResponse?.code != 0 || videoTokenResponse.data == null) {
                    return@withContext null
                }

                val videoToken = videoTokenResponse.data.token

                Log.i("VIDEO", "video token: $videoToken")
                Log.i("VIDEO", "url $url")

                val response: Response<ResponseBody>? = videoRemoteManager.downloadM3U8File(
                    url = SignatureUtils.encryptURL(url),
                    videoToken = videoToken
                )

                if (response?.isSuccessful ?: false) {
                    val responseBody = response.body()


                    if (responseBody != null) {
                        Log.i("VIDEO", "获取到 M3U8 文件")
                        val tempFile = File(context.cacheDir, "temp_video_${System.currentTimeMillis()}.m3u8")
                        tempFile.outputStream().use { output ->
                            responseBody.byteStream().use { input ->
                                input.copyTo(output)
                            }
                        }
                        return@withContext tempFile
                    }
                }

                return@withContext null
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext null
            }
        }
    }

    suspend fun parseM3U8File(filePath: String) {

    }
}