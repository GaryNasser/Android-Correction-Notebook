package com.github.garynasser.correction_notebook.ui.screens.home

import com.google.gson.JsonParseException
import retrofit2.HttpException
import java.io.IOException
import java.net.SocketTimeoutException

internal fun articleErrorMessage(error: Exception, fallback: String): String = when (error) {
    is HttpException -> when (error.code()) {
        429 -> "请求过于频繁，请稍后重试"
        in 500..599 -> "内容服务暂时不可用，请稍后重试"
        else -> fallback
    }
    is SocketTimeoutException -> "内容加载超时，请稍后重试"
    is IOException -> "无法连接内容服务，请检查网络后重试"
    is JsonParseException -> "内容格式异常，请稍后重试"
    is IllegalStateException -> error.message?.takeIf { it.isNotBlank() } ?: fallback
    else -> fallback
}
