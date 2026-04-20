package com.github.garynasser.correction_notebook.data.remote.ai

import java.io.IOException

object AiErrorMapper {
    fun mapHttpError(code: Int, rawBody: String?): String {
        val body = rawBody.orEmpty()
        return when (code) {
            400 -> "请求格式不正确或接口地址与所选协议不匹配"
            401 -> "API Key 无效或已失效"
            403 -> "当前 API Key 无权限访问该模型或接口"
            404 -> "接口地址或模型不存在"
            408 -> "请求超时，请稍后重试"
            409 -> "请求冲突，请稍后重试"
            422 -> "请求参数不符合接口要求"
            429 -> "请求过于频繁或额度已用尽"
            in 500..599 -> "AI 服务暂时不可用，请稍后重试"
            else -> {
                if ("model" in body.lowercase() && "not" in body.lowercase()) {
                    "模型不存在或当前接口不支持该模型"
                } else {
                    "AI 接口调用失败（HTTP $code）"
                }
            }
        }
    }

    fun mapThrowable(throwable: Throwable): String {
        return when (throwable) {
            is IOException -> "网络连接失败，请检查网络后重试"
            else -> throwable.message ?: "发送失败"
        }
    }
}
