package com.github.garynasser.correction_notebook.data.remote.school

import android.net.Uri
import com.github.garynasser.correction_notebook.data.model.school.SchoolCourseRaw
import com.github.garynasser.correction_notebook.data.model.school.SchoolScheduleException
import com.github.garynasser.correction_notebook.data.model.school.SchoolTerm
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
        val json = getJson(CURRENT_TERM_URL)
        parseTerms(json, currentOnly = true).firstOrNull()
            ?: throw SchoolScheduleException("学校系统没有返回当前学期")
    }

    suspend fun getTerms(studentId: String, password: String): List<SchoolTerm> = withContext(Dispatchers.IO) {
        establishSession(studentId, password)
        parseTerms(getJson(TERMS_URL), currentOnly = false)
    }

    suspend fun getSchedule(studentId: String, password: String, termId: String): List<SchoolCourseRaw> = withContext(Dispatchers.IO) {
        establishSession(studentId, password)
        val body = FormBody.Builder()
            .add("XNXQDM", termId)
            .add("xnxqdm", termId)
            .build()
        val request = Request.Builder()
            .url(SCHEDULE_URL)
            .headers(defaultHeaders())
            .post(body)
            .build()
        parseCourses(executeJsonRequest(request))
    }

    private suspend fun establishSession(studentId: String, password: String) {
        val st = bitCasClient.getServiceTicketFor(studentId, password, SCHOOL_INDEX_URL)
        val callback = Uri.parse(SCHOOL_INDEX_URL)
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

    private fun parseCourses(root: JsonObject): List<SchoolCourseRaw> {
        val rows = findRows(root, listOf("cxxszhxqkb", "rows"))
        return rows.mapNotNull { element ->
            val item = element.asJsonObjectOrNull() ?: return@mapNotNull null
            val courseName = item.firstString("KCM", "KCMC", "courseName", "kcmc", "kcm").orEmpty()
            if (courseName.isBlank()) return@mapNotNull null
            val weekdayText = item.firstString("XQ", "SKXQ", "weekday", "xq", "XQJ", "SKXQJ", "weekdayName")
            val weekday = item.firstInt("XQ", "SKXQ", "weekday", "xq") ?: parseWeekday(weekdayText)
            val sectionRange = parseSectionRange(item.firstString("JC", "SKJC", "JCDM", "sections", "jc"))
            val weeks = parseWeeks(item.firstString("ZC", "SKZC", "ZCMC", "weeks", "zc", "zcmc"))
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

    private fun parseWeekday(raw: String?): Int? {
        val text = raw?.trim().orEmpty()
        return when {
            text.contains("一") -> 1
            text.contains("二") -> 2
            text.contains("三") -> 3
            text.contains("四") -> 4
            text.contains("五") -> 5
            text.contains("六") -> 6
            text.contains("日") || text.contains("天") -> 7
            else -> text.filter(Char::isDigit).toIntOrNull()
        }
    }

    private fun parseSectionRange(raw: String?): Pair<Int, Int>? {
        val numbers = raw.orEmpty().split(Regex("[^0-9]+")).mapNotNull { it.toIntOrNull() }
        return when {
            numbers.size >= 2 -> numbers.first() to numbers.last()
            numbers.size == 1 -> numbers.first() to numbers.first()
            else -> null
        }
    }

    private fun parseWeeks(raw: String?): List<Int> {
        val text = raw.orEmpty()
        if (text.isBlank()) return emptyList()
        val results = mutableSetOf<Int>()
        val rangeRegex = Regex("(\\d+)\\s*-\\s*(\\d+)")
        rangeRegex.findAll(text).forEach { match ->
            val start = match.groupValues[1].toInt()
            val end = match.groupValues[2].toInt()
            val oddOnly = text.contains("单")
            val evenOnly = text.contains("双")
            (start..end).filter { (!oddOnly || it % 2 == 1) && (!evenOnly || it % 2 == 0) }.forEach(results::add)
        }
        val withoutRanges = rangeRegex.replace(text, " ")
        Regex("\\d+").findAll(withoutRanges).map { it.value.toInt() }.forEach(results::add)
        return results.sorted()
    }

    private fun JsonElement.asJsonObjectOrNull(): JsonObject? = takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.firstString(vararg keys: String): String? {
        keys.forEach { key ->
            get(key)?.takeUnless { it.isJsonNull }?.let { element ->
                return element.asString.trim().takeIf { it.isNotBlank() }
            }
        }
        return null
    }

    private fun JsonObject.firstInt(vararg keys: String): Int? {
        return firstString(*keys)?.filter(Char::isDigit)?.toIntOrNull()
    }

    private fun JsonObject.firstDate(vararg keys: String): LocalDate? {
        val raw = firstString(*keys) ?: return null
        val patterns = listOf("yyyy-MM-dd", "yyyy/MM/dd", "yyyyMMdd")
        return patterns.firstNotNullOfOrNull { pattern ->
            runCatching { LocalDate.parse(raw, DateTimeFormatter.ofPattern(pattern)) }.getOrNull()
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
