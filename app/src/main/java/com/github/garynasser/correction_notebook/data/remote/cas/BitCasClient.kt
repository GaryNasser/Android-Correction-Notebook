package com.github.garynasser.correction_notebook.data.remote.cas

import android.net.Uri
import com.github.garynasser.correction_notebook.di.BasicRetrofit
import com.github.garynasser.correction_notebook.utils.SignatureUtils
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BitCasClient @Inject constructor(
    @BasicRetrofit private val okHttpClient: OkHttpClient,
) {
    suspend fun getYanheToken(studentId: String, password: String): String = withContext(Dispatchers.IO) {
        val tgtUrl = getTgtUrl(studentId, password)
        val st = getServiceTicket(tgtUrl, YANHE_CALLBACK_URL)
        val callbackUrl = Uri.parse(YANHE_CALLBACK_URL)
            .buildUpon()
            .appendQueryParameter("ticket", st)
            .build()
            .toString()

        val request = Request.Builder()
            .url(callbackUrl)
            .headers(defaultHeadersBuilder().build())
            .get()
            .build()

        okHttpClient.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
            .newCall(request)
            .execute()
            .use { response ->
                val finalUrl = response.request.url
                val token = finalUrl.queryParameter("token")
                val code = finalUrl.queryParameter("code")
                if (!response.isSuccessful && token.isNullOrBlank() && code.isNullOrBlank()) {
                    throw CasAuthException("延河课堂认证失败：${response.code}")
                }
                token?.takeIf { it.isNotBlank() }
                    ?: code?.takeIf { it.isNotBlank() }?.let(::exchangeCodeForToken)
                    ?: throw CasAuthException("延河课堂认证成功回调中没有 token")
            }
    }

    suspend fun getServiceTicketFor(studentId: String, password: String, serviceUrl: String): String = withContext(Dispatchers.IO) {
        val tgtUrl = getTgtUrl(studentId, password)
        getServiceTicket(tgtUrl, serviceUrl)
    }

    private fun exchangeCodeForToken(code: String): String {
        val url = Uri.parse(YANHE_AUTH_TOKEN_URL)
            .buildUpon()
            .appendQueryParameter("code", code)
            .appendQueryParameter("type", "1")
            .build()
            .toString()

        val request = Request.Builder()
            .url(url)
            .headers(defaultYanheHeadersBuilder().build())
            .get()
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw CasAuthException("延河课堂 token 换取失败：${response.code}")
            }

            val root = runCatching {
                JsonParser.parseString(body).asJsonObject
            }.getOrElse {
                throw CasAuthException("延河课堂 token 响应格式异常")
            }
            val codeValue = root.get("code")?.asInt
            if (codeValue != 0) {
                val message = root.get("message")?.asString ?: root.get("msg")?.asString ?: "未知错误"
                throw CasAuthException("延河课堂 token 换取失败：$message")
            }

            val dataElement = root.get("data")
            if (dataElement == null || !dataElement.isJsonObject) {
                throw CasAuthException("延河课堂 token 响应缺少数据")
            }
            val token = dataElement.asJsonObject.get("token")?.asString
            if (token.isNullOrBlank()) {
                throw CasAuthException("延河课堂 token 响应缺少 token")
            }
            return token
        }
    }

    private fun getTgtUrl(studentId: String, password: String): String {
        val body = FormBody.Builder()
            .add("username", studentId)
            .add("password", password)
            .build()

        val request = Request.Builder()
            .url(CAS_TICKET_URL)
            .headers(defaultHeadersBuilder().build())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .post(body)
            .build()

        val html = fetchBody(request)
        return extractFormAction(html)
            ?: throw CasAuthException("统一认证失败，请检查学号或密码")
    }

    private fun getServiceTicket(tgtUrl: String, serviceUrl: String): String {
        val body = FormBody.Builder()
            .add("service", serviceUrl)
            .build()

        val request = Request.Builder()
            .url(tgtUrl)
            .headers(defaultHeadersBuilder().build())
            .header("Content-Type", "application/x-www-form-urlencoded")
            .post(body)
            .build()

        return fetchBody(request).trim()
            .takeIf { it.startsWith("ST-") }
            ?: throw CasAuthException("统一认证未返回有效票据")
    }

    private fun fetchBody(request: Request): String {
        try {
            okHttpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    if (response.code == 401) {
                        throw CasAuthException("统一认证失败，请检查学号或密码")
                    }
                    throw CasAuthException("统一认证请求失败：${response.code}")
                }
                return body
            }
        } catch (e: IOException) {
            throw CasAuthException("无法连接北理工统一认证", e)
        }
    }

    private fun extractFormAction(html: String): String? {
        val action = FORM_ACTION_REGEX.find(html)
            ?.groupValues
            ?.getOrNull(2)
            ?.trim()
            .orEmpty()

        if (action.isBlank()) return null
        return if (action.startsWith("http")) action else URI(CAS_BASE_URL).resolve(action).toString()
    }

    private fun defaultHeadersBuilder() = Headers.Builder()
        .add("User-Agent", USER_AGENT)
        .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")

    private fun defaultYanheHeadersBuilder(): Headers.Builder {
        val signature = SignatureUtils.getSignature()
        return Headers.Builder()
            .add("User-Agent", USER_AGENT)
            .add("Accept", "application/json, text/plain, */*")
            .add("Origin", "https://www.yanhekt.cn")
            .add("Referer", "https://www.yanhekt.cn/")
            .add("xdomain-client", "web_user")
            .add("Xdomain-Client", "web_user")
            .add("X-TRACE-ID", UUID.randomUUID().toString())
            .add("xclient-timestamp", signature["Xclient-Timestamp"].orEmpty())
            .add("xclient-signature", signature["Xclient-Signature"].orEmpty())
            .add("xclient-version", "v1")
            .add("Xclient-Version", "v1")
    }

    fun convertToWebVpnUrl(originalUrl: String): String {
        val uri = URI(originalUrl)
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return originalUrl
        val encodedHost = encodeVpnHost(host)
        val path = uri.rawPath.orEmpty()
        return URI(
            "https",
            "webvpn.bit.edu.cn",
            "/${uri.scheme}/$encodedHost$path",
            uri.rawQuery,
            uri.rawFragment
        ).toString()
    }

    private fun encodeVpnHost(host: String): String {
        val vpnKey = VPN_KEY.toByteArray(StandardCharsets.UTF_8)
        val vpnIv = VPN_KEY.toByteArray(StandardCharsets.UTF_8)
        val padLen = (16 - host.length % 16) % 16
        val plaintext = (host + "0".repeat(padLen)).toByteArray(StandardCharsets.UTF_8)
        val cipher = Cipher.getInstance("AES/ECB/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(vpnKey, "AES"))

        val ciphertext = ByteArray(plaintext.size)
        var feedback = vpnIv.copyOf()
        for (start in plaintext.indices step 16) {
            val keystream = cipher.doFinal(feedback)
            val currentBlock = ByteArray(16)
            for (offset in 0 until 16) {
                val index = start + offset
                if (index >= plaintext.size) break
                ciphertext[index] = (plaintext[index].toInt() xor keystream[offset].toInt()).toByte()
                currentBlock[offset] = ciphertext[index]
            }
            feedback = currentBlock
        }

        return vpnIv.toHex() + ciphertext.toHex().substring(0, host.length * 2)
    }

    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    companion object {
        private const val CAS_BASE_URL = "https://sso.bit.edu.cn/cas/"
        private const val CAS_TICKET_URL = "https://sso.bit.edu.cn/cas/v1/tickets"
        private const val YANHE_CALLBACK_URL = "https://cbiz.yanhekt.cn/v1/cas/callback"
        private const val YANHE_AUTH_TOKEN_URL = "https://cbiz.yanhekt.cn/v1/auth/token"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        private const val VPN_KEY = "wrdvpnisthebest!"
        private val FORM_ACTION_REGEX = Regex(
            "<form[^>]*action\\s*=\\s*(['\"])(.*?)\\1",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
    }
}

class CasAuthException(message: String, cause: Throwable? = null) : Exception(message, cause)
