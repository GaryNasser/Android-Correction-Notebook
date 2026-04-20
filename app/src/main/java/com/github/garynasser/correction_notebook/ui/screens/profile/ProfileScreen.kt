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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.garynasser.correction_notebook.data.local.AISettingsManager
import com.github.garynasser.correction_notebook.data.model.auth.AuthState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onNavigateToLogin: () -> Unit = {}
) {
    val authState by viewModel.authState.collectAsState()
    val aiEnabled by viewModel.aiEnabled.collectAsState()
    val apiKey by viewModel.apiKey.collectAsState()
    val apiBaseUrl by viewModel.apiBaseUrl.collectAsState()
    val aiModel by viewModel.aiModel.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showAiSettingsDialog by remember { mutableStateOf(false) }

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
                        supportingContent = {
                            Text(
                                if (aiEnabled) {
                                    if (apiKey.isNotEmpty()) "已启用 - ${aiModel}" else "已启用 - 请配置API"
                                } else "已关闭"
                            )
                        },
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
                                onCheckedChange = { enabled ->
                                    if (enabled) {
                                        // 开启时打开设置对话框
                                        showAiSettingsDialog = true
                                    } else {
                                        // 关闭时直接禁用
                                        viewModel.setAiEnabled(false)
                                    }
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
                            onClick = { showAiSettingsDialog = true }
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

    // AI Settings Dialog
    if (showAiSettingsDialog) {
        AiSettingsDialog(
            apiBaseUrl = apiBaseUrl,
            apiKey = apiKey,
            aiModel = aiModel,
            aiEnabled = aiEnabled,
            onDismiss = { showAiSettingsDialog = false },
            onSave = { url, key, model, enabled ->
                viewModel.setApiBaseUrl(url)
                viewModel.setApiKey(key)
                viewModel.setAiModel(model)
                viewModel.setAiEnabled(enabled)
                showAiSettingsDialog = false
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsDialog(
    apiBaseUrl: String,
    apiKey: String,
    aiModel: String,
    aiEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (url: String, key: String, model: String, enabled: Boolean) -> Unit
) {
    var urlInput by remember { mutableStateOf(apiBaseUrl) }
    var keyInput by remember { mutableStateOf(apiKey) }
    var modelInput by remember { mutableStateOf(aiModel) }
    var enabledInput by remember { mutableStateOf(aiEnabled) }
    var modelDropdownExpanded by remember { mutableStateOf(false) }

    val modelOptions = listOf(
        "gpt-4o" to "GPT-4o (推荐)",
        "gpt-4o-mini" to "GPT-4o Mini",
        "gpt-4-turbo" to "GPT-4 Turbo",
        "gpt-3.5-turbo" to "GPT-3.5 Turbo",
        "claude-3-5-sonnet-20241022" to "Claude 3.5 Sonnet",
        "claude-3-haiku-20240307" to "Claude 3 Haiku"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 设置") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Enable switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("启用 AI 导师")
                    Switch(
                        checked = enabledInput,
                        onCheckedChange = { enabledInput = it }
                    )
                }

                HorizontalDivider()

                // API Base URL
                OutlinedTextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    label = { Text("API Base URL") },
                    placeholder = { Text("https://api.openai.com/v1") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // API Key
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text("API Key") },
                    placeholder = { Text("sk-...") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )

                // Model selector
                ExposedDropdownMenuBox(
                    expanded = modelDropdownExpanded,
                    onExpandedChange = { modelDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = modelOptions.find { it.first == modelInput }?.second ?: modelInput,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("模型") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = modelDropdownExpanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                    )
                    ExposedDropdownMenu(
                        expanded = modelDropdownExpanded,
                        onDismissRequest = { modelDropdownExpanded = false }
                    ) {
                        modelOptions.forEach { (value, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    modelInput = value
                                    modelDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Text(
                    text = "支持 OpenAI 兼容 API 和 Claude 等主流 AI 接口",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(urlInput, keyInput, modelInput, enabledInput) }
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
