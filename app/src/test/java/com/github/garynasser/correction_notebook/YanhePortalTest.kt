package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.model.common.ApiResponse
import com.github.garynasser.correction_notebook.data.remote.api.YanhePortal
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import java.security.MessageDigest
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class YanhePortalTest {
    private fun json(value: String) = JsonParser.parseString(value).asJsonObject
    @Test fun personalCourseFieldsAndEncodedTitleAreMapped() {
        val row = YanhePortal.course(json("""{"Id":"123","Title":"Science &amp; Engineering","Teacher":"Teacher","TermName":"2026-2027-1"}"""))!!
        assertEquals(123, row.id)
        assertEquals("Science & Engineering", row.nameZh)
        assertEquals("2026-2027 秋季", row.semester)
        assertEquals(listOf("Teacher"), row.professors)
    }
    @Test fun paramsEnvelopeIsReadForProfileAndPersonalCourses() {
        val type = object : TypeToken<ApiResponse<JsonElement>>() {}.type
        val body = Gson().fromJson<ApiResponse<JsonElement>>("""{"code":200,"params":{"result":{"total":1,"data":[]}}}""", type)
        assertEquals(1, body.data!!.asJsonObject.getAsJsonObject("result").get("total").asInt)
    }
    @Test fun nestedReplaysAreSortedAndUnrecordedRowsAreExcluded() {
        val data = json("""{"sub_list":{"2026":{"10":{"week":[{"id":"2","sub_title":"Later","playback_status":"1","class_begin":"200"},{"id":"1","sub_title":"Earlier","playback_status":"1","class_begin":"100"},{"id":"3","sub_title":"Not ready","playback_status":"0"}]}}}}""")
        val sections = YanhePortal.sections(data, 12)
        assertEquals(listOf(1, 2), sections.map { it.id })
        assertTrue(sections.all { it.courseId == 12 })
    }
    @Test fun readyTeacherAndScreenStreamsAreSelectedFromObjectMap() {
        val data = json("""{"video_list":{"a":{"type":"3","status":"2","preview_url":"/play/teacher.m3u8"},"b":{"type":2,"status":2,"preview_url":"https://aita.yanhekt.cn/play/screen.m3u8"},"c":{"type":3,"status":1,"preview_url":"bad"}},"duration":"100"}""")
        val section = YanhePortal.section(data, 12, 34)
        assertEquals("https://aita.yanhekt.cn/play/teacher.m3u8", section.videos.single().mainUrl)
        assertEquals("https://aita.yanhekt.cn/play/screen.m3u8", section.videos.single().vgaUrl)
    }
    @Test fun mediaSignatureIsPathBoundAndReplacesOldTokenForEachSegment() {
        val profile = json("""{"id":"123","tenant_id":21,"phone":"13900000000"}""")
        val signed = YanhePortal.sign("https://aita.yanhekt.cn/play/a/segment.ts?t=old&quality=high", profile, 1700000000).toHttpUrl()
        val expected = MessageDigest.getInstance("MD5").digest("/play/a/segment.ts12321000000009311700000000".toByteArray()).joinToString("") { "%02x".format(it) }
        assertEquals(listOf("123-1700000000-$expected"), signed.queryParameterValues("t"))
        assertEquals("high", signed.queryParameter("quality"))
        assertNotEquals(signed.toString(), YanhePortal.sign(signed.toString(), profile, 1700000001))
    }
    @Test fun mediaCredentialsCannotBeSentToExternalDomains() {
        val profile = json("""{"id":"123","tenant_id":"21","phone":"13900000000"}""")
        assertTrue(runCatching { YanhePortal.sign("https://attacker.invalid/play/a", profile, 1) }.isFailure)
        assertTrue(runCatching { YanhePortal.sign("http://aita.yanhekt.cn/play/a", profile, 1) }.isFailure)
    }
    @Test fun missingSigningProfileIsNotSilentlyAccepted() {
        assertTrue(runCatching { YanhePortal.sign("https://aita.yanhekt.cn/play/a", json("{}"), 1) }.isFailure)
    }
}
