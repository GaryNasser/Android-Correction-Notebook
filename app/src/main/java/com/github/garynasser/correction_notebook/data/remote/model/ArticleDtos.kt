package com.github.garynasser.correction_notebook.data.remote.model

import com.github.garynasser.correction_notebook.data.model.home.Article
import com.github.garynasser.correction_notebook.data.model.home.ArticleContentBlock
import com.github.garynasser.correction_notebook.data.model.home.ArticleDetail
import com.google.gson.annotations.SerializedName

data class ArticleDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("summary") val summary: String,
    @SerializedName("coverImageUrl") val coverImageUrl: String? = null,
    @SerializedName("imageUrl") val imageUrl: String? = null,
    @SerializedName("source") val source: String,
    @SerializedName("publishTime") val publishTime: Long,
    @SerializedName("fallbackUrl") val fallbackUrl: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("detailAvailable") val detailAvailable: Boolean = true
)

data class ArticleDetailDto(
    @SerializedName("id") val id: String,
    @SerializedName("title") val title: String,
    @SerializedName("source") val source: String,
    @SerializedName("publishTime") val publishTime: Long,
    @SerializedName("fallbackUrl") val fallbackUrl: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("blocks") val blocks: List<ArticleContentBlockDto> = emptyList()
)

data class ArticleContentBlockDto(
    @SerializedName("type") val type: String,
    @SerializedName("text") val text: String? = null,
    @SerializedName("imageUrl") val imageUrl: String? = null,
    @SerializedName("caption") val caption: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("url") val url: String? = null,
    @SerializedName("description") val description: String? = null
)

fun ArticleDto.toDomain(): Article {
    return Article(
        id = id,
        title = title,
        summary = summary,
        imageUrl = coverImageUrl ?: imageUrl,
        source = source,
        publishTime = publishTime,
        url = fallbackUrl ?: url,
        detailAvailable = detailAvailable
    )
}

fun ArticleDetailDto.toDomain(): ArticleDetail {
    return ArticleDetail(
        id = id,
        title = title,
        source = source,
        publishTime = publishTime,
        url = fallbackUrl ?: url,
        blocks = blocks.mapNotNull { it.toDomainOrNull() }
    )
}

private fun ArticleContentBlockDto.toDomainOrNull(): ArticleContentBlock? {
    return when (type.uppercase()) {
        "TEXT" -> text?.takeIf { it.isNotBlank() }?.let { ArticleContentBlock.Text(it) }
        "IMAGE" -> imageUrl?.takeIf { it.isNotBlank() }?.let {
            ArticleContentBlock.Image(
                imageUrl = it,
                caption = caption.orEmpty()
            )
        }
        "LINK" -> {
            val safeUrl = url?.takeIf { it.isNotBlank() } ?: return null
            ArticleContentBlock.Link(
                title = title?.takeIf { it.isNotBlank() } ?: safeUrl,
                url = safeUrl,
                description = description.orEmpty()
            )
        }
        else -> null
    }
}
