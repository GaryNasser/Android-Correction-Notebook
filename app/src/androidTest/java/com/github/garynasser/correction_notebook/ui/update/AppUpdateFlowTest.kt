package com.github.garynasser.correction_notebook.ui.update

import android.content.ActivityNotFoundException
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.remote.api.UpdateApiService
import com.github.garynasser.correction_notebook.data.remote.model.GitHubReleaseDto
import com.github.garynasser.correction_notebook.data.repository.AppUpdateRepository
import com.github.garynasser.correction_notebook.data.model.appupdate.AppVersionInfo
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.Rule
import okhttp3.Call
import okhttp3.EventListener
import okhttp3.OkHttpClient
import okhttp3.ResponseBody.Companion.toResponseBody
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.IOException
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference

class AppUpdateFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun failedRecheckDoesNotLeaveAnOldDownloadOffer() = withViewModel { vm, api ->
        withContext(Dispatchers.Main) { vm.checkForUpdates(silent = false) }
        vm.uiState.first { !it.isChecking }
        assertNotNull(vm.uiState.value.availableUpdate)
        api.failure = IOException("offline")
        withContext(Dispatchers.Main) { vm.checkForUpdates(silent = false) }
        vm.uiState.first { !it.isChecking }
        assertNull(vm.uiState.value.availableUpdate)
        assertNotNull(vm.uiState.value.snackbarMessage)
    }

    @Test fun silentFailureStaysQuietAndTheNextManualCheckCanRecover() = withViewModel { vm, api ->
        api.failure = IOException("offline")
        withContext(Dispatchers.Main) { vm.checkForUpdates(silent = true) }
        vm.uiState.first { !it.isChecking }
        assertNull(vm.uiState.value.snackbarMessage)
        assertNull(vm.uiState.value.availableUpdate)
        api.failure = null
        withContext(Dispatchers.Main) { vm.checkForUpdates(silent = false) }
        vm.uiState.first { !it.isChecking }
        assertNotNull(vm.uiState.value.availableUpdate)
        assertNull(vm.uiState.value.snackbarMessage)
    }

    @Test fun repeatedTapsOnlyStartOnePendingRequest() = withViewModel { vm, api ->
        api.gate = CompletableDeferred()
        withContext(Dispatchers.Main) {
            vm.checkForUpdates(silent = false)
            vm.checkForUpdates(silent = false)
        }
        api.entered.await()
        assertEquals(1, api.calls)
        assertTrue(vm.uiState.value.isChecking)
        api.gate!!.complete(Unit)
        vm.uiState.first { !it.isChecking }
        assertNotNull(vm.uiState.value.availableUpdate)
    }

    @Test fun manualNoUpdateMessageDoesNotAppearDuringSilentChecks() = withViewModel { vm, api ->
        api.release = api.release.copy(tagName = vm.uiState.value.currentVersionName)
        withContext(Dispatchers.Main) { vm.checkForUpdates(silent = true) }
        vm.uiState.first { !it.isChecking }
        assertNull(vm.uiState.value.snackbarMessage)
        withContext(Dispatchers.Main) { vm.checkForUpdates(silent = false) }
        vm.uiState.first { !it.isChecking }
        assertNull(vm.uiState.value.availableUpdate)
        assertEquals("当前已是最新版本", vm.uiState.value.snackbarMessage)
        withContext(Dispatchers.Main) { vm.consumeSnackbarMessage() }
        assertNull(vm.uiState.value.snackbarMessage)
    }

    @Test fun serverFailuresExplainTheProblemWithoutReportingUpToDate() = withViewModel { vm, api ->
        listOf(404 to "暂未找到可用的发布版本", 403 to "更新服务暂时受限，请稍后重试",
            429 to "更新服务暂时受限，请稍后重试", 500 to "更新服务暂不可用，请稍后重试").forEach { (code, message) ->
            api.failure = HttpException(Response.error<GitHubReleaseDto>(code, "{}".toResponseBody()))
            withContext(Dispatchers.Main) { vm.checkForUpdates(silent = false) }
            vm.uiState.first { !it.isChecking }
            assertNull(vm.uiState.value.availableUpdate)
            assertEquals(message, vm.uiState.value.snackbarMessage)
        }
    }

    @Test fun malformedDownloadNeverLaunchesAndRetryKeepsTheDialogUntilSuccess() = withViewModel { vm, _ ->
        withContext(Dispatchers.Main) { vm.checkForUpdates(silent = false) }
        vm.uiState.first { !it.isChecking }
        val browser = RecordingContext()
        browser.failure = ActivityNotFoundException()
        compose.setContent {
            val state by vm.uiState.collectAsState()
            CorrectionNotebookTheme(dynamicColor = false) {
                state.availableUpdate?.let { update ->
                    AppUpdateDialog(update, state.currentVersionName, state.downloadErrorMessage, vm::dismissUpdateDialog) {
                        val error = openUpdateDownload(browser, update.downloadUrl)
                        if (error == null) vm.dismissUpdateDialog() else vm.reportDownloadFailure(error)
                    }
                }
            }
        }
        assertEquals("下载链接格式不正确", openUpdateDownload(browser, "https:/app.apk"))
        assertNull(browser.opened)
        compose.onNodeWithText("前往更新").performClick()
        compose.onNodeWithText("没有可打开下载链接的应用").assertIsDisplayed()
        assertNotNull(vm.uiState.value.availableUpdate)
        browser.failure = null
        compose.onNodeWithText("前往更新").performClick()
        compose.onNodeWithText("前往更新").assertDoesNotExist()
        assertEquals(Intent.ACTION_VIEW, browser.opened!!.action)
        assertEquals("https://example.com/releases/v99", browser.opened!!.dataString)
        assertNull(vm.uiState.value.availableUpdate)
        assertNull(vm.uiState.value.downloadErrorMessage)
    }

    @Test fun browserSecurityFailureIsRecoverable() {
        val context = RecordingContext().also { it.failure = SecurityException() }
        assertEquals("无法打开下载链接", openUpdateDownload(context, "https://example.com/app.apk"))
        assertNull(context.opened)
        context.failure = null
        assertNull(openUpdateDownload(context, "HTTPS://example.com/app.apk?signature=a%2Fb"))
        assertEquals("https", context.opened!!.data!!.scheme)
        assertEquals("a/b", context.opened!!.data!!.getQueryParameter("signature"))
    }

    @Test fun optionalUpdateCanBeDismissedButMandatoryUpdateHasNoSkipButton() {
        var dismisses = 0
        var forced by androidx.compose.runtime.mutableStateOf(true)
        compose.setContent {
            CorrectionNotebookTheme(dynamicColor = false) {
                AppUpdateDialog(versionInfo.copy(forceUpdate = forced), "1.0.2", null, { dismisses++ }, {})
            }
        }
        compose.onNodeWithText("稍后").assertDoesNotExist()
        compose.onNodeWithText("当前版本需要更新后继续使用。").assertIsDisplayed()
        compose.onNode(isDialog()).performTouchInput { click(Offset(1f, height / 2f)) }
        compose.runOnIdle { assertEquals(0, dismisses) }
        androidx.test.espresso.Espresso.pressBack()
        compose.waitForIdle()
        assertEquals(0, dismisses)
        compose.runOnIdle { forced = false }
        compose.waitForIdle()
        compose.onNode(isDialog()).performTouchInput { click(Offset(1f, height / 2f)) }
        compose.runOnIdle { assertEquals(1, dismisses) }
        compose.onNodeWithText("稍后").performClick()
        assertEquals(2, dismisses)
    }

    @Test fun lightDialogFitsNarrowScreenWithLargeText() = checkLayout(false)
    @Test fun darkDialogFitsNarrowScreenWithLargeText() = checkLayout(true)

    @Test fun stalledHttpResponseTimesOutAndCancelsTheActualNetworkCall() = runBlocking {
        val server = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))
        val socket = AtomicReference<Socket?>()
        val request = CompletableDeferred<String>()
        val failed = CompletableDeferred<IOException>()
        val client = OkHttpClient.Builder().eventListener(object : EventListener() {
            override fun callFailed(call: Call, ioe: IOException) { failed.complete(ioe) }
        }).build()
        val worker = launch(Dispatchers.IO) {
            try {
                server.accept().use { connection ->
                    socket.set(connection)
                    connection.soTimeout = 5_000
                    val input = connection.getInputStream().bufferedReader()
                    val lines = mutableListOf<String>()
                    while (true) {
                        val line = input.readLine() ?: break
                        if (line.isEmpty()) break
                        lines.add(line)
                    }
                    request.complete(lines.joinToString("\n"))
                    val output = connection.getOutputStream()
                    output.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 100000\r\nConnection: close\r\n\r\n{".toByteArray())
                    output.flush()
                    // Keep bytes arriving below the read timeout; only an overall deadline can stop this response.
                    while (isActive) {
                        delay(500)
                        output.write(" ".toByteArray())
                        output.flush()
                    }
                }
            } catch (_: IOException) { }
        }
        val store = ViewModelStore()
        try {
            withTimeout(25_000) {
                val api = Retrofit.Builder().baseUrl("http://127.0.0.1:${server.localPort}/").client(client)
                    .addConverterFactory(GsonConverterFactory.create()).build().create(UpdateApiService::class.java)
                val vm = withContext(Dispatchers.Main) {
                    AppUpdateViewModel(AppUpdateRepository(api), InstrumentationRegistry.getInstrumentation().targetContext)
                        .also { store.put("http-update", it); it.checkForUpdates(silent = false) }
                }
                assertTrue(request.await().contains("GET /repos/GaryNasser/Android-Correction-Notebook/releases/latest"))
                vm.uiState.first { !it.isChecking }
                assertEquals("检查更新超时，请重试", vm.uiState.value.snackbarMessage)
                assertNull(vm.uiState.value.availableUpdate)
                failed.await()
                withTimeout(3_000) { while (client.dispatcher.runningCallsCount() != 0) delay(10) }
            }
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
            socket.get()?.close()
            server.close()
            worker.cancelAndJoin()
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdownNow()
        }
    }

    private fun checkLayout(dark: Boolean) {
        val error = "没有可打开下载链接的应用，请安装浏览器后重试"
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3.375f, 1.3f)) {
                CorrectionNotebookTheme(darkTheme = dark, dynamicColor = false) {
                    AppUpdateDialog(versionInfo, "1.0.2", error, {}, {})
                }
            }
        }
        listOf(versionInfo.updateTitle, "当前 1.0.2", "更新到 v99.0.0", error, "前往更新", "稍后").forEach(::assertFullText)
        assertFullText(versionInfo.updateContent)
        compose.onNodeWithText("前往更新").assertIsDisplayed()
        compose.onNodeWithText(error).assertIsDisplayed()
        saveScreenshot(if (dark) "dark" else "light")
        compose.onNode(hasScrollAction()).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100000f) }
        compose.onNodeWithText("前往更新").assertIsDisplayed()
        val notes = compose.onNodeWithText(versionInfo.updateContent).fetchSemanticsNode().boundsInRoot
        val errorBounds = compose.onNodeWithText(error).fetchSemanticsNode().boundsInRoot
        assertTrue("Release notes cannot cover the download error", notes.bottom <= errorBounds.top + 1f)
        saveScreenshot(if (dark) "dark-scrolled" else "light-scrolled")
    }

    private fun assertFullText(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        layouts.forEach { layout ->
            assertFalse(text, layout.didOverflowHeight)
            assertEquals(text.length, layout.getLineEnd(layout.lineCount - 1))
            for (line in 0 until layout.lineCount) {
                assertFalse(layout.isLineEllipsized(line))
                assertTrue("Text extends beyond its width: $text", layout.getLineRight(line) <= layout.size.width + 1f)
            }
        }
    }

    private fun saveScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(350)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "qa-update-$name.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    private fun withViewModel(test: suspend (AppUpdateViewModel, FakeApi) -> Unit) = runBlocking {
        val store = ViewModelStore()
        val api = FakeApi()
        try {
            withTimeout(15_000) {
                val vm = withContext(Dispatchers.Main) {
                    AppUpdateViewModel(AppUpdateRepository(api), InstrumentationRegistry.getInstrumentation().targetContext)
                        .also { store.put("update", it) }
                }
                test(vm, api)
            }
        } finally {
            withContext(Dispatchers.Main) { store.clear() }
        }
    }

    private class FakeApi : UpdateApiService {
        var release = GitHubReleaseDto(tagName = "v99.0.0", htmlUrl = "https://example.com/releases/v99")
        var failure: Exception? = null
        var gate: CompletableDeferred<Unit>? = null
        var calls = 0
        val entered = CompletableDeferred<Unit>()
        override suspend fun getLatestVersion(): GitHubReleaseDto {
            calls++
            entered.complete(Unit)
            gate?.await()
            failure?.let { throw it }
            return release
        }
    }

    private class RecordingContext : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
        var opened: Intent? = null
        var failure: RuntimeException? = null
        override fun startActivity(intent: Intent) {
            failure?.let { throw it }
            opened = intent
        }
    }

    companion object {
        private val versionInfo = AppVersionInfo(latestVersionName = "v99.0.0", latestVersionCode = 0,
            minSupportedVersionCode = 0, downloadUrl = "https://example.com/releases/v99",
            updateTitle = "BITStudy_for_Android_v99.0.0_课程与专注体验更新",
            updateContent = (1..40).joinToString("\n") { "第 $it 项：改进课表、课程笔记和学习体验。" },
            forceUpdate = false, publishTime = 0)
    }
}
