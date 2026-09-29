package com.github.garynasser.correction_notebook.ui.screens.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.github.garynasser.correction_notebook.data.model.home.Article
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ArticlesSection(
    articles: List<Article>,
    isLoading: Boolean,
    errorMessage: String?,
    onArticleClick: (Article) -> Unit,
    onRefresh: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "学习推荐",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "看看校园动态和实用学习内容",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(
                onClick = onRefresh,
                enabled = !isLoading,
                modifier = Modifier.widthIn(min = 76.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                } else {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Spacer(modifier = Modifier.width(4.dp))
                Text("刷新")
            }
        }
        when {
            isLoading && articles.isEmpty() -> {
                ArticleLoadingRow()
            }
            errorMessage != null && articles.isEmpty() -> {
                ArticleMessageCard(
                    title = "推荐内容加载失败",
                    description = errorMessage,
                    actionLabel = "重试",
                    onAction = onRefresh,
                    isError = true
                )
            }
            articles.isEmpty() -> {
                ArticleMessageCard(
                    title = "暂时还没有推荐内容",
                    description = "稍后刷新看看，或等待后端推送新的学习文章。"
                )
            }
            else -> {
                if (errorMessage != null) {
                    ArticleMessageCard(
                        title = "推荐内容刷新失败",
                        description = errorMessage,
                        actionLabel = "重试",
                        onAction = onRefresh,
                        compact = true,
                        isError = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(articles, key = { it.id }) { article ->
                        ArticleCard(
                            article = article,
                            onClick = { onArticleClick(article) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArticleLoadingRow() {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(3) {
            Card(
                modifier = Modifier
                    .width(184.dp)
                    .height(220.dp),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                )
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(88.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.8f)
                            .height(16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))
                    )
                }
            }
        }
    }
}

@Composable
private fun ArticleMessageCard(
    title: String,
    description: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    compact: Boolean = false,
    isError: Boolean = false
) {
    Card(
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isError) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.68f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            }
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (compact) 12.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.72f)
                }
            )
            if (actionLabel != null && onAction != null) {
                TextButton(
                    onClick = onAction,
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Text(actionLabel)
                }
            }
        }
    }
}

@Composable
fun ArticleCard(
    article: Article,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .width(184.dp)
            .height(220.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            // Image or placeholder
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(88.dp)
                    .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                val safeImageUrl = safeArticleWebUrl(article.imageUrl)
                if (safeImageUrl != null) {
                    AsyncImage(
                        model = safeImageUrl,
                        contentDescription = article.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.AutoMirrored.Filled.Article,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .padding(10.dp)
                    .weight(1f)
            ) {
                Text(
                    text = article.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(3.dp))

                Text(
                    text = article.summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.weight(1f))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = article.source,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = formatTimeAgo(article.publishTime),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }
    }
}

private fun formatTimeAgo(timestamp: Long): String {
    if (timestamp <= 0L) return "日期未知"
    val now = System.currentTimeMillis()
    val diff = (now - timestamp).coerceAtLeast(0L)
    val minutes = diff / (1000 * 60)
    val hours = diff / (1000 * 60 * 60)
    val days = diff / (1000 * 60 * 60 * 24)

    return when {
        minutes < 60 -> "${minutes}分钟前"
        hours < 24 -> "${hours}小时前"
        days < 7 -> "${days}天前"
        else -> SimpleDateFormat("MM月dd日", Locale.getDefault()).format(Date(timestamp))
    }
}
