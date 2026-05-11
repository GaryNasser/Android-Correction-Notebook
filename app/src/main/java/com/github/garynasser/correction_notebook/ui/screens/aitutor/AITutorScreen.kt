package com.github.garynasser.correction_notebook.ui.screens.aitutor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.garynasser.correction_notebook.data.model.ai.AIProviderType
import com.github.garynasser.correction_notebook.data.model.ai.AiProviderForm
import com.github.garynasser.correction_notebook.data.repository.ProviderRecord

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AITutorScreen(
    viewModel: AITutorViewModel = hiltViewModel(),
    onNavigateToProfile: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    var inputText by remember { mutableStateOf("") }
    var showProviderDialog by remember { mutableStateOf(false) }
    var showMemoryDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 学习中枢") },
                windowInsets = WindowInsets(0, 0, 0, 0),
                actions = {
                    IconButton(onClick = { viewModel.newSession() }) {
                        Icon(Icons.Default.Add, contentDescription = "新建对话")
                    }
                    IconButton(onClick = { showMemoryDialog = true }) {
                        Icon(Icons.Default.Memory, contentDescription = "记忆")
                    }
                    IconButton(onClick = { showProviderDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "设置")
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("重命名当前对话") },
                                onClick = {
                                    menuExpanded = false
                                    showRenameDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("清空当前对话") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.clearMessages()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("删除当前对话") },
                                onClick = {
                                    menuExpanded = false
                                    viewModel.deleteCurrentSession()
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (!uiState.isConfigured) {
                EmptyAiState(onConfigure = { showProviderDialog = true })
            } else {
                AiTutorHeader(
                    uiState = uiState,
                    onSelectSession = viewModel::selectSession,
                    onKnowledgeModeChange = viewModel::setKnowledgeMode
                )

                if (uiState.messages.isEmpty()) {
                    EmptyChatState(
                        isKnowledgeMode = uiState.isKnowledgeMode,
                        onSuggestion = { inputText = it }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(uiState.messages, key = { it.id }) { message ->
                            ChatMessageItem(message)
                        }
                    }
                }

                uiState.error?.let { errorMessage ->
                    ErrorBanner(message = errorMessage, onDismiss = viewModel::clearError)
                }

                Surface(modifier = Modifier.fillMaxWidth(), tonalElevation = 3.dp) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(if (uiState.isKnowledgeMode) "询问知识库资料..." else "输入学习问题...")
                            },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (inputText.isNotBlank() && !uiState.isLoading) {
                                        viewModel.sendMessage(inputText)
                                        inputText = ""
                                    }
                                }
                            ),
                            maxLines = 4,
                            enabled = !uiState.isLoading
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledIconButton(
                            onClick = {
                                viewModel.sendMessage(inputText)
                                inputText = ""
                            },
                            enabled = inputText.isNotBlank() && !uiState.isLoading
                        ) {
                            if (uiState.isLoading) {
                                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "发送")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showProviderDialog) {
        ProviderDialog(
            uiState = uiState,
            onDismiss = { showProviderDialog = false },
            onSave = {
                viewModel.saveProvider(it)
                showProviderDialog = false
            },
            onActivate = viewModel::activateProvider,
            onDelete = viewModel::deleteProvider
        )
    }

    if (showMemoryDialog) {
        MemoryDialog(
            uiState = uiState,
            onDismiss = { showMemoryDialog = false },
            onSave = viewModel::saveMemory,
            onDelete = viewModel::deleteMemory
        )
    }

    if (showRenameDialog) {
        var title by remember(uiState.selectedSessionId) {
            mutableStateOf(uiState.sessions.firstOrNull { it.id == uiState.selectedSessionId }?.title.orEmpty())
        }
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text("重命名对话") },
            text = {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("标题") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.renameCurrentSession(title)
                    showRenameDialog = false
                }) { Text("保存") }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun EmptyAiState(onConfigure: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.SmartToy,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text("AI Provider 未配置", style = MaterialTheme.typography.titleMedium)
            Text("添加 OpenAI 兼容或 Anthropic 兼容接口后即可使用")
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onConfigure) { Text("配置 AI") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AiTutorHeader(
    uiState: AITutorUiState,
    onSelectSession: (Long) -> Unit,
    onKnowledgeModeChange: (Boolean) -> Unit
) {
    var sessionExpanded by remember { mutableStateOf(false) }
    val selectedSession = uiState.sessions.firstOrNull { it.id == uiState.selectedSessionId }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Psychology, contentDescription = null)
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(uiState.activeProvider?.name.orEmpty(), fontWeight = FontWeight.Medium)
                    Text(
                        text = uiState.activeProvider?.defaultModel.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
                AssistChip(
                    onClick = { onKnowledgeModeChange(!uiState.isKnowledgeMode) },
                    leadingIcon = { Icon(Icons.Default.LibraryBooks, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    label = { Text(if (uiState.isKnowledgeMode) "资料问答" else "普通问答") }
                )
            }

            ExposedDropdownMenuBox(
                expanded = sessionExpanded,
                onExpandedChange = { sessionExpanded = it }
            ) {
                OutlinedTextField(
                    value = selectedSession?.title ?: "选择对话",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("当前对话") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sessionExpanded) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor()
                )
                ExposedDropdownMenu(
                    expanded = sessionExpanded,
                    onDismissRequest = { sessionExpanded = false }
                ) {
                    uiState.sessions.forEach { session ->
                        DropdownMenuItem(
                            text = {
                                Text(session.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            onClick = {
                                onSelectSession(session.id)
                                sessionExpanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChatState(isKnowledgeMode: Boolean, onSuggestion: (String) -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (isKnowledgeMode) Icons.Default.LibraryBooks else Icons.Default.SmartToy,
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(if (isKnowledgeMode) "问你的知识库" else "AI 学习导师已就绪")
            Spacer(modifier = Modifier.height(12.dp))
            SuggestionChip(
                onClick = {
                    onSuggestion(
                        if (isKnowledgeMode) "根据我的资料，总结一下最近需要复习的重点"
                        else "帮我制定今天的学习安排"
                    )
                },
                label = { Text(if (isKnowledgeMode) "总结资料重点" else "制定学习安排") }
            )
        }
    }
}

@Composable
fun ChatMessageItem(message: ChatUiMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!message.isUser) {
            Avatar(isUser = false)
            Spacer(modifier = Modifier.width(8.dp))
        }
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = if (message.isUser) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.width(300.dp)
        ) {
            Text(
                text = message.content,
                modifier = Modifier.padding(12.dp),
                color = if (message.isUser) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (message.isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Avatar(isUser = true)
        }
    }
}

@Composable
private fun Avatar(isUser: Boolean) {
    Surface(
        modifier = Modifier.size(32.dp),
        shape = MaterialTheme.shapes.small,
        color = if (isUser) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                if (isUser) Icons.Default.Person else Icons.Default.SmartToy,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = if (isUser) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        shape = MaterialTheme.shapes.small
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
            Spacer(modifier = Modifier.width(8.dp))
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "关闭", tint = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProviderDialog(
    uiState: AITutorUiState,
    onDismiss: () -> Unit,
    onSave: (AiProviderForm) -> Unit,
    onActivate: (Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    var form by remember(uiState.activeProvider?.id) {
        mutableStateOf(uiState.activeProvider?.toForm() ?: AiProviderForm())
    }
    var typeExpanded by remember { mutableStateOf(false) }
    var presetExpanded by remember { mutableStateOf(false) }
    var modelExpanded by remember { mutableStateOf(false) }
    var showAdvanced by remember { mutableStateOf(false) }
    val modelOptions = modelOptionsFor(form.type)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI Provider 配置") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    ExposedDropdownMenuBox(
                        expanded = presetExpanded,
                        onExpandedChange = { presetExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = "选择常用服务商预设",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("服务商预设") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(presetExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(presetExpanded, { presetExpanded = false }) {
                            providerPresets.forEach { preset ->
                                DropdownMenuItem(
                                    text = { Text(preset.label) },
                                    onClick = {
                                        form = form.copy(
                                            name = preset.label,
                                            type = preset.type,
                                            baseUrl = preset.baseUrl,
                                            model = preset.defaultModel,
                                            customHeaders = preset.customHeaders
                                        )
                                        presetExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = form.name,
                        onValueChange = { form = form.copy(name = it) },
                        label = { Text("名称") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    ExposedDropdownMenuBox(
                        expanded = typeExpanded,
                        onExpandedChange = { typeExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = when (form.type) {
                                AIProviderType.OPENAI_COMPATIBLE -> "OpenAI 兼容"
                                AIProviderType.ANTHROPIC_COMPATIBLE -> "Anthropic 兼容"
                            },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("接口类型") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(typeExpanded) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor()
                        )
                        ExposedDropdownMenu(typeExpanded, { typeExpanded = false }) {
                            AIProviderType.values().forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(if (type == AIProviderType.OPENAI_COMPATIBLE) "OpenAI 兼容" else "Anthropic 兼容") },
                                    onClick = {
                                        form = form.copy(type = type)
                                        typeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                item {
                    OutlinedTextField(
                        value = form.baseUrl,
                        onValueChange = { form = form.copy(baseUrl = it) },
                        label = { Text("Base URL") },
                        supportingText = { Text(if (form.type == AIProviderType.OPENAI_COMPATIBLE) "示例：https://api.openai.com/v1 或兼容服务 /v1" else "示例：https://api.anthropic.com/v1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = form.apiKey,
                        onValueChange = { form = form.copy(apiKey = it) },
                        label = { Text("API Key") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Box {
                        OutlinedTextField(
                            value = form.model,
                            onValueChange = { form = form.copy(model = it) },
                            label = { Text("默认模型") },
                            supportingText = { Text("可直接输入任意模型名，例如 gpt-4o-mini、deepseek-chat、claude-3-5-sonnet-20241022") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth(),
                            trailingIcon = {
                                TextButton(onClick = { modelExpanded = true }) {
                                    Text("常用")
                                }
                            }
                        )
                        DropdownMenu(
                            expanded = modelExpanded,
                            onDismissRequest = { modelExpanded = false }
                        ) {
                            modelOptions.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model) },
                                    onClick = {
                                        form = form.copy(model = model)
                                        modelExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("高级参数", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Headers、温度、最大输出、上下文长度",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = showAdvanced, onCheckedChange = { showAdvanced = it })
                    }
                }
                if (showAdvanced) {
                    item {
                        OutlinedTextField(
                            value = form.customHeaders,
                            onValueChange = { form = form.copy(customHeaders = it) },
                            label = { Text("自定义 Headers") },
                            supportingText = { Text("支持 JSON：{\"HTTP-Referer\":\"...\"}，或每行 Key: Value") },
                            minLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = form.temperature,
                                onValueChange = { form = form.copy(temperature = it) },
                                label = { Text("Temperature") },
                                placeholder = { Text("0.7") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = form.maxTokens,
                                onValueChange = { form = form.copy(maxTokens = it) },
                                label = { Text("Max Tokens") },
                                placeholder = { Text("2048") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = form.contextMessageLimit,
                            onValueChange = { form = form.copy(contextMessageLimit = it) },
                            label = { Text("上下文消息数") },
                            supportingText = { Text("建议 8-20，较大值会增加费用和上下文长度") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
                if (uiState.providers.isNotEmpty()) {
                    item {
                        HorizontalDivider()
                        Text("已保存 Provider", style = MaterialTheme.typography.titleSmall)
                    }
                    items(uiState.providers) { provider ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(provider.name, fontWeight = FontWeight.Medium)
                                Text(
                                    "${provider.type.displayName()} · ${provider.defaultModel}",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(onClick = { onActivate(provider.id) }) { Text("启用") }
                            IconButton(onClick = { onDelete(provider.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "删除")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(form) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

@Composable
private fun MemoryDialog(
    uiState: AITutorUiState,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
    onDelete: (Long) -> Unit
) {
    var category by remember { mutableStateOf("学习偏好") }
    var content by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AI 记忆") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text("AI 会把这些信息作为长期学习偏好使用，你可以随时删除。")
                }
                item {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("分类") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = content,
                        onValueChange = { content = it },
                        label = { Text("记忆内容") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
                item {
                    Button(
                        onClick = {
                            onSave(category, content)
                            content = ""
                        },
                        enabled = content.isNotBlank()
                    ) { Text("保存记忆") }
                }
                items(uiState.memories) { memory ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("[${memory.category}] ${memory.content}")
                        }
                        IconButton(onClick = { onDelete(memory.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("完成") }
        }
    )
}

private fun ProviderRecord.toForm(): AiProviderForm =
    AiProviderForm(
        id = id,
        name = name,
        type = type,
        baseUrl = baseUrl,
        apiKey = apiKey,
        model = defaultModel,
        customHeaders = customHeadersJson,
        temperature = temperature?.toString().orEmpty(),
        maxTokens = maxTokens?.toString().orEmpty(),
        contextMessageLimit = contextMessageLimit.toString(),
        isActive = isActive
    )

private data class ProviderPreset(
    val label: String,
    val type: AIProviderType,
    val baseUrl: String,
    val defaultModel: String,
    val customHeaders: String = "{}"
)

private val providerPresets = listOf(
    ProviderPreset("OpenAI", AIProviderType.OPENAI_COMPATIBLE, "https://api.openai.com/v1", "gpt-4o-mini"),
    ProviderPreset("OpenRouter", AIProviderType.OPENAI_COMPATIBLE, "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
    ProviderPreset("DeepSeek", AIProviderType.OPENAI_COMPATIBLE, "https://api.deepseek.com/v1", "deepseek-chat"),
    ProviderPreset("通义千问 Qwen", AIProviderType.OPENAI_COMPATIBLE, "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus"),
    ProviderPreset("Moonshot Kimi", AIProviderType.OPENAI_COMPATIBLE, "https://api.moonshot.cn/v1", "moonshot-v1-8k"),
    ProviderPreset("智谱 GLM", AIProviderType.OPENAI_COMPATIBLE, "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash"),
    ProviderPreset("本地 Ollama", AIProviderType.OPENAI_COMPATIBLE, "http://127.0.0.1:11434/v1", "qwen2.5:7b"),
    ProviderPreset("Anthropic Claude", AIProviderType.ANTHROPIC_COMPATIBLE, "https://api.anthropic.com/v1", "claude-3-5-sonnet-20241022")
)

private fun modelOptionsFor(type: AIProviderType): List<String> {
    return when (type) {
        AIProviderType.OPENAI_COMPATIBLE -> listOf(
            "gpt-4o-mini",
            "gpt-4o",
            "gpt-4.1-mini",
            "gpt-4.1",
            "deepseek-chat",
            "deepseek-reasoner",
            "qwen-plus",
            "qwen-max",
            "moonshot-v1-8k",
            "glm-4-flash",
            "llama3.1:8b",
            "qwen2.5:7b"
        )
        AIProviderType.ANTHROPIC_COMPATIBLE -> listOf(
            "claude-3-5-sonnet-20241022",
            "claude-3-5-haiku-20241022",
            "claude-3-opus-20240229",
            "claude-3-haiku-20240307"
        )
    }
}

private fun AIProviderType.displayName(): String =
    when (this) {
        AIProviderType.OPENAI_COMPATIBLE -> "OpenAI 兼容"
        AIProviderType.ANTHROPIC_COMPATIBLE -> "Anthropic 兼容"
    }
