package com.github.garynasser.correction_notebook.data.remote.network

import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Timeout
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamingResponseTest {
    @Test
    fun lateResponseAfterCancellationIsClosedWithoutConsumption() {
        val fixture = Fixture()
        var consumes = 0
        val request = fixture.scope.launch { fixture.call.awaitStreamingResponse { consumes++ } }
        fixture.dispatcher.drain()
        request.cancel()
        fixture.dispatcher.drain()
        fixture.respond()
        fixture.dispatcher.drain()
        assertTrue(request.isCompleted)
        assertTrue(fixture.call.isCanceled())
        assertTrue(fixture.closed)
        assertEquals(0, consumes)
        fixture.close()
    }

    @Test
    fun responseIsClosedWhenTheReaderIsCancelledBeforeItStarts() {
        val fixture = Fixture()
        var consumes = 0
        val request = fixture.scope.launch { fixture.call.awaitStreamingResponse { consumes++ } }
        fixture.dispatcher.drain()
        fixture.respond()
        assertFalse(fixture.closed)
        request.cancel()
        fixture.dispatcher.drain()
        assertTrue(request.isCompleted)
        assertTrue(fixture.call.isCanceled())
        assertTrue(fixture.closed)
        assertEquals(0, consumes)
        fixture.close()
    }

    @Test
    fun suspendingConsumerKeepsTheResponseOpenUntilImportCompletes() {
        val fixture = Fixture()
        val commit = CompletableDeferred<Unit>()
        var result = 0
        val request = fixture.scope.launch {
            result = fixture.call.awaitStreamingResponse {
                assertEquals("notes", it.body!!.source().readUtf8())
                commit.await()
                123
            }
        }
        fixture.dispatcher.drain()
        fixture.respond()
        fixture.dispatcher.drain()
        assertFalse(fixture.closed)
        assertFalse(request.isCompleted)
        commit.complete(Unit)
        fixture.dispatcher.drain()
        assertTrue(request.isCompleted)
        assertTrue(fixture.closed)
        assertFalse(fixture.call.isCanceled())
        assertEquals(123, result)
        fixture.close()
    }

    private class Fixture {
        val dispatcher = QueuedDispatcher()
        val scope = CoroutineScope(Job() + dispatcher)
        val call = ControlledCall()
        var closed = false
        private val source = object : ForwardingSource(Buffer().writeUtf8("notes")) {
            override fun close() { closed = true; super.close() }
        }.buffer()
        fun respond() {
            val body = object : ResponseBody() {
                override fun contentType(): MediaType? = null
                override fun contentLength(): Long = 5L
                override fun source(): BufferedSource = source
            }
            call.callback.onResponse(call, Response.Builder().request(call.request()).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(body).build())
        }
        fun close() { scope.cancel(); dispatcher.drain() }
    }

    private class QueuedDispatcher : CoroutineDispatcher() {
        private val queue = ConcurrentLinkedQueue<Runnable>()
        override fun dispatch(context: CoroutineContext, block: Runnable) { queue.add(block) }
        fun drain() { while (true) (queue.poll() ?: return).run() }
    }

    private class ControlledCall : Call {
        lateinit var callback: Callback
        private var cancelled = false
        override fun request(): Request = Request.Builder().url("https://unused.invalid/notes").build()
        override fun enqueue(responseCallback: Callback) { callback = responseCallback }
        override fun cancel() { cancelled = true }
        override fun isCanceled(): Boolean = cancelled
        override fun isExecuted(): Boolean = ::callback.isInitialized
        override fun timeout(): Timeout = Timeout()
        override fun clone(): Call = ControlledCall()
        override fun execute(): Response = error("Only async requests are supported")
    }
}
