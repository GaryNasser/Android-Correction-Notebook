package com.github.garynasser.correction_notebook.data.repository

import com.github.garynasser.correction_notebook.data.model.home.Article
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

class ArticleRepository {

    fun getArticles(): Flow<List<Article>> = flow {
        emit(getMockArticles())
    }

    private fun getMockArticles(): List<Article> {
        return listOf(
            Article(
                id = UUID.randomUUID().toString(),
                title = "高效学习的10个科学方法",
                summary = "基于认知心理学研究，介绍经过验证的高效学习方法...",
                imageUrl = null,
                source = "学习科学",
                publishTime = System.currentTimeMillis() - 86400000,
                url = "https://example.com/article1"
            ),
            Article(
                id = UUID.randomUUID().toString(),
                title = "番茄工作法详解",
                summary = "如何正确使用番茄工作法提升专注力...",
                imageUrl = null,
                source = "时间管理",
                publishTime = System.currentTimeMillis() - 172800000,
                url = "https://example.com/article2"
            ),
            Article(
                id = UUID.randomUUID().toString(),
                title = "深度工作：如何有效使用每一点脑力",
                summary = "在分散注意力的世界中培养深度工作的能力...",
                imageUrl = null,
                source = " Productivity",
                publishTime = System.currentTimeMillis() - 259200000,
                url = "https://example.com/article3"
            ),
            Article(
                id = UUID.randomUUID().toString(),
                title = "记忆宫殿：提升记忆力的古老技巧",
                summary = "探索古代记忆术在现代学习中的应用...",
                imageUrl = null,
                source = "记忆技巧",
                publishTime = System.currentTimeMillis() - 345600000,
                url = "https://example.com/article4"
            )
        )
    }
}
