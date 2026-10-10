package com.github.garynasser.correction_notebook.ui.screens.register

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performImeAction
import androidx.test.espresso.Espresso
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.local.CredentialManager
import com.github.garynasser.correction_notebook.data.local.TokenManager
import com.github.garynasser.correction_notebook.data.model.auth.AuthState
import com.github.garynasser.correction_notebook.data.model.auth.UserCredential
import com.github.garynasser.correction_notebook.data.remote.api.VideoApiService
import com.github.garynasser.correction_notebook.data.remote.cas.BitCasClient
import com.github.garynasser.correction_notebook.data.remote.cas.CasChallengeCoordinator
import com.github.garynasser.correction_notebook.data.remote.cas.ssoResponseFixture
import com.github.garynasser.correction_notebook.data.remote.manager.VideoRemoteManager
import com.github.garynasser.correction_notebook.data.repository.AuthStateManager
import com.github.garynasser.correction_notebook.data.repository.VideoRepository
import com.github.garynasser.correction_notebook.data.repository.YanheRepository
import com.github.garynasser.correction_notebook.ui.components.AuthFormTemplate
import com.github.garynasser.correction_notebook.ui.components.CasChallengeDialog
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import java.io.IOException
import java.io.File
import android.graphics.Bitmap
import android.graphics.Rect
import android.content.ContextWrapper
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.FormBody
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class CasScreenFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test
    fun smsDialogKeepsOldSessionUntilTheCorrectCodeCompletesLogin() = withFixture { fixture ->
        fixture.network.requireSms = true
        fixture.network.release.countDown()
        showWithChallenges(fixture)
        compose.runOnIdle { fixture.vm.submitYanheLogin() }
        compose.waitUntil(5_000) { fixture.network.challenges.prompt.value != null }
        compose.onNodeWithText("验证码已发送至 138****8000").assertIsDisplayed()
        assertEquals(fixture.previousToken, runBlocking { fixture.tokens.getYanheLoginToken() })
        assertEquals(fixture.previousCredential, fixture.credentials.getCredentials())
        compose.onNodeWithTag("cas-code-input").performTextReplacement("000000")
        compose.onNodeWithText("验证").performClick()
        compose.waitUntil(5_000) { fixture.network.challenges.prompt.value?.error != null }
        compose.onNodeWithText("验证码错误").assertIsDisplayed()
        capture("qa-cas-sms-retry.png")
        compose.onNodeWithTag("cas-code-input").performTextReplacement("123456")
        compose.onNodeWithText("验证").performClick()
        compose.waitUntil(5_000) { fixture.auth.authState.value is AuthState.Authenticated }
        assertEquals("qa-token", runBlocking { fixture.tokens.getYanheLoginToken() })
        assertEquals(UserCredential("qa-student", "qa-password"), fixture.credentials.getCredentials())
        assertEquals(1, fixture.network.smsSends.get())
        assertNull(fixture.network.challenges.prompt.value)
    }

    @Test
    fun dismissingSmsDoesNotChangeCredentialsAndRetryRemainsAvailable() = withFixture { fixture ->
        fixture.network.requireSms = true
        fixture.network.release.countDown()
        showWithChallenges(fixture)
        compose.runOnIdle { fixture.vm.submitYanheLogin() }
        compose.waitUntil(5_000) { fixture.network.challenges.prompt.value != null }
        compose.onNodeWithText("取消").performClick()
        compose.waitUntil(5_000) { !fixture.vm.isCasLoading }
        assertEquals(fixture.previousToken, runBlocking { fixture.tokens.getYanheLoginToken() })
        assertEquals(fixture.previousCredential, fixture.credentials.getCredentials())
        compose.onNodeWithText("登录延河课堂").performScrollTo().assertIsEnabled().performClick()
        compose.waitUntil(5_000) { fixture.network.challenges.prompt.value != null }
        compose.onNodeWithTag("cas-code-input").performTextReplacement("123456")
        compose.onNodeWithText("验证").performClick()
        compose.waitUntil(5_000) { fixture.auth.authState.value is AuthState.Authenticated }
    }

    private fun showWithChallenges(fixture: Fixture) {
        compose.setContent {
            CorrectionNotebookTheme {
                CasScreen(viewModel = fixture.vm, onBackButtonClick = {})
                val prompt by fixture.network.challenges.prompt.collectAsState()
                prompt?.let { current ->
                    CasChallengeDialog(current, { fixture.network.challenges.submit(current.id, it) },
                        { fixture.network.challenges.cancel(current.id) })
                }
            }
        }
    }

    @Test
    fun inFlightAuthenticationKeepsBackAvailable() = withFixture { fixture ->
        var backs = 0
        compose.setContent {
            CorrectionNotebookTheme { CasScreen(viewModel = fixture.vm, onBackButtonClick = { backs++ }) }
        }
        compose.runOnIdle { fixture.vm.submitYanheLogin() }
        assertTrue(fixture.network.entered.await(3, TimeUnit.SECONDS))
        compose.onNodeWithContentDescription("返回").assertIsEnabled().performClick()
        assertTrue(fixture.network.cancelled.await(3, TimeUnit.SECONDS))
        fixture.network.release.countDown()
        compose.runOnIdle {
            assertEquals(1, backs)
            assertFalse(fixture.vm.isCasLoading)
            assertNull(fixture.vm.errorMessage)
            assertTrue(fixture.auth.authState.value is AuthState.Unauthenticated)
        }
        assertEquals(fixture.previousToken, runBlocking { fixture.tokens.getYanheLoginToken() })
        assertEquals(fixture.previousCredential, fixture.credentials.getCredentials())
    }

    @Test
    fun systemBackCancelsPendingAuthentication() = withFixture { fixture ->
        var backs = 0
        compose.setContent { CorrectionNotebookTheme { CasScreen(viewModel = fixture.vm, onBackButtonClick = { backs++ }) } }
        compose.runOnIdle { fixture.vm.submitYanheLogin() }
        assertTrue(fixture.network.entered.await(3, TimeUnit.SECONDS))
        Espresso.pressBack()
        assertTrue(fixture.network.cancelled.await(3, TimeUnit.SECONDS))
        compose.runOnIdle { assertEquals(1, backs); assertFalse(fixture.vm.isCasLoading) }
    }

    @Test
    fun immediateRetryIgnoresOldCancellationAndRejectsDoubleSubmission() = withFixture { fixture ->
        var successes = 0
        compose.setContent { CorrectionNotebookTheme { CasScreen(viewModel = fixture.vm, onBackButtonClick = {}) } }
        compose.runOnIdle { fixture.vm.submitYanheLogin { successes++ } }
        assertTrue(fixture.network.entered.await(3, TimeUnit.SECONDS))
        compose.runOnIdle {
            fixture.vm.cancelYanheLogin()
            fixture.vm.studentId = "  qa-student  "
            fixture.vm.casPassword = "retry-password"
            fixture.vm.submitYanheLogin { successes++ }
            fixture.vm.submitYanheLogin { successes++ }
        }
        compose.waitUntil(3_000) { fixture.network.callbackRequests.get() == 2 }
        compose.runOnIdle { assertTrue(fixture.vm.isCasLoading) }
        fixture.network.release.countDown()
        compose.waitUntil(5_000) { !fixture.vm.isCasLoading }
        compose.runOnIdle {
            assertEquals(1, successes)
            assertNull(fixture.vm.errorMessage)
            assertTrue(fixture.auth.authState.value is AuthState.Authenticated)
        }
        assertEquals("qa-token", runBlocking { fixture.tokens.getYanheLoginToken() })
        assertEquals(UserCredential("qa-student", "retry-password"), fixture.credentials.getCredentials())
    }

    @Test
    fun rejectedCredentialsCanBeCorrectedAndSubmittedByKeyboard() = withFixture { fixture ->
        fixture.network.rejectCredentials = true
        compose.setContent { CorrectionNotebookTheme { CasScreen(viewModel = fixture.vm, onBackButtonClick = {}) } }
        compose.onNodeWithText("登录延河课堂").performScrollTo().performClick()
        compose.waitUntil(5_000) { fixture.vm.errorMessage != null }
        compose.onNodeWithText("统一认证失败，请检查学号、密码或验证码").performScrollTo().assertIsDisplayed()
        assertEquals(fixture.previousToken, runBlocking { fixture.tokens.getYanheLoginToken() })
        assertEquals(fixture.previousCredential, fixture.credentials.getCredentials())
        fixture.network.rejectCredentials = false
        fixture.network.release.countDown()
        val password = compose.onNodeWithText("统一认证密码")
        password.performScrollTo().performTextReplacement("corrected-password")
        password.performImeAction()
        compose.waitUntil(5_000) { fixture.auth.authState.value is AuthState.Authenticated }
        compose.runOnIdle { assertFalse(fixture.vm.isCasLoading); assertNull(fixture.vm.errorMessage) }
        assertEquals(UserCredential("qa-student", "corrected-password"), fixture.credentials.getCredentials())
    }

    @Test
    fun shortLightLayoutKeepsFormAndSubmitReachable() = assertShortLayout(dark = false)

    @Test
    fun shortDarkLayoutKeepsFormAndSubmitReachable() = assertShortLayout(dark = true)

    @Test
    fun keyboardDoesNotCoverTheSubmitButton() = withFixture { fixture ->
        var view: View? = null
        var submits = 0
        compose.setContent {
            view = LocalView.current
            CorrectionNotebookTheme {
                CasScreen(viewModel = fixture.vm, onBackButtonClick = {}, onConfirm = { submits++ })
            }
        }
        val initial = Rect()
        val visible = Rect()
        compose.runOnIdle {
            generateSequence(view!!.context) { (it as? ContextWrapper)?.baseContext }
                .filterIsInstance<ComponentActivity>().first().enableEdgeToEdge()
            view!!.getWindowVisibleDisplayFrame(initial)
        }
        compose.onNodeWithText("统一认证密码").performClick()
        compose.waitUntil(5_000) {
            compose.runOnIdle { view!!.getWindowVisibleDisplayFrame(visible) }
            visible.height() < initial.height() - 100
        }
        val button = compose.onNodeWithText("登录延河课堂")
        button.performScrollTo().assertIsEnabled().assertIsDisplayed()
        val bounds = button.fetchSemanticsNode().boundsInRoot
        val location = IntArray(2)
        compose.runOnIdle { view!!.getLocationOnScreen(location); view!!.getWindowVisibleDisplayFrame(visible) }
        capture("qa-cas-keyboard.png")
        assertTrue("Submit bottom ${bounds.bottom + location[1]} must stay above keyboard top ${visible.bottom}", bounds.bottom + location[1] <= visible.bottom)
        button.performClick()
        compose.runOnIdle { assertEquals(1, submits) }
        compose.waitUntil(5_000) {
            compose.runOnIdle { view!!.getWindowVisibleDisplayFrame(visible) }
            visible.height() >= initial.height() - 10
        }
    }

    private fun assertShortLayout(dark: Boolean) = withFixture { fixture ->
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.3f)) {
                CorrectionNotebookTheme(darkTheme = dark) {
                    Box(Modifier.safeDrawingPadding().width(320.dp).height(320.dp).testTag("auth-viewport")) {
                        CasScreen(viewModel = fixture.vm, onBackButtonClick = {})
                    }
                }
            }
        }
        val button = compose.onNodeWithText("登录延河课堂")
        button.performScrollTo().assertIsDisplayed().assertIsEnabled()
        val viewport = compose.onNodeWithTag("auth-viewport").fetchSemanticsNode().boundsInRoot
        val bounds = button.fetchSemanticsNode().boundsInRoot
        capture("qa-cas-${if (dark) "dark" else "light"}.png")
        assertTrue("Submit bounds $bounds must be inside viewport $viewport",
            bounds.left >= viewport.left && bounds.top >= viewport.top && bounds.right <= viewport.right && bounds.bottom <= viewport.bottom)
        compose.onNodeWithText("统一认证密码").performScrollTo().assertIsDisplayed()
        compose.onNodeWithContentDescription("显示密码").performClick()
        compose.onNodeWithContentDescription("隐藏密码").assertIsEnabled().performClick()
        button.performScrollTo()
    }

    private fun capture(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
        File(instrumentation.targetContext.getExternalFilesDir(null), name).outputStream().use {
            screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
        screenshot.recycle()
    }

    @Test
    fun formIsBoundedOnWideScreens() {
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(1f, 1.3f)) {
                CorrectionNotebookTheme {
                    Box(Modifier.width(700.dp)) {
                        AuthFormTemplate("统一认证", buttonText = "登录", onButtonClick = {}, inputFields = {
                            OutlinedTextField("", {}, modifier = Modifier.fillMaxWidth().testTag("account"))
                        })
                    }
                }
            }
        }
        val field = compose.onNodeWithTag("account").fetchSemanticsNode().boundsInRoot
        assertTrue("Auth form must not grow beyond its 420dp maximum", field.width <= 420f)
    }

    private fun withFixture(test: (Fixture) -> Unit) = runBlocking {
        val fixture = Fixture()
        val oldToken = fixture.tokens.getYanheLoginToken()
        val oldCredential = fixture.credentials.getCredentials()
        fixture.previousToken = oldToken
        fixture.previousCredential = oldCredential
        try {
            withContext(Dispatchers.Main) { fixture.createViewModel() }
            test(fixture)
        } finally {
            withContext(Dispatchers.Main) { fixture.store.clear() }
            fixture.network.release.countDown()
            if (oldToken == null) fixture.tokens.removeYanheLoginToken() else fixture.tokens.saveYanheLoginTokens(oldToken)
            if (oldCredential == null) fixture.credentials.removeCredentials() else fixture.credentials.saveCredentials(oldCredential)
            fixture.network.client.dispatcher.executorService.shutdownNow()
            fixture.network.client.connectionPool.evictAll()
        }
    }

    private class Fixture {
        private val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tokens = TokenManager(context)
        val credentials = CredentialManager(context)
        val auth = AuthStateManager().apply { updateState(AuthState.Unauthenticated) }
        val network = CasNetwork()
        val store = ViewModelStore()
        var previousToken: String? = null
        var previousCredential: UserCredential? = null
        lateinit var vm: RegistrationViewModel
        fun createViewModel() {
            val yanhe = YanheRepository(tokens, credentials, BitCasClient(network.client, network.challenges))
            val api = Retrofit.Builder().baseUrl("https://unused.invalid/")
                .client(OkHttpClient.Builder().addInterceptor { throw AssertionError("Unexpected video request") }.build())
                .addConverterFactory(GsonConverterFactory.create()).build().create(VideoApiService::class.java)
            val videos = VideoRepository(VideoRemoteManager(api, tokens, credentials, auth, yanhe))
            vm = RegistrationViewModel(yanhe, auth, videos).apply {
                studentId = "qa-student"
                casPassword = "qa-password"
            }
            store.put("registration", vm)
        }
    }

    private class CasNetwork {
        val challenges = CasChallengeCoordinator()
        val smsSends = AtomicInteger()
        @Volatile var requireSms = false
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val cancelled = CountDownLatch(1)
        val callbackRequests = AtomicInteger()
        @Volatile var rejectCredentials = false
        val client = OkHttpClient.Builder().eventListener(object : EventListener() {
            override fun canceled(call: Call) { cancelled.countDown() }
        }).addInterceptor { chain ->
            val request = chain.request()
            if (rejectCredentials && request.url.encodedPath == "/cas/login" && request.method == "POST") {
                return@addInterceptor Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                    .code(401).message("Unauthorized").body("Unauthorized".toResponseBody()).build()
            }
            val smsBody = when {
                requireSms && request.url.encodedPath == "/cas/login" && request.method == "POST" &&
                    (request.body as FormBody).let { form -> (0 until form.size).any { form.name(it) == "type" && form.value(it) == "UsernamePassword" } } ->
                    "<form action='${request.url}' id='secondSmsLoginForm'><p id='login-page-flowkey'>second-qa</p><p id='user-object-id'>user-qa</p></form>"
                request.url.encodedPath.endsWith("/getPhoneNumberByUserId") -> """{"data":{"tel":"opaque-phone","maskTel":"138****8000"}}"""
                request.url.encodedPath.endsWith("/sendSmsCode") -> { smsSends.incrementAndGet(); """{"code":200}""" }
                request.url.encodedPath.endsWith("/checkToken") -> {
                    val body = okio.Buffer().also { request.body!!.writeTo(it) }.readUtf8()
                    if (com.google.gson.JsonParser.parseString(body).asJsonObject.get("token").asString == "123456")
                        """{"code":200}""" else """{"code":400,"message":"验证码错误"}"""
                }
                else -> null
            }
            if (smsBody != null) return@addInterceptor Response.Builder().request(request).protocol(Protocol.HTTP_1_1)
                .code(200).message("OK").body(smsBody.toResponseBody()).build()
            ssoResponseFixture(request)?.let { return@addInterceptor it }
            val body = when (request.url.encodedPath) {
                "/v1/cas/callback" -> {
                    callbackRequests.incrementAndGet()
                    entered.countDown()
                    while (!release.await(10, TimeUnit.MILLISECONDS)) {
                        if (chain.call().isCanceled()) throw IOException("Cancelled")
                    }
                    ""
                }
                else -> throw AssertionError("Unexpected CAS request: ${request.url}")
            }
            val finalRequest = if (request.url.encodedPath == "/v1/cas/callback") {
                request.newBuilder().url(request.url.newBuilder().addQueryParameter("token", "qa-token").build()).build()
            } else request
            Response.Builder().request(finalRequest).protocol(Protocol.HTTP_1_1).code(200)
                .message("OK").body(body.toResponseBody()).build()
        }.build()
    }
}
