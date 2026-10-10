package com.github.garynasser.correction_notebook.data.remote.cas

import android.annotation.SuppressLint
import com.github.garynasser.correction_notebook.di.BasicRetrofit
import com.github.garynasser.correction_notebook.data.remote.network.awaitResponse
import com.github.garynasser.correction_notebook.utils.SignatureUtils
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
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
    challenges: CasChallengeCoordinator,
) {
    constructor(okHttpClient: OkHttpClient) : this(okHttpClient, CasChallengeCoordinator())

    private val sso = BitSsoClient(okHttpClient, challenges)

    fun clearSession() = sso.clearSession()

    suspend fun getYanheToken(studentId: String, password: String): String = withContext(Dispatchers.IO) {
        val st = sso.ticket(studentId, password, YANHE_CALLBACK_URL)
        val callbackUrl = YANHE_CALLBACK_URL.toHttpUrl()
            .newBuilder()
            .addQueryParameter("ticket", st)
            .build()

        val request = Request.Builder()
            .url(callbackUrl)
            .headers(defaultHeadersBuilder().build())
            .get()
            .build()

        val finalUrl = okHttpClient.newBuilder()
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
            .newCall(request)
            .awaitResponse { response ->
                val finalUrl = response.request.url
                val token = finalUrl.queryParameter("token")
                val code = finalUrl.queryParameter("code")
                if (!response.isSuccessful && token.isNullOrBlank() && code.isNullOrBlank()) {
                    throw CasAuthException("延河课堂认证失败：${response.code}")
                }
                finalUrl
            }
        finalUrl.queryParameter("token")?.takeIf { it.isNotBlank() }
            ?: finalUrl.queryParameter("code")?.takeIf { it.isNotBlank() }?.let { exchangeCodeForToken(it) }
            ?: throw CasAuthException("延河课堂认证成功回调中没有 token")
    }

    suspend fun getServiceTicketFor(studentId: String, password: String, serviceUrl: String): String = withContext(Dispatchers.IO) {
        sso.ticket(studentId, password, serviceUrl)
    }

    private suspend fun exchangeCodeForToken(code: String): String {
        val url = YANHE_AUTH_TOKEN_URL.toHttpUrl()
            .newBuilder()
            .addQueryParameter("code", code)
            .addQueryParameter("type", "1")
            .build()

        val request = Request.Builder()
            .url(url)
            .headers(defaultYanheHeadersBuilder().build())
            .get()
            .build()

        return okHttpClient.newCall(request).awaitResponse { response ->
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
            token
        }
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

    @SuppressLint("GetInstance")
    private fun encodeVpnHost(host: String): String {
        val vpnKey = VPN_KEY.toByteArray(StandardCharsets.UTF_8)
        val vpnIv = VPN_KEY.toByteArray(StandardCharsets.UTF_8)
        val padLen = (16 - host.length % 16) % 16
        val plaintext = (host + "0".repeat(padLen)).toByteArray(StandardCharsets.UTF_8)
        // WebVPN host encoding uses AES as a block primitive with manual feedback.
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
        private const val YANHE_CALLBACK_URL = "https://cbiz.yanhekt.cn/v1/cas/callback"
        private const val YANHE_AUTH_TOKEN_URL = "https://cbiz.yanhekt.cn/v1/auth/token"
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        private const val VPN_KEY = "wrdvpnisthebest!"
    }
}

open class CasAuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

class CasCredentialException(message: String) : CasAuthException(message)
