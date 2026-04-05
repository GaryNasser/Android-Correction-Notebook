package com.github.garynasser.correction_notebook.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.garynasser.correction_notebook.ui.screens.main.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    settingsViewModel: SettingsViewModel
) {
    val aiEnabled by settingsViewModel.aiEnabled.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("个人中心") }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 用户信息卡片
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(64.dp),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "未登录",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "点击登录校园账号",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }

                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // AI 功能开关
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = { Text("AI导师功能") },
                        supportingContent = { Text("开启后可使用AI辅助学习") },
                        leadingContent = {
                            Icon(
                                Icons.Default.SmartToy,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = aiEnabled,
                                onCheckedChange = { settingsViewModel.setAiEnabled(it) }
                            )
                        }
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // 设置列表
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingsItem(
                            icon = Icons.Default.Key,
                            title = "API配置",
                            subtitle = "设置AI接口密钥",
                            onClick = { /* TODO */ }
                        )
                        HorizontalDivider()
                        SettingsItem(
                            icon = Icons.Default.Notifications,
                            title = "通知设置",
                            subtitle = "日程提醒、直播提醒",
                            onClick = { /* TODO */ }
                        )
                        HorizontalDivider()
                        SettingsItem(
                            icon = Icons.Default.Storage,
                            title = "存储管理",
                            subtitle = "清理缓存、管理下载",
                            onClick = { /* TODO */ }
                        )
                        HorizontalDivider()
                        SettingsItem(
                            icon = Icons.Default.DarkMode,
                            title = "深色模式",
                            subtitle = "跟随系统",
                            onClick = { /* TODO */ }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(8.dp)) }

            // 其他设置
            item {
                Card(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingsItem(
                            icon = Icons.Default.Info,
                            title = "关于",
                            subtitle = "版本 1.0.0",
                            onClick = { /* TODO */ }
                        )
                        HorizontalDivider()
                        SettingsItem(
                            icon = Icons.Default.Help,
                            title = "帮助与反馈",
                            subtitle = "常见问题、联系客服",
                            onClick = { /* TODO */ }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }
}

@Composable
fun SettingsItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(subtitle) },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
