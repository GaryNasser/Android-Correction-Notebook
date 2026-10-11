package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.model.auth.UserCredential
import com.github.garynasser.correction_notebook.data.remote.cas.BitCasClient
import com.github.garynasser.correction_notebook.data.remote.eclass.EclassRemoteDataSource
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList

class EclassRemoteDataSourceTest {
    @Test fun homeworkDetailsAreReadAndNonHomeworkActivitiesAreExcluded() = runBlocking {
        val requests = CopyOnWriteArrayList<String>()
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            requests.add(request.url.encodedPath)
            val body = when (request.url.encodedPath) {
                "/api/profile" -> """{"active":true}"""
                "/api/my-courses" -> """{"courses":[{"id":12,"name":"Computer Vision"}]}"""
                "/api/courses/12/activities" -> """{"activities":[{"id":34,"type":"homework","title":"Assignment"},{"id":35,"type":"material"}]}"""
                "/api/activities/34" -> """{"id":34,"title":"Assignment","user_submit_count":1,"end_time":"2026-10-13T15:59:00Z","is_closed":false}"""
                "/api/courses/12/exams" -> """{"exams":[{"id":56,"title":"Exam","end_time":null}]}"""
                else -> error("Unexpected request")
            }
            assertEquals("zy-eclass.bit.edu.cn", request.url.host)
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK").body(body.toResponseBody()).build()
        }.build()
        try {
            val rows = EclassRemoteDataSource(BitCasClient(client), client).fetch(UserCredential("qa", "qa"))
            assertEquals(2, rows.size)
            assertEquals(true, rows.single { it.kind == "homework" }.submitted)
            assertNull(rows.single { it.kind == "exam" }.submitted)
            assertTrue("/api/activities/34" in requests)
            assertFalse("/api/activities/35" in requests)
        } finally { client.dispatcher.executorService.shutdownNow(); client.connectionPool.evictAll() }
    }

    @Test fun failedCourseReadDoesNotReturnAnIncompleteDeadlineList() = runBlocking {
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val (status, body) = when (request.url.encodedPath) {
                "/api/profile" -> 200 to """{"active":true}"""
                "/api/my-courses" -> 200 to """{"courses":[{"id":12,"name":"Course"}]}"""
                else -> 503 to "{}"
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(status).message("QA").body(body.toResponseBody()).build()
        }.build()
        try {
            assertTrue(runCatching { EclassRemoteDataSource(BitCasClient(client), client).fetch(UserCredential("qa", "qa")) }.isFailure)
        } finally { client.dispatcher.executorService.shutdownNow(); client.connectionPool.evictAll() }
    }
}
