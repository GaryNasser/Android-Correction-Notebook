package com.github.garynasser.correction_notebook.data.remote.cas

import android.annotation.SuppressLint
import com.github.garynasser.correction_notebook.di.BasicRetrofit
import com.github.garynasser.correction_notebook.data.remote.network.awaitResponse
import com.github.garynasser.correction_notebook.data.remote.api.YanhePortal
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.nio.charset.StandardCharsets
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URLDecoder
import okhttp3.JavaNetCookieJar
import okhttp3.Cookie
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
        val cookies = CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)
        val client = okHttpClient.newBuilder().cookieJar(JavaNetCookieJar(cookies))
            .followRedirects(false).followSslRedirects(false).build()
        val entry = "${YanhePortal.ORIGIN}/casapi/index.php".toHttpUrl().newBuilder()
            .addQueryParameter("r", "auth/login").addQueryParameter("tenant_code", "21")
            .addQueryParameter("forward", "${YanhePortal.ORIGIN}/course")
        client.newCall(Request.Builder().url(entry.addQueryParameter("auType", "").build())
            .header("User-Agent", YanhePortal.USER_AGENT).build())
            .awaitResponse { Unit }
        val service = client.newCall(Request.Builder().url(entry.setQueryParameter("auType", "cas").build())
            .header("User-Agent", YanhePortal.USER_AGENT).build())
            .awaitResponse { response ->
                val target = response.header("Location")?.let { response.request.url.resolve(it) }
                    ?: throw CasAuthException("延河课堂未返回统一认证入口")
                if (target.scheme != "https" || target.port != 443 ||
                    target.host !in setOf("sso.bit.edu.cn", "login.bit.edu.cn")) {
                    throw CasAuthException("延河课堂统一认证入口异常")
                }
                target.queryParameter("service")?.toHttpUrl()
                    ?: throw CasAuthException("延河课堂没有提供认证回调")
            }
        if (service.scheme != "https" || service.host != "aita.yanhekt.cn" || service.port != 443 ||
            service.encodedPath != "/casapi/index.php" || service.queryParameter("r") != "auth/login" ||
            service.username.isNotEmpty() || service.password.isNotEmpty()) {
            throw CasAuthException("已阻止非延河课堂认证回调")
        }
        val st = sso.ticket(studentId, password, service.toString())
        var next = service.newBuilder().addQueryParameter("ticket", st).build()
        var finished = false
        var callbackToken: String? = null
        repeat(10) {
            if (!finished) {
                if (next.scheme != "https" || next.host != "aita.yanhekt.cn" || next.port != 443) {
                    throw CasAuthException("延河课堂认证跳转异常")
                }
                client.newCall(Request.Builder().url(next).header("User-Agent", YanhePortal.USER_AGENT).build())
                    .awaitResponse { response ->
                        Cookie.parseAll(response.request.url, response.headers).firstOrNull { it.name == "_token" }
                            ?.let { callbackToken = portalToken(it.value) }
                        if (response.code in 300..399) {
                            next = response.header("Location")?.let { next.resolve(it) }
                                ?: throw CasAuthException("延河课堂认证跳转缺少地址")
                        } else {
                            if (!response.isSuccessful) throw CasAuthException("延河课堂认证失败：${response.code}")
                            finished = true
                        }
                    }
            }
        }
        if (!finished) throw CasAuthException("延河课堂认证跳转次数过多")
        val token = cookies.cookieStore.cookies.filter { it.name == "_token" }
            .firstNotNullOfOrNull { cookie ->
                portalToken(cookie.value)
            } ?: callbackToken ?: throw CasAuthException("新版延河课堂认证未返回登录信息")
        client.newCall(Request.Builder().url("${YanhePortal.ORIGIN}/userapi/v1/infosimple")
            .header("User-Agent", YanhePortal.USER_AGENT).header("Authorization", "Bearer $token").build()).awaitResponse { response ->
                val root = runCatching { JsonParser.parseString(response.body?.string().orEmpty()).asJsonObject }.getOrNull()
                if (!response.isSuccessful || root?.get("code")?.asInt != 200) {
                    throw CasAuthException("新版延河课堂登录验证失败")
                }
            }
        token
    }

    suspend fun getServiceTicketFor(studentId: String, password: String, serviceUrl: String): String = withContext(Dispatchers.IO) {
        sso.ticket(studentId, password, serviceUrl)
    }

    private fun portalToken(raw: String): String? {
        val value = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
        return Regex("eyJ[\\w-]+\\.[\\w-]+\\.[\\w-]+").find(value)?.value
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
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        private const val VPN_KEY = "wrdvpnisthebest!"
    }
}

open class CasAuthException(message: String, cause: Throwable? = null) : Exception(message, cause)

class CasCredentialException(message: String) : CasAuthException(message)
