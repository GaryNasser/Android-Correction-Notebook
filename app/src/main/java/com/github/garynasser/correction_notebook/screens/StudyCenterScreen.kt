package com.github.garynasser.correction_notebook.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.* // 包含了大多数 M3 组件
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

// 关键修复点：添加这个注解来消除实验性 API 的报错
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyCenterScreen() {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("学习中心") }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // 搜索框
            OutlinedTextField(
                value = "",
                onValueChange = {},
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                placeholder = { Text("搜索错题...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
            )

            // 分类标签
            ScrollableTabRow(
                selectedTabIndex = 0,
                edgePadding = 16.dp,
                divider = {}
            ) {
                listOf("全部", "数学", "物理", "英语").forEachIndexed { index, title ->
                    Tab(
                        selected = index == 0,
                        onClick = { /* TODO */ },
                        text = { Text(title) }
                    )
                }
            }

            // 占位内容
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("暂无错题记录", color = Color.Gray)
            }
        }
    }
}