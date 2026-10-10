package com.github.garynasser.correction_notebook.data.remote.cas

import com.google.gson.JsonParser
import java.util.Base64
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test

class BitSsoFlowTest {
    @Test fun aesMatchesReferenceVectorAndCsrfMatchesSchoolAlgorithm() {
        assertEquals("R4lDIBO/32oRLZSjtsPrGQ==", BitSsoCrypto.encrypt("password", KEY))
        val headers = BitSsoCrypto.csrfHeaders()
        val encoded = Base64.getEncoder().encodeToString(headers.getValue("Csrf-Key").toByteArray())
        assertEquals(32, headers.getValue("Csrf-Key").length)
        assertEquals(BitSsoCrypto.digest("MD5", encoded.take(encoded.length / 2) + encoded + encoded.drop(encoded.length / 2)), headers["Csrf-Value"])
    }

    @Test fun encryptedPhoneEnvelopeAndQuotedResponseRoundTrip() {
        val envelope = BitSsoCrypto.phoneRequest("user-object-id")
        assertEquals(256, Base64.getDecoder().decode(envelope.encryptedKey).size)
        val encoded = Base64.getEncoder().encodeToString(envelope.key)
        val json = """{"data":{"tel":"opaque","maskTel":"138****8000"}}"""
        val encrypted = BitSsoCrypto.encrypt(json, encoded)
        assertEquals("opaque", BitSsoCrypto.phoneResponse("\"$encrypted\"", envelope.key).getAsJsonObject("data").get("tel").asString)
        assertNotEquals(json, encrypted)
    }

    @Test fun passwordAndCaptchaPayloadAreEncryptedAndRiskTokenIsSubmitted() = withFixture { fixture ->
        fixture.risk = true
        assertEquals("ST-new", fixture.login())
        val fields = fixture.posts.single().fields()
        assertEquals("UsernamePassword", fields["type"])
        assertEquals(BitSsoCrypto.encrypt("password", KEY), fields["password"])
        assertEquals(BitSsoCrypto.encrypt("{}", KEY), fields["captcha_payload"])
        assertEquals(BitSsoCrypto.encrypt("""{"token":"risk-test","groupId":""}""", KEY), fields["risk_payload"])
        assertTrue(fixture.seen.all { it.url.scheme == "https" && it.url.host == "sso.bit.edu.cn" })
        assertTrue(fixture.seen.filter { "/protected/" in it.url.encodedPath }.all { !it.header("Csrf-Key").isNullOrBlank() })
    }

    @Test fun captchaSuspendsUntilMatchingPromptIsAnswered() = withFixture { fixture ->
        fixture.captcha = true
        val login = async { fixture.login() }
        val prompt = fixture.coordinator.prompt.first { it != null }!!
        assertEquals(CasCodeKind.CAPTCHA, prompt.kind)
        assertArrayEquals(byteArrayOf(1, 2, 3), prompt.image)
        assertTrue(fixture.posts.isEmpty())
        fixture.coordinator.submit("stale-id", "ignored")
        assertTrue(login.isActive)
        fixture.coordinator.submit(prompt.id, " aB42 ")
        assertEquals("ST-new", login.await())
        assertEquals("aB42", fixture.posts.single().fields()["captcha_code"])
        assertEquals(BitSsoCrypto.encrypt("""{"ts":"value"}""", KEY), fixture.posts.single().fields()["captcha_payload"])
        assertNull(fixture.coordinator.prompt.value)
    }

    @Test fun smsRejectsWrongCodeWithoutResendingAndThenContinuesSameExecution() = withFixture { fixture ->
        fixture.sms = true
        fixture.validSmsAlreadySent = true
        val login = async { fixture.login() }
        val first = fixture.coordinator.prompt.first { it != null }!!
        assertEquals(CasCodeKind.SMS, first.kind)
        assertEquals("138****8000", first.maskedPhone)
        fixture.coordinator.submit(first.id, "000000")
        val retry = fixture.coordinator.prompt.first { it != null && it.id != first.id }!!
        assertEquals("验证码错误", retry.error)
        fixture.coordinator.submit(retry.id, "123456")
        assertEquals("ST-new", login.await())
        assertEquals(1, fixture.seen.count { it.url.encodedPath.endsWith("/sendSmsCode") })
        val smsForm = fixture.posts.last().fields()
        assertEquals("smsLogin", smsForm["type"])
        assertEquals("second-execution", smsForm["execution"])
        assertEquals("false", smsForm["trustDevice"])
        val checked = fixture.seen.last { it.url.encodedPath.endsWith("/checkToken") }.jsonBody()
        assertEquals("opaque-phone", checked.get("phone").asString)
        assertFalse(checked.get("trustDevice").asBoolean)
    }

