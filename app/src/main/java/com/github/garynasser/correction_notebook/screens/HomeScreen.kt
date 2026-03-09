package com.github.garynasser.correction_notebook.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector // 解决 ImageVector 报错
import androidx.compose.ui.tooling.preview.Preview     // 解决 Preview 报错
import androidx.compose.ui.unit.dp
// 导入你项目的主题
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme

@Composable
fun HomeScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            text = "AI 学习助手",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // 核心功能：OCR 扫描大卡片
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clickable { /* TODO: 处理拍照 */ },
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(40.dp))
                    Spacer(Modifier.height(8.dp))
                    Text("拍照扫描试卷", style = MaterialTheme.typography.titleLarge)
                    Text("自动识别题目并加入错题库", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 次要功能：文档与音视频
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FeatureCard("文档分析", Icons.Default.Description, Modifier.weight(1f))
            FeatureCard("音视频总结", Icons.Default.VideoLibrary, Modifier.weight(1f))
        }
    }
}

@Composable
fun FeatureCard(title: String, icon: ImageVector, modifier: Modifier) {
    Card(
        modifier = modifier.height(100.dp).clickable { /* TODO */ },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null)
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
fun HomeScreenPreview() {
    // 关键点：将 YourProjectTheme 改为你项目实际的主题名
    // 根据你的目录，它应该叫 CorrectionNotebookTheme
    CorrectionNotebookTheme {
        HomeScreen()
    }
}