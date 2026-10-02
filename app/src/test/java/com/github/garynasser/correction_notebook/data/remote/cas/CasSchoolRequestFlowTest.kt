package com.github.garynasser.correction_notebook.data.remote.cas

import com.github.garynasser.correction_notebook.data.model.school.SchoolScheduleException
import com.github.garynasser.correction_notebook.data.remote.cas.CasNetworkCancellationTest.Companion.SCHOOL_INDEX
import com.github.garynasser.correction_notebook.data.remote.cas.CasNetworkCancellationTest.Companion.SCHOOL_SCHEDULE
import com.github.garynasser.correction_notebook.data.remote.cas.CasNetworkCancellationTest.Companion.successfulResponse
import com.github.garynasser.correction_notebook.data.remote.school.SchoolScheduleRemoteDataSource
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.ForwardingSource
import okio.buffer
import org.junit.Assert.*
import org.junit.Test

class CasSchoolRequestFlowTest {
    @Test
    fun serviceTicketPreservesEncodedCredentialsAndService() = withClient { requests ->
        val service = "https://school.example/path?term=1&value=with space"
        val ticket = BitCasClient(requests.client).getServiceTicketFor("student+id", "test&password+", service)
        assertEquals("ST-test", ticket)
        val login = requests.seen[0].body as FormBody
        assertEquals("student+id", login.value(0))
        assertEquals("test&password+", login.value(1))
        assertEquals(service, (requests.seen[1].body as FormBody).value(0))
        assertTrue(requests.seen.all { it.method == "POST" })
    }

    @Test
    fun codeExchangeClosesCallbackBeforeRequestingToken() = withClient { requests ->
        var callbackClosed = false
        requests.response = { request ->
            val response = successfulResponse(request)
            when (request.url.encodedPath) {
                "/v1/cas/callback" -> {
                    assertEquals("ST-test", request.url.queryParameter("ticket"))
                    tracked(response) { callbackClosed = true }
                }
                "/v1/auth/token" -> {
                    assertTrue("Callback body must be closed before the token exchange", callbackClosed)
                    assertEquals("test+code &value", request.url.queryParameter("code"))
                    assertEquals("1", request.url.queryParameter("type"))
                    assertEquals("web_user", request.header("xdomain-client"))
                    assertFalse(request.header("xclient-signature").isNullOrBlank())
                    response
                }
                else -> response
            }
        }
        assertEquals("test-token", BitCasClient(requests.client).getYanheToken("student", "test-password"))
        assertEquals(4, requests.seen.size)
    }

    @Test
    fun directCallbackTokenDoesNotMakeAnExchangeRequest() = withClient { requests ->
        requests.response = { request ->
            val response = successfulResponse(request)
            if (request.url.encodedPath == "/v1/cas/callback") {
                response.newBuilder().request(request.newBuilder()
                    .url(request.url.newBuilder().addQueryParameter("token", "direct-token").build()).build()).build()
            } else response
        }
        assertEquals("direct-token", BitCasClient(requests.client).getYanheToken("student", "test-password"))
        assertEquals(3, requests.seen.size)
    }

    @Test
    fun schoolScheduleUsesAuthenticatedCallbackAndTermForm() = withClient { requests ->
        val source = SchoolScheduleRemoteDataSource(BitCasClient(requests.client), requests.client)
        val result = source.getCurrentTermSchedule("student", "test-password")
        assertEquals("2026-2027-1", result.term.id)
        assertEquals(LocalDate.of(2026, 9, 7), result.term.startDate)
        assertEquals("Linear algebra", result.courses.single().courseName)
        assertEquals("A101", result.courses.single().location)
        assertEquals((1..16).toList(), result.courses.single().weeks)
        val callback = requests.seen.single { it.url.encodedPath == SCHOOL_INDEX }
        assertEquals("ST-test", callback.url.queryParameter("ticket"))
        assertEquals("GET", callback.method)
        val schedule = requests.seen.single { it.url.encodedPath == SCHOOL_SCHEDULE }
        assertEquals("POST", schedule.method)
        assertEquals("https://jxzxehallapp.bit.edu.cn", schedule.header("Origin"))
        val form = schedule.body as FormBody
        assertEquals("2026-2027-1", form.value(0))
        assertEquals("2026-2027-1", form.value(1))
    }

    @Test
    fun credentialAndServerFailuresCloseBodiesAndAllowRetry() = withClient { requests ->
        val cas = BitCasClient(requests.client)
        listOf(401, 503).forEach { status ->
            var closed = false
            requests.response = { request -> tracked(successfulResponse(request).newBuilder().code(status).build()) { closed = true } }
            val failure = runCatching { cas.getServiceTicketFor("student", "test-password", "https://school.example/") }.exceptionOrNull()
            assertTrue(failure is CasAuthException)
            assertEquals(status == 401, failure is CasCredentialException)
            assertTrue("Failed responses must not keep connections open", closed)
        }
        requests.response = ::successfulResponse
        assertEquals("ST-test", cas.getServiceTicketFor("student", "test-password", "https://school.example/"))
    }

    @Test
    fun invalidSchoolJsonClosesTheBodyAndReportsTheDomainError() = withClient { requests ->
        var closed = false
        requests.response = { request ->
            val response = successfulResponse(request)
            if (request.url.encodedPath == SCHOOL_SCHEDULE) {
                tracked(response.newBuilder().body("<html>Login expired</html>".toResponseBody()).build()) { closed = true }
            } else response
        }
        val failure = runCatching {
            SchoolScheduleRemoteDataSource(BitCasClient(requests.client), requests.client)
                .getCurrentTermSchedule("student", "test-password")
        }.exceptionOrNull()
        assertTrue(failure is SchoolScheduleException)
        assertTrue(failure!!.message.orEmpty().contains("已保留本地日程"))
        assertTrue(closed)
    }

    private class Requests {
        val seen = mutableListOf<Request>()
        var response: (Request) -> Response = ::successfulResponse
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            seen.add(chain.request())
            response(chain.request())
        }.build()
    }

    private fun withClient(test: suspend (Requests) -> Unit) = runBlocking {
        val requests = Requests()
        try {
            test(requests)
        } finally {
            requests.client.dispatcher.executorService.shutdownNow()
            requests.client.connectionPool.evictAll()
        }
    }

    private fun tracked(response: Response, onClose: () -> Unit): Response {
        val body = response.body!!
        val source = object : ForwardingSource(body.source()) {
            override fun close() { onClose(); super.close() }
        }.buffer()
        return response.newBuilder().body(object : ResponseBody() {
            override fun contentType() = body.contentType()
            override fun contentLength() = body.contentLength()
            override fun source() = source
        }).build()
    }
}
