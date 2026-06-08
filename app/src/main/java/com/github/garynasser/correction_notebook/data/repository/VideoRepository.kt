package com.github.garynasser.correction_notebook.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.github.garynasser.correction_notebook.data.model.yanhe.Classroom
import com.github.garynasser.correction_notebook.data.model.yanhe.Course
import com.github.garynasser.correction_notebook.data.model.yanhe.CourseSection
import com.github.garynasser.correction_notebook.data.remote.manager.VideoRemoteManager
import com.github.garynasser.correction_notebook.utils.SignatureUtils
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
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
    private val gson = Gson()
    @Volatile private var cachedVideoToken: CachedVideoToken? = null
    @Volatile private var cachedUserBadge: String? = null

    data class YanheAuthData(
        val authenticatedUrl: String,
        val headers: Map<String, String>,
    )

    private data class CachedVideoToken(
        val token: String,
        val expiredAtSeconds: Long
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
            response?.let {
                if (it.code != 0 && it.code != 200) return emptyList()
                parseCourseResult(it.data, it.message, it.code, page).courses
            } ?: emptyList()
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
            val result = getPersonalCoursePage(
                page = page,
                pageSize = pageSize
            )
            result.courses.filter { course ->
                keyword.isNullOrBlank() ||
                    course.nameZh.contains(keyword, ignoreCase = true) ||
                    course.nameEn.contains(keyword, ignoreCase = true) ||
                    course.professors.any { it.contains(keyword, ignoreCase = true) }
            }
        } catch (e: Exception) {
            Log.e("VIDEO_REPO", "获取个人课程列表失败", e)
            emptyList()
        }
    }

    suspend fun getPersonalCoursePage(
        page: Int,
        pageSize: Int,
    ): PaginatedCourseResult {
        val response = videoRemoteManager.getPrivateCourseList(
            page = page,
            pageSize = pageSize
        ) ?: throw Exception("请先登录延河课堂")

        if (response.code != 0 && response.code != 200) {
            throw Exception(response.message.ifBlank { "延河课堂返回异常：${response.code}" })
        }

        return parseCourseResult(response.data, response.message, response.code, page).also {
            Log.d(
                "VIDEO_REPO",
                "我的课程私有列表 page=$page/${it.lastPage}, count=${it.courses.size}, shape=${describeJson(response.data)}"
            )
        }
    }

    suspend fun getAllPersonalCourses(pageSize: Int = 10, maxPages: Int = 20): List<Course> {
        val allCourses = mutableListOf<Course>()
        var page = 1
        var lastPage: Int
        do {
            val result = getPersonalCoursePage(page = page, pageSize = pageSize)
            allCourses += result.courses
            lastPage = result.lastPage
            page++
        } while (page <= lastPage && page <= maxPages && result.courses.isNotEmpty())

        return allCourses.distinctBy { it.id }
    }

    private fun parseCourseResult(
        dataElement: JsonElement?,
        message: String,
        code: Int,
        page: Int
    ): PaginatedCourseResult {
        val element = dataElement
            ?: throw Exception("延河课堂没有返回课程数据：$code")

        if (element.isJsonArray()) {
            val courses = parseCourseArray(element)
            return PaginatedCourseResult(
                courses = courses,
                currentPage = page,
                lastPage = page
            )
        }

        if (!element.isJsonObject()) {
            throw Exception(message.ifBlank { "延河课堂课程数据格式异常" })
        }

        val obj = element.asJsonObject
        val courseContainer = obj.firstCourseListContainer("data", "courses", "list", "records")
        val coursesElement = courseContainer?.firstArrayOf("data", "courses", "list", "records")
        if (coursesElement != null) {
            return PaginatedCourseResult(
                courses = parseCourseArray(coursesElement),
                currentPage = courseContainer.intValue(
                    "current_page",
                    courseContainer.intValue("currentPage", obj.intValue("currentPage", page))
                ),
                lastPage = courseContainer.intValue(
                    "last_page",
                    courseContainer.intValue(
                        "lastPage",
                        calculatedLastPage(courseContainer, obj, page)
                    )
                )
            )
        }

        val singleCourse = parseCourseElement(element)
        if (singleCourse != null) {
            return PaginatedCourseResult(
                courses = listOf(singleCourse),
                currentPage = page,
                lastPage = page
            )
        }

        throw Exception(message.ifBlank { "延河课堂没有返回分页课程数据" })
    }

    private fun parseCourseArray(element: JsonElement): List<Course> {
        if (!element.isJsonArray()) return emptyList()
        return element.asJsonArray.mapNotNull { parseCourseElement(it) }
    }

    private fun parseCourseElement(element: JsonElement): Course? {
        if (!element.isJsonObject()) return null
        val wrapper = element.asJsonObject
        val obj = wrapper.objectValue("course")
            ?: wrapper.objectValue("course_info")
            ?: wrapper.objectValue("courseInfo")
            ?: wrapper
        val id = obj.intValue("id", 0)
        val nameZh = firstNonBlank(
            obj.stringValue("name_zh", ""),
            obj.stringValue("name", ""),
            wrapper.stringValue("course_name", ""),
            wrapper.stringValue("courseName", "")
        )
        if (id == 0 && nameZh.isBlank()) return null

        val schoolYear = firstNonBlank(
            obj.stringValue("school_year", ""),
            obj.stringValue("schoolYear", ""),
            wrapper.stringValue("school_year", ""),
            wrapper.stringValue("schoolYear", ""),
            wrapper.stringValue("school_year_name", ""),
            wrapper.stringValue("schoolYearName", "")
        )
        val rawSemester = firstNonBlank(
            obj.stringOrObjectName("semester"),
            obj.stringValue("semester_name", ""),
            obj.stringValue("semesterName", ""),
            wrapper.stringOrObjectName("semester"),
            wrapper.stringValue("semester_name", ""),
            wrapper.stringValue("semesterName", ""),
            wrapper.stringValue("semester_name_zh", "")
        )
        val semester = formatCourseSemester(schoolYear, rawSemester)

        return Course(
            nameZh = nameZh,
            orientation = obj.intValue("orientation", 0),
            code = obj.stringValue("code", ""),
            collegeName = obj.stringValue("college_name", ""),
            imageUrl = obj.stringValue("image_url", obj.stringValue("cover", "")),
            collegeCode = obj.stringValue("college_code", ""),
            schoolYear = schoolYear,
            universityCode = obj.stringValue("university_code", ""),
            lxUrl = obj.stringValue("lx_url", ""),
            number = obj.stringValue("number", ""),
            universityId = obj.intValue("university_id", 0),
            semester = semester,
            id = id,
            state = obj.intValue("state", 0),
            views = obj.intValue("views", 0),
            nameEn = obj.stringValue("name_en", ""),
            participantCount = obj.intValue("participant_count", 0),
            professors = parseNames(obj.get("professors")).ifEmpty {
                parseNames(wrapper.get("professors"))
            },
            classrooms = parseClassrooms(obj.get("classrooms")).ifEmpty {
                parseClassrooms(wrapper.get("classrooms"))
            }
        )
    }

    private fun formatCourseSemester(schoolYear: String, rawSemester: String): String {
        return when {
            schoolYear.isNotBlank() && rawSemester == "1" -> "$schoolYear 秋季"
            schoolYear.isNotBlank() && rawSemester == "2" -> "$schoolYear 春季"
            schoolYear.isNotBlank() && rawSemester.isNotBlank() && rawSemester.all { it.isDigit() } ->
                "$schoolYear 第${rawSemester}学期"
            rawSemester.isNotBlank() -> rawSemester
            schoolYear.isNotBlank() -> schoolYear
            else -> "未标注学期"
        }
    }

    private fun parseNames(element: JsonElement?): List<String> {
        if (element == null || !element.isJsonArray()) return emptyList()
        return element.asJsonArray.mapNotNull { item ->
            when {
                item.isJsonPrimitive() -> item.asStringOrNull()
                item.isJsonObject() -> {
                    val obj = item.asJsonObject
                    obj.stringValue("name", obj.stringValue("name_zh", "")).ifBlank { null }
                }
                else -> null
            }
        }
    }

    private fun parseClassrooms(element: JsonElement?): List<Classroom> {
        if (element == null || !element.isJsonArray()) return emptyList()
        return element.asJsonArray.mapNotNull { item ->
            if (!item.isJsonObject()) return@mapNotNull null
            val obj = item.asJsonObject
            Classroom(
                id = obj.intValue("id", 0),
                name = obj.stringValue("name", ""),
                number = obj.stringValue("number", "")
            )
        }
    }

    private fun JsonObject.firstArrayOf(vararg names: String): JsonElement? {
        return names.firstNotNullOfOrNull { name ->
            get(name)?.takeIf { it.isJsonArray() }
        }
    }

    private fun JsonObject.firstCourseListContainer(vararg names: String): JsonObject? {
        if (firstArrayOf(*names) != null) return this
        return names.firstNotNullOfOrNull { name ->
            get(name)
                ?.takeIf { it.isJsonObject() }
                ?.asJsonObject
                ?.takeIf { it.firstArrayOf(*names) != null }
        }
    }

    private fun JsonObject.objectValue(name: String): JsonObject? {
        return get(name)?.takeIf { it.isJsonObject() }?.asJsonObject
    }

    private fun JsonObject.stringValue(name: String, default: String): String {
        return get(name)?.asStringOrNull() ?: default
    }

    private fun JsonObject.intValue(name: String, default: Int): Int {
        val element = get(name) ?: return default
        return runCatching {
            when {
                element.isJsonPrimitive() && element.asJsonPrimitive.isNumber -> element.asInt
                element.isJsonPrimitive() -> element.asString.toIntOrNull() ?: default
                else -> default
            }
        }.getOrDefault(default)
    }

    private fun JsonObject.stringOrObjectName(name: String): String {
        val element = get(name) ?: return ""
        return when {
            element.isJsonPrimitive() -> element.asStringOrNull().orEmpty()
            element.isJsonObject() -> {
                val obj = element.asJsonObject
                firstNonBlank(
                    obj.stringValue("name", ""),
                    obj.stringValue("name_zh", ""),
                    obj.stringValue("title", ""),
                    obj.stringValue("value", "")
                )
            }
            else -> ""
        }
    }

    private fun calculatedLastPage(container: JsonObject, root: JsonObject, default: Int): Int {
        val total = container.intValue("total", root.intValue("total", 0))
        val perPage = container.intValue(
            "per_page",
            container.intValue("perPage", root.intValue("per_page", root.intValue("perPage", 0)))
        )
        if (total > 0 && perPage > 0) {
            return ((total + perPage - 1) / perPage).coerceAtLeast(default)
        }
        return default
    }

    private fun describeJson(element: JsonElement?): String {
        return when {
            element == null -> "null"
            element.isJsonArray() -> "array(size=${element.asJsonArray.size()})"
            element.isJsonObject() -> {
                val keys = element.asJsonObject.entrySet()
                    .map { it.key }
                    .take(8)
                    .joinToString(",")
                "object(keys=$keys)"
            }
            else -> element.javaClass.simpleName
        }
    }

    private fun firstNonBlank(vararg values: String): String {
        return values.firstOrNull { it.isNotBlank() } ?: ""
    }

    private fun JsonElement.asStringOrNull(): String? {
        return runCatching {
            if (isJsonPrimitive()) asString else null
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun JsonElement.isJsonArray(): Boolean = isJsonArray

    private fun JsonElement.isJsonObject(): Boolean = isJsonObject

    private fun JsonElement.isJsonPrimitive(): Boolean = isJsonPrimitive

    data class PaginatedCourseResult(
        val courses: List<Course>,
        val currentPage: Int,
        val lastPage: Int
    )

    /**
     * 获取课程的章节/课时列表
     * @param courseId 课程的唯一 ID
     * @return 章节列表，如果失败或为空则返回空列表
     */
    suspend fun getCourseSession(courseId: Int): List<CourseSection> {
        return try {
            val response = videoRemoteManager.getCourseSession(
                courseId = courseId,
                withPage = true,
                page = 1,
                pageSize = 200,
                orderType = null,
                orderTypeWeight = null
            )

            if (response == null) {
                throw Exception("请先登录延河课堂")
            }
            if (response.code != 0 && response.code != 200) {
                throw Exception(response.message.ifBlank { "延河课堂返回异常：${response.code}" })
            }

            parseCourseSections(response.data)
        } catch (e: Exception) {
            Log.e("VIDEO_REPO", "获取课程章节失败: courseId=$courseId", e)
            throw e
        }
    }

    private fun parseCourseSections(dataElement: JsonElement?): List<CourseSection> {
        val element = dataElement ?: throw Exception("延河课堂没有返回课程资源数据")
        val listElement = when {
            element.isJsonArray() -> element
            element.isJsonObject() -> {
                val obj = element.asJsonObject
                obj.firstArrayOf("data", "sessions", "list", "records")
                    ?: obj.get("data")?.takeIf { it.isJsonObject() }?.asJsonObject?.firstArrayOf("data", "sessions", "list", "records")
                    ?: throw Exception("延河课堂课程资源数据格式异常")
            }
            else -> throw Exception("延河课堂课程资源数据格式异常")
        }
        return listElement.asJsonArray.mapNotNull { item ->
            parseCourseSectionElement(item)
        }
    }

    suspend fun getCourseSessionDetail(sessionId: Int): CourseSection {
        val response = videoRemoteManager.getCourseSessionDetail(sessionId)
            ?: throw Exception("请先登录延河课堂")
        if (response.code != 0 && response.code != 200) {
            throw Exception(response.message.ifBlank { "延河课堂节次详情返回异常：${response.code}" })
        }
        return parseCourseSectionElement(response.data)
            ?: throw Exception("延河课堂没有返回节次详情")
    }

    private fun parseCourseSectionElement(element: JsonElement?): CourseSection? {
        if (element == null || !element.isJsonObject()) return null
        val wrapper = element.asJsonObject
        val obj = wrapper.objectValue("session")
            ?: wrapper.objectValue("course_session")
            ?: wrapper.objectValue("courseSession")
            ?: wrapper
        return runCatching { gson.fromJson(obj, CourseSection::class.java) }.getOrNull()
    }

    /**
     * 辅助函数：获取新鲜 Token
     */
    suspend fun getFreshVideoToken(): String {
        val nowSeconds = System.currentTimeMillis() / 1000
        cachedVideoToken
            ?.takeIf { it.token.isNotBlank() && it.expiredAtSeconds > nowSeconds + 180 }
            ?.let { return it.token }

        val badge = getYanheUserBadge()
        val response = videoRemoteManager.getVideoToken(id = badge)
            ?: throw Exception("无法获取有效的视频 Token")
        val data = response.data ?: throw Exception("无法获取有效的视频 Token")
        if (response.code != 0 && response.code != 200) {
            throw Exception(response.message.ifBlank { "延河课堂视频授权失败：${response.code}" })
        }
        if (data.token.isBlank()) {
            throw Exception("延河课堂视频授权缺少 Token")
        }
        cachedVideoToken = CachedVideoToken(
            token = data.token,
            expiredAtSeconds = data.expiredAt
        )
        return data.token
    }

    private suspend fun getYanheUserBadge(): String {
        cachedUserBadge?.takeIf { it.isNotBlank() }?.let { return it }
        val response = videoRemoteManager.getYanheUser()
            ?: throw Exception("无法获取延河课堂用户信息")
        if (response.code != 0 && response.code != 200) {
            throw Exception(response.message.ifBlank { "延河课堂用户信息返回异常：${response.code}" })
        }
        val badge = response.data
            ?.takeIf { it.isJsonObject() }
            ?.asJsonObject
            ?.stringValue("badge", "")
            ?.takeIf { it.isNotBlank() }
            ?: "0"
        cachedUserBadge = badge
        return badge
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
