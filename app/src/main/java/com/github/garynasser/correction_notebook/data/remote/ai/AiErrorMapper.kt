package com.github.garynasser.correction_notebook.data.remote.ai

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

object AiErrorMapper {
    fun mapHttpError(code: Int, rawBody: String?): String {
        val body = rawBody.orEmpty()
        val apiMessage = extractMessage(body)
        return when (code) {
            400 -> apiMessage ?: "请求格式不正确或接口地址与所选协议不匹配"
            401 -> authError("API Key 无效或已失效", body, apiMessage)
            403 -> authError("当前 API Key 无权限访问该模型或接口", body, apiMessage)
            404 -> apiMessage ?: "接口地址或模型不存在"
            408 -> "请求超时，请稍后重试"
            409 -> "请求冲突，请稍后重试"
            422 -> apiMessage ?: "请求参数不符合接口要求"
            429 -> apiMessage ?: "请求过于频繁或额度已用尽"
            in 500..599 -> mapServerError(code, body, apiMessage)
            else -> {
                if ("model" in body.lowercase() && "not" in body.lowercase()) {
                    "模型不存在或当前接口不支持该模型"
                } else {
                    apiMessage ?: "AI 接口调用失败（HTTP $code），响应片段：${AiResponseParser.preview(body)}"
                }
            }
        }
    }

    fun mapThrowable(throwable: Throwable): String {
        return when (throwable) {
            is SocketTimeoutException -> "AI 请求超时。聊天、总结文件等长任务可能需要更久，请稍后重试；如果多次出现，请减少上下文或换用更快的模型/接口。"
            is UnknownHostException -> "无法解析 AI 服务地址，请检查 Base URL 是否为 API 地址，以及当前网络是否可访问该域名"
            is IOException -> "AI 网络连接失败，请检查网络、代理服务和 Base URL 后重试"
            else -> throwable.message ?: "发送失败"
        }
    }

    private fun extractMessage(body: String): String? {
        if (body.isBlank()) return null
        val messageRegex = Regex(""""message"\s*:\s*"([^"]+)"""")
        val errorRegex = Regex(""""error"\s*:\s*"([^"]+)"""")
        return messageRegex.find(body)?.groupValues?.getOrNull(1)
            ?: errorRegex.find(body)?.groupValues?.getOrNull(1)
            ?: body.takeIf { !it.trim().startsWith("{") }?.let {
                "AI 接口返回异常响应：${AiResponseParser.preview(it)}"
            }
    }

    private fun authError(defaultMessage: String, body: String, apiMessage: String?): String {
        if (apiMessage == null) return defaultMessage
        val normalized = apiMessage.lowercase()
        val isPlainAuthMessage = listOf(
            "authentication",
            "auth",
            "unauthorized",
            "api key",
            "invalid key",
            "permission"
        ).any { it in normalized }
        return if (isPlainAuthMessage || apiMessage.startsWith("AI 接口返回异常响应")) {
            "$defaultMessage。响应片段：${AiResponseParser.preview(body)}"
        } else {
            apiMessage
        }
    }

    private fun mapServerError(code: Int, body: String, apiMessage: String?): String {
        val normalized = body.trim().lowercase()
        if (normalized == "internal server error" || normalized == "server error") {
            return "AI 服务端返回 $code：Internal Server Error。通常是服务商或代理后端异常、模型名不被该接口支持、Base URL 指向了错误后端，或协议类型与接口不匹配。请先点“测试连接/获取模型”，并确认当前 Provider 的协议、Base URL、模型名和 API Key。"
        }
        return apiMessage ?: "AI 服务端返回 $code，暂时不可用。请稍后重试，或检查模型名、额度、代理服务状态和 Base URL。"
    }
}
