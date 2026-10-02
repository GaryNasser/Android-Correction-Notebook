package com.github.garynasser.correction_notebook.data.remote.cas

import com.github.garynasser.correction_notebook.data.remote.school.SchoolScheduleRemoteDataSource
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertTrue
import org.junit.Test

class CasNetworkCancellationTest {
    @Test
    fun cancellingAuthenticationInterruptsARequestWaitingForHeaders() = checkCancellation(afterHeaders = false)

    @Test
    fun cancellingAuthenticationInterruptsAStalledResponseBody() = checkCancellation(afterHeaders = true)

    @Test
    fun cancellingServiceTicketInterruptsHeadersAndBody() {
        listOf(false, true).forEach { checkCancellation(it, "/cas/v1/tickets/TGT-test") }
    }

    @Test
    fun cancellingYanheCallbackInterruptsTheRequest() = checkCancellation(false, "/v1/cas/callback", yanhe = true)

    @Test
    fun cancellingTokenExchangeInterruptsHeadersAndBody() {
        listOf(false, true).forEach { checkCancellation(it, "/v1/auth/token", yanhe = true) }
    }

    @Test
    fun cancellingSchoolCallbackInterruptsTheRequest() = checkCancellation(false, SCHOOL_INDEX, school = true)

    @Test
    fun cancellingSchoolTermAndScheduleInterruptsHeadersAndBody() {
        listOf(SCHOOL_TERM, SCHOOL_SCHEDULE).forEach { path ->
            listOf(false, true).forEach { checkCancellation(it, path, school = true) }
        }
    }

    @Test
    fun authenticationTimeoutCancelsTheUnderlyingCall() = checkCancellation(false, timed = true)

    private fun checkCancellation(
        afterHeaders: Boolean,
        stalledPath: String = "/cas/v1/tickets",
        yanhe: Boolean = false,
        school: Boolean = false,
        timed: Boolean = false
    ) = runBlocking { supervisorScope {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val callFinished = CountDownLatch(1)
        val failure = AtomicReference<Throwable>()
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        var connection: Socket? = null
        val worker = thread(isDaemon = true, name = "cas-stalled-test-server") {
            runCatching {
                server.accept().use { socket ->
                    connection = socket
                    val reader = socket.getInputStream().bufferedReader()
                    while (!reader.readLine().isNullOrEmpty()) { }
                    if (afterHeaders) {
                        socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 100\r\nConnection: close\r\n\r\n ".toByteArray())
                        socket.getOutputStream().flush()
                    }
                    entered.countDown()
                    release.await(4, TimeUnit.SECONDS)
                    if (!afterHeaders) socket.getOutputStream().write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                }
            }
        }
        val client = OkHttpClient.Builder()
            .eventListener(object : EventListener() {
                override fun canceled(call: Call) { cancelled.countDown() }
                override fun callFailed(call: Call, ioe: IOException) {
                    if (call.request().url.encodedPath == stalledPath) callFinished.countDown()
                }
                override fun callEnd(call: Call) {
                    if (call.request().url.encodedPath == stalledPath) callFinished.countDown()
                }
            })
            .addInterceptor { chain ->
                if (chain.request().url.encodedPath != stalledPath) {
                    successfulResponse(chain.request())
                } else {
                    val url = chain.request().url.newBuilder().scheme("http").host("127.0.0.1")
                        .port(server.localPort).build()
                    chain.proceed(chain.request().newBuilder().url(url).build())
                }
            }.build()
        val request = async(Dispatchers.Default) {
            try {
                suspend fun fetch() {
                    val cas = BitCasClient(client)
                    when {
                        school -> SchoolScheduleRemoteDataSource(cas, client).getCurrentTermSchedule("test-student", "test-password")
                        yanhe -> cas.getYanheToken("test-student", "test-password")
                        else -> cas.getServiceTicketFor("test-student", "test-password", "https://school.example/")
                    }
                }
                if (timed) withTimeout(1_000) { fetch() } else fetch()
            } catch (error: Throwable) {
                failure.set(error)
                throw error
            }
        }
        try {
            assertTrue("The real HTTP request must be in flight", withContext(Dispatchers.IO) { entered.await(3, TimeUnit.SECONDS) })
            if (!timed) request.cancel()
            val finishedPromptly = withTimeoutOrNull(2_000) { request.join(); true } ?: false
            assertTrue("Cancelled request at $stalledPath must return without waiting for the server", finishedPromptly)
            assertTrue("Coroutine cancellation must cancel the underlying HTTP call", cancelled.await(1, TimeUnit.SECONDS))
            assertTrue("The cancelled HTTP call must release its connection", callFinished.await(1, TimeUnit.SECONDS))
            assertTrue("Cancellation must not become a credential or network error", failure.get() is CancellationException)
        } finally {
            release.countDown()
            request.cancel()
            connection?.close()
            server.close()
            worker.join(1_000)
            client.dispatcher.executorService.shutdownNow()
            client.connectionPool.evictAll()
        }
    } }

    companion object {
        const val SCHOOL_INDEX = "/jwapp/sys/wdkbby/*default/index.do"
        const val SCHOOL_TERM = "/jwapp/sys/wdkbby/modules/jshkcb/dqxnxq.do"
        const val SCHOOL_SCHEDULE = "/jwapp/sys/wdkbby/modules/xskcb/cxxszhxqkb.do"

        fun successfulResponse(request: Request): Response {
            val body = when (request.url.encodedPath) {
                "/cas/v1/tickets" -> "<form action='/cas/v1/tickets/TGT-test'></form>"
                "/cas/v1/tickets/TGT-test" -> "ST-test"
                "/v1/cas/callback", SCHOOL_INDEX -> ""
                "/v1/auth/token" -> """{"code":0,"data":{"token":"test-token"}}"""
                SCHOOL_TERM -> """{"dqxnxq":{"XNXQDM":"2026-2027-1","XNXQMC":"Autumn","KSRQ":"2026-09-07"}}"""
                SCHOOL_SCHEDULE -> """{"cxxszhxqkb":[{"KCMC":"Linear algebra","SKXQ":"1","SKJC":"3-4","SKZC":"1-16","JASMC":"A101"}]}"""
                else -> throw AssertionError("Unexpected request: ${request.url}")
            }
            val finalRequest = if (request.url.encodedPath == "/v1/cas/callback") {
                request.newBuilder().url(request.url.newBuilder().addQueryParameter("code", "test+code &value").build()).build()
            } else request
            return Response.Builder().request(finalRequest).protocol(Protocol.HTTP_1_1).code(200)
                .message("OK").body(body.toResponseBody()).build()
        }
    }
}
