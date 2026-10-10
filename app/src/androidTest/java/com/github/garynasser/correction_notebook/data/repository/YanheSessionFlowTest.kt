package com.github.garynasser.correction_notebook.data.repository

import androidx.test.platform.app.InstrumentationRegistry
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.github.garynasser.correction_notebook.data.local.CredentialManager
import com.github.garynasser.correction_notebook.data.local.TokenManager
import com.github.garynasser.correction_notebook.data.model.auth.UserCredential
import com.github.garynasser.correction_notebook.data.model.auth.AuthState
import com.github.garynasser.correction_notebook.data.model.common.ApiResponse
import com.github.garynasser.correction_notebook.data.model.yanhe.VideoTokenResponse
import com.github.garynasser.correction_notebook.data.model.yanhe.CourseSection
import com.github.garynasser.correction_notebook.data.model.yanhe.Video
import com.github.garynasser.correction_notebook.data.remote.api.VideoApiService
import com.github.garynasser.correction_notebook.data.remote.cas.BitCasClient
import com.github.garynasser.correction_notebook.data.remote.cas.ssoResponseFixture
import com.github.garynasser.correction_notebook.data.remote.manager.VideoRemoteManager
import com.github.garynasser.correction_notebook.ui.screens.yanhe.CourseListViewModel
import com.github.garynasser.correction_notebook.ui.screens.yanhe.CourseUiState
import com.github.garynasser.correction_notebook.ui.screens.yanhe.VideoListViewModel
import com.github.garynasser.correction_notebook.ui.screens.yanhe.VideoUIState
import com.github.garynasser.correction_notebook.ui.screens.yanhe.PlayState
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import com.google.gson.Gson
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

class YanheSessionFlowTest {
    @Test fun aPendingLoginCannotRestoreCredentialsAfterLogout() = withFixture { f ->
        val pending = async { runCatching { f.yanhe.authenticateStudent(OLD) } }
        f.cas.awaitOld()
        f.yanhe.clearYanheSession()
        f.cas.release.countDown()
        assertTrue(pending.await().exceptionOrNull() is CancellationException)
        assertNull(f.credentials.getCredentials())
        assertNull(f.tokens.getYanheLoginToken())
    }

    @Test fun aPendingRefreshCannotRestoreTheLoggedOutToken() = withFixture { f ->
        val pending = async { runCatching { f.yanhe.getYanheLoginToken() } }
        f.cas.awaitOld()
        f.yanhe.clearYanheSession()
        f.cas.release.countDown()
        assertTrue(pending.await().exceptionOrNull() is CancellationException)
        assertNull(f.credentials.getCredentials())
        assertNull(f.tokens.getYanheLoginToken())
    }

    @Test fun oldLoginCannotOverwriteANewerSuccessfulLogin() = withFixture { f ->
        val pending = async { runCatching { f.yanhe.authenticateStudent(OLD) } }
        f.cas.awaitOld()
        assertEquals("cas-qa-new", f.yanhe.authenticateStudent(NEW).getOrThrow())
        f.cas.release.countDown()
        assertTrue(pending.await().exceptionOrNull() is CancellationException)
        assertEquals(NEW, f.credentials.getCredentials())
        assertEquals("cas-qa-new", f.tokens.getYanheLoginToken())
    }

    @Test fun clearingTheVideoCacheRejectsLateBadgeAndUsesANewBadgeNextTime() = withFixture { f ->
        val gate = Gate()
        f.api.userGate = gate
        val pending = async { runCatching { f.videos.getFreshVideoToken() } }
        gate.entered.await()
        f.videos.clearSessionCache()
        f.api.userGate = null
        gate.release.complete(Unit)
        assertTrue(pending.await().exceptionOrNull() is CancellationException)
        assertTrue(f.api.videoCalls.isEmpty())
        assertTrue(f.videos.getFreshVideoToken().startsWith("video-session-old"))
        assertEquals(2, f.api.userCalls.size)
    }

