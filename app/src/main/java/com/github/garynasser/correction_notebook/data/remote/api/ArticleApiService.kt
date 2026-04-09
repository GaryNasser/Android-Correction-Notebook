package com.github.garynasser.correction_notebook.data.remote.api

import com.github.garynasser.correction_notebook.data.model.home.Article
import retrofit2.http.GET

interface ArticleApiService {
    @GET("articles")
    suspend fun getArticles(): List<Article>
}
