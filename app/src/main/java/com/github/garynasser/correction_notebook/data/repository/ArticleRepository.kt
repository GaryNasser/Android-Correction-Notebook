package com.github.garynasser.correction_notebook.data.repository

import com.github.garynasser.correction_notebook.data.model.home.Article
import com.github.garynasser.correction_notebook.data.model.home.ArticleDetail
import com.github.garynasser.correction_notebook.data.remote.api.ArticleApiService
import com.github.garynasser.correction_notebook.data.remote.model.toDomain
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArticleRepository @Inject constructor(
    private val articleApiService: ArticleApiService
) {
    private var cachedArticles: List<Article>? = null
    private val articleDetailCache = linkedMapOf<String, ArticleDetail>()

    suspend fun getRecommendedArticles(forceRefresh: Boolean = false): List<Article> {
        if (!forceRefresh) {
            cachedArticles?.let { return it }
        }

        val response = articleApiService.getRecommendedArticles()
        if (response.code != 200 || response.data == null) {
            throw IllegalStateException(response.message.ifBlank { "推荐内容加载失败" })
        }

        val articles = response.data.map { it.toDomain() }
        cachedArticles = articles
        return articles
    }

    suspend fun getArticleDetail(articleId: String, forceRefresh: Boolean = false): ArticleDetail {
        if (!forceRefresh) {
            articleDetailCache[articleId]?.let { return it }
        }

        val response = articleApiService.getArticleDetail(articleId)
        if (response.code != 200 || response.data == null) {
            throw IllegalStateException(response.message.ifBlank { "文章详情加载失败" })
        }

        val detail = response.data.toDomain()
        articleDetailCache[articleId] = detail
        return detail
    }
}
