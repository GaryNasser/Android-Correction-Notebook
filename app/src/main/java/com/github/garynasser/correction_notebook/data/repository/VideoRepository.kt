package com.github.garynasser.correction_notebook.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.github.garynasser.correction_notebook.data.model.yanhe.Course
import com.github.garynasser.correction_notebook.data.model.yanhe.CourseSection
import com.github.garynasser.correction_notebook.data.remote.manager.VideoRemoteManager
import com.github.garynasser.correction_notebook.utils.SignatureUtils
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ResponseBody
import retrofit2.Response
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import androidx.core.net.toUri

@Singleton
class VideoRepository @Inject constructor(
    private val videoRemoteManager: VideoRemoteManager,
    @ApplicationContext private val context: Context,
) {
    data class YanheAuthData(
        val authenticatedUrl: String,
        val headers: Map<String, String>,
    )

    suspend fun getYanheAuthData(originalUrl: String): YanheAuthData {
        val token = getFreshVideoToken()

        // 1. 调用你之前的 SignatureUtils 生成签名
        // 假设你的 SignatureUtils 会根据 URL、Token、时间戳生成签名
        val signature = SignatureUtils.getSignature()

        Log.d("VIDEO", signature["Xclient_Timestamp"]?:"param")

        val md5Url = SignatureUtils.encryptURL(originalUrl)
        Log.d("VIDEO", "encrypt url: $md5Url")

        // 2. 构造带 Query 参数的 URL (对应你 Retrofit 的 @Query)
        val finalUrl = md5Url.toUri().buildUpon()
            .appendQueryParameter("Xvideo_Token", token)
            .appendQueryParameter("Xclient_Timestamp", signature["Xclient-Timestamp"])
            .appendQueryParameter("Xclient_Signature", signature["Xclient-Signature"])
            .appendQueryParameter("Platform", "yhkt_user")
            .appendQueryParameter("Xclient_Version", "v1")
            .build().toString()

        Log.d("VIDEO", "encrypt url: $finalUrl")

        // 3. 构造 Header (对应你 Retrofit 的 @Headers)
        val headers = mapOf(
            "Origin" to "https://www.yanhekt.cn",
            "Referer" to "https://www.yanhekt.cn/",
            "xdomain-client" to "web_user",
            "Xdomain-Client" to "web_user",
            "Xclient-Version" to "v1",
            "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        )

        return YanheAuthData(finalUrl, headers)
    }

    /**
     * 下载原始的 M3U8 文件并保存到缓存目录
     * @param url 视频原始链接
     * @return 临时文件 File 对象
     */
//    suspend fun getM3U8File(url: String): File? {
//        Log.d("VIDEO_REPO", "=== 开始下载 M3U8，URL: $url ===")
//        return withContext(Dispatchers.IO) {
//            try {
//                Log.d("VIDEO_REPO", "1. 获取 Token...")
//                val vToken = getFreshVideoToken()
//                Log.d("VIDEO_REPO", "2. Token 获取成功: ${vToken.take(10)}...")
//
//                Log.d("VIDEO_REPO", "3. 开始网络请求...")
//                val response = videoRemoteManager.downloadFile(
//                    url = SignatureUtils.encryptURL(url),
//                    videoToken = vToken
//                )
//
//                Log.d("VIDEO_REPO", "4. 网络响应: code=${response?.code()}, isSuccessful=${response?.isSuccessful}")
//
//                if (response?.isSuccessful == true) {
//                    val responseBody = response.body()
//                    if (responseBody != null) {
//                        val tempFile = File(context.cacheDir, "temp_raw_${System.currentTimeMillis()}.m3u8")
//                        Log.d("VIDEO_REPO", "5. 开始写入文件: ${tempFile.absolutePath}")
//
//                        responseBody.byteStream().use { input ->
//                            tempFile.outputStream().use { output ->
//                                val bytesCopied = input.copyTo(output)
//                                Log.d("VIDEO_REPO", "6. 写入完成，大小: $bytesCopied 字节")
//                            }
//                        }
//
//                        Log.d("VIDEO_REPO", "7. M3U8 下载成功，文件大小: ${tempFile.length()} 字节")
//                        return@withContext tempFile
//                    } else {
//                        Log.e("VIDEO_REPO", "响应体为空")
//                    }
//                } else {
//                    Log.e("VIDEO_REPO", "M3U8 下载失败: HTTP ${response?.code()}")
//                }
//                null
//            } catch (e: Exception) {
//                Log.e("VIDEO_REPO", "getM3U8File 发生异常", e)
//                null
//            }
//        }
//    }

    /**
     * 获取全校课程列表
     * @param semester 学期 ID (可选)
     * @param page 当前页码
     * @param pageSize 每页条数
     * @param keyword 搜索关键词 (可选)
     */
    suspend fun getCourse(
        semester: Int?,
        page: Int,
        pageSize: Int,
        keyword: String? = null,
    ): List<Course> {
        return try {
            val response = videoRemoteManager.getCourseList(
                semester = semester,
                page = page,
                pageSize = pageSize,
                keyword = keyword
            )
            // 从 ApiResponse -> PaginatedData -> courses 提取列表
            response?.data?.courses ?: emptyList()
        } catch (e: Exception) {
            Log.e("VIDEO_REPO", "获取全校课程列表失败", e)
            emptyList()
        }
    }

    /**
     * 获取个人课程列表（我听的课）
     * @param semester 学期 ID (可选)
     * @param page 当前页码
     * @param pageSize 每页条数
     * @param keyword 搜索关键词 (可选)
     */
    suspend fun getPersonalCourse(
        semester: Int?,
        page: Int,
        pageSize: Int,
        keyword: String? = null,
    ): List<Course> {
        return try {
            val response = videoRemoteManager.getPersonalCourseList(
                semester = semester,
                page = page,
                pageSize = pageSize,
                keyword = keyword
            )
            // 数据提取路径同上
            response?.data?.courses ?: emptyList()
        } catch (e: Exception) {
            Log.e("VIDEO_REPO", "获取个人课程列表失败", e)
            emptyList()
        }
    }

    /**
     * 获取课程的章节/课时列表
     * @param courseId 课程的唯一 ID
     * @return 章节列表，如果失败或为空则返回空列表
     */
    suspend fun getCourseSession(courseId: Int): List<CourseSection> {
        return try {
            // 1. 调用 Manager 层的方法
            // Manager 内部会自动处理获取 Login Token 和 异常重试逻辑
            val response = videoRemoteManager.getCourseSession(courseId)

            // 2. 提取数据并返回
            // 如果 response 为 null 或者 data 为 null，则返回一个空列表防止 UI 报错
            response?.data ?: emptyList()
        } catch (e: Exception) {
            Log.e("VIDEO_REPO", "获取课程章节失败: courseId=$courseId", e)
            emptyList()
        }
    }

    /**
     * 辅助函数：获取新鲜 Token
     */
    suspend fun getFreshVideoToken(): String {
        val response = videoRemoteManager.getVideoToken()
        // 根据你的 ApiResponse 结构提取 token
        return response?.data?.token ?: throw Exception("无法获取有效的视频 Token")
    }

//    suspend fun processM3U8File(
//        originalUrl: String,
//        tempFile: File,
//        videoName: String
//    ): M3U8DownloadTask? {
//        return withContext(Dispatchers.IO) {
//            try {
//                // --- 诊断 1: 检查源文件 ---
//                if (!tempFile.exists()) {
//                    Log.e("VIDEO", "解析失败原因: 原始临时文件不存在 - ${tempFile.absolutePath}")
//                    return@withContext null
//                }
//                if (tempFile.length() == 0L) {
//                    Log.e("VIDEO", "解析失败原因: 原始临时文件大小为 0，下载可能未完成")
//                    return@withContext null
//                }
//
//                Log.d("VIDEO", "开始解析 M3U8，源文件大小: ${tempFile.length()} 字节")
//
//                // --- 准备工作目录 ---
//                val baseUrl = if (originalUrl.contains("/")) {
//                    originalUrl.substringBeforeLast("/") + "/"
//                } else {
//                    originalUrl + "/"
//                }
//
//                val workDir = File(context.filesDir, "videos/$videoName").apply {
//                    if (!exists()) mkdirs()
//                }
//                val newM3u8File = File(workDir, "$videoName.m3u8")
//                val tsUrlList = mutableListOf<String>()
//
//                // --- 开始读写 ---
//                var tsCount = 0 // 确保这里定义了变量
//
//                newM3u8File.bufferedWriter().use { writer ->
//                    tempFile.forEachLine { line ->
//                        val trimmed = line.trim()
//                        if (trimmed.isEmpty()) return@forEachLine // 跳过空行
//
//                        if (trimmed.startsWith("#")) {
//                            // 处理标签行
//                            if (trimmed.contains("EXT-X-KEY") && trimmed.contains("URI=")) {
//                                val rawKeyUri = trimmed.substringAfter("URI=\"").substringBefore("\"")
//                                val absKeyUrl = if (rawKeyUri.startsWith("http")) rawKeyUri else baseUrl + rawKeyUri
//
//                                // 这里我们先跳过下载 Key，只改写路径
//                                val rewrittenKeyLine = trimmed.replace(Regex("URI=\".*?\""), "URI=\"./key\"")
//                                writer.write(rewrittenKeyLine)
//                            } else {
//                                // 原样保留其他标签（#EXTM3U, #EXTINF 等）
//                                writer.write(trimmed)
//                            }
//                        } else {
//                            // 处理 TS 链接行
//                            val absTsUrl = if (trimmed.startsWith("http")) trimmed else baseUrl + trimmed
//                            tsUrlList.add(absTsUrl)
//
//                            // 写入 0.ts, 1.ts...
//                            writer.write("$tsCount.ts")
//                            tsCount++
//                        }
//                        writer.newLine()
//                    }
//                    writer.flush()
//                }
//
//                // --- 诊断 2: 检查结果 ---
//                if (tsUrlList.isEmpty()) {
//                    Log.e("VIDEO", "解析失败原因: 未能在文件中找到任何 TS 链接")
//                    return@withContext null
//                }
//
//                Log.d("VIDEO", "解析成功! 生成文件: ${newM3u8File.absolutePath}, TS 片段数: ${tsUrlList.size}")
//
//                M3U8DownloadTask(workDir, newM3u8File, tsUrlList)
//
//            } catch (e: Exception) {
//                Log.e("VIDEO", "解析 M3U8 异常崩溃", e)
//                null
//            }
//        }
//    }
//
//    // 下载单个 TS 文件 (内部刷新 Token 和签名)
//    suspend fun downloadSingleTs(url: String, targetFile: File): Boolean {
//        return try {
//            val vToken = getFreshVideoToken()
//            val response = videoRemoteManager.downloadFile(SignatureUtils.encryptURL(url), vToken) // 内部已处理签名
//            if (response?.isSuccessful == true) {
//                response.body()?.byteStream()?.use { input ->
//                    targetFile.outputStream().use { output -> input.copyTo(output) }
//                }
//                true
//            } else false
//        } catch (e: Exception) { false }
//    }
//
//    private suspend fun downloadSingleFile(url: String, target: File) {
//        val vToken = getFreshVideoToken()
//        videoRemoteManager.downloadFile(url, vToken)?.body()?.byteStream()?.use { it.copyTo(target.outputStream()) }
//    }
}

//data class M3U8DownloadTask(val workDir: File, val m3u8File: File, val tsUrls: List<String>)