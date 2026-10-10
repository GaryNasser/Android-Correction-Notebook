package com.github.garynasser.correction_notebook.data.remote.school

import com.github.garynasser.correction_notebook.data.remote.network.awaitResponse
import com.github.garynasser.correction_notebook.data.model.school.SchoolCourseRaw
import com.github.garynasser.correction_notebook.data.model.school.SchoolScheduleException
import com.github.garynasser.correction_notebook.data.model.school.SchoolTerm
import com.github.garynasser.correction_notebook.data.model.school.SchoolTermSchedule
import com.github.garynasser.correction_notebook.data.remote.cas.BitCasClient
import com.github.garynasser.correction_notebook.di.BasicRetrofit
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.JavaNetCookieJar
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.CookieManager
import java.net.CookiePolicy
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SchoolScheduleRemoteDataSource @Inject constructor(
    private val bitCasClient: BitCasClient,
    @BasicRetrofit private val okHttpClient: OkHttpClient
) {
    private fun newSchoolClient(): OkHttpClient = okHttpClient.newBuilder()
        .cookieJar(JavaNetCookieJar(CookieManager().apply {
            setCookiePolicy(CookiePolicy.ACCEPT_ALL)
        }))
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun getCurrentTerm(studentId: String, password: String): SchoolTerm = withContext(Dispatchers.IO) {
        val client = newSchoolClient()
        establishSession(client, studentId, password)
        resolveTermStart(client, parseCurrentTerm(getJson(client, CURRENT_TERM_URL)))
    }

    suspend fun getTerms(studentId: String, password: String): List<SchoolTerm> = withContext(Dispatchers.IO) {
        val client = newSchoolClient()
        establishSession(client, studentId, password)
        parseTerms(getJson(client, TERMS_URL), currentOnly = false)
    }

    suspend fun getCurrentTermSchedule(studentId: String, password: String): SchoolTermSchedule = withContext(Dispatchers.IO) {
        val client = newSchoolClient()
        establishSession(client, studentId, password)
        val term = resolveTermStart(client, parseCurrentTerm(getJson(client, CURRENT_TERM_URL)))
        SchoolTermSchedule(
            term = term,
            courses = fetchSchedule(client, term.id)
        )
    }

    suspend fun getTermSchedule(studentId: String, password: String, term: SchoolTerm): SchoolTermSchedule = withContext(Dispatchers.IO) {
        val client = newSchoolClient()
        establishSession(client, studentId, password)
        val resolved = resolveTermStart(client, term)
        SchoolTermSchedule(resolved, fetchSchedule(client, resolved.id))
    }

    private suspend fun resolveTermStart(client: OkHttpClient, term: SchoolTerm): SchoolTerm {
        if (term.startDate != null) return term
        val payload = JsonObject().apply { addProperty("XNXQDM", term.id); addProperty("ZC", "1") }
        val request = Request.Builder().url(WEEK_DATES_URL).headers(defaultHeaders())
            .post(FormBody.Builder().add("requestParamStr", payload.toString()).build()).build()
        val root = executeJsonRequest(client, request)
        val rows = root.get("data")?.takeIf { it.isJsonArray }?.asJsonArray?.toList().orEmpty()
        val monday = rows.mapNotNull { it.asJsonObjectOrNull() }
            .singleOrNull { it.firstInt("XQ") == 1 }?.firstDate("RQ")
            ?.takeIf { it.dayOfWeek == java.time.DayOfWeek.MONDAY }
            ?: throw SchoolScheduleException("学校校历没有返回有效的第一周周一日期，已保留本地课表")
        return term.copy(startDate = monday)
    }

    private suspend fun fetchSchedule(client: OkHttpClient, termId: String): List<SchoolCourseRaw> {
        val body = FormBody.Builder()
            .add("XNXQDM", termId)
            .add("xnxqdm", termId)
            .build()
        val request = Request.Builder()
            .url(SCHEDULE_URL)
            .headers(defaultHeaders())
            .post(body)
            .build()
        return parseCourses(executeJsonRequest(client, request))
    }

    private suspend fun establishSession(client: OkHttpClient, studentId: String, password: String) {
        val preflight = Request.Builder().url(SCHOOL_AUTH_URL).headers(defaultHeaders()).get().build()
        val service = client.newBuilder().followRedirects(false).followSslRedirects(false).build()
            .newCall(preflight).awaitResponse { response ->
                if (response.code !in 300..399) throw SchoolScheduleException("教学中心认证入口没有返回跳转地址")
                val loginUrl = response.header("Location")?.let { response.request.url.resolve(it) }
                    ?: throw SchoolScheduleException("教学中心认证入口缺少跳转地址")
                if (loginUrl.scheme != "https" || loginUrl.host != "sso.bit.edu.cn" || loginUrl.port != 443 || loginUrl.encodedPath != "/cas/login") {
                    throw SchoolScheduleException("教学中心返回了非学校统一认证地址")
                }
                val callback = loginUrl.queryParameter("service")?.toHttpUrl()
                    ?: throw SchoolScheduleException("教学中心未提供统一认证回调")
                if (callback.scheme != "https" || callback.host != "jxzxehall.bit.edu.cn" || callback.port != 443 ||
                    callback.encodedPath != "/auth-protocol-core/loginSuccess" || callback.queryParameter("sessionToken").isNullOrBlank() ||
                    callback.username.isNotEmpty() || callback.password.isNotEmpty()) {
                    throw SchoolScheduleException("教学中心统一认证回调地址异常")
                }
                callback.toString()
            }
        val st = bitCasClient.getServiceTicketFor(studentId, password, service)
        val callback = service.toHttpUrl()
            .newBuilder()
            .addQueryParameter("ticket", st)
            .build()
        val request = Request.Builder()
            .url(callback)
            .headers(defaultHeaders())
            .get()
            .build()
        visitSchoolPage(client, request)
        listOf(SCHOOL_APP_INDEX_URL, SCHOOL_CONFIG_URL, SCHOOL_INDEX_URL).forEach { url ->
            visitSchoolPage(client, Request.Builder().url(url).headers(defaultHeaders()).get().build())
        }
    }

    private suspend fun visitSchoolPage(client: OkHttpClient, request: Request) {
        client.newCall(request).awaitResponse { response ->
            if (!response.isSuccessful || response.request.url.host !in setOf("jxzxehall.bit.edu.cn", "jxzxehallapp.bit.edu.cn")) {
                throw SchoolScheduleException("学校系统认证或应用初始化失败：${response.code}")
            }
        }
    }

    private suspend fun getJson(client: OkHttpClient, url: String): JsonObject {
        val request = Request.Builder()
            .url(url)
            .headers(defaultHeaders())
            .get()
            .build()
        return executeJsonRequest(client, request)
    }

    private suspend fun executeJsonRequest(client: OkHttpClient, request: Request): JsonObject {
        return client.newCall(request).awaitResponse { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw SchoolScheduleException("学校系统请求失败：${response.code}")
            }
            runCatching { JsonParser.parseString(body).asJsonObject }
                .getOrElse { throw SchoolScheduleException("学校课表格式暂不支持，已保留本地日程", it) }
        }
    }

    internal fun parseCurrentTerm(root: JsonObject): SchoolTerm {
        return parseTerms(root, currentOnly = true).firstOrNull()
            ?: throw SchoolScheduleException("学校系统没有返回当前学期")
    }

    private fun parseTerms(root: JsonObject, currentOnly: Boolean): List<SchoolTerm> {
        val rows = findRows(
            root = root,
            keys = listOf("dqxnxq", "xnxqcx", "rows"),
            rowMarkerKeys = listOf("XNXQDM", "XNXQID", "xnxqdm")
        )
            .ifEmpty { findObjectsByKeys(root, listOf("dqxnxq", "xnxqcx")) }
        return rows.mapNotNull { element ->
            val item = element.asJsonObjectOrNull() ?: return@mapNotNull null
            val id = item.firstString("XNXQDM", "XNXQID", "DM", "id", "value", "xnxqdm")
                ?: return@mapNotNull null
            val name = item.firstString("XNXQMC", "MC", "name", "label", "xnxqmc") ?: id
            SchoolTerm(
                id = id,
                name = name,
                startDate = item.firstDate("KSRQ", "startDate", "ksrq"),
                endDate = item.firstDate("JSRQ", "endDate", "jsrq"),
                isCurrent = currentOnly || item.firstString("DQXQ", "isCurrent", "current") == "1"
            )
        }
    }

    internal fun parseCourses(root: JsonObject): List<SchoolCourseRaw> {
        val rows = findRows(
            root = root,
            keys = listOf("cxxszhxqkb", "rows"),
            rowMarkerKeys = listOf("KCM", "KCMC", "courseName", "kcmc", "kcm")
        )
        return rows.mapIndexed { index, element ->
            val item = element.asJsonObjectOrNull()
                ?: throw SchoolScheduleException("第 ${index + 1} 条课程格式有误，已保留本地课表")
            val courseName = item.firstString("KCM", "KCMC", "courseName", "kcmc", "kcm").orEmpty()
            if (courseName.isBlank()) {
                throw SchoolScheduleException("第 ${index + 1} 条课程缺少名称，已保留本地课表")
            }
            val weekdayText = item.firstString("XQ", "SKXQ", "weekday", "xq", "XQJ", "SKXQJ", "weekdayName")
            val weekday = item.firstInt("XQ", "SKXQ", "weekday", "xq")
                ?: SchoolScheduleParsers.parseWeekday(weekdayText)
            val usesNativeSections = item.has("KSJC") || item.has("JSJC")
            val sectionRange = if (usesNativeSections) {
                val first = item.firstInt("KSJC")
                val last = item.firstInt("JSJC")
                if (first != null && last != null) first to last else null
            } else SchoolScheduleParsers.parseSectionRange(item.firstString("JC", "SKJC", "JCDM", "sections", "jc"))
            val weeks = SchoolScheduleParsers.parseWeeks(
                item.firstString("ZC", "SKZC", "ZCMC", "weeks", "zc", "zcmc"),
                binaryMask = usesNativeSections
            )
            if (weekday == null || sectionRange == null || weeks.isEmpty()) {
                throw SchoolScheduleException("课程“$courseName”的星期、节次或周次不完整，已保留本地课表")
            }

            SchoolCourseRaw(
                courseName = courseName,
                teacherName = item.firstString("SKJS", "JSXM", "teacherName", "jsxm").orEmpty(),
                location = item.firstString("JASMC", "JAS", "CDMC", "location", "jsmc").orEmpty(),
                weekday = weekday,
                startSection = sectionRange.first,
                endSection = sectionRange.second,
                weeks = weeks,
                courseCode = item.firstString("KCH", "KCDM", "courseCode", "kch").orEmpty(),
                classCode = item.firstString("JXBID", "JXBMC", "classCode", "jxbmc").orEmpty(),
                campus = item.firstString("XQMC", "campus", "xqmc").orEmpty(),
                extraDescription = item.firstString("BZ", "remark", "note").orEmpty()
            )
        }
    }

    private fun findRows(
        root: JsonObject,
        keys: List<String>,
        rowMarkerKeys: List<String>
    ): List<JsonElement> {
        val queue = ArrayDeque<JsonElement>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            when {
                current.isJsonArray -> {
                    val array = current.asJsonArray
                    if (array.any { element ->
                            element.isJsonObject && rowMarkerKeys.any(element.asJsonObject::has)
                        }
                    ) {
                        return array.toList()
                    }
                    array.forEach { queue.add(it) }
                }
                current.isJsonObject -> {
                    val obj = current.asJsonObject
                    keys.forEach { key ->
                        obj.get(key)?.let { found ->
                            if (found.isJsonArray) return found.asJsonArray.toList()
                            if (found.isJsonObject) queue.add(found)
                        }
                    }
                    obj.entrySet().forEach { queue.add(it.value) }
                }
            }
        }
        return emptyList()
    }

    private fun findObjectsByKeys(root: JsonObject, keys: List<String>): List<JsonElement> {
        val queue = ArrayDeque<JsonElement>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (current.isJsonObject) {
                val obj = current.asJsonObject
                keys.forEach { key ->
                    val found = obj.get(key)
                    if (found != null && found.isJsonObject) return listOf(found)
                }
                obj.entrySet().forEach { queue.add(it.value) }
            } else if (current.isJsonArray) {
                current.asJsonArray.forEach { queue.add(it) }
            }
        }
        return emptyList()
    }

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.firstString(vararg keys: String): String? {
        keys.forEach { key ->
            val element = get(key)?.takeUnless { it.isJsonNull } ?: return@forEach
            if (!element.isJsonPrimitive) return@forEach
            return element.asString.trim().takeIf { it.isNotBlank() }
        }
        return null
    }

    private fun JsonObject.firstInt(vararg keys: String): Int? {
        return firstString(*keys)?.toIntOrNull()
    }

    private fun JsonObject.firstDate(vararg keys: String): LocalDate? {
        val raw = firstString(*keys) ?: return null
        val candidates = buildList {
            add(raw)
            raw.substringBefore("T").takeIf { it != raw }?.let(::add)
            raw.substringBefore(" ").takeIf { it != raw }?.let(::add)
            Regex("""\d{4}[-/.]\d{1,2}[-/.]\d{1,2}""")
                .find(raw)
                ?.value
                ?.let(::add)
            Regex("""\d{4}年\d{1,2}月\d{1,2}日""")
                .find(raw)
                ?.value
                ?.let(::add)
        }
        val patterns = listOf(
            "yyyy-MM-dd",
            "yyyy-M-d",
            "yyyy/MM/dd",
            "yyyy/M/d",
            "yyyy.MM.dd",
            "yyyy.M.d",
            "yyyyMMdd",
            "yyyy年M月d日"
        )
        return candidates.firstNotNullOfOrNull { candidate ->
            patterns.firstNotNullOfOrNull { pattern ->
                runCatching { LocalDate.parse(candidate, DateTimeFormatter.ofPattern(pattern)) }.getOrNull()
            }
        }
    }

    private fun defaultHeaders(): Headers = Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Accept", "application/json, text/plain, */*")
        .add("Referer", SCHOOL_INDEX_URL)
        .add("Origin", SCHOOL_BASE_URL)
        .build()

    companion object {
        private const val SCHOOL_BASE_URL = "https://jxzxehallapp.bit.edu.cn"
        private const val SCHOOL_APP_INDEX_URL = "$SCHOOL_BASE_URL/jwapp/sys/xsfacx/*default/index.do"
        private val SCHOOL_AUTH_URL = "https://jxzxehall.bit.edu.cn/auth-protocol-core/login".toHttpUrl()
            .newBuilder().addQueryParameter("service", SCHOOL_APP_INDEX_URL).build()
        private const val SCHOOL_CONFIG_URL = "$SCHOOL_BASE_URL/jwapp/sys/funauthapp/api/getAppConfig/xsfacx-4766859113956613.do?v=08260885168155102"
        private const val SCHOOL_INDEX_URL = "$SCHOOL_BASE_URL/jwapp/sys/wdkbby/*default/index.do"
        private const val CURRENT_TERM_URL = "$SCHOOL_BASE_URL/jwapp/sys/wdkbby/modules/jshkcb/dqxnxq.do"
        private const val TERMS_URL = "$SCHOOL_BASE_URL/jwapp/sys/wdkbby/modules/jshkcb/xnxqcx.do"
        private const val SCHEDULE_URL = "$SCHOOL_BASE_URL/jwapp/sys/wdkbby/modules/xskcb/cxxszhxqkb.do"
        private const val WEEK_DATES_URL = "$SCHOOL_BASE_URL/jwapp/sys/wdkbby/wdkbByController/cxzkbrq.do"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    }
}
