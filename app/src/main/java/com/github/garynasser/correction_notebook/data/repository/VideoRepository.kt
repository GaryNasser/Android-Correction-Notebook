package com.github.garynasser.correction_notebook.data.repository

import android.util.Log
import com.github.garynasser.correction_notebook.data.model.yanhe.Classroom
import com.github.garynasser.correction_notebook.data.model.yanhe.Course
import com.github.garynasser.correction_notebook.data.model.yanhe.CourseSection
import com.github.garynasser.correction_notebook.data.remote.manager.VideoRemoteManager
import com.github.garynasser.correction_notebook.data.remote.api.YanhePortal
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class VideoRepository @Inject constructor(
    private val videoRemoteManager: VideoRemoteManager,
) {
    private val gson = Gson()
    private val cacheLock = Any()
    private val profileMutex = Mutex()
    private var cacheGeneration = 0L
    @Volatile private var cachedProfile: CachedProfile? = null

    data class YanheAuthData(val authenticatedUrl: String, val headers: Map<String, String>)
    private data class CachedProfile(val profile: JsonObject, val sessionVersion: Long, val generation: Long)

    fun clearSessionCache() {
        synchronized(cacheLock) {
            cacheGeneration++
            cachedProfile = null
        }
    }

    suspend fun getYanheAuthData(originalUrl: String): YanheAuthData {
        val generation = synchronized(cacheLock) { cacheGeneration }
        val version = videoRemoteManager.sessionVersion
        val profile = profileMutex.withLock {
            val cached = synchronized(cacheLock) {
                ensureCacheCurrent(generation, version)
                cachedProfile?.takeIf { it.sessionVersion == version && it.generation == generation }
            }
            cached?.profile ?: run {
                val response = videoRemoteManager.getYanheUser(version) ?: throw IllegalStateException("请先登录延河课堂")
                if (response.code != 200 && response.code != 0) {
                    throw IllegalStateException(response.message.ifBlank { "无法获取播放授权信息" })
                }
                val data = response.data?.takeIf { it.isJsonObject }?.asJsonObject
                    ?: throw IllegalStateException("延河课堂授权信息格式异常")
                synchronized(cacheLock) {
                    ensureCacheCurrent(generation, version)
                    cachedProfile = CachedProfile(data, version, generation)
                }
                data
            }
        }
        val finalUrl = YanhePortal.sign(originalUrl, profile, System.currentTimeMillis() / 1000)
        return synchronized(cacheLock) {
            ensureCacheCurrent(generation, version)
            YanheAuthData(finalUrl, mapOf(
                "Origin" to YanhePortal.ORIGIN,
                "Referer" to "${YanhePortal.ORIGIN}/",
                "User-Agent" to YanhePortal.USER_AGENT
            ))
        }
    }

    private fun ensureCacheCurrent(generation: Long, version: Long) {
        videoRemoteManager.ensureSession(version)
        if (cacheGeneration != generation) throw CancellationException("视频授权缓存已变更")
    }

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
            val result = response ?: throw Exception("延河课堂课程列表无响应")
            if (result.code != 0 && result.code != 200) {
                throw Exception(result.message.ifBlank { "延河课堂返回异常：${result.code}" })
            }
            parseCourseResult(result.data, result.message, result.code, page, pageSize).courses
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("VIDEO_REPO", "获取全校课程列表失败", e)
            throw e
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("VIDEO_REPO", "获取个人课程列表失败", e)
            throw e
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

        val portalSuccess = response.code == 1000 && response.data?.takeIf { it.isJsonObject }
            ?.asJsonObject?.objectValue("result")?.get("data")?.isJsonArray == true
        if (response.code != 0 && response.code != 200 && !portalSuccess) {
            throw Exception(response.message.ifBlank { "延河课堂返回异常：${response.code}" })
        }

        return parseCourseResult(response.data, response.message, response.code, page, pageSize)
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
        page: Int,
        pageSize: Int
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

        val root = element.asJsonObject
        val obj = root.objectValue("result") ?: root
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
                        calculatedLastPage(courseContainer, obj, page, pageSize)
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
        YanhePortal.course(wrapper)?.let { return it }
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
        if (id <= 0 || nameZh.isBlank()) return null

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

    private fun calculatedLastPage(container: JsonObject, root: JsonObject, default: Int, pageSize: Int): Int {
        val total = container.intValue("total", root.intValue("total", 0))
        val perPage = container.intValue(
            "per_page",
            container.intValue("perPage", root.intValue("per_page", root.intValue("perPage", pageSize)))
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
            if (response.code == 10002) return emptyList()
            if (response.code != 0 && response.code != 200) {
                throw Exception(response.message.ifBlank { "延河课堂返回异常：${response.code}" })
            }

            response.data?.takeIf { it.isJsonObject && it.asJsonObject.has("sub_list") }?.let {
                YanhePortal.sections(it.asJsonObject, courseId)
            } ?: parseCourseSections(response.data)
        } catch (e: CancellationException) {
            throw e
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

    suspend fun getCourseSessionDetail(sessionId: Int, courseId: Int): CourseSection {
        val response = videoRemoteManager.getCourseSessionDetail(sessionId, courseId)
            ?: throw Exception("请先登录延河课堂")
        if (response.code != 0 && response.code != 200) {
            throw Exception(response.message.ifBlank { "延河课堂节次详情返回异常：${response.code}" })
        }
        val data = response.data?.takeIf { it.isJsonObject }?.asJsonObject
            ?: throw Exception("延河课堂没有返回节次详情")
        return if (data.has("video_list")) YanhePortal.section(data, courseId, sessionId)
            else parseCourseSectionElement(data) ?: throw Exception("延河课堂没有返回节次详情")
    }

    private fun parseCourseSectionElement(element: JsonElement?): CourseSection? {
        if (element == null || !element.isJsonObject()) return null
        val wrapper = element.asJsonObject
        val obj = wrapper.objectValue("session")
            ?: wrapper.objectValue("course_session")
            ?: wrapper.objectValue("courseSession")
            ?: wrapper
        return runCatching { gson.fromJson(obj, CourseSection::class.java) }
            .getOrNull()
            ?.takeIf { it.id > 0 }
    }

}
