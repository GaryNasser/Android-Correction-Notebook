package com.github.garynasser.correction_notebook.data.model.home

data class Article(
    val id: String,
    val title: String,
    val summary: String,
    val imageUrl: String?,
    val source: String,
    val publishTime: Long,
    val url: String?,
    val detailAvailable: Boolean = true
)

data class ArticleDetail(
    val id: String,
    val title: String,
    val source: String,
    val publishTime: Long,
    val url: String?,
    val blocks: List<ArticleContentBlock>
)

sealed interface ArticleContentBlock {
    data class Text(
        val text: String
    ) : ArticleContentBlock

    data class Image(
        val imageUrl: String,
        val caption: String = ""
    ) : ArticleContentBlock

    data class Link(
        val title: String,
        val url: String,
        val description: String = ""
    ) : ArticleContentBlock
}
