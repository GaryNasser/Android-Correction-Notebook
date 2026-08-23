package com.github.garynasser.correction_notebook.ui.screens.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.garynasser.correction_notebook.data.model.auth.AuthState
import com.github.garynasser.correction_notebook.data.repository.ProviderRecord
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
    currentVersionName: String = "",
    isCheckingForUpdates: Boolean = false,
) {
    val authState by viewModel.authState.collectAsStateWithLifecycle()
    val aiEnabled by viewModel.aiEnabled.collectAsStateWithLifecycle()
    val activeProvider by viewModel.activeProvider.collectAsStateWithLifecycle()
    val providers by viewModel.providers.collectAsStateWithLifecycle()
    val fetchedModels by viewModel.fetchedModels.collectAsStateWithLifecycle()
    val isProviderBusy by viewModel.isProviderBusy.collectAsStateWithLifecycle()
    val providerStatusMessage by viewModel.providerStatusMessage.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val profileMessage by viewModel.profileMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showLogoutDialog by remember { mutableStateOf(false) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showAiSettingsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(profileMessage) {
        val message = profileMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeProfileMessage()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("设置") },
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
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    AccountStatusCard(
                        authState = authState,
                        enabled = !isLoading,
                        onClick = {
                            if (authState is AuthState.Unauthenticated) {
                                onNavigateToLogin()
                            } else {
                                showLogoutDialog = true
                            }
                        }
                    )
                }

                item {
                    AiStatusCard(
                        aiEnabled = aiEnabled,
                        activeProvider = activeProvider,
                        providerCount = providers.size,
                        isProviderBusy = isProviderBusy,
                        enabled = !isLoading,
                        onOpenSettings = { showAiSettingsDialog = true },
                        onCheckedChange = { enabled ->
                            if (enabled) {
                                showAiSettingsDialog = true
                            } else {
                                viewModel.setAiEnabled(false)
                            }
                        }
                    )
                }

                item {
                    FreshCard(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            SettingsItem(
                                icon = Icons.Default.Key,
                                title = "API配置",
                                subtitle = activeProvider?.let { "${it.name} · ${it.defaultModel}" } ?: "设置 AI Provider、模型和接口密钥",
                                enabled = !isLoading,
                                onClick = { showAiSettingsDialog = true }
                            )
                            HorizontalDivider()
                            SettingsItem(
                                icon = Icons.Default.SystemUpdate,
                                title = "检查更新",
                                subtitle = if (isCheckingForUpdates) {
                                    "正在检查更新..."
                                } else {
                                    "当前版本 ${currentVersionName.ifBlank { "未知" }}"
                                },
                                enabled = !isLoading && !isCheckingForUpdates,
                                onClick = onCheckForUpdates,
                                trailingContent = if (isCheckingForUpdates) {
                                    {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(18.dp),
                                            strokeWidth = 2.dp
                                        )
                                    }
                                } else {
                                    null
                                }
                            )
                            HorizontalDivider()
                            SettingsItem(
                                icon = Icons.AutoMirrored.Filled.Help,
                                title = "帮助与反馈",
                                subtitle = "常见问题、联系客服",
                                enabled = !isLoading,
                                onClick = { showFeedbackDialog = true }
                            )
                        }
                    }
                }

                item { Spacer(modifier = Modifier.height(8.dp)) }
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
            shape = MaterialTheme.shapes.extraSmall,
            title = { Text("退出延河课堂") },
            text = { Text("确定要清除当前延河课堂登录吗？") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.logout()
                        showLogoutDialog = false
                    },
                    enabled = !isLoading
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
            shape = MaterialTheme.shapes.extraSmall,
            title = { Text("帮助与反馈") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "如有 bug 或功能建议，请联系开发者邮箱：",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "fangmierui@gmail.com",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
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

    if (isLoading) {
        ProfileLoadingOverlay()
    }
}

@Composable
private fun AccountStatusCard(
    authState: AuthState,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val isAuthenticated = authState is AuthState.Authenticated

    FreshCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.size(38.dp),
                shape = RoundedCornerShape(8.dp),
                color = if (isAuthenticated) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.34f))
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (isAuthenticated) Icons.Default.AccountCircle else Icons.Default.Person,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = if (isAuthenticated) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "延河课堂",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    StatusChip(
                        text = if (isAuthenticated) "已登录" else "未登录",
                        isPositive = isAuthenticated
                    )
                }
                Text(
                    text = if (isAuthenticated) {
                        "可同步课程、观看记录和学习进度"
                    } else {
                        "登录北理工统一认证后使用课程相关功能"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Icon(
                imageVector = if (isAuthenticated) Icons.AutoMirrored.Filled.Logout else Icons.Default.ChevronRight,
                contentDescription = if (isAuthenticated) "退出延河课堂" else null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AiStatusCard(
    aiEnabled: Boolean,
    activeProvider: ProviderRecord?,
    providerCount: Int,
    isProviderBusy: Boolean,
    enabled: Boolean,
    onOpenSettings: () -> Unit,
    onCheckedChange: (Boolean) -> Unit
) {
    val hasActiveProvider = activeProvider != null

    FreshCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(vertical = 1.dp)
        ) {
            SettingsSwitchItem(
                icon = Icons.Default.SmartToy,
                title = "AI 导师功能",
                subtitle = when {
                    aiEnabled && hasActiveProvider -> "${activeProvider?.name} · ${activeProvider?.defaultModel}"
                    aiEnabled -> "已启用，但还需要配置可用 Provider"
                    else -> "关闭后隐藏 AI 入口和智能学习建议"
                },
                checked = aiEnabled,
                enabled = enabled && !isProviderBusy,
                onCheckedChange = onCheckedChange
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp))
            SettingsItem(
                icon = if (hasActiveProvider) Icons.Default.Verified else Icons.Default.Tune,
                title = if (hasActiveProvider) "当前 Provider" else "配置 Provider",
                subtitle = when {
                    hasActiveProvider -> "已保存 $providerCount 个配置，可测试连接或切换模型"
                    providerCount > 0 -> "已有配置但未激活，进入后选择默认 Provider"
                    else -> "添加 OpenAI 兼容、Anthropic 或自定义接口"
                },
                enabled = enabled,
                onClick = onOpenSettings
            )
        }
    }
}

@Composable
private fun StatusChip(
    text: String,
    isPositive: Boolean
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (isPositive) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.82f)
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.28f))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (isPositive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun ProfileLoadingOverlay() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)),
            tonalElevation = 0.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text(
                    text = "正在处理账号状态...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SettingsSwitchItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier.size(32.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.62f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.56f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
    trailingContent: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            modifier = Modifier.size(32.dp),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.62f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f))
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.48f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.48f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.56f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (trailingContent != null) {
            Box(
                modifier = Modifier.size(22.dp),
                contentAlignment = Alignment.Center
            ) {
                trailingContent()
            }
        } else {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.48f)
            )
        }
    }
}
