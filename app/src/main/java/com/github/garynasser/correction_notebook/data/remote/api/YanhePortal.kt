package com.github.garynasser.correction_notebook.data.remote.api

import com.github.garynasser.correction_notebook.data.model.yanhe.Course
import com.github.garynasser.correction_notebook.data.model.yanhe.CourseSection
import com.github.garynasser.correction_notebook.data.model.yanhe.Video
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.security.MessageDigest
import java.time.Instant
import org.jsoup.parser.Parser
import okhttp3.HttpUrl.Companion.toHttpUrl

internal object YanhePortal {
    const val ORIGIN = "https://aita.yanhekt.cn"
    const val USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 Chrome/130.0 Safari/537.36"

    fun course(row: JsonObject): Course? {
        val id = row.text("Id").toIntOrNull() ?: row.text("course_id").toIntOrNull()
            ?: row.text("id").toIntOrNull() ?: return null
        val name = row.text("Title").ifBlank { row.text("title") }
        if (id <= 0 || name.isBlank()) return null
        val rawTerm = row.text("TermName").ifBlank { row.text("term_name") }
        val term = Regex("^(\\d{4})-(\\d{4})-([12])$").matchEntire(rawTerm)
        val semester = term?.let { "${it.groupValues[1]}-${it.groupValues[2]} ${if (it.groupValues[3] == "1") "秋季" else "春季"}" } ?: rawTerm
        return Course(id = id, nameZh = Parser.unescapeEntities(name, false),
            semester = semester,
            professors = listOf(row.text("Teacher").ifBlank { row.text("realname") }.ifBlank { row.text("teacher") }).filter(String::isNotBlank),
            imageUrl = row.text("Cover").ifBlank { row.text("cover") })
    }

    fun sections(data: JsonObject, courseId: Int): List<CourseSection> {
        val rows = mutableListOf<JsonObject>()
        fun visit(node: JsonElement?) {
            when {
                node == null || node.isJsonNull -> Unit
                node.isJsonArray -> node.asJsonArray.forEach(::visit)
                node.isJsonObject -> {
                    val obj = node.asJsonObject
                    if (obj.has("sub_title")) {
                        if (obj.text("playback_status") == "1") rows += obj
                    } else obj.entrySet().forEach { visit(it.value) }
                }
            }
        }
        require(data.has("sub_list")) { "延河课堂没有返回课程节次" }
        visit(data.get("sub_list"))
        return rows.sortedBy { it.text("class_begin").toLongOrNull() ?: Long.MAX_VALUE }
            .mapNotNull { row -> row.text("id").toIntOrNull()?.takeIf { it > 0 }?.let { id ->
                val started = row.text("class_begin").toLongOrNull()?.let { Instant.ofEpochSecond(it).toString() }.orEmpty()
                CourseSection(id = id, courseId = courseId, title = Parser.unescapeEntities(row.text("sub_title"), false),
                    startedAt = started, isRecord = 1, location = row.text("classroom_name"))
            } }.distinctBy { it.id }
    }

    fun section(data: JsonObject, courseId: Int, subId: Int): CourseSection {
        val list = data.get("video_list") ?: throw IllegalStateException("延河课堂没有返回播放资源")
        val rows = when {
            list.isJsonObject -> list.asJsonObject.entrySet().map { it.value }
            list.isJsonArray -> list.asJsonArray.toList()
            list.isJsonNull -> emptyList()
            else -> throw IllegalStateException("延河课堂播放资源格式异常")
        }
        val ready = rows.filter { it.isJsonObject }.map { it.asJsonObject }.filter { it.text("status") == "2" }
        val teacher = ready.firstOrNull { it.text("type") == "3" }?.text("preview_url").orEmpty()
        val screen = ready.firstOrNull { it.text("type") == "2" }?.text("preview_url").orEmpty()
        return CourseSection(id = subId, courseId = courseId, title = data.text("sub_title"),
            videos = if (teacher.isBlank() && screen.isBlank()) emptyList() else listOf(
                Video(mainUrl = resolveMedia(teacher), vgaUrl = resolveMedia(screen), duration = data.text("duration"))))
    }

    private fun resolveMedia(value: String): String = if (value.isBlank()) "" else
        ORIGIN.toHttpUrl().resolve(value)?.toString() ?: throw IllegalStateException("视频地址无效")

    fun sign(url: String, profile: JsonObject, nowSeconds: Long): String {
        val parsed = url.toHttpUrl()
        require(parsed.scheme == "https" && parsed.host == "aita.yanhekt.cn" && parsed.port == 443 &&
            parsed.username.isEmpty() && parsed.password.isEmpty()) { "已阻止非延河课堂视频地址" }
        if (!parsed.encodedPath.startsWith("/play/")) return url
        val id = profile.text("id")
        val tenant = profile.text("tenant_id")
        val phone = profile.text("phone")
        require(id.isNotBlank() && tenant.isNotBlank() && phone.isNotBlank()) { "延河课堂没有返回完整播放授权信息" }
        val input = parsed.encodedPath + id + tenant + phone.reversed() + nowSeconds
        val digest = MessageDigest.getInstance("MD5").digest(input.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
        return parsed.newBuilder().removeAllQueryParameters("t").addQueryParameter("t", "$id-$nowSeconds-$digest").build().toString()
    }

    fun JsonObject.text(name: String): String = get(name)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
}
