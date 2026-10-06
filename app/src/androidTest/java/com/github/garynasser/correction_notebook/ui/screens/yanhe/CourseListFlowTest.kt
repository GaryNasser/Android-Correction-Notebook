package com.github.garynasser.correction_notebook.ui.screens.yanhe

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Rect
import android.view.View
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.local.CredentialManager
import com.github.garynasser.correction_notebook.data.local.TokenManager
import com.github.garynasser.correction_notebook.data.model.common.ApiResponse
import com.github.garynasser.correction_notebook.data.remote.api.VideoApiService
import com.github.garynasser.correction_notebook.data.remote.cas.BitCasClient
import com.github.garynasser.correction_notebook.data.remote.manager.VideoRemoteManager
import com.github.garynasser.correction_notebook.data.repository.*
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import com.google.gson.JsonElement
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList

class CourseListFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun unsubmittedSearchNeverChangesTheKeywordOfTheNextPage() = withFixture { f ->
        f.publicCourses("矩阵")
        withContext(Dispatchers.Main) {
            f.vm.updateSearchQuery("机器学习")
            f.vm.loadCourses(isNextPage = true)
        }
        f.await { f.api.publicCalls.size == 2 && !f.vm.isLoadingMore }
        assertEquals(listOf(1 to "矩阵", 2 to "矩阵"), f.api.publicCalls.toList())
    }

    @Test fun typingDuringPersonalSyncPreservesLoadingUntilTheFilteredResultArrives() = withFixture { f ->
        val gate = Gate()
        f.api.personalGate = gate
        withContext(Dispatchers.Main) { f.vm.refreshMySchedule() }
        gate.entered.await()
        withContext(Dispatchers.Main) {
            f.vm.updateSearchQuery("矩阵")
            assertTrue(f.vm.uiState is CourseUiState.Loading)
        }
        gate.release.complete(Unit)
        f.await { !f.vm.isRefreshingSchedule }
        assertEquals(listOf("矩阵分析"), (f.vm.uiState as CourseUiState.Success).courses.map { it.nameZh })
    }

    @Test fun changingSemesterDoesNotCancelPersonalSync() = withFixture { f ->
        val gate = Gate()
        f.api.personalGate = gate
        withContext(Dispatchers.Main) { f.vm.refreshMySchedule() }
        gate.entered.await()
        withContext(Dispatchers.Main) { f.vm.selectSemester("2024-2025 秋季") }
        assertFalse(gate.cancelled.isCompleted)
        gate.release.complete(Unit)
        f.await { !f.vm.isRefreshingSchedule }
        assertEquals(listOf("机器学习"), (f.vm.uiState as CourseUiState.Success).courses.map { it.nameZh })
        assertEquals("2024-2025 秋季", f.vm.selectedSemester)
    }

    @Test fun editingSearchDoesNotHidePersonalLoadFailureAndRetryWorks() = withFixture { f ->
        f.api.failPersonal = true
        withContext(Dispatchers.Main) { f.vm.refreshMySchedule() }
        f.await { !f.vm.isRefreshingSchedule && f.vm.uiState is CourseUiState.Error }
        withContext(Dispatchers.Main) {
            f.vm.updateSearchQuery("矩阵")
            assertTrue(f.vm.uiState is CourseUiState.Error)
        }
        f.api.failPersonal = false
        withContext(Dispatchers.Main) { f.vm.retryLoadCourses() }
        f.await { !f.vm.isRefreshingSchedule && f.vm.uiState is CourseUiState.Success }
        assertEquals(listOf("矩阵分析"), f.vm.courses.map { it.nameZh })
    }

    @Test fun returningToPersonalModeRestartsAnInterruptedInitialSync() = withFixture { f ->
        val gate = Gate()
        f.api.personalGate = gate
        withContext(Dispatchers.Main) { f.vm.refreshMySchedule() }
        gate.entered.await()
        withContext(Dispatchers.Main) { f.vm.toggleCourseMode() }
        gate.cancelled.await()
        f.api.personalGate = null
        withContext(Dispatchers.Main) { f.vm.toggleCourseMode() }
        f.await { f.vm.uiState is CourseUiState.Success && f.vm.courses.isNotEmpty() }
        assertEquals(2, f.api.personalCalls)
        assertTrue(f.vm.isPersonalCoursesMode)
    }

    @Test fun failedNextPageKeepsLoadedCoursesAndRetriesTheSamePage() = withFixture { f ->
        f.publicCourses("矩阵")
        f.api.failPublicPage = 2
        withContext(Dispatchers.Main) { f.vm.loadCourses(isNextPage = true) }
        f.await { !f.vm.isLoadingMore && f.vm.loadMoreErrorMessage != null }
        assertEquals(listOf(101), f.vm.courses.map { it.id })
        assertTrue(f.vm.uiState is CourseUiState.Success)
        f.api.failPublicPage = null
        withContext(Dispatchers.Main) { f.vm.loadCourses(isNextPage = true) }
        f.await { !f.vm.isLoadingMore && f.vm.courses.size == 2 }
        assertEquals(listOf(1 to "矩阵", 2 to "矩阵", 2 to "矩阵"), f.api.publicCalls.toList())
    }

    @Test fun aNewSearchStartsAtPageOneAndRejectsTheLatePreviousPage() = withFixture { f ->
        f.publicCourses("矩阵")
        val gate = Gate(ignoreCancellation = true)
        try {
            f.api.publicGate = gate
            withContext(Dispatchers.Main) { f.vm.loadCourses(isNextPage = true) }
            gate.entered.await()
            f.api.publicGate = null
            withContext(Dispatchers.Main) { f.vm.updateSearchQuery("机器学习"); f.vm.loadCourses() }
            f.await { f.vm.uiState is CourseUiState.Success && f.vm.courses.firstOrNull()?.nameZh == "机器学习 1" }
            gate.release.complete(Unit)
            gate.returned.await()
            withContext(Dispatchers.Main) { }
            assertEquals(listOf("机器学习 1"), f.vm.courses.map { it.nameZh })
            assertFalse(f.vm.isLoadingMore)
        } finally { gate.release.complete(Unit) }
    }

    @Test fun shortPagesKeepLoadingUntilTheEndWithoutAnExtraScroll() = withFixture { f ->
        f.publicCourses("矩阵")
        compose.setContent { CorrectionNotebookTheme { CourseListScreen(f.vm) { _, _ -> } } }
        compose.waitUntil(5_000) { f.api.publicCalls.size >= 4 }
        f.await { !f.vm.isLoadingMore }
        assertEquals(listOf(1, 2, 3, 4), f.api.publicCalls.map { it.first })
        assertEquals(listOf(101, 102, 103), f.vm.courses.map { it.id })
    }

    @Test fun returningToLoadedPersonalCoursesRejectsALatePublicResponse() = withFixture { f ->
        withContext(Dispatchers.Main) { f.vm.refreshMySchedule() }
        f.await { !f.vm.isRefreshingSchedule }
        val gate = Gate(ignoreCancellation = true)
        f.api.publicGate = gate
        withContext(Dispatchers.Main) { f.vm.toggleCourseMode() }
        gate.entered.await()
        withContext(Dispatchers.Main) { f.vm.toggleCourseMode() }
        val personalIds = f.vm.courses.map { it.id }
        gate.release.complete(Unit)
        gate.returned.await()
        withContext(Dispatchers.Main) { }
        assertTrue(f.vm.isPersonalCoursesMode)
        assertEquals(personalIds, f.vm.courses.map { it.id })
        assertTrue(personalIds.isNotEmpty())
        assertFalse(f.vm.isLoadingMore)
    }

    @Test fun unmatchedPersonalSearchCanClearFiltersWithoutAnotherSync() = withFixture { f ->
        withContext(Dispatchers.Main) { f.vm.refreshMySchedule(); f.vm.updateSearchQuery("不存在的课程") }
        f.await { !f.vm.isRefreshingSchedule }
        compose.setContent { CorrectionNotebookTheme { CourseListScreen(f.vm) { _, _ -> } } }
        compose.onNodeWithText("没有匹配的课程").assertIsDisplayed()
        compose.onNodeWithText("暂无个人课程").assertDoesNotExist()
        compose.onNodeWithText("清除筛选").performClick()
        compose.onNodeWithText("矩阵分析").assertIsDisplayed()
        compose.onNodeWithText("机器学习").assertIsDisplayed()
        assertEquals(1, f.api.personalCalls)
    }

    @Test fun narrowGridKeepsTwoCoursesInTheSameRow() = withFixture { f ->
        withContext(Dispatchers.Main) { f.vm.refreshMySchedule() }
        f.await { !f.vm.isRefreshingSchedule }
        withContext(Dispatchers.Main) { f.vm.selectSemester(ALL_SEMESTERS) }
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3.375f, 1.3f)) {
                CorrectionNotebookTheme(dynamicColor = false) {
                    Box(Modifier.navigationBarsPadding()) { CourseListScreen(f.vm) { _, _ -> } }
                }
            }
        }
        assertFullText("矩阵分析")
        assertFullText("机器学习")
        val first = compose.onNodeWithText("矩阵分析", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val second = compose.onNodeWithText("机器学习", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertEquals(first.top, second.top, 1f)
        assertTrue(first.right < second.left)
        saveScreenshot("grid")
    }

    @Test fun lightSearchAndSemesterFitNarrowScreenAndLargeFont() = checkLayout(false)
    @Test fun darkSearchAndSemesterFitNarrowScreenAndLargeFont() = checkLayout(true)

    @Test fun shortLightCourseErrorKeepsRetryReachable() = checkErrorRecovery(false)
    @Test fun shortDarkCourseErrorKeepsRetryReachable() = checkErrorRecovery(true)

    @Test fun retryCanRecoverToAnEmptyPersonalListWithoutARepeatedAutomaticSync() = withFixture { f ->
        f.api.failPersonal = true
        withContext(Dispatchers.Main) { f.vm.refreshMySchedule() }
        f.await { f.vm.uiState is CourseUiState.Error && !f.vm.isRefreshingSchedule }
        compose.setContent { CorrectionNotebookTheme { CourseListScreen(f.vm) { _, _ -> } } }
        f.api.failPersonal = false
        f.api.emptyPersonal = true
        compose.onNodeWithText("重试").performClick()
        f.await { f.vm.uiState is CourseUiState.Success && !f.vm.isRefreshingSchedule }
        compose.onNodeWithText("暂无个人课程").assertIsDisplayed()
        compose.onNode(hasSetTextAction()).performImeAction()
        compose.runOnIdle { assertEquals(2, f.api.personalCalls) }
    }

    private fun checkErrorRecovery(dark: Boolean) = withFixture { f ->
        f.api.failPersonal = true
        f.api.personalErrorMessage = (1..12).joinToString("\n") { "网络连接暂不可用，课程同步未完成（$it）。" }
        withContext(Dispatchers.Main) { f.vm.refreshMySchedule() }
        f.await { f.vm.uiState is CourseUiState.Error && !f.vm.isRefreshingSchedule }
        val message = (f.vm.uiState as CourseUiState.Error).message
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.3f)) {
                CorrectionNotebookTheme(darkTheme = dark, dynamicColor = false) {
                    Box(Modifier.size(width = 320.dp, height = 280.dp)) { CourseListScreen(f.vm) { _, _ -> } }
                }
            }
        }
        compose.onNodeWithText("重试").performScrollTo().assertIsDisplayed().assertIsEnabled()
        assertFullText(message)
        saveScreenshot("error-${if (dark) "dark" else "light"}")
        f.api.failPersonal = false
        compose.onNodeWithText("重试").performClick()
        f.await { f.vm.uiState is CourseUiState.Success && !f.vm.isRefreshingSchedule }
        compose.onNodeWithText("课程加载失败").assertDoesNotExist()
        assertEquals(2, f.api.personalCalls)
    }

    @Test fun imeSearchSubmitsTheTrimmedKeywordAndHidesTheKeyboard() = withFixture { f ->
        f.publicCourses("矩阵")
        var view: View? = null
        compose.setContent {
            view = LocalView.current
            CorrectionNotebookTheme { CourseListScreen(f.vm) { _, _ -> } }
        }
        val initial = Rect()
        val visible = Rect()
        compose.runOnIdle {
            generateSequence(view!!.context) { (it as? ContextWrapper)?.baseContext }
                .filterIsInstance<ComponentActivity>().first().enableEdgeToEdge()
            view!!.getWindowVisibleDisplayFrame(initial)
        }
        val field = compose.onNode(hasSetTextAction())
        field.performClick()
        compose.waitUntil(5_000) {
            compose.runOnIdle { view!!.getWindowVisibleDisplayFrame(visible) }
            visible.height() < initial.height() - 100
        }
        field.performTextReplacement(" 机器学习 ")
        saveScreenshot("keyboard")
        field.performImeAction()
        compose.waitUntil(5_000) {
            compose.runOnIdle { view!!.getWindowVisibleDisplayFrame(visible) }
            visible.height() >= initial.height() - 100 && f.api.publicCalls.any { it == 1 to "机器学习" }
        }
        compose.onNodeWithText("机器学习 1").assertIsDisplayed()
        saveScreenshot("submitted")
    }

    private fun checkLayout(dark: Boolean) = withFixture { f ->
        withContext(Dispatchers.Main) { f.vm.refreshMySchedule() }
        f.await { !f.vm.isRefreshingSchedule }
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3.375f, 1.3f)) {
                CorrectionNotebookTheme(darkTheme = dark, dynamicColor = false) {
                    Box(Modifier.navigationBarsPadding()) { CourseListScreen(f.vm) { _, _ -> } }
                }
            }
        }
        assertFullText("我的课程")
        assertFullText("全校课程")
        assertFullText("2025-2026 春季")
        assertFullText("课程名称或老师")
        compose.onNodeWithContentDescription("搜索课程").assertHasClickAction()
        saveScreenshot(if (dark) "dark" else "light")
        compose.onNode(hasSetTextAction()).performTextReplacement("矩阵")
        compose.onNodeWithContentDescription("清空搜索").performClick()
        compose.onNodeWithText("矩阵分析").assertIsDisplayed()
        compose.onNodeWithContentDescription("选择学期").performClick()
        compose.onNodeWithText("2024-2025 秋季").performClick()
        compose.onNodeWithText("机器学习").assertIsDisplayed()
        compose.onNodeWithText("矩阵分析").assertDoesNotExist()
    }

    private fun assertFullText(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onAllNodesWithText(text, useUnmergedTree = true).onFirst()
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        layouts.forEach {
            assertFalse("Clipped text: $text", it.didOverflowHeight)
            assertEquals(text.length, it.getLineEnd(it.lineCount - 1))
            repeat(it.lineCount) { line ->
                assertFalse(it.isLineEllipsized(line))
                assertTrue(it.getLineRight(line) <= it.size.width + 1)
            }
        }
    }

    private fun saveScreenshot(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(350)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "qa-course-list-$name.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    private fun withFixture(test: suspend (Fixture) -> Unit) = runBlocking {
        val f = Fixture()
        try {
            withTimeout(15_000) {
                f.tokens.saveYanheLoginTokens("course-flow-qa")
                withContext(Dispatchers.Main) { f.create() }
                test(f)
            }
        } finally {
            f.api.publicGate?.release?.complete(Unit)
            f.api.personalGate?.release?.complete(Unit)
            withContext(Dispatchers.Main) { f.store.clear() }
            f.vm.viewModelScope.coroutineContext[Job]?.join()
            f.scope.coroutineContext[Job]?.cancelAndJoin()
            f.client.dispatcher.executorService.shutdownNow()
            f.client.connectionPool.evictAll()
            f.file.delete()
            f.context.deleteSharedPreferences(f.preferencesName)
        }
    }

    private class Fixture {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val preferencesName = "course-flow-qa-${UUID.randomUUID()}"
        val file = File(context.cacheDir, "$preferencesName.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val preferences = PreferenceDataStoreFactory.create(scope = scope) { file }
        val tokens = TokenManager(preferences)
        val credentials = CredentialManager(context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE))
        val client = OkHttpClient.Builder().addInterceptor { throw AssertionError("Unexpected external request") }.build()
        val api = CourseApi()
        val yanhe = YanheRepository(tokens, credentials, BitCasClient(client))
        val videos = VideoRepository(VideoRemoteManager(api, tokens, credentials, AuthStateManager(), yanhe))
        val store = ViewModelStore()
        lateinit var vm: CourseListViewModel
        fun create() {
            vm = CourseListViewModel(yanhe, AuthStateManager(), videos, CourseLearningRepository(preferences))
            store.put("courses", vm)
        }
        suspend fun await(condition: () -> Boolean) = withTimeout(3_000) {
            while (!withContext(Dispatchers.Main) { condition() }) delay(10)
        }
        suspend fun publicCourses(keyword: String) {
            withContext(Dispatchers.Main) { vm.updateSearchQuery(keyword); vm.toggleCourseMode() }
            await { vm.uiState is CourseUiState.Success && vm.courses.isNotEmpty() }
        }
    }

    private class Gate(private val ignoreCancellation: Boolean = false) {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val returned = CompletableDeferred<Unit>()
        suspend fun await() {
            entered.complete(Unit)
            try {
                if (ignoreCancellation) withContext(NonCancellable) { release.await() } else release.await()
            } catch (e: CancellationException) {
                cancelled.complete(Unit)
                throw e
            } finally { returned.complete(Unit) }
        }
    }

    private class CourseApi : VideoApiService by unusedApi() {
        val publicCalls = CopyOnWriteArrayList<Pair<Int, String?>>()
        var personalCalls = 0
        var personalGate: Gate? = null
        var publicGate: Gate? = null
        var failPersonal = false
        var emptyPersonal = false
        var personalErrorMessage = "Personal courses unavailable"
        var failPublicPage: Int? = null
        override suspend fun getCourseList(token: String, semester: Int?, page: Int, pageSize: Int, keyword: String?): ApiResponse<JsonElement> {
            publicCalls.add(page to keyword)
            publicGate?.await()
            if (page == failPublicPage) throw IllegalStateException("Next page unavailable")
            return ApiResponse(0, data = JsonParser.parseString(if (page <= 3)
                "[{\"id\":${100 + page},\"name_zh\":\"$keyword $page\",\"semester\":\"2025-2026 春季\"}]" else "[]"))
        }
        override suspend fun getPrivateCourseList(token: String, page: Int, pageSize: Int, type: Int): ApiResponse<JsonElement> {
            personalCalls++
            personalGate?.await()
            if (failPersonal) throw IllegalStateException(personalErrorMessage)
            if (emptyPersonal) return ApiResponse(0, data = JsonParser.parseString("[]"))
            return ApiResponse(0, data = JsonParser.parseString("""[
                {"id":1,"name_zh":"矩阵分析","semester":"2025-2026 春季","professors":["张老师"]},
                {"id":2,"name_zh":"机器学习","semester":"2024-2025 秋季","professors":["李老师"]}
            ]"""))
        }
    }

    companion object {
        private fun unusedApi(): VideoApiService = Retrofit.Builder().baseUrl("https://unused.invalid/")
            .client(OkHttpClient.Builder().addInterceptor { throw AssertionError("Unexpected external request") }.build())
            .addConverterFactory(GsonConverterFactory.create()).build().create(VideoApiService::class.java)
    }
}
