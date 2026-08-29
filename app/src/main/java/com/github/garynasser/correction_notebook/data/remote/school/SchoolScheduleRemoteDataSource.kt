package com.github.garynasser.correction_notebook.data.remote.school

import androidx.core.net.toUri
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
    private val schoolClient: OkHttpClient = okHttpClient.newBuilder()
        .cookieJar(JavaNetCookieJar(CookieManager().apply {
            setCookiePolicy(CookiePolicy.ACCEPT_ALL)
        }))
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    suspend fun getCurrentTerm(studentId: String, password: String): SchoolTerm = withContext(Dispatchers.IO) {
        establishSession(studentId, password)
        parseCurrentTerm(getJson(CURRENT_TERM_URL))
    }

    suspend fun getTerms(studentId: String, password: String): List<SchoolTerm> = withContext(Dispatchers.IO) {
        establishSession(studentId, password)
        parseTerms(getJson(TERMS_URL), currentOnly = false)
    }

    suspend fun getCurrentTermSchedule(studentId: String, password: String): SchoolTermSchedule = withContext(Dispatchers.IO) {
        establishSession(studentId, password)
        val term = parseCurrentTerm(getJson(CURRENT_TERM_URL))
        SchoolTermSchedule(
            term = term,
            courses = fetchSchedule(term.id)
        )
    }

    suspend fun getSchedule(studentId: String, password: String, termId: String): List<SchoolCourseRaw> = withContext(Dispatchers.IO) {
        establishSession(studentId, password)
        fetchSchedule(termId)
    }

    private fun fetchSchedule(termId: String): List<SchoolCourseRaw> {
        val body = FormBody.Builder()
            .add("XNXQDM", termId)
            .add("xnxqdm", termId)
            .build()
        val request = Request.Builder()
            .url(SCHEDULE_URL)
            .headers(defaultHeaders())
            .post(body)
            .build()
        return parseCourses(executeJsonRequest(request))
    }

    private suspend fun establishSession(studentId: String, password: String) {
        val st = bitCasClient.getServiceTicketFor(studentId, password, SCHOOL_INDEX_URL)
        val callback = SCHOOL_INDEX_URL.toUri()
            .buildUpon()
            .appendQueryParameter("ticket", st)
            .build()
            .toString()
        val request = Request.Builder()
            .url(callback)
            .headers(defaultHeaders())
            .get()
            .build()
        schoolClient
            .newCall(request)
            .execute()
            .use { response ->
                if (!response.isSuccessful) {
                    throw SchoolScheduleException("学校系统认证回调失败：${response.code}")
                }
            }
    }

    private fun getJson(url: String): JsonObject {
        val request = Request.Builder()
            .url(url)
            .headers(defaultHeaders())
            .get()
            .build()
        return executeJsonRequest(request)
    }

    private fun executeJsonRequest(request: Request): JsonObject {
        schoolClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw SchoolScheduleException("学校系统请求失败：${response.code}")
            }
            return runCatching { JsonParser.parseString(body).asJsonObject }
                .getOrElse { throw SchoolScheduleException("学校课表格式暂不支持，已保留本地日程", it) }
        }
    }

    internal fun parseCurrentTerm(root: JsonObject): SchoolTerm {
        return parseTerms(root, currentOnly = true).firstOrNull()
            ?: throw SchoolScheduleException("学校系统没有返回当前学期")
    }

    private fun parseTerms(root: JsonObject, currentOnly: Boolean): List<SchoolTerm> {
        val rows = findRows(root, listOf("dqxnxq", "xnxqcx", "rows"))
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
        val rows = findRows(root, listOf("cxxszhxqkb", "rows"))
        return rows.mapNotNull { element ->
            val item = element.asJsonObjectOrNull() ?: return@mapNotNull null
            val courseName = item.firstString("KCM", "KCMC", "courseName", "kcmc", "kcm").orEmpty()
            if (courseName.isBlank()) return@mapNotNull null
            val weekdayText = item.firstString("XQ", "SKXQ", "weekday", "xq", "XQJ", "SKXQJ", "weekdayName")
            val weekday = item.firstInt("XQ", "SKXQ", "weekday", "xq")
                ?: SchoolScheduleParsers.parseWeekday(weekdayText)
            val sectionRange = SchoolScheduleParsers.parseSectionRange(
                item.firstString("JC", "SKJC", "JCDM", "sections", "jc")
            )
            val weeks = SchoolScheduleParsers.parseWeeks(
                item.firstString("ZC", "SKZC", "ZCMC", "weeks", "zc", "zcmc")
            )
            if (weekday == null || sectionRange == null || weeks.isEmpty()) return@mapNotNull null

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

    private fun findRows(root: JsonObject, keys: List<String>): List<JsonElement> {
        val queue = ArrayDeque<JsonElement>()
        queue.add(root)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            when {
                current.isJsonArray -> {
                    val array = current.asJsonArray
                    if (array.all { it.isJsonObject }) return array.toList()
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
        return firstString(*keys)?.filter(Char::isDigit)?.toIntOrNull()
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
        private const val SCHOOL_INDEX_URL = "$SCHOOL_BASE_URL/jwapp/sys/wdkbby/*default/index.do"
        private const val CURRENT_TERM_URL = "$SCHOOL_BASE_URL/jwapp/sys/wdkbby/modules/jshkcb/dqxnxq.do"
        private const val TERMS_URL = "$SCHOOL_BASE_URL/jwapp/sys/wdkbby/modules/jshkcb/xnxqcx.do"
        private const val SCHEDULE_URL = "$SCHOOL_BASE_URL/jwapp/sys/wdkbby/modules/xskcb/cxxszhxqkb.do"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
    }
}
