package com.github.garynasser.correction_notebook.data.remote.cas

import com.github.garynasser.correction_notebook.data.remote.network.awaitResponse
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpCookie
import java.util.Base64
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.JavaNetCookieJar
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

internal class BitSsoClient(
    private val client: OkHttpClient,
    private val challenges: CasChallengeCoordinator,
) {
    private class Session(val studentId: String, val generation: Long, client: OkHttpClient) {
        val cookies = CookieManager(null, CookiePolicy.ACCEPT_ORIGINAL_SERVER)
        val client = client.newBuilder().cookieJar(JavaNetCookieJar(cookies))
            .followRedirects(false).followSslRedirects(false).build()
    }

    private val mutex = Mutex()
    @Volatile private var session: Session? = null

    fun clearSession() {
        challenges.cancelPending()
        session = null
    }

    suspend fun ticket(studentId: String, password: String, service: String): String = mutex.withLock {
        val id = studentId.trim()
        if (id.isEmpty() || password.isEmpty()) throw CasCredentialException("请输入学号和统一认证密码")
        val active = session?.takeIf { it.studentId == id && it.generation == challenges.generation }
            ?: Session(id, challenges.generation, client).also { session = it }
        val serviceUrl = service.toHttpUrl()
        try {
            val loginUrl = "$BASE/cas/login".toHttpUrl().newBuilder().addQueryParameter("service", service).build()
            val loaded = request(active, loginUrl)
            findTicket(loaded, serviceUrl)?.let { return@withLock checkedTicket(active, it) }
            val page = parsePage(loaded)
            val cryptoKey = page.textAt("login-croypto")
            if (cryptoKey.isBlank()) throw CasAuthException("学校统一认证页面已变化，请稍后重试")
            val captchaCountUrl = "$BASE/cas/api/protected/user/findCaptchaCount/".toHttpUrl()
                .newBuilder().addPathSegment(id).build()
            val captchaData = json(active, captchaCountUrl.toString())
                .getAsJsonObject("data")
            val captchaNeeded = captchaData?.get("captchaInvisible")?.let {
                !it.isJsonNull && it.asString !in listOf("", "false", "0")
            } == true
            val captchaCode = if (captchaNeeded) {
                val image = captchaImage(active, captchaData!!.stringAt("captchaUrl"))
                challenges.requestCode(CasCodePrompt(CasCodeKind.CAPTCHA, image = image), active.generation)
            } else ""
            val form = linkedMapOf(
                "type" to "UsernamePassword", "_eventId" to "submit", "geolocation" to "",
                "execution" to page.textAt("login-page-flowkey"), "username" to id,
                "croypto" to cryptoKey, "password" to BitSsoCrypto.encrypt(password, cryptoKey),
                "captcha_code" to captchaCode,
                "captcha_payload" to BitSsoCrypto.encrypt(
                    if (captchaNeeded) captchaData?.get("captchaPayload")?.takeIf { it.isJsonObject }?.toString() ?: "{}" else "{}", cryptoKey
                ),
            )
            if (page.textAt("riskSystemSwitch").equals("USTC", true)) {
                form["risk_payload"] = BitSsoCrypto.encrypt(riskPayload(active).toString(), cryptoKey)
                form["riskEngine"] = "true"
                form["targetSystem"] = page.textAt("targetSystem").ifBlank { "sso" }
                form["siteId"] = page.textAt("siteId").ifBlank { "sourceId" }
            }
            val response = postLogin(active, page, loaded.url, form)
            findTicket(response, serviceUrl)?.let { return@withLock checkedTicket(active, it) }
            val secondPage = Jsoup.parse(response.body)
            if (secondPage.textAt("user-object-id").isNotBlank() &&
                listOf("secondSmsLoginForm", "second-auth-tip", "cas-gateway").any { it in response.body }) {
                return@withLock checkedTicket(active, completeSms(active, id, secondPage, response.url, serviceUrl))
            }
            throw loginError(response, afterPassword = true)
        } catch (error: IOException) {
            throw CasAuthException("无法连接北理工统一认证，请检查网络连接", error)
        }
    }

    private fun checkedTicket(active: Session, ticket: String): String {
        ensureSession(active)
        return ticket
    }

    private fun ensureSession(active: Session) {
        if (session !== active || active.generation != challenges.generation) {
            throw CancellationException("统一认证会话已退出或变更")
        }
    }

    private suspend fun completeSms(active: Session, id: String, page: Document, url: HttpUrl, service: HttpUrl): String {
        val crypto = BitSsoCrypto.phoneRequest(page.textAt("user-object-id"))
        val phoneReply = request(active, "$BASE/cas/api/protected/sms/getPhoneNumberByUserId".toHttpUrl(),
            crypto.body.toRequestBody(JSON), mapOf("hasCrypto" to "true", "privateKey" to crypto.encryptedKey))
        val phoneData = BitSsoCrypto.phoneResponse(phoneReply.body, crypto.key).getAsJsonObject("data")
            ?: throw CasAuthException("学校未返回绑定手机信息")
        val phone = phoneData.stringAt("tel").ifBlank { page.textAt("phone-number") }
        if (phone.isBlank()) throw CasAuthException("学校账号未提供可用的绑定手机")
        val maskedPhone = phoneData.stringAt("maskTel").ifBlank { "绑定手机" }
        val sent = json(active, "$BASE/cas/api/protected/sms/publicNoToken/sendSmsCode", jsonBody(
            "phone" to phone, "businessNo" to "0008"
        ))
        if (!sent.isSuccess() && !sent.message().let { "验证码" in it && "有效期内" in it && "重复发送" in it }) {
            throw CasAuthException(sent.message().ifBlank { "短信发送失败，请稍后重试" })
        }
        var error: String? = null
        repeat(5) {
            val code = challenges.requestCode(CasCodePrompt(CasCodeKind.SMS, maskedPhone = maskedPhone, error = error), active.generation)
            val checked = json(active, "$BASE/cas/api/protected/sms/checkToken", jsonBody(
                "phone" to phone, "token" to code, "delete" to false, "trustDevice" to false
            ))
            if (!checked.isSuccess()) {
                error = checked.message().ifBlank { "验证码错误或已过期" }
            } else {
                val result = postLogin(active, page, url, mapOf(
                    "username" to id, "password" to code, "type" to "smsLogin", "_eventId" to "submit",
                    "geolocation" to "", "execution" to page.textAt("login-page-flowkey"),
                    "captcha_code" to "", "trustDevice" to "false",
                ))
                return findTicket(result, service) ?: throw loginError(result)
            }
        }
        throw CasAuthException("验证码多次验证失败，请重新登录")
    }

    private suspend fun captchaImage(active: Session, value: String): ByteArray {
        if (value.startsWith("data:image/") && ";base64," in value) {
            return runCatching { Base64.getDecoder().decode(value.substringAfter(",")) }
                .getOrElse { throw CasAuthException("学校图形验证码格式异常") }
        }
        val url = "$BASE/cas/".toHttpUrl().resolve(value)?.takeIf { value.isNotBlank() }
            ?: throw CasAuthException("学校未提供图形验证码")
        return request(active, url).bytes
    }

    private suspend fun riskPayload(active: Session): JsonObject {
        val store = active.cookies.cookieStore
        var device = store.cookies.firstOrNull { it.name == "device" }?.value
        if (device == null) {
            device = BitSsoCrypto.digest("SHA-256", UUID.randomUUID().toString())
            store.add(BASE.toHttpUrl().toUri(), HttpCookie("device", device).apply { domain = "sso.bit.edu.cn"; path = "/"; secure = true })
        }
        val groupId = store.cookies.firstOrNull { it.name == "riskSystemGroupId" }?.value.orEmpty()
        // Match the protocol's desktop browser profile; these values are not user credentials.
        val values = linkedMapOf(
            "fonts" to "[\"Arial\",\"Helvetica Neue\",\"PingFang SC\",\"Times New Roman\"]",
            "deviceMemory" to "16", "hardwareConcurrency" to "10", "timezone" to "\"Asia/Shanghai\"",
            "cpuClass" to "\"not available\"", "platform" to "\"MacIntel\"", "language" to "\"zh-CN\"",
            "screenResolution" to "[956,1470]",
        )
        val fp = JsonObject().apply {
            values.forEach { (key, value) -> addProperty(key, if (key in setOf("fonts", "deviceMemory", "hardwareConcurrency", "cpuClass")) BitSsoCrypto.digest("SHA-256", value) else value) }
            addProperty("fingerprint", BitSsoCrypto.digest("SHA-256", values.values.joinToString("")))
            addProperty("cookieValue", device); addProperty("localgroupId", groupId)
            addProperty("userAgent", USER_AGENT); addProperty("platformAuthenticator", "support")
        }
        val result = json(active, "$BASE/ustc-rba-front/fp", fp.toString().toRequestBody(JSON))
        val token = result.stringAt("responsetoken").ifBlank { result.getAsJsonObject("data")?.stringAt("responsetoken").orEmpty() }
        if (token.isBlank()) throw CasAuthException("学校设备验证未通过，请稍后重试")
        return JsonObject().apply { addProperty("token", token); addProperty("groupId", groupId) }
    }

    private class Reply(val url: HttpUrl, val status: Int, val location: String?, val bytes: ByteArray) {
        val body: String get() = bytes.toString(Charsets.UTF_8)
    }

    private suspend fun request(active: Session, url: HttpUrl, body: RequestBody? = null, headers: Map<String, String> = emptyMap()): Reply {
        ensureSession(active)
        requireSsoUrl(url)
        val builder = Request.Builder().url(url).header("User-Agent", USER_AGENT)
            .header("Referer", "$BASE/cas/login").header("Accept-Language", "zh-CN,zh;q=0.9")
        if (body != null) builder.header("Origin", BASE).post(body)
        if ("/protected/" in url.encodedPath) {
            BitSsoCrypto.csrfHeaders().forEach { (key, value) -> builder.header(key, value) }
            builder.header("Sid-Language", "zh_CN")
        }
        headers.forEach { (key, value) -> builder.header(key, value) }
        return active.client.newCall(builder.build()).awaitResponse { response ->
            ensureSession(active)
            val reply = Reply(response.request.url, response.code, response.header("Location"), response.body?.bytes() ?: byteArrayOf())
            if (response.code >= 400 && !(body != null && url.encodedPath == "/cas/login" && response.code in setOf(400, 401, 403))) {
                throw CasAuthException("学校统一认证请求失败：${response.code}")
            }
            reply
        }
    }

    private suspend fun json(active: Session, url: String, body: RequestBody? = null): JsonObject =
        runCatching { JsonParser.parseString(request(active, url.toHttpUrl(), body).body).asJsonObject }
            .getOrElse {
                if (it is CancellationException || it is CasAuthException || it is IOException) throw it
                throw CasAuthException("学校统一认证响应格式异常", it)
            }

    private suspend fun postLogin(active: Session, page: Document, url: HttpUrl, form: Map<String, String>): Reply {
        val action = url.resolve(page.selectFirst("form")?.attr("action")?.takeIf { it.isNotBlank() } ?: "login")
            ?: throw CasAuthException("学校统一认证表单地址无效")
        requireSsoUrl(action)
        if (action.encodedPath != "/cas/login") throw CasAuthException("学校统一认证表单地址异常")
        return request(active, action, FormBody.Builder().apply { form.forEach { (key, value) -> add(key, value) } }.build())
    }

    private fun parsePage(reply: Reply): Document {
        val document = Jsoup.parse(reply.body)
        if (document.textAt("login-page-flowkey").isBlank()) throw loginError(reply)
        return document
    }

    private fun loginError(reply: Reply, afterPassword: Boolean = false): CasAuthException {
        val page = Jsoup.parse(reply.body)
        val rawMessage = page.textAt("login-error-msg")
        val code = page.textAt("login-error-code").ifBlank { rawMessage.takeIf { it.all(Char::isDigit) }.orEmpty() }
        val message = rawMessage.takeUnless { it.all(Char::isDigit) }.orEmpty().ifBlank {
            LOGIN_ERRORS[code] ?: "统一认证失败，请检查学号、密码或验证码"
        }
        val passwordRejected = afterPassword && (code in setOf("1030027", "1030031") ||
            (code.isEmpty() && "密码" in rawMessage && ("错误" in rawMessage || "不正确" in rawMessage)))
        return if (passwordRejected) CasCredentialException(message) else CasAuthException(message)
    }

    private fun findTicket(reply: Reply, service: HttpUrl): String? {
        if (reply.status !in 200..399) return null
        val target = reply.location?.let { reply.url.resolve(it) } ?: reply.url
        if (target.scheme != service.scheme || target.host != service.host || target.port != service.port ||
            target.encodedPath != service.encodedPath || service.queryParameterNames.any { target.queryParameterValues(it) != service.queryParameterValues(it) }) return null
        return target.queryParameterValues("ticket").singleOrNull()?.takeIf { it.startsWith("ST-") && it.length > 3 }
    }

    private fun requireSsoUrl(url: HttpUrl) {
        if (url.scheme != "https" || url.host != "sso.bit.edu.cn" || url.port != 443 || url.username.isNotEmpty() || url.password.isNotEmpty()) {
            throw CasAuthException("已阻止非学校统一认证地址")
        }
    }

    private fun Document.textAt(id: String) = getElementById(id)?.text()?.trim().orEmpty()
    private fun JsonObject.stringAt(key: String): String = get(key)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
    private fun JsonObject.message() = stringAt("message").ifBlank { stringAt("msg") }
    private fun JsonObject.isSuccess() = stringAt("code") == "200"
    private fun jsonBody(vararg values: Pair<String, Any>) = JsonObject().apply {
        values.forEach { (key, value) -> if (value is Boolean) addProperty(key, value) else addProperty(key, value.toString()) }
    }.toString().toRequestBody(JSON)

    companion object {
        private const val BASE = "https://sso.bit.edu.cn"
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val LOGIN_ERRORS = mapOf(
            "1030027" to "用户名或密码错误", "1030031" to "用户名或密码错误",
            "1030028" to "账号已被锁定", "1320007" to "验证码错误或已失效",
            "1320010" to "图形验证码错误", "1330001" to "登录被账号风控拒绝",
            "1410040" to "账号状态无效", "1410041" to "账号状态无效",
            "3910001" to "账号已休眠，请先完成账号激活",
        )
        private const val USER_AGENT = "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36"
    }
}