    @Test fun clearingTheVideoCacheRejectsALateVideoToken() = withFixture { f ->
        val gate = Gate()
        f.api.videoGate = gate
        val pending = async { runCatching { f.videos.getFreshVideoToken() } }
        gate.entered.await()
        f.videos.clearSessionCache()
        f.api.videoGate = null
        gate.release.complete(Unit)
        assertTrue(pending.await().exceptionOrNull() is CancellationException)
        f.videos.getFreshVideoToken()
        assertEquals(2, f.api.userCalls.size)
        assertEquals(2, f.api.videoCalls.size)
    }

    @Test fun changingAccountCannotReturnOrCacheAnOldVideoToken() = withFixture { f ->
        val gate = Gate()
        f.api.videoGate = gate
        val pending = async { runCatching { f.videos.getFreshVideoToken() } }
        gate.entered.await()
        f.yanhe.authenticateStudent(NEW).getOrThrow()
        f.api.videoGate = null
        gate.release.complete(Unit)
        assertTrue(pending.await().exceptionOrNull() is CancellationException)
        assertTrue(f.videos.getFreshVideoToken().startsWith("video-cas-qa-new"))
        assertEquals("badge-cas-qa-new", f.api.videoCalls.last().second)
    }

    @Test fun cachedVideoAuthorizationIsNotReusedAfterReauthentication() = withFixture { f ->
        val old = f.videos.getFreshVideoToken()
        assertEquals(old, f.videos.getFreshVideoToken())
        assertEquals(1, f.api.videoCalls.size)
        f.yanhe.authenticateStudent(NEW).getOrThrow()
        assertTrue(f.videos.getFreshVideoToken().startsWith("video-cas-qa-new"))
        assertEquals(2, f.api.userCalls.size)
    }

    @Test fun concurrentVideoAuthorizationUsesOneNetworkRequest() = withFixture { f ->
        val gate = Gate()
        f.api.videoGate = gate
        val first = async { f.videos.getFreshVideoToken() }
        gate.entered.await()
        val second = async(start = CoroutineStart.UNDISPATCHED) { f.videos.getFreshVideoToken() }
        assertEquals(1, f.api.videoCalls.size)
        gate.release.complete(Unit)
        assertEquals(first.await(), second.await())
        assertEquals(1, f.api.userCalls.size)
        assertEquals(1, f.api.videoCalls.size)
    }

    @Test fun oldUnauthorizedResponseCannotRetryUsingTheNewAccount() = withFixture { f ->
        val gate = Gate()
        f.api.courseGates["session-old"] = gate
        f.api.rejectOldCourse = true
        val pending = async { runCatching { f.remote.getPrivateCourseList(1, 10) } }
        gate.entered.await()
        f.yanhe.authenticateStudent(NEW).getOrThrow()
        gate.release.complete(Unit)
        assertTrue(pending.await().exceptionOrNull() is CancellationException)
        assertEquals(listOf("session-old"), f.api.courseCalls.toList())
        assertEquals(NEW, f.credentials.getCredentials())
        assertEquals("cas-qa-new", f.tokens.getYanheLoginToken())
    }

    @Test fun reauthenticationReloadsPersonalCoursesEvenWhenAuthStateIsAlreadyAuthenticated() = withFixture { f ->
        val vm = f.createCourseViewModel()
        awaitCourses(vm, "session-old")
        f.yanhe.authenticateStudent(NEW).getOrThrow()
        awaitCourses(vm, "cas-qa-new")
        assertEquals(listOf("session-old", "cas-qa-new"), f.api.courseCalls.toList())
    }

    @Test fun failedLoginWritePreservesBothOldCredentialsAndToken() = withFixture { f ->
        f.writes.failNext.set(true)
        assertTrue(f.yanhe.authenticateStudent(NEW).isFailure)
        assertEquals(OLD, f.credentials.getCredentials())
        assertEquals("session-old", f.tokens.getYanheLoginToken())
        assertEquals(0L, f.yanhe.sessionVersion)
        assertTrue(f.yanhe.authenticateStudent(NEW).isSuccess)
    }

