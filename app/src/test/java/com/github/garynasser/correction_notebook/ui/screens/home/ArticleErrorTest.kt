package com.github.garynasser.correction_notebook.ui.screens.home

import com.google.gson.JsonSyntaxException
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ArticleErrorTest {
    @Test fun gatewayAndServiceFailuresHaveReadableMessages() {
        listOf(500, 502, 503, 504).forEach { code ->
            assertEquals("内容服务暂时不可用，请稍后重试", articleErrorMessage(httpError(code), FALLBACK))
        }
        assertEquals("请求过于频繁，请稍后重试", articleErrorMessage(httpError(429), FALLBACK))
        listOf(400, 401, 403, 404).forEach { code ->
            assertEquals(FALLBACK, articleErrorMessage(httpError(code), FALLBACK))
        }
    }

    @Test fun timeoutsConnectionsAndInvalidResponsesDoNotExposeTechnicalDetails() {
        assertEquals("内容加载超时，请稍后重试", articleErrorMessage(SocketTimeoutException("read timed out"), FALLBACK))
        listOf(UnknownHostException("internal.example"), IOException("connection reset")).forEach {
            assertEquals("无法连接内容服务，请检查网络后重试", articleErrorMessage(it, FALLBACK))
        }
        assertEquals("内容格式异常，请稍后重试", articleErrorMessage(JsonSyntaxException("unexpected HTML body"), FALLBACK))
        assertEquals(FALLBACK, articleErrorMessage(NullPointerException("missing response field"), FALLBACK))
    }

    @Test fun meaningfulBusinessErrorsArePreservedButEmptyMessagesUseTheScreenFallback() {
        assertEquals("文章已下架", articleErrorMessage(IllegalStateException("文章已下架"), FALLBACK))
        assertEquals(FALLBACK, articleErrorMessage(IllegalStateException("  "), FALLBACK))
    }

    private fun httpError(code: Int) = HttpException(Response.error<Unit>(code, "raw error".toResponseBody()))

    companion object { private const val FALLBACK = "文章加载失败，请稍后重试" }
}