    @Test fun cancelAndCoroutineCancellationClearPromptAndReleaseNextAttempt() = withFixture { fixture ->
        fixture.captcha = true
        val first = async { runCatching { fixture.login() } }
        fixture.coordinator.prompt.first { it != null }!!.let { fixture.coordinator.cancel(it.id) }
        assertTrue(first.await().exceptionOrNull() is CasAuthException)
        assertTrue(fixture.posts.isEmpty())
        val second = async { fixture.login() }
        fixture.coordinator.prompt.first { it != null }
        second.cancel()
        second.join()
        assertNull(fixture.coordinator.prompt.value)
        val third = async { fixture.login() }
        fixture.coordinator.prompt.first { it != null }!!.let { fixture.coordinator.submit(it.id, "42") }
        assertEquals("ST-new", third.await())
    }

    @Test fun signOutDuringCodeEntryCannotReturnATicket() = withFixture { fixture ->
        fixture.sms = true
        val login = async { runCatching { fixture.login() } }
        fixture.coordinator.prompt.first { it != null }
        fixture.cas.clearSession()
        assertTrue(login.await().isFailure)
        assertEquals(1, fixture.posts.size)
        assertNull(fixture.coordinator.prompt.value)
    }

    @Test fun aLateChallengeFromBeforeLogoutNeverAppears() = runBlocking {
        val coordinator = CasChallengeCoordinator()
        val previousGeneration = coordinator.generation
        coordinator.cancelPending()
        val result = runCatching {
            coordinator.requestCode(CasCodePrompt(CasCodeKind.SMS), previousGeneration)
        }
        assertTrue(result.exceptionOrNull() is CancellationException)
        assertNull(coordinator.prompt.value)
    }

    @Test fun rejectsForeignFormCaptchaAndMismatchedTicketCallbacks() = withFixture { fixture ->
        fixture.formAction = "https://attacker.invalid/cas/login"
        assertTrue(runCatching { fixture.login() }.exceptionOrNull() is CasAuthException)
        assertTrue(fixture.posts.isEmpty())
        fixture.formAction = null
        fixture.captcha = true
        fixture.captchaUrl = "https://attacker.invalid/captcha.png"
        assertTrue(runCatching { fixture.login() }.exceptionOrNull() is CasAuthException)
        assertNull(fixture.coordinator.prompt.value)
        fixture.captcha = false
        fixture.ticketUrl = "https://school.example/other?ticket=ST-stolen"
        assertTrue(runCatching { fixture.login() }.exceptionOrNull() is CasAuthException)
    }

    @Test fun rejectsLoginPageReturnedAfterPasswordAndKeepsBackendError() = withFixture { fixture ->
        fixture.reject = true
        listOf(400, 401, 403).forEach { status ->
            fixture.rejectStatus = status
            val error = runCatching { fixture.login() }.exceptionOrNull()
            assertTrue(error is CasCredentialException)
            assertEquals("账号密码错误", error!!.message)
        }
    }

    @Test fun captchaRiskAndExpiredExecutionAreNotMisclassifiedAsPasswordRejections() = withFixture { fixture ->
        fixture.reject = true
        fixture.rejectMessage = ""
        listOf("1320007", "1320010", "1330001", "1030028", "").forEach { code ->
            fixture.rejectCode = code
            val error = runCatching { fixture.login() }.exceptionOrNull()
            assertTrue(error is CasAuthException)
            assertFalse("Only an explicit password rejection may discard saved credentials", error is CasCredentialException)
        }
        fixture.rejectCode = "1030027"
        val error = runCatching { fixture.login() }.exceptionOrNull()
        assertTrue(error is CasCredentialException)
        assertEquals("用户名或密码错误", error!!.message)
    }

    @Test fun serializesChallengesAndStaleAnswersCannotCompleteNextLogin() = withFixture { fixture ->
        fixture.captcha = true
        val first = async { fixture.login() }
        val firstPrompt = fixture.coordinator.prompt.first { it != null }!!
        val second = async { fixture.cas.getServiceTicketFor("other-student", "password", SERVICE) }
        fixture.coordinator.submit(firstPrompt.id, "11")
        assertEquals("ST-new", first.await())
        val secondPrompt = fixture.coordinator.prompt.first { it != null && it.id != firstPrompt.id }!!
        fixture.coordinator.submit(firstPrompt.id, "22")
        assertTrue(second.isActive)
        fixture.coordinator.submit(secondPrompt.id, "33")
        assertEquals("ST-new", second.await())
        assertEquals(listOf("11", "33"), fixture.posts.map { it.fields()["captcha_code"] })
    }