    @Test fun failedCredentialWriteRollsBackTheNewToken() = withFixture { f ->
        f.encrypted.failNext.set(true)
        assertTrue(f.yanhe.authenticateStudent(NEW).isFailure)
        assertEquals(OLD, f.credentials.getCredentials())
        assertEquals("session-old", f.tokens.getYanheLoginToken())
        assertEquals(0L, f.yanhe.sessionVersion)
    }

    @Test fun cancellingDuringATokenWriteDoesNotLeaveAHalfCommittedLogin() = withFixture { f ->
        val gate = Gate()
        f.writes.gate = gate
        val login = launch { f.yanhe.authenticateStudent(NEW) }
        gate.entered.await()
        login.cancel()
        gate.release.complete(Unit)
        login.join()
        assertEquals(OLD, f.credentials.getCredentials())
        assertEquals("session-old", f.tokens.getYanheLoginToken())
        assertEquals(0L, f.yanhe.sessionVersion)
    }

    @Test fun failedCredentialCleanupStillRemovesTheTokenAndAllowsRetry() = withFixture { f ->
        f.encrypted.failNext.set(true)
        assertTrue(runCatching { f.yanhe.clearYanheSession() }.isFailure)
        assertNull(f.tokens.getYanheLoginToken())
        assertNull(f.yanhe.getStudentCredential())
        assertTrue(f.yanhe.getYanheLoginToken().isFailure)
        f.yanhe.clearYanheSession()
        assertNull(f.credentials.getCredentials())
    }

    @Test fun logoutInvalidatesAWarmVideoCacheWithoutDependingOnScreenCallbacks() = withFixture { f ->
        f.videos.getFreshVideoToken()
        f.yanhe.clearYanheSession()
        assertTrue(runCatching { f.videos.getYanheAuthData("https://media.example/lesson.mp4") }.isFailure)
        assertEquals(1, f.api.videoCalls.size)
    }

    @Test fun sessionChangeEndsPendingPlaybackLoadingAndTheNextChoiceWorks() = withFixture { f ->
        val vm = f.createVideoViewModel()
        withTimeout(3_000) { while (!withContext(Dispatchers.Main) { vm.uiState is VideoUIState.Success }) delay(10) }
        val gate = Gate()
        f.api.detailGate = gate
        withContext(Dispatchers.Main) { vm.playSection(f.api.section, true) }
        gate.entered.await()
        f.yanhe.authenticateStudent(NEW).getOrThrow()
        gate.release.complete(Unit)
        withTimeout(3_000) { while (!withContext(Dispatchers.Main) { vm.playState is PlayState.Idle }) delay(10) }
        f.api.detailGate = null
        withContext(Dispatchers.Main) { vm.playSection(f.api.section, true) }
        withTimeout(3_000) { while (!withContext(Dispatchers.Main) { vm.playState is PlayState.Success }) delay(10) }
        assertEquals("https://media.example/cas-qa-new.mp4", (vm.playState as PlayState.Success).url)
    }

    @Test fun sessionChangeEndsVideoListLoadingAndReloadWorks() = withFixture { f ->
        val gate = Gate()
        f.api.sectionGate = gate
        val vm = f.createVideoViewModel()
        gate.entered.await()
        f.yanhe.authenticateStudent(NEW).getOrThrow()
        gate.release.complete(Unit)
        withTimeout(3_000) { while (!withContext(Dispatchers.Main) { vm.uiState is VideoUIState.Error }) delay(10) }
        f.api.sectionGate = null
        withContext(Dispatchers.Main) { vm.getVideoList(12) }
        withTimeout(3_000) { while (!withContext(Dispatchers.Main) { vm.uiState is VideoUIState.Success }) delay(10) }
    }

