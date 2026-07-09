package com.github.garynasser.correction_notebook.ui.screens.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.garynasser.correction_notebook.data.model.auth.AuthState
import com.github.garynasser.correction_notebook.ui.screens.aitutor.AITutorUiState
import com.github.garynasser.correction_notebook.ui.screens.aitutor.ProviderDialog
import com.github.garynasser.correction_notebook.ui.components.FreshCard
import com.github.garynasser.correction_notebook.ui.components.FreshScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel: ProfileViewModel = hiltViewModel(),
    onNavigateToLogin: () -> Unit = {},
    onCheckForUpdates: () -> Unit = {},
    currentVersionName: String = ""
) {
    val authState by viewModel.authState.collectAsState()
    val aiEnabled by viewModel.aiEnabled.collectAsState()
    val activeProvider by viewModel.activeProvider.collectAsState()
    val providers by viewModel.providers.collectAsState()
    val fetchedModels by viewModel.fetchedModels.collectAsState()
    val isProviderBusy by viewModel.isProviderBusy.collectAsState()
    val providerStatusMessage by viewModel.providerStatusMessage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showAiSettingsDialog by remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("个人中心") },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        FreshScreen(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // 用户信息卡片
            item {
                FreshCard(
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
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            modifier = Modifier.size(52.dp),
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
                                    modifier = Modifier.size(32.dp),
                                    tint = if (authState is AuthState.Authenticated)
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    else
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (authState is AuthState.Authenticated) "延河课堂已登录" else "延河课堂未登录",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = if (authState is AuthState.Authenticated)
                                    "点击退出延河课堂账号"
                                else
                                    "点击登录北理工统一认证",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                            )
                        }

                        if (authState is AuthState.Authenticated) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "退出延河课堂")
                        } else {
                            Icon(Icons.Default.ChevronRight, contentDescription = null)
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(2.dp)) }

            // AI 功能开关
            item {
                FreshCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    ListItem(
                        headlineContent = { Text("AI导师功能") },
                        supportingContent = {
                            Text(
                                if (aiEnabled) {
                                    activeProvider?.let { "已启用 - ${it.defaultModel}" } ?: "已启用 - 请配置 Provider"
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

            item { Spacer(modifier = Modifier.height(2.dp)) }

            // 设置列表
            item {
                FreshCard(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        SettingsItem(
                            icon = Icons.Default.Key,
                            title = "API配置",
                            subtitle = activeProvider?.let { "${it.name} · ${it.defaultModel}" } ?: "设置 AI Provider、模型和接口密钥",
                            onClick = { showAiSettingsDialog = true }
                        )
                        HorizontalDivider()
                        SettingsItem(
                            icon = Icons.Default.SystemUpdate,
                            title = "检查更新",
                            subtitle = "当前版本 $currentVersionName",
                            onClick = onCheckForUpdates
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

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
        }
    }

    // AI Settings Dialog
    if (showAiSettingsDialog) {
        ProviderDialog(
            uiState = AITutorUiState(
                activeProvider = activeProvider,
                providers = providers,
                fetchedModels = fetchedModels,
                isProviderBusy = isProviderBusy,
                providerStatusMessage = providerStatusMessage
            ),
            onDismiss = { showAiSettingsDialog = false },
            onSave = {
                viewModel.saveProvider(it)
            },
            onFetchModels = viewModel::fetchModels,
            onTestProvider = viewModel::testProvider,
            onClearProviderStatus = viewModel::clearProviderStatus,
            onActivate = viewModel::activateProvider,
            onDelete = viewModel::deleteProvider
        )
    }

    // Logout Dialog
    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("退出延河课堂") },
            text = { Text("确定要清除当前延河课堂登录吗？") },
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
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall
            )
        },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(22.dp)
            )
        },
        trailingContent = {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(22.dp)
            )
        },
        modifier = Modifier.clickable(onClick = onClick)
    )
}
