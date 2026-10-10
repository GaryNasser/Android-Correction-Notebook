package com.github.garynasser.correction_notebook.data.remote.cas

import com.github.garynasser.correction_notebook.data.model.school.SchoolScheduleException
import com.github.garynasser.correction_notebook.data.model.school.SchoolTerm
import com.github.garynasser.correction_notebook.data.remote.cas.CasNetworkCancellationTest.Companion.SCHOOL_INDEX
import com.github.garynasser.correction_notebook.data.remote.cas.CasNetworkCancellationTest.Companion.SCHOOL_AUTH_CALLBACK
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
import okio.Buffer
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class CasSchoolRequestFlowTest {
    @Test fun schoolCalendarCompletesNativeTermBeforeMappingBinaryWeekRows() = withClient { requests ->
        requests.response = { request ->
            when (request.url.encodedPath) {
                CasNetworkCancellationTest.SCHOOL_TERM -> successfulResponse(request).newBuilder()
                    .body("""{"datas":{"dqxnxq":{"rows":[{"DM":"2026-2027-1","MC":"Autumn"}]}}}""".toResponseBody()).build()
                WEEK_DATES -> {
                    val form = request.body as FormBody
                    assertEquals("requestParamStr", form.name(0))
                    val payload = JsonParser.parseString(form.value(0)).asJsonObject
                    assertEquals("2026-2027-1", payload.get("XNXQDM").asString)
                    assertEquals("1", payload.get("ZC").asString)
                    Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK")
                        .body("""{"data":[{"XQ":2,"RQ":"2026-09-08"},{"XQ":1,"RQ":"2026-09-07"}]}""".toResponseBody()).build()
                }
                SCHOOL_SCHEDULE -> successfulResponse(request).newBuilder()
                    .body("""{"datas":{"cxxszhxqkb":{"rows":[{"KCM":"Native course","SKXQ":2,"KSJC":3,"JSJC":4,"SKZC":"1010000000000000","JASMC":"A101"}]}}}""".toResponseBody()).build()
                else -> successfulResponse(request)
            }
        }
        val source = SchoolScheduleRemoteDataSource(BitCasClient(requests.client), requests.client)
        val result = source.getCurrentTermSchedule("student", "test-password")
        assertEquals(LocalDate.of(2026, 9, 7), result.term.startDate)
        assertEquals(listOf(1, 3), result.courses.single().weeks)
        assertTrue(requests.seen.indexOfFirst { it.url.encodedPath == WEEK_DATES } < requests.seen.indexOfFirst { it.url.encodedPath == SCHOOL_SCHEDULE })
    }

    @Test fun selectedHistoricalTermFetchesItsOwnCalendarInsteadOfCurrentYear() = withClient { requests ->
        requests.response = { request ->
            if (request.url.encodedPath == WEEK_DATES) {
                val form = request.body as FormBody
                assertEquals("2025-2026-1", JsonParser.parseString(form.value(0)).asJsonObject.get("XNXQDM").asString)
                Response.Builder().request(request).protocol(okhttp3.Protocol.HTTP_1_1).code(200).message("OK")
                    .body("""{"data":[{"XQ":1,"RQ":"2025-08-25"}]}""".toResponseBody()).build()
            } else successfulResponse(request)
        }
        val source = SchoolScheduleRemoteDataSource(BitCasClient(requests.client), requests.client)
        val result = source.getTermSchedule("student", "test-password", SchoolTerm("2025-2026-1", "Old term"))
        assertEquals(LocalDate.of(2025, 8, 25), result.term.startDate)
    }

    @Test fun untrustedSchoolPreflightCannotSendSchoolCredentialsAnywhere() = withClient { requests ->
        listOf(
            "https://attacker.invalid/cas/login?service=https://jxzxehall.bit.edu.cn/auth-protocol-core/loginSuccess",
            "https://sso.bit.edu.cn/cas/login?service=https%3A%2F%2Fattacker.invalid%2FloginSuccess%3FsessionToken%3Dfake"
        ).forEach { location ->
            requests.seen.clear()
            requests.response = { request -> successfulResponse(request).newBuilder().header("Location", location).build() }
            val source = SchoolScheduleRemoteDataSource(BitCasClient(requests.client), requests.client)
            assertTrue(runCatching { source.getCurrentTermSchedule("student", "test-password") }.exceptionOrNull() is SchoolScheduleException)
            assertEquals(1, requests.seen.size)
            assertEquals("/auth-protocol-core/login", requests.seen.single().url.encodedPath)
        }
    }

    @Test
    fun serviceTicketPreservesEncodedCredentialsAndService() = withClient { requests ->
        val service = "https://school.example/path?term=1&value=with space"
        val ticket = BitCasClient(requests.client).getServiceTicketFor("student+id", "test&password+", service)
        assertEquals("ST-test", ticket)
        val submitted = requests.seen.single { it.method == "POST" && it.url.encodedPath == "/cas/login" }
        val login = submitted.body as FormBody
        val fields = (0 until login.size).associate { login.name(it) to login.value(it) }
        assertEquals("student+id", fields["username"])
        assertEquals(BitSsoCrypto.encrypt("test&password+", "MDEyMzQ1Njc4OWFiY2RlZg=="), fields["password"])
        assertFalse(fields["password"] == "test&password+")
        assertEquals(service, submitted.url.queryParameter("service"))
        assertEquals("execution-test", fields["execution"])
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
        assertEquals(5, requests.seen.size)
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
        assertEquals(4, requests.seen.size)
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
        val callback = requests.seen.single { it.url.encodedPath == SCHOOL_AUTH_CALLBACK }
        assertEquals("ST-test", callback.url.queryParameter("ticket"))
        assertEquals("qa-test", callback.url.queryParameter("sessionToken"))
        assertEquals("GET", callback.method)
        assertTrue(requests.seen.indexOf(callback) < requests.seen.indexOfFirst { it.url.encodedPath == SCHOOL_INDEX })
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
            requests.response = { request ->
                val response = successfulResponse(request)
                if (status == 503 || request.method == "POST") {
                    tracked(response.newBuilder().code(status).body("<p id='login-error-code'>1030027</p>".toResponseBody()).build()) { closed = true }
                } else response
            }
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

    companion object {
        private const val WEEK_DATES = "/jwapp/sys/wdkbby/wdkbByController/cxzkbrq.do"
    }
}