    private fun withFixture(test: suspend CoroutineScope.(Fixture) -> Unit) = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.cacheDir, "session-qa-${UUID.randomUUID()}.preferences_pb")
        val preferencesName = "credential-qa-${UUID.randomUUID()}"
        val dataScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val data = PreferenceDataStoreFactory.create(scope = dataScope, produceFile = { file })
        val encrypted = EncryptedSharedPreferences.create(context, preferencesName,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
        val f = Fixture(ControlledPreferences(data), ControlledCredentials(encrypted))
        try {
            f.tokens.saveYanheLoginTokens("session-old")
            f.credentials.saveCredentials(OLD)
            withTimeout(15_000) { test(f) }
        } finally {
            withContext(Dispatchers.Main) { f.models.clear() }
            f.jobs.forEach { it.join() }
            f.cas.release.countDown()
            f.cas.client.dispatcher.executorService.shutdownNow()
            f.cas.client.connectionPool.evictAll()
            dataScope.coroutineContext[Job]?.cancelAndJoin()
            file.delete()
            encrypted.edit().clear().commit()
            context.deleteSharedPreferences(preferencesName)
        }
    }

    private suspend fun awaitCourses(vm: CourseListViewModel, name: String) = withTimeout(3_000) {
        while (!withContext(Dispatchers.Main) { (vm.uiState as? CourseUiState.Success)?.courses?.any { it.nameZh == name } == true }) delay(10)
    }

    private class Fixture(val writes: ControlledPreferences, val encrypted: ControlledCredentials) {
        val tokens = TokenManager(writes)
        val credentials = CredentialManager(encrypted)
        val cas = CasNetwork()
        val api = FakeVideoApi()
        val models = ViewModelStore()
        val jobs = mutableListOf<Job>()
        val auth = AuthStateManager().apply { updateState(AuthState.Authenticated) }
        val yanhe = YanheRepository(tokens, credentials, BitCasClient(cas.client))
        val remote = VideoRemoteManager(api, tokens, credentials, auth, yanhe)
        val videos = VideoRepository(remote)
        suspend fun createCourseViewModel(): CourseListViewModel = withContext(Dispatchers.Main) {
            CourseListViewModel(yanhe, auth, videos, CourseLearningRepository(writes)).also {
                jobs.add(it.viewModelScope.coroutineContext[Job]!!); models.put("courses", it)
            }
        }
        suspend fun createVideoViewModel(): VideoListViewModel = withContext(Dispatchers.Main) {
            VideoListViewModel(SavedStateHandle(mapOf("courseId" to 12, "courseName" to "Session QA")),
                videos, CourseLearningRepository(writes)).also {
                jobs.add(it.viewModelScope.coroutineContext[Job]!!); models.put("videos", it)
            }
        }
    }

    private class Gate {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        suspend fun await() { entered.complete(Unit); release.await() }
    }

    private class FakeVideoApi : VideoApiService by unusedApi() {
        val section = CourseSection(id = 34, courseId = 12, title = "Session QA lesson")
        var sectionGate: Gate? = null
        var detailGate: Gate? = null
        var userGate: Gate? = null
        var videoGate: Gate? = null
        val userCalls = CopyOnWriteArrayList<String>()
        val videoCalls = CopyOnWriteArrayList<Pair<String, String>>()
        val courseCalls = CopyOnWriteArrayList<String>()
        val courseGates = java.util.concurrent.ConcurrentHashMap<String, Gate>()
        var rejectOldCourse = false
        override suspend fun getYanheUser(token: String): ApiResponse<JsonElement> {
            val id = token.removePrefix("Bearer ")
            userCalls.add(id)
            userGate?.await()
            return ApiResponse(0, data = JsonParser.parseString("{\"badge\":\"badge-$id\"}"))
        }
        override suspend fun getVideoToken(token: String, id: String): ApiResponse<VideoTokenResponse> {
            val login = token.removePrefix("Bearer ")
            videoCalls.add(login to id)
            videoGate?.await()
            return ApiResponse(0, data = VideoTokenResponse("video-$login-$id", System.currentTimeMillis() / 1000 + 3600))
        }
        override suspend fun getPrivateCourseList(token: String, page: Int, pageSize: Int, type: Int): ApiResponse<JsonElement> {
            val login = token.removePrefix("Bearer ")
            courseCalls.add(login)
            courseGates[login]?.await()
            if (rejectOldCourse && login == "session-old") throw HttpException(Response.error<JsonElement>(401, "{}".toResponseBody()))
            return ApiResponse(0, data = JsonParser.parseString("[{\"id\":1,\"name_zh\":\"$login\",\"semester\":\"2026 秋季\"}]"))
        }
        override suspend fun getCourseSession(token: String, courseId: Int, withPage: Boolean?, page: Int?, pageSize: Int?,
            orderType: String?, orderTypeWeight: String?): ApiResponse<JsonElement> {
            sectionGate?.await()
            return ApiResponse(0, data = Gson().toJsonTree(listOf(section)))
        }
        override suspend fun getCourseSessionDetail(token: String, sessionId: Int, withVideo: Boolean): ApiResponse<JsonElement> {
            detailGate?.await()
            return ApiResponse(0, data = Gson().toJsonTree(section.copy(videos = listOf(
                Video(mainUrl = "https://media.example/${token.removePrefix("Bearer ")}.mp4")))))
        }
    }

    private class ControlledPreferences(private val delegate: DataStore<Preferences>) : DataStore<Preferences> by delegate {
        val failNext = AtomicBoolean()
        var gate: Gate? = null
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            if (failNext.getAndSet(false)) throw IOException("Token write failed")
            val pending = gate.also { gate = null }
            return if (pending == null) delegate.updateData(transform) else withContext(NonCancellable) {
                pending.await()
                delegate.updateData(transform)
            }
        }
    }

    private class ControlledCredentials(private val delegate: SharedPreferences) : SharedPreferences by delegate {
        val failNext = AtomicBoolean()
        override fun edit(): SharedPreferences.Editor {
            val editor = delegate.edit()
            return object : SharedPreferences.Editor by editor {
                override fun apply() {
                    if (failNext.getAndSet(false)) throw IOException("Credential write failed")
                    editor.apply()
                }
            }
        }
    }

    private class CasNetwork {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        suspend fun awaitOld() = withContext(Dispatchers.IO) { assertTrue(entered.await(5, TimeUnit.SECONDS)) }
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            ssoResponseFixture(request)?.let { return@addInterceptor it }
            val (body, callbackToken) = when {
                request.url.encodedPath == "/v1/cas/callback" -> {
                    val id = request.url.queryParameter("ticket")!!.removePrefix("ST-")
                    if (id == OLD.studentId) {
                        entered.countDown()
                        while (!release.await(10, TimeUnit.MILLISECONDS)) {
                            if (chain.call().isCanceled()) throw IOException("Cancelled")
                        }
                    }
                    "" to "cas-$id"
                }
                else -> throw AssertionError("Unexpected CAS endpoint: ${request.url.encodedPath}")
            }
            val responseRequest = if (callbackToken == null) request else request.newBuilder()
                .url(request.url.newBuilder().addQueryParameter("token", callbackToken).build()).build()
            okhttp3.Response.Builder().request(responseRequest).protocol(Protocol.HTTP_1_1).code(200)
                .message("OK").body(body.toResponseBody()).build()
        }.build()
    }

    companion object {
        private val OLD = UserCredential("qa-old", "old-password")
        private val NEW = UserCredential("qa-new", "new-password")
        private fun unusedApi(): VideoApiService = Retrofit.Builder().baseUrl("https://unused.invalid/")
            .client(OkHttpClient.Builder().addInterceptor { throw AssertionError("Unexpected external request") }.build())
            .addConverterFactory(GsonConverterFactory.create()).build().create(VideoApiService::class.java)
    }
}
