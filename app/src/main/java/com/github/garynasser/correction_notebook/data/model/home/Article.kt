package com.github.garynasser.correction_notebook.data.model.home

data class Article(
    val id: String,
    val title: String,
    val summary: String,
    val imageUrl: String?,
    val source: String,
    val publishTime: Long,
    val url: String
)
