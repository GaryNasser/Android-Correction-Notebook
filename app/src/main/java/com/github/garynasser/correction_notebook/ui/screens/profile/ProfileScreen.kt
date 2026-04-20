package com.github.garynasser.correction_notebook.ui.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.garynasser.correction_notebook.data.model.auth.AuthState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onNavigateToLogin: () -> Unit = {}
) {
    val authState by viewModel.authState.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showApiKeyDialog by remember { mutableStateOf(false) }
    var showLogoutDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }

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
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (authState is AuthState.Unauthenticated) {
                                onNavigateToLogin()
                            } else {
                                showLogoutDialog = true
                            }
                        }
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
                            color = if (authState is AuthState.Authenticated)
                                MaterialTheme.colorScheme.primaryContainer
                            else
                                MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    if (authState is AuthState.Authenticated)
                                        Icons.Default.AccountCircle
                                    else
                                        Icons.Default.Person,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp),
                                    tint = if (authState is AuthState.Authenticated)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(16.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (authState is AuthState.Authenticated) "已登录" else "未登录",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (authState is AuthState.Authenticated)
                                    "点击此处退出登录"
                                else
                                    "点击登录校园账号",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }

                        if (authState is AuthState.Authenticated) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "退出登录")
                        } else {
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
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
                                checked = apiKey.isNotEmpty(),
                                onCheckedChange = {
                                    if (it) showApiKeyDialog = true
                                }
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
                            subtitle = if (apiKey.isNotEmpty()) "已配置" else "设置AI接口密钥",
                            onClick = { showApiKeyDialog = true }
                        )
                        HorizontalDivider()
                        SettingsItem(
                            icon = Icons.AutoMirrored.Filled.Help,
                            title = "帮助与反馈",
                            subtitle = "常见问题、联系客服",
                            onClick = { showFeedbackDialog = true }
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    // API Key Dialog
    if (showApiKeyDialog) {
        ApiKeyDialog(
            currentKey = apiKey,
            onDismiss = { showApiKeyDialog = false },
            onSave = { key ->
                viewModel.setApiKey(key)
                showApiKeyDialog = false
            }
        )
    }

    // Logout Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("退出登录") },
            text = { Text("确定要退出当前账号吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.logout()
                        showLogoutDialog = false
                    }
                ) {
                    Text("确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    // Feedback Dialog
    if (showFeedbackDialog) {
        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            title = { Text("帮助与反馈") },
            text = {
                Column {
                    Text("如有bug或功能建议，请联系开发者邮箱：")
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "fangmierui@gmail.com",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showFeedbackDialog = false }) {
                    Text("确定")
                }
            }
        )
    }

    // Loading indicator
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
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

@Composable
fun ApiKeyDialog(
    currentKey: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var apiKeyInput by remember { mutableStateOf(currentKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("API 配置") },
        text = {
            Column {
                Text(
                    text = "请输入您的 AI 接口密钥",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = { apiKeyInput = it },
                    label = { Text("API Key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(apiKeyInput) },
                enabled = apiKeyInput.isNotBlank()
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
