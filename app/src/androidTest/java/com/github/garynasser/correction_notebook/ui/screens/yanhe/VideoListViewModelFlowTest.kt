package com.github.garynasser.correction_notebook.ui.screens.yanhe

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.local.CredentialManager
import com.github.garynasser.correction_notebook.data.local.TokenManager
import com.github.garynasser.correction_notebook.data.model.common.ApiResponse
import com.github.garynasser.correction_notebook.data.model.yanhe.CourseSection
import com.github.garynasser.correction_notebook.data.model.yanhe.Video
import com.github.garynasser.correction_notebook.data.remote.api.VideoApiService
import com.github.garynasser.correction_notebook.data.remote.cas.BitCasClient
import com.github.garynasser.correction_notebook.data.remote.manager.VideoRemoteManager
import com.github.garynasser.correction_notebook.data.repository.AuthStateManager
import com.github.garynasser.correction_notebook.data.repository.CourseLearningRepository
import com.github.garynasser.correction_notebook.data.repository.VideoRepository
import com.github.garynasser.correction_notebook.data.repository.YanheRepository
import com.google.gson.Gson
import com.google.gson.JsonElement
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class VideoListViewModelFlowTest {
    @Test
    fun refreshingCancelsPendingPlaybackAndLateResponseDoesNotOpenPlayer() = withViewModel { vm, api, _ ->
        val gate = CompletableDeferred<Unit>()
        api.detailGate = gate
        withContext(Dispatchers.Main) { vm.playSection(api.section, preferScreen = true) }
        api.detailEntered.await()
        withContext(Dispatchers.Main) {
            vm.getVideoList(vm.courseId)
            assertTrue(vm.playState is PlayState.Idle)
        }
        gate.complete(Unit)
        api.refreshReturned.await()
        withContext(Dispatchers.Main) {
            assertTrue(vm.uiState is VideoUIState.Success)
            assertTrue("A stale video request must not reopen the player", vm.playState is PlayState.Idle)
        }
    }

    @Test
    fun localProgressReadFailureDoesNotReplaceTheLoadedVideoList() = withViewModel(readFailure = true) { vm, _, _ ->
        withContext(Dispatchers.Main) {
            assertTrue("Ancillary progress failure must not hide available videos", vm.uiState is VideoUIState.Success)
            assertTrue(vm.sectionActionMessage.orEmpty().contains("学习进度加载失败"))
        }
    }

    @Test
    fun slowLearningWriteDoesNotBlockTheNextPlaybackChoice() = withViewModel { vm, api, store ->
        val gate = CompletableDeferred<Unit>()
        store.writeGate = gate
        val first = api.section.copy(videos = listOf(Video(mainUrl = "https://media.example/first.mp4")))
        val second = first.copy(id = first.id + 1, videos = listOf(Video(mainUrl = "https://media.example/second.mp4")))
        withContext(Dispatchers.Main) { vm.playSection(first, false) }
        store.writeEntered.await()
        withContext(Dispatchers.Main) {
            vm.resetPlayState()
            vm.playSection(second, false)
            assertEquals("https://media.example/second.mp4", (vm.playState as? PlayState.Success)?.url)
        }
        gate.complete(Unit)
    }

    @Test
    fun existingGenericVideoPathDoesNotNeedAnotherDetailRequest() = withViewModel { vm, api, _ ->
        val section = api.section.copy(videos = listOf(Video(path = "https://media.example/lecture.mp4")))
        api.failDetails = true
        withContext(Dispatchers.Main) {
            vm.playSection(section, false)
            assertEquals("https://media.example/lecture.mp4", (vm.playState as? PlayState.Success)?.url)
        }
        assertEquals(0, api.detailCalls)
    }

    @Test
    fun cancellingLookupReturnsToIdleWithoutOpeningTheLateVideo() = withViewModel { vm, api, _ ->
        val gate = CompletableDeferred<Unit>()
        api.detailGate = gate
        withContext(Dispatchers.Main) { vm.playSection(api.section, true) }
        api.detailEntered.await()
        withContext(Dispatchers.Main) { vm.cancelPlayback() }
        api.detailFinished.await()
        gate.complete(Unit)
        withContext(Dispatchers.Main) { assertTrue(vm.playState is PlayState.Idle) }
    }

    @Test
    fun completionWriteRejectsRepeatedTapsAndCanBeUndone() = withViewModel { vm, api, store ->
        val gate = CompletableDeferred<Unit>()
        store.writeGate = gate
        withContext(Dispatchers.Main) {
            vm.setSectionCompleted(api.section, true)
            vm.setSectionCompleted(api.section, true)
            assertEquals(1, store.writes)
            assertTrue(api.section.id in vm.updatingCompletionSectionIds)
        }
        gate.complete(Unit)
        store.writeReturned.await()
        withContext(Dispatchers.Main) {
            assertTrue(api.section.id in vm.progress!!.completedSectionIds)
            assertTrue(vm.updatingCompletionSectionIds.isEmpty())
            vm.setSectionCompleted(api.section, false)
            assertFalse(api.section.id in vm.progress!!.completedSectionIds)
            assertEquals(2, store.writes)
        }
    }

    private fun withViewModel(
        readFailure: Boolean = false,
        test: suspend (VideoListViewModel, FakeApi, MemoryPreferences) -> Unit
    ) = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tokens = TokenManager(context)
        val credentials = CredentialManager(context)
        val oldToken = tokens.getYanheLoginToken()
        tokens.saveYanheLoginTokens("video-flow-test")
        val api = FakeApi()
        val store = MemoryPreferences(readFailure)
        val remote = VideoRemoteManager(api, tokens, credentials, AuthStateManager(),
            YanheRepository(tokens, credentials, BitCasClient(OkHttpClient())))
        val viewModels = ViewModelStore()
        try {
            withTimeout(15_000) {
                val vm = withContext(Dispatchers.Main) {
                    VideoListViewModel(
                        SavedStateHandle(mapOf("courseId" to api.section.courseId, "courseName" to "Flow test")),
                        VideoRepository(remote), CourseLearningRepository(store)
                    ).also { viewModels.put("video-flow", it) }
                }
                api.listReturned.await()
                // Drain the main queue after the repository's response and progress read.
                withContext(Dispatchers.Main) { }
                test(vm, api, store)
            }
        } finally {
            withContext(Dispatchers.Main) { viewModels.clear() }
            if (oldToken == null) tokens.removeYanheLoginToken() else tokens.saveYanheLoginTokens(oldToken)
        }
    }

    private class MemoryPreferences(private val readFailure: Boolean) : DataStore<Preferences> {
        private val values = MutableStateFlow(emptyPreferences())
        var writeGate: CompletableDeferred<Unit>? = null
        val writeEntered = CompletableDeferred<Unit>()
        val writeReturned = CompletableDeferred<Unit>()
        var writes = 0
        override val data: Flow<Preferences> = flow {
            if (readFailure) throw SecurityException("Local progress unavailable")
            emitAll(values)
        }
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            writes++
            writeEntered.complete(Unit)
            writeGate?.await()
            return transform(values.value).also { values.value = it; writeReturned.complete(Unit) }
        }
    }

    private class FakeApi : VideoApiService by unusedApi() {
        val section = CourseSection(id = 987651, courseId = 98765, title = "Flow test lesson")
        val listReturned = CompletableDeferred<Unit>()
        val refreshReturned = CompletableDeferred<Unit>()
        val detailEntered = CompletableDeferred<Unit>()
        val detailFinished = CompletableDeferred<Unit>()
        var detailGate: CompletableDeferred<Unit>? = null
        var failDetails = false
        var detailCalls = 0
        private var listCalls = 0
        override suspend fun getCourseSession(
            token: String, courseId: Int, withPage: Boolean?, page: Int?, pageSize: Int?,
            orderType: String?, orderTypeWeight: String?
        ): ApiResponse<JsonElement> {
            assertEquals("Bearer video-flow-test", token)
            if (++listCalls == 1) listReturned.complete(Unit) else refreshReturned.complete(Unit)
            return ApiResponse(0, data = Gson().toJsonTree(listOf(section)))
        }
        override suspend fun getCourseSessionDetail(token: String, sessionId: Int, withVideo: Boolean): ApiResponse<JsonElement> {
            detailCalls++
            detailEntered.complete(Unit)
            try {
                detailGate?.await()
                if (failDetails) throw IllegalStateException("Unexpected detail request")
                return ApiResponse(0, data = Gson().toJsonTree(section.copy(videos = listOf(Video(vgaUrl = "https://media.example/screen.mp4")))))
            } finally {
                detailFinished.complete(Unit)
            }
        }
    }

    companion object {
        private fun unusedApi(): VideoApiService = Retrofit.Builder().baseUrl("https://unused.invalid/")
            .client(OkHttpClient.Builder().addInterceptor { throw AssertionError("No real network call is allowed in this test") }.build())
            .addConverterFactory(GsonConverterFactory.create()).build().create(VideoApiService::class.java)
    }
}