    private class Fixture {
        val coordinator = CasChallengeCoordinator()
        val seen = java.util.Collections.synchronizedList(mutableListOf<Request>())
        val posts get() = seen.filter { it.url.encodedPath == "/cas/login" && it.method == "POST" }
        var captcha = false
        var sms = false
        var risk = false
        var reject = false
        var rejectStatus = 401
        var rejectMessage = "账号密码错误"
        var rejectCode = ""
        var validSmsAlreadySent = false
        var formAction: String? = null
        var captchaUrl = "data:image/png;base64,AQID"
        var ticketUrl: String? = null
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            seen.add(request)
            val reply = Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
            val body = when {
                request.url.encodedPath == "/cas/login" && request.method == "GET" -> loginPage(formAction ?: request.url.toString(), risk)
                request.url.encodedPath.startsWith("/cas/api/protected/user/findCaptchaCount/") ->
                    """{"code":200,"data":{"captchaInvisible":$captcha,"captchaUrl":"$captchaUrl","captchaPayload":{"ts":"value"}}}"""
                request.url.encodedPath == "/ustc-rba-front/fp" -> """{"code":200,"data":{"responsetoken":"risk-test"}}"""
                request.url.encodedPath.endsWith("/getPhoneNumberByUserId") -> {
                    assertEquals("true", request.header("hasCrypto"))
                    assertEquals(256, Base64.getDecoder().decode(request.header("privateKey")).size)
                    assertFalse(request.rawBody().contains("user-object"))
                    """{"code":200,"data":{"tel":"opaque-phone","maskTel":"138****8000"}}"""
                }
                request.url.encodedPath.endsWith("/sendSmsCode") -> if (validSmsAlreadySent)
                    """{"code":400,"message":"验证码在有效期内，请勿重复发送"}""" else """{"code":200}"""
                request.url.encodedPath.endsWith("/checkToken") -> if (request.jsonBody().get("token").asString == "123456")
                    """{"code":200}""" else """{"code":400,"message":"验证码错误"}"""
                request.url.encodedPath == "/cas/login" && request.method == "POST" -> {
                    val type = request.fields()["type"]
                    when {
                        reject -> { reply.code(rejectStatus); "<span id='login-error-msg'>$rejectMessage</span><span id='login-error-code'>$rejectCode</span>" }
                        sms && type == "UsernamePassword" -> "<form action='${request.url}' id='secondSmsLoginForm'><span id='login-page-flowkey'>second-execution</span><span id='user-object-id'>user-object</span></form>"
                        else -> { reply.code(302).header("Location", ticketUrl ?: "$SERVICE?ticket=ST-new"); "" }
                    }
                }
                else -> throw AssertionError("Unexpected SSO request: ${request.url}")
            }
            reply.body(body.toResponseBody()).build()
        }.build()
        val cas = BitCasClient(client, coordinator)
        suspend fun login() = cas.getServiceTicketFor("student", "password", SERVICE)
    }

    private fun withFixture(block: suspend kotlinx.coroutines.CoroutineScope.(Fixture) -> Unit) = runBlocking {
        val fixture = Fixture()
        try { withTimeout(10_000) { block(fixture) } }
        finally { fixture.client.dispatcher.executorService.shutdownNow(); fixture.client.connectionPool.evictAll() }
    }

    companion object {
        private const val KEY = "MDEyMzQ1Njc4OWFiY2RlZg=="
        private const val SERVICE = "https://school.example/callback"
        private fun loginPage(action: String, risk: Boolean) = "<form action='$action'><span id='login-page-flowkey'>first-execution</span><span id='login-croypto'>$KEY</span><span id='riskSystemSwitch'>${if (risk) "USTC" else ""}</span></form>"
        private fun Request.fields(): Map<String, String> = (body as FormBody).let { form -> (0 until form.size).associate { form.name(it) to form.value(it) } }
        private fun Request.rawBody(): String = Buffer().also { body!!.writeTo(it) }.readUtf8()
        private fun Request.jsonBody() = JsonParser.parseString(rawBody()).asJsonObject
    }
}
