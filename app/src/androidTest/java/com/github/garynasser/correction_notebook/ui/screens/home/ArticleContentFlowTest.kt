package com.github.garynasser.correction_notebook.ui.screens.home

import android.graphics.Bitmap
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.UriHandler
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.model.common.ApiResponse
import com.github.garynasser.correction_notebook.data.remote.api.ArticleApiService
import com.github.garynasser.correction_notebook.data.remote.model.ArticleContentBlockDto
import com.github.garynasser.correction_notebook.data.remote.model.ArticleDetailDto
import com.github.garynasser.correction_notebook.data.remote.model.ArticleDto
import com.github.garynasser.correction_notebook.data.repository.ArticleRepository
import com.github.garynasser.correction_notebook.ui.components.LocalAiEnabled
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import kotlinx.coroutines.*
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

class ArticleContentFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightHomeFailureRemainsCompactAndRetriesWithoutDuplicateRequests() = checkHomeFailure(false)
    @Test fun darkHomeFailureRemainsCompactAndRetriesWithoutDuplicateRequests() = checkHomeFailure(true)

    private fun checkHomeFailure(dark: Boolean) = withHome { api, f ->
        api.listFailure = httpFailure()
        renderHome(f, dark)
        openRecommendations()
        f.await { !f.home.uiState.value.isArticlesLoading && f.home.uiState.value.articleErrorMessage != null }
        scrollHomeTo(hasText(SERVICE_ERROR))
        compose.onNodeWithText(SERVICE_ERROR).assertIsDisplayed()
        compose.onNodeWithText("HTTP 502").assertDoesNotExist()
        compose.onNode(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)).assertExists()
        val message = compose.onNodeWithText("推荐内容加载失败").fetchSemanticsNode().boundsInRoot
        val retry = compose.onNodeWithContentDescription("重试推荐").fetchSemanticsNode().boundsInRoot
        assertTrue("Retry must be beside the message, not in a separate oversized row", retry.center.y in message.top..(message.bottom + 100f))
        capture(if (dark) "dark-home-failure" else "light-home-failure")
        api.listFailure = null
        val gate = CompletableDeferred<Unit>().also { api.listGate = it }
        compose.onNodeWithContentDescription("重试推荐").performClick()
        f.await { api.listCalls.get() == 2 }
        compose.onNodeWithContentDescription("正在刷新推荐").assertIsNotEnabled()
        withContext(Dispatchers.Main) { repeat(3) { f.home.refreshArticles() } }
        assertEquals(2, api.listCalls.get())
        gate.complete(Unit)
        f.await { !f.home.uiState.value.isArticlesLoading && f.home.uiState.value.articles.isNotEmpty() }
        compose.onNodeWithText(SERVICE_ERROR).assertDoesNotExist()
        scrollHomeTo(hasText(TITLE))
        compose.onNodeWithText(TITLE).assertIsDisplayed()
        assertEquals(2, api.listCalls.get())
        compose.onNodeWithText("BIT").performClick()
        compose.onNodeWithContentDescription("添加日程").assertExists()
    }

    @Test fun cachedHomeArticlesRemainReadableAndClickableAfterARefreshFails() = withHome { api, f ->
        var opened: String? = null
        renderHome(f, false) { opened = it }
        openRecommendations()
        f.await { f.home.uiState.value.articles.isNotEmpty() }
        scrollHomeTo(hasText(TITLE))
        api.listFailure = httpFailure()
        val gate = CompletableDeferred<Unit>().also { api.listGate = it }
        compose.onNodeWithContentDescription("刷新推荐").performClick()
        f.await { api.listCalls.get() == 2 }
        assertEquals(TITLE, f.home.uiState.value.articles.single().title)
        capture("cached-home-pending")
        scrollHomeTo(hasText(TITLE))
        compose.onNodeWithText(TITLE).assertIsDisplayed().performClick()
        assertEquals("first", opened)
        gate.complete(Unit)
        f.await { !f.home.uiState.value.isArticlesLoading && f.home.uiState.value.articleErrorMessage != null }
        scrollHomeTo(hasText("推荐内容刷新失败"))
        compose.onNodeWithText("推荐内容刷新失败").assertIsDisplayed()
        scrollHomeTo(hasText(TITLE))
        compose.onNodeWithText(TITLE).assertIsDisplayed()
        assertEquals(TITLE, f.home.uiState.value.articles.single().title)
        api.listGate = null
        api.listFailure = null
        scrollHomeTo(hasContentDescription("重试推荐"))
        compose.onNodeWithContentDescription("重试推荐").performClick()
        f.await { !f.home.uiState.value.isArticlesLoading && f.home.uiState.value.articleErrorMessage == null }
        assertEquals(3, api.listCalls.get())
    }

    @Test fun lightDetailWithALongBusinessErrorKeepsOriginalAndRetryReachable() = checkLongDetailError(false)
    @Test fun darkDetailWithALongBusinessErrorKeepsOriginalAndRetryReachable() = checkLongDetailError(true)

    private fun checkLongDetailError(dark: Boolean) = withDetail { api, vm ->
        api.detailFailure = IllegalStateException(LONG_ERROR)
        // The initial request is held until the desired failure is configured.
        api.detailGate.complete(Unit)
        await { !vm.uiState.value.isLoading }
        var opened: String? = null
        compose.setContent {
            CompositionLocalProvider(LocalUriHandler provides object : UriHandler {
                override fun openUri(uri: String) { opened = uri }
            }) {
                TestTheme(dark) { ArticleDetailScreen(onBack = {}, viewModel = vm) }
            }
        }
        capture(if (dark) "dark-long-detail-failure" else "light-long-detail-failure")
        compose.onNodeWithText("打开原文").performScrollTo().assertIsDisplayed().performClick()
        assertEquals(URL, opened)
        compose.onNodeWithText("重试").performScrollTo().assertIsDisplayed()
        capture(if (dark) "dark-detail-actions" else "light-detail-actions")
        api.detailFailure = null
        compose.onNodeWithText("重试").performClick()
        await { vm.uiState.value.articleDetail != null }
        compose.onNodeWithText(BODY).assertIsDisplayed()
        assertEquals(2, api.detailCalls.get())
    }

    @Test fun failedDetailRefreshRetainsItsBodyAndAFollowingRetryClearsTheError() = withDetail { api, vm ->
        api.detailGate.complete(Unit)
        await { vm.uiState.value.articleDetail != null }
        compose.setContent { TestTheme { ArticleDetailScreen(onBack = {}, viewModel = vm) } }
        api.detailFailure = httpFailure()
        api.detailGate = CompletableDeferred()
        compose.onNodeWithContentDescription("刷新").performClick()
        await { api.detailCalls.get() == 2 }
        compose.onNodeWithText(BODY).assertIsDisplayed()
        withContext(Dispatchers.Main) { repeat(3) { vm.refresh() } }
        assertEquals(2, api.detailCalls.get())
        api.detailGate.complete(Unit)
        await { !vm.uiState.value.isRefreshing }
        compose.onNodeWithText(SERVICE_ERROR).assertIsDisplayed()
        compose.onNodeWithText(BODY).assertIsDisplayed()
        api.detailFailure = null
        compose.onNodeWithText("重试").performClick()
        await { api.detailCalls.get() == 3 && !vm.uiState.value.isRefreshing }
        compose.onNodeWithText(SERVICE_ERROR).assertDoesNotExist()
        compose.onNodeWithText(BODY).assertIsDisplayed()
    }

    @Test fun clearingTheDetailOwnerCancelsThePendingLoadWithoutTurningItIntoAnError() = withDetail { api, vm ->
        await { api.detailCalls.get() == 1 }
        withContext(Dispatchers.Main) { api.detailStore.clear() }
        await { api.detailCancellations.get() == 1 }
        api.detailGate.complete(Unit)
        assertNull(vm.uiState.value.articleDetail)
        assertNull(vm.uiState.value.errorMessage)
    }

    private fun renderHome(f: HomeFormSaveFailureTest.Fixture, dark: Boolean, onOpen: (String) -> Unit = {}) {
        compose.setContent {
            TestTheme(dark) {
                HomeScreen(f.home, f.statistics, onOpenArticle = { onOpen(it.id) })
            }
        }
    }

    @Composable
    private fun TestTheme(dark: Boolean = false, content: @Composable () -> Unit) {
        CompositionLocalProvider(LocalAiEnabled provides false,
            LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
            CorrectionNotebookTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize().safeDrawingPadding(), content = content)
            }
        }
    }

    private fun openRecommendations() {
        compose.onNodeWithText("Study").performClick()
        scrollHomeTo(hasText("学习推荐"))
    }

    private fun scrollHomeTo(matcher: SemanticsMatcher) {
        val list = compose.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange))
        list.performScrollToNode(matcher)
        // A nested article row can match its whole parent item while its text remains below the viewport.
        if (!compose.onNode(matcher).isDisplayed()) list.performTouchInput { swipeUp() }
    }

    private fun withHome(test: suspend (ControlledService, HomeFormSaveFailureTest.Fixture) -> Unit) = runBlocking {
        val api = ControlledService()
        val f = HomeFormSaveFailureTest.Fixture(api)
        try {
            withContext(Dispatchers.Main) { f.create() }
            withTimeout(25_000) { test(api, f) }
        } finally {
            withContext(Dispatchers.Main) { f.store.clear() }
            f.scope.cancel()
            f.scope.coroutineContext[Job]?.join()
            f.files.forEach { it.delete() }
            f.database.close()
            f.knowledge.close()
            f.network.dispatcher.executorService.shutdownNow()
            f.network.connectionPool.evictAll()
        }
    }

    private fun withDetail(test: suspend (ControlledService, ArticleDetailViewModel) -> Unit) = runBlocking {
        val api = ControlledService()
        val vm = withContext(Dispatchers.Main) {
            ArticleDetailViewModel(ArticleRepository(api), SavedStateHandle(mapOf("articleId" to "first", "fallbackUrl" to URL)))
                .also { api.detailStore.put("detail", it) }
        }
        try { withTimeout(20_000) { test(api, vm) } }
        finally { withContext(Dispatchers.Main) { api.detailStore.clear() } }
    }

    private suspend fun await(predicate: () -> Boolean) = withTimeout(5_000) { while (!predicate()) delay(10) }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        Thread.sleep(300)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "qa-article-$name.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    private class ControlledService : ArticleApiService {
        val listCalls = AtomicInteger()
        val detailCalls = AtomicInteger()
        val detailCancellations = AtomicInteger()
        val detailStore = ViewModelStore()
        var listFailure: Exception? = null
        var detailFailure: Exception? = null
        var listGate: CompletableDeferred<Unit>? = null
        var detailGate = CompletableDeferred<Unit>()
        override suspend fun getRecommendedArticles(): ApiResponse<List<ArticleDto>> {
            listCalls.incrementAndGet()
            listGate?.await()
            listFailure?.let { throw it }
            return ApiResponse(200, data = listOf(ArticleDto("first", TITLE, "复习课程与课堂笔记。", source = "BITStudy", publishTime = 0)))
        }
        override suspend fun getArticleDetail(id: String): ApiResponse<ArticleDetailDto> {
            detailCalls.incrementAndGet()
            try { detailGate.await() }
            catch (error: CancellationException) { detailCancellations.incrementAndGet(); throw error }
            detailFailure?.let { throw it }
            return ApiResponse(200, data = ArticleDetailDto(id, TITLE, "BITStudy", 0, fallbackUrl = URL,
                blocks = listOf(ArticleContentBlockDto("TEXT", text = BODY))))
        }
    }

    companion object {
        private const val TITLE = "矩阵分析复习安排"
        private const val BODY = "整理矩阵分析第三章的证明步骤，并复查课堂笔记。"
        private const val URL = "https://example.org/article/first"
        private const val SERVICE_ERROR = "内容服务暂时不可用，请稍后重试"
        private val LONG_ERROR = "文章服务正在维护，暂时无法获取完整正文，原文链接仍然可以访问。".repeat(8)
        private fun httpFailure() = HttpException(Response.error<Unit>(502, "Bad gateway".toResponseBody()))
    }
}
