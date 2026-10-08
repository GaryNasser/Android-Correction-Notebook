package com.github.garynasser.correction_notebook.data.repository

import com.github.garynasser.correction_notebook.data.model.common.ApiResponse
import com.github.garynasser.correction_notebook.data.remote.api.ArticleApiService
import com.github.garynasser.correction_notebook.data.remote.model.ArticleDto
import com.github.garynasser.correction_notebook.data.remote.model.ArticleDetailDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ArticleRepositoryTest {
    @Test fun failedRecommendationRefreshPreservesCacheAndASuccessfulRetryReplacesIt() = runBlocking {
        val api = FakeService()
        val repository = ArticleRepository(api)
        val original = repository.getRecommendedArticles()
        assertEquals(original, repository.getRecommendedArticles())
        assertEquals(1, api.listCalls)
        val failure = IOException("offline")
        api.failure = failure
        try { repository.getRecommendedArticles(forceRefresh = true); fail("Refresh must fail") }
        catch (error: IOException) { assertSame(failure, error) }
        assertEquals(original, repository.getRecommendedArticles())
        assertEquals(2, api.listCalls)
        api.failure = null
        api.list = ApiResponse(200, data = listOf(article.copy(title = "Updated title")))
        assertEquals("Updated title", repository.getRecommendedArticles(forceRefresh = true).single().title)
        assertEquals(3, api.listCalls)
    }

    @Test fun failedDetailRefreshDoesNotDestroyTheBodyOrLeakItToAnotherArticle() = runBlocking {
        val api = FakeService()
        val repository = ArticleRepository(api)
        val original = repository.getArticleDetail("first")
        api.failure = IOException("offline")
        try { repository.getArticleDetail("first", forceRefresh = true); fail("Refresh must fail") }
        catch (_: IOException) { }
        assertEquals(original, repository.getArticleDetail("first"))
        try { repository.getArticleDetail("second"); fail("Another article must not use the first body") }
        catch (_: IOException) { }
        api.failure = null
        assertEquals("second", repository.getArticleDetail("second").id)
        assertEquals(4, api.detailCalls)
    }

    @Test fun aValidEmptyFeedIsCachedButRejectedOrMissingDataIsNot() = runBlocking {
        val api = FakeService()
        val repository = ArticleRepository(api)
        api.list = ApiResponse(503, "推荐服务维护中", data = null)
        try { repository.getRecommendedArticles(); fail("Rejected data must not be cached") }
        catch (error: IllegalStateException) { assertEquals("推荐服务维护中", error.message) }
        api.list = ApiResponse(200, data = null)
        try { repository.getRecommendedArticles(); fail("Missing data must not be cached") }
        catch (_: IllegalStateException) { }
        api.list = ApiResponse(200, data = emptyList())
        assertTrue(repository.getRecommendedArticles().isEmpty())
        assertTrue(repository.getRecommendedArticles().isEmpty())
        assertEquals(3, api.listCalls)
    }

    @Test fun cancellingEitherRequestPropagatesCancellationAndDoesNotCacheAnEmptySuccess() = runBlocking {
        val api = FakeService()
        val repository = ArticleRepository(api)
        val cancellation = CancellationException("Stop this request")
        api.failure = cancellation
        try { repository.getRecommendedArticles(); fail("Cancellation must propagate") }
        catch (error: CancellationException) { assertSame(cancellation, error) }
        try { repository.getArticleDetail("first"); fail("Cancellation must propagate") }
        catch (error: CancellationException) { assertSame(cancellation, error) }
        api.failure = null
        assertEquals(article.title, repository.getRecommendedArticles().single().title)
        assertEquals("first", repository.getArticleDetail("first").id)
        assertEquals(2, api.listCalls)
        assertEquals(2, api.detailCalls)
    }

    private class FakeService : ArticleApiService {
        var list = ApiResponse(200, data = listOf(article))
        var failure: Exception? = null
        var listCalls = 0
        var detailCalls = 0
        override suspend fun getRecommendedArticles(): ApiResponse<List<ArticleDto>> {
            listCalls++
            failure?.let { throw it }
            return list
        }
        override suspend fun getArticleDetail(id: String): ApiResponse<ArticleDetailDto> {
            detailCalls++
            failure?.let { throw it }
            return ApiResponse(200, data = ArticleDetailDto(id, "Article $id", "BITStudy", 0))
        }
    }

    companion object { private val article = ArticleDto("first", "Article title", "Summary", source = "BITStudy", publishTime = 0) }
}
