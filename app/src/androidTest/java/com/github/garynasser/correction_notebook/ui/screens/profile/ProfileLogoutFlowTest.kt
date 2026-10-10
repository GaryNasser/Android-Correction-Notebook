package com.github.garynasser.correction_notebook.ui.screens.profile

import android.content.SharedPreferences
import android.graphics.Bitmap
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.espresso.Espresso
import androidx.test.espresso.action.ViewActions
import androidx.test.espresso.matcher.RootMatchers
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.local.AISettingsManager
import com.github.garynasser.correction_notebook.data.local.CredentialManager
import com.github.garynasser.correction_notebook.data.local.TokenManager
import com.github.garynasser.correction_notebook.data.local.ai.AiCredentialCipher
import com.github.garynasser.correction_notebook.data.local.ai.AiDatabase
import com.github.garynasser.correction_notebook.data.model.auth.AuthState
import com.github.garynasser.correction_notebook.data.model.auth.UserCredential
import com.github.garynasser.correction_notebook.data.model.home.ScheduleEvent
import com.github.garynasser.correction_notebook.data.model.home.TodoItem
import com.github.garynasser.correction_notebook.data.remote.ai.AnthropicCompatibleAdapter
import com.github.garynasser.correction_notebook.data.remote.ai.OpenAiCompatibleAdapter
import com.github.garynasser.correction_notebook.data.remote.api.AIApiService
import com.github.garynasser.correction_notebook.data.remote.api.VideoApiService
import com.github.garynasser.correction_notebook.data.remote.cas.BitCasClient
import com.github.garynasser.correction_notebook.data.remote.manager.VideoRemoteManager
import com.github.garynasser.correction_notebook.data.repository.*
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import com.google.gson.Gson
import java.io.File
import java.io.IOException
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ProfileLogoutFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun confirmationRestoresWithoutSigningOutAndBothCancelActionsKeepTheSession() = withFixture { f ->
        val restoration = StateRestorationTester(compose)
        render(f, restoration)
        openLogout()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(WARNING).assertIsDisplayed()
        compose.onNodeWithText("退出").assertIsDisplayed()
        assertEquals("qa-session", f.tokens.getYanheLoginToken())
        assertTrue(f.auth.authState.value is AuthState.Authenticated)
        compose.onNodeWithText("取消").performClick()
        openLogout()
        Espresso.onView(ViewMatchers.isRoot()).inRoot(RootMatchers.isDialog()).perform(ViewActions.pressBack())
        compose.onNodeWithText(WARNING).assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(WARNING).assertDoesNotExist()
        assertEquals("qa-session", f.tokens.getYanheLoginToken())
        assertNotNull(f.credentials.getCredentials())
    }

    @Test fun completeWarningAndActionsRemainReachableAtTheActualSystemFontScale() = withFixture { f ->
        render(f)
        openLogout()
        capture("layout")
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(WARNING).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { assertTrue(it(layouts)) }
        assertTrue(layouts.isNotEmpty())
        layouts.forEach { layout ->
            assertFalse("The logout warning must be complete", layout.didOverflowHeight)
            assertEquals(WARNING.length, layout.getLineEnd(layout.lineCount - 1))
            assertEquals(f.context.resources.configuration.fontScale, layout.layoutInput.density.fontScale, 0.01f)
            repeat(layout.lineCount) { line ->
                assertFalse(layout.isLineEllipsized(line))
                assertTrue(layout.getLineRight(line) <= layout.size.width + 1f)
            }
        }
        val scrolling = hasScrollAction() and hasAnyAncestor(isDialog())
        if (compose.onAllNodes(scrolling).fetchSemanticsNodes().isNotEmpty()) {
            compose.onNode(scrolling).performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
        }
        compose.waitForIdle()
        val text = compose.onNodeWithText(WARNING).fetchSemanticsNode()
        val viewport = if (compose.onAllNodes(scrolling).fetchSemanticsNodes().isNotEmpty())
            compose.onNode(scrolling).fetchSemanticsNode().boundsInRoot else text.boundsInRoot
        val actionsTop = compose.onNodeWithText("退出").fetchSemanticsNode().boundsInRoot.top
        layouts.forEach { layout ->
            val last = layout.lineCount - 1
            assertTrue(text.positionInRoot.y + layout.getLineTop(last) >= viewport.top - 2f)
            assertTrue(text.positionInRoot.y + layout.getLineBottom(last) <= viewport.bottom + 2f)
            assertTrue("The warning cannot cover the actions", text.positionInRoot.y + layout.getLineBottom(last) <= actionsTop)
        }
        listOf("退出", "取消").forEach {
            val bounds = compose.onNodeWithText(it).assertIsDisplayed().assertIsEnabled().fetchSemanticsNode().touchBoundsInRoot
            val minimum = 48f * f.context.resources.displayMetrics.density
            assertTrue("$it must have a 48dp touch target", bounds.height + 1f >= minimum && bounds.width + 1f >= minimum)
        }
        capture("scrolled")
        compose.onNodeWithText("取消").performClick()
        assertEquals("qa-session", f.tokens.getYanheLoginToken())
    }

    @Test fun pendingLogoutOnlySubmitsOnceAndPreservesLocalCoursesAndTodos() = checkLogout(false)
    @Test fun failedCredentialCleanupStillSignsOutAndExplainsThePartialFailure() = checkLogout(true)

    private fun checkLogout(failCleanup: Boolean) = withFixture { f ->
        render(f)
        val before = f.data.data.first().asMap()
        val aiBefore = f.settings.aiEnabled.first()
        val writes = f.data.attempts.get()
        val gate = CompletableDeferred<Unit>().also { f.data.gate = it }
        f.encrypted.failCleanup = failCleanup
        openLogout()
        compose.onNodeWithText("退出").performClick()
        compose.waitUntil(5_000) { f.data.attempts.get() == writes + 1 }
        compose.onNodeWithContentDescription("退出延河课堂").assertIsNotEnabled()
        compose.runOnIdle { f.vm.logout(); f.vm.logout() }
        assertEquals(writes + 1, f.data.attempts.get())
        gate.complete(Unit)
        compose.waitUntil(5_000) { !f.vm.isLoading.value && f.auth.authState.value is AuthState.Unauthenticated }
        compose.onNodeWithText("未登录").assertIsDisplayed()
        val message = if (failCleanup) "已退出，部分登录缓存清理失败" else "已退出延河课堂"
        compose.onNodeWithText(message).assertIsDisplayed()
        assertNull(f.tokens.getYanheLoginToken())
        assertNull(f.yanhe.getStudentCredential())
        assertEquals(before.filterKeys { it.name != "yanhe_login_key" }, f.data.data.first().asMap())
        assertEquals(aiBefore, f.settings.aiEnabled.first())
        assertEquals(listOf(f.todo), f.todos.todoItems.first())
        assertEquals(listOf(f.course), f.schedules.scheduleEvents.first())
        if (failCleanup) {
            f.encrypted.failCleanup = false
            f.yanhe.clearYanheSession()
        }
        assertNull(f.credentials.getCredentials())
    }

    private fun render(f: Fixture, restoration: StateRestorationTester? = null) {
        val content: @androidx.compose.runtime.Composable () -> Unit = {
            CorrectionNotebookTheme(darkTheme = InstrumentationRegistry.getArguments().getString("qaDark") == "true",
                dynamicColor = false) { ProfileScreen(f.vm) }
        }
        if (restoration == null) compose.setContent(content) else restoration.setContent(content)
    }

    private fun openLogout() {
        compose.onNodeWithContentDescription("退出延河课堂").performClick()
        compose.onNodeWithText("退出").assertIsDisplayed()
    }

    private fun withFixture(test: suspend (Fixture) -> Unit) = runBlocking {
        val f = Fixture()
        try {
            f.tokens.saveYanheLoginTokens("qa-session")
            f.credentials.saveCredentials(UserCredential("qa-profile", "qa-password"))
            f.todos.addTodo(f.todo)
            f.schedules.addEvent(f.course)
            withContext(Dispatchers.Main) { f.create() }
            test(f)
        } finally {
            withContext(NonCancellable) {
                f.data.gate?.complete(Unit)
                val job = f.vm.viewModelScope.coroutineContext[Job]
                withContext(Dispatchers.Main) { f.store.clear() }
                job?.join()
                f.scope.coroutineContext[Job]?.cancelAndJoin()
                f.file.delete()
                f.encrypted.failCleanup = false
                f.preferences.edit().clear().commit()
                f.context.deleteSharedPreferences(f.preferencesName)
                f.database.close()
                f.network.dispatcher.executorService.shutdownNow()
                f.network.connectionPool.evictAll()
            }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        Thread.sleep(300)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "qa-profile-logout-$name.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    private class Fixture {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(context.cacheDir, "profile-logout-${UUID.randomUUID()}.preferences_pb")
        val data = ControlledWrites(PreferenceDataStoreFactory.create(scope = scope) { file })
        val tokens = TokenManager(data)
        val preferencesName = "profile-credentials-${UUID.randomUUID()}"
        val preferences = EncryptedSharedPreferences.create(context, preferencesName,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        val encrypted = ControlledCredentials(preferences)
        val credentials = CredentialManager(encrypted)
        val auth = AuthStateManager().apply { updateState(AuthState.Authenticated) }
        val settings = AISettingsManager(context)
        val network = OkHttpClient.Builder().addInterceptor { throw AssertionError("Unexpected external request") }.build()
        val yanhe = YanheRepository(tokens, credentials, BitCasClient(network))
        val database = Room.inMemoryDatabaseBuilder(context, AiDatabase::class.java).build()
        val store = ViewModelStore()
        val todos = TodoRepository(data)
        val schedules = ScheduleRepository(data)
        val todo = TodoItem(id = "local-todo", title = "复习计算理论", isCompleted = true, createdAt = 100, completedAt = 200)
        val course = ScheduleEvent(id = "local-course", title = "计算理论", location = "文萃楼 M134",
            startAt = LocalDateTime.of(2026, 10, 5, 8, 0), endAt = LocalDateTime.of(2026, 10, 5, 9, 35))
        lateinit var vm: ProfileViewModel
        fun create() {
            val retrofit = Retrofit.Builder().baseUrl("https://unused.invalid/").client(network)
                .addConverterFactory(GsonConverterFactory.create()).build()
            val providers = ProviderRepository(database.aiProviderDao(), AiCredentialCipher(context))
            val gson = Gson()
            val api = retrofit.create(AIApiService::class.java)
            val ai = AIRepository(settings, providers, OpenAiCompatibleAdapter(api, gson), AnthropicCompatibleAdapter(api, gson), gson)
            val videos = VideoRepository(VideoRemoteManager(retrofit.create(VideoApiService::class.java), tokens, credentials, auth, yanhe))
            vm = ProfileViewModel(auth, settings, providers, ai, yanhe, videos)
            store.put("profile", vm)
        }
    }

    private class ControlledWrites(private val delegate: DataStore<Preferences>) : DataStore<Preferences> by delegate {
        val attempts = AtomicInteger()
        var gate: CompletableDeferred<Unit>? = null
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            attempts.incrementAndGet()
            gate?.await()
            return delegate.updateData(transform)
        }
    }

    private class ControlledCredentials(private val delegate: SharedPreferences) : SharedPreferences by delegate {
        var failCleanup = false
        override fun edit(): SharedPreferences.Editor {
            val editor = delegate.edit()
            return object : SharedPreferences.Editor by editor {
                override fun apply() {
                    if (failCleanup) throw IOException("Credential cleanup failed")
                    editor.apply()
                }
            }
        }
    }

    companion object {
        private const val WARNING = "退出后会清除当前课程登录状态，已保存的学习数据和本地设置不会删除。"
    }
}
