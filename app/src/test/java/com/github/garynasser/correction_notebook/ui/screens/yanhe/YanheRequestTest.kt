package com.github.garynasser.correction_notebook.ui.screens.yanhe

import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class YanheRequestTest {
    @Test
    fun returnsSuccessfulResponsesIncludingEmptyLists() = runBlocking {
        assertEquals(emptyList<String>(), awaitYanheResource(1_000, "超时") { emptyList<String>() })
    }

    @Test
    fun requestTimeoutBecomesRecoverableErrorAndCancelsTheRequest() = runBlocking {
        var requestStopped = false
        try {
            awaitYanheResource(10, "课程同步超时，请重试") {
                try {
                    awaitCancellation()
                } finally {
                    requestStopped = true
                }
            }
            fail("Expected a recoverable request error")
        } catch (error: IOException) {
            assertEquals("课程同步超时，请重试", error.message)
        }
        assertTrue(requestStopped)
        assertEquals("重试成功", awaitYanheResource(1_000, "超时") { "重试成功" })
    }

    @Test
    fun parentTimeoutIsNotConvertedToARequestError() = runBlocking {
        try {
            withTimeout(10) {
                awaitYanheResource(1_000, "请求超时") { awaitCancellation() }
            }
            fail("Expected parent cancellation")
        } catch (error: TimeoutCancellationException) {
            assertFalse(error.message == "请求超时")
        }
    }

    @Test
    fun cancellationAndNetworkFailuresKeepTheirOriginalType() = runBlocking {
        val cancelled = CancellationException("页面已关闭")
        try {
            awaitYanheResource(1_000, "超时") { throw cancelled }
            fail("Expected cancellation")
        } catch (error: CancellationException) {
            assertEquals(cancelled.javaClass, error.javaClass)
            assertEquals(cancelled.message, error.message)
        }
        val failure = IOException("网络不可用")
        try {
            awaitYanheResource(1_000, "超时") { throw failure }
            fail("Expected network failure")
        } catch (error: IOException) {
            assertEquals(failure.javaClass, error.javaClass)
            assertEquals(failure.message, error.message)
            assertFalse(error.message == "超时")
        }
    }
}
