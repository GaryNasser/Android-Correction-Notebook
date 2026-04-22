package com.github.garynasser.correction_notebook.data.remote.api

import com.github.garynasser.correction_notebook.data.remote.model.ArticleDetailDto
import com.github.garynasser.correction_notebook.data.remote.model.ArticleDto
import retrofit2.http.GET
import retrofit2.http.Path

interface ArticleApiService {
    @GET("articles/recommendations")
    suspend fun getRecommendedArticles(): List<ArticleDto>

    @GET("articles/{id}")
    suspend fun getArticleDetail(@Path("id") id: String): ArticleDetailDto
}
