package com.github.garynasser.correction_notebook.ui.screens.aitutor

import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.garynasser.correction_notebook.data.model.ai.AIProviderType
import com.github.garynasser.correction_notebook.data.model.ai.AiProviderForm
import com.github.garynasser.correction_notebook.data.repository.ProviderRecord
import java.nio.charset.StandardCharsets

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
            },
            onFetchModels = viewModel::fetchModels,
            onTestProvider = viewModel::testProvider,
            onClearProviderStatus = viewModel::clearProviderStatus,
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
            modifier = Modifier
                .fillMaxWidth(if (message.isUser) 0.78f else 0.86f)
                .widthIn(max = 560.dp)
        ) {
            SelectionContainer {
                if (message.isUser) {
                    Text(
                        text = message.content,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    MarkdownMessage(
                        content = message.content,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }
        }
        if (message.isUser) {
            Spacer(modifier = Modifier.width(8.dp))
            Avatar(isUser = true)
        }
    }
}

@Composable
private fun MarkdownMessage(
    content: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(content) { parseMarkdownBlocks(content) }
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        blocks.forEach { block ->
            when (block) {
                is MarkdownBlock.Code -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = block.text,
                            modifier = Modifier.padding(10.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                is MarkdownBlock.Math -> {
                    MathFormulaView(
                        formula = block.text,
                        displayMode = block.displayMode
                    )
                }
                MarkdownBlock.HorizontalRule -> {
                    HorizontalDivider(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
                is MarkdownBlock.Table -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = block.rows.joinToString("\n") { row ->
                                row.joinToString("  |  ") { it.trim() }
                            },
                            modifier = Modifier.padding(10.dp),
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
                is MarkdownBlock.Heading -> {
                    Text(
                        text = inlineMarkdown(block.text),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.titleMedium
                            2 -> MaterialTheme.typography.titleSmall
                            3 -> MaterialTheme.typography.bodyLarge
                            else -> MaterialTheme.typography.bodyMedium
                        },
                        fontWeight = FontWeight.SemiBold
                    )
                }
                is MarkdownBlock.Quote -> {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = inlineMarkdown(block.text),
                            modifier = Modifier.padding(10.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                is MarkdownBlock.ListItem -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = block.marker,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = inlineMarkdown(block.text),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                is MarkdownBlock.Paragraph -> {
                    Text(
                        text = inlineMarkdown(block.text),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

private sealed interface MarkdownBlock {
    data class Paragraph(val text: String) : MarkdownBlock
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class ListItem(val marker: String, val text: String) : MarkdownBlock
    data class Quote(val text: String) : MarkdownBlock
    data class Code(val text: String) : MarkdownBlock
    data class Math(val text: String, val displayMode: Boolean) : MarkdownBlock
    data class Table(val rows: List<List<String>>) : MarkdownBlock
    data object HorizontalRule : MarkdownBlock
}

private fun parseMarkdownBlocks(content: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraph = mutableListOf<String>()
    val quote = mutableListOf<String>()
    val tableRows = mutableListOf<List<String>>()
    val code = StringBuilder()
    val math = StringBuilder()
    var inCodeBlock = false
    var inMathBlock = false
    var mathEndFence = "$$"

    fun flushParagraph() {
        if (paragraph.isNotEmpty()) {
            blocks += MarkdownBlock.Paragraph(paragraph.joinToString(" ").trim())
            paragraph.clear()
        }
    }
    fun flushQuote() {
        if (quote.isNotEmpty()) {
            blocks += MarkdownBlock.Quote(quote.joinToString("\n").trim())
            quote.clear()
        }
    }
    fun flushTable() {
        if (tableRows.isNotEmpty()) {
            val rows = tableRows.filterNot { row ->
                row.isNotEmpty() && row.all { cell -> cell.trim().matches(Regex(":?-{3,}:?")) }
            }
            if (rows.isNotEmpty()) blocks += MarkdownBlock.Table(rows)
            tableRows.clear()
        }
    }
    fun flushTextBlocks() {
        flushParagraph()
        flushQuote()
        flushTable()
    }

    content.lineSequence().forEach { rawLine ->
        val line = rawLine.trimEnd()
        val trimmed = line.trim()
        if (inMathBlock) {
            if (trimmed == mathEndFence) {
                blocks += MarkdownBlock.Math(math.toString().trim(), displayMode = true)
                math.clear()
                inMathBlock = false
            } else {
                math.appendLine(rawLine)
            }
            return@forEach
        }

        if (trimmed.startsWith("```")) {
            if (inCodeBlock) {
                blocks += MarkdownBlock.Code(code.toString().trimEnd())
                code.clear()
                inCodeBlock = false
            } else {
                flushTextBlocks()
                inCodeBlock = true
            }
            return@forEach
        }

        if (inCodeBlock) {
            code.appendLine(rawLine)
            return@forEach
        }

        val singleLineDollarMath = Regex("""^\$\$(.+)\$\$$""").find(trimmed)
        val singleLineBracketMath = Regex("""^\\\[(.+)\\\]$""").find(trimmed)
        when {
            singleLineDollarMath != null -> {
                flushTextBlocks()
                blocks += MarkdownBlock.Math(singleLineDollarMath.groupValues[1].trim(), displayMode = true)
                return@forEach
            }
            singleLineBracketMath != null -> {
                flushTextBlocks()
                blocks += MarkdownBlock.Math(singleLineBracketMath.groupValues[1].trim(), displayMode = true)
                return@forEach
            }
            trimmed == "$$" || trimmed == "\\[" -> {
                flushTextBlocks()
                inMathBlock = true
                mathEndFence = if (trimmed == "$$") "$$" else "\\]"
                return@forEach
            }
        }

        if (trimmed.isBlank()) {
            flushTextBlocks()
            return@forEach
        }

        val headingLevel = trimmed.takeWhile { it == '#' }.length
        val orderedMatch = Regex("""^(\d+)[.)]\s+(.+)$""").find(trimmed)
        val taskMatch = Regex("""^[-*+]\s+\[([ xX])]\s+(.+)$""").find(trimmed)
        when {
            trimmed.matches(Regex("""^([-*_])\1{2,}\s*$""")) -> {
                flushTextBlocks()
                blocks += MarkdownBlock.HorizontalRule
            }
            isTableLine(trimmed) -> {
                flushParagraph()
                flushQuote()
                tableRows += trimmed.trim('|').split('|').map { it.trim() }
            }
            headingLevel in 1..6 && trimmed.getOrNull(headingLevel) == ' ' -> {
                flushTextBlocks()
                blocks += MarkdownBlock.Heading(headingLevel, trimmed.drop(headingLevel).trim())
            }
            trimmed.startsWith(">") -> {
                flushParagraph()
                flushTable()
                quote += trimmed.removePrefix(">").trim()
            }
            taskMatch != null -> {
                flushTextBlocks()
                val checked = taskMatch.groupValues[1].isNotBlank()
                blocks += MarkdownBlock.ListItem(if (checked) "☑" else "☐", taskMatch.groupValues[2].trim())
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ") || trimmed.startsWith("• ") -> {
                flushTextBlocks()
                blocks += MarkdownBlock.ListItem("•", trimmed.drop(2).trim())
            }
            orderedMatch != null -> {
                flushTextBlocks()
                blocks += MarkdownBlock.ListItem("${orderedMatch.groupValues[1]}.", orderedMatch.groupValues[2].trim())
            }
            else -> {
                flushQuote()
                flushTable()
                paragraph += trimmed
            }
        }
    }
    if (inCodeBlock && code.isNotBlank()) {
        blocks += MarkdownBlock.Code(code.toString().trimEnd())
    }
    if (inMathBlock && math.isNotBlank()) {
        blocks += MarkdownBlock.Math(math.toString().trim(), displayMode = true)
    }
    flushTextBlocks()
    return blocks.ifEmpty { listOf(MarkdownBlock.Paragraph(content)) }
}

private fun isTableLine(line: String): Boolean {
    if (!line.contains("|")) return false
    val cells = line.trim('|').split('|')
    return cells.size >= 2 && cells.any { it.trim().isNotEmpty() }
}

@Composable
private fun inlineMarkdown(text: String): AnnotatedString {
    val codeBackground = MaterialTheme.colorScheme.surface
    val primary = MaterialTheme.colorScheme.primary
    return buildAnnotatedString {
        var index = 0
        while (index < text.length) {
            when {
                text.startsWith("**", index) || text.startsWith("__", index) -> {
                    val marker = text.substring(index, index + 2)
                    val end = text.indexOf(marker, startIndex = index + 2)
                    if (end > index) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(index + 2, end))
                        }
                        index = end + 2
                    } else {
                        append(text[index])
                        index++
                    }
                }
                text.startsWith("~~", index) -> {
                    val end = text.indexOf("~~", startIndex = index + 2)
                    if (end > index) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            append(text.substring(index + 2, end))
                        }
                        index = end + 2
                    } else {
                        append(text[index])
                        index++
                    }
                }
                text.startsWith("$$", index) -> {
                    val end = text.indexOf("$$", startIndex = index + 2)
                    if (end > index) {
                        appendInlineMath(text.substring(index + 2, end), codeBackground, primary)
                        index = end + 2
                    } else {
                        append(text[index])
                        index++
                    }
                }
                text.startsWith("\\(", index) -> {
                    val end = text.indexOf("\\)", startIndex = index + 2)
                    if (end > index) {
                        appendInlineMath(text.substring(index + 2, end), codeBackground, primary)
                        index = end + 2
                    } else {
                        append(text[index])
                        index++
                    }
                }
                text[index] == '$' -> {
                    val end = text.indexOf('$', startIndex = index + 1)
                    if (end > index + 1) {
                        appendInlineMath(text.substring(index + 1, end), codeBackground, primary)
                        index = end + 1
                    } else {
                        append(text[index])
                        index++
                    }
                }
                text[index] == '*' || text[index] == '_' -> {
                    val marker = text[index]
                    val end = text.indexOf(marker, startIndex = index + 1)
                    if (end > index + 1 && text.getOrNull(index + 1) != marker) {
                        withStyle(SpanStyle(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                            append(text.substring(index + 1, end))
                        }
                        index = end + 1
                    } else {
                        append(text[index])
                        index++
                    }
                }
                text[index] == '[' -> {
                    val labelEnd = text.indexOf("](", startIndex = index)
                    val urlEnd = if (labelEnd > index) text.indexOf(')', startIndex = labelEnd + 2) else -1
                    if (labelEnd > index && urlEnd > labelEnd) {
                        val label = text.substring(index + 1, labelEnd)
                        val url = text.substring(labelEnd + 2, urlEnd)
                        withStyle(SpanStyle(color = primary, textDecoration = TextDecoration.Underline)) {
                            append(label)
                        }
                        append(" ($url)")
                        index = urlEnd + 1
                    } else {
                        append(text[index])
                        index++
                    }
                }
                text[index] == '`' -> {
                    val end = text.indexOf('`', startIndex = index + 1)
                    if (end > index) {
                        withStyle(
                            SpanStyle(
                                fontFamily = FontFamily.Monospace,
                                background = codeBackground,
                                color = primary
                            )
                        ) {
                            append(text.substring(index + 1, end))
                        }
                        index = end + 1
                    } else {
                        append(text[index])
                        index++
                    }
                }
                else -> {
                    append(text[index])
                    index++
                }
            }
        }
    }
}

private fun AnnotatedString.Builder.appendInlineMath(
    formula: String,
    background: androidx.compose.ui.graphics.Color,
    color: androidx.compose.ui.graphics.Color
) {
    withStyle(
        SpanStyle(
            fontFamily = FontFamily.Monospace,
            background = background,
            color = color
        )
    ) {
        append(formula.trim())
    }
}

@Composable
private fun MathFormulaView(
    formula: String,
    displayMode: Boolean,
    modifier: Modifier = Modifier
) {
    val html = remember(formula, displayMode) { katexHtml(formula, displayMode) }
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
        shape = MaterialTheme.shapes.small,
        modifier = modifier.fillMaxWidth()
    ) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp, max = 220.dp),
            factory = { context ->
                WebView(context).apply {
                    webViewClient = WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = false
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            },
            update = { webView ->
                webView.loadDataWithBaseURL(
                    "https://cdn.jsdelivr.net/",
                    html,
                    "text/html",
                    "UTF-8",
                    null
                )
            }
        )
    }
}

private fun katexHtml(formula: String, displayMode: Boolean): String {
    val encodedFormula = Base64.encodeToString(
        formula.toByteArray(StandardCharsets.UTF_8),
        Base64.NO_WRAP
    )
    val display = if (displayMode) "true" else "false"
    return """
        <!doctype html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.css">
            <script defer src="https://cdn.jsdelivr.net/npm/katex@0.16.11/dist/katex.min.js"></script>
            <style>
                html, body {
                    margin: 0;
                    padding: 6px;
                    background: transparent;
                    color: #1f2937;
                    font-size: 16px;
                    overflow-x: auto;
                    overflow-y: hidden;
                }
                #formula { min-height: 36px; }
                .fallback {
                    font-family: monospace;
                    white-space: pre-wrap;
                    word-break: break-word;
                }
            </style>
        </head>
        <body>
            <div id="formula"></div>
            <script>
                const formula = decodeURIComponent(escape(atob("$encodedFormula")));
                window.addEventListener("load", function () {
                    try {
                        katex.render(formula, document.getElementById("formula"), {
                            throwOnError: false,
                            displayMode: $display,
                            strict: "ignore"
                        });
                    } catch (error) {
                        document.getElementById("formula").textContent = formula;
                        document.getElementById("formula").className = "fallback";
                    }
                });
            </script>
        </body>
        </html>
    """.trimIndent()
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
    onFetchModels: (AiProviderForm) -> Unit,
    onTestProvider: (AiProviderForm) -> Unit,
    onClearProviderStatus: () -> Unit,
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
    var fetchedModelsScopeKey by remember { mutableStateOf<String?>(null) }
    val formScopeKey = form.modelScopeKey()
    val fetchedModelOptions = if (fetchedModelsScopeKey == formScopeKey) {
        uiState.fetchedModels.map { it.id }
    } else {
        emptyList()
    }
    val modelOptions = (fetchedModelOptions + modelOptionsFor(form.type)).distinct()
    fun updateForm(next: AiProviderForm) {
        if (next.modelScopeKey() != form.modelScopeKey()) {
            fetchedModelsScopeKey = null
        }
        form = next
    }

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
                                        updateForm(form.copy(
                                            name = preset.label,
                                            type = preset.type,
                                            baseUrl = preset.baseUrl,
                                            model = preset.defaultModel,
                                            customHeaders = preset.customHeaders
                                        ))
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
                        onValueChange = { updateForm(form.copy(name = it)) },
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
                                        updateForm(form.copy(type = type))
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
                        onValueChange = { updateForm(form.copy(baseUrl = it)) },
                        label = { Text("Base URL") },
                        supportingText = { Text(if (form.type == AIProviderType.OPENAI_COMPATIBLE) "示例：https://api.openai.com/v1 或兼容服务 /v1" else "示例：https://api.anthropic.com/v1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = form.apiKey,
                        onValueChange = { updateForm(form.copy(apiKey = it)) },
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
                            onValueChange = { updateForm(form.copy(model = it)) },
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
                                        updateForm(form.copy(model = model))
                                        modelExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                onClearProviderStatus()
                                fetchedModelsScopeKey = formScopeKey
                                onFetchModels(form)
                            },
                            enabled = !uiState.isProviderBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            if (uiState.isProviderBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            } else {
                                Text("获取模型")
                            }
                        }
                        Button(
                            onClick = {
                                onClearProviderStatus()
                                onTestProvider(form)
                            },
                            enabled = !uiState.isProviderBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("测试连接")
                        }
                    }
                }
                uiState.providerStatusMessage?.let { message ->
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = message,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodySmall
                            )
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
                            onValueChange = { updateForm(form.copy(customHeaders = it)) },
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
                                onValueChange = { updateForm(form.copy(temperature = it)) },
                                label = { Text("Temperature") },
                                placeholder = { Text("0.7") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = form.maxTokens,
                                onValueChange = { updateForm(form.copy(maxTokens = it)) },
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
                            onValueChange = { updateForm(form.copy(contextMessageLimit = it)) },
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
                            TextButton(onClick = { updateForm(provider.toForm()) }) { Text("编辑") }
                            TextButton(onClick = { onActivate(provider.id) }) {
                                Text(if (provider.isActive) "已启用" else "启用")
                            }
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

private fun AiProviderForm.modelScopeKey(): String =
    "${type.name}|${baseUrl.trim().trimEnd('/')}|${apiKey.trim().takeLast(6)}|${customHeaders.trim()}"

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
    ProviderPreset("Google Gemini OpenAI", AIProviderType.OPENAI_COMPATIBLE, "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-1.5-flash"),
    ProviderPreset("Groq", AIProviderType.OPENAI_COMPATIBLE, "https://api.groq.com/openai/v1", "llama-3.1-8b-instant"),
    ProviderPreset("Mistral", AIProviderType.OPENAI_COMPATIBLE, "https://api.mistral.ai/v1", "mistral-small-latest"),
    ProviderPreset("xAI", AIProviderType.OPENAI_COMPATIBLE, "https://api.x.ai/v1", "grok-2-latest"),
    ProviderPreset("SiliconFlow", AIProviderType.OPENAI_COMPATIBLE, "https://api.siliconflow.cn/v1", "Qwen/Qwen2.5-7B-Instruct"),
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
            "gemini-1.5-flash",
            "gemini-1.5-pro",
            "llama-3.1-8b-instant",
            "llama-3.3-70b-versatile",
            "mistral-small-latest",
            "mistral-large-latest",
            "grok-2-latest",
            "qwen-plus",
            "qwen-max",
            "moonshot-v1-8k",
            "moonshot-v1-32k",
            "glm-4-flash",
            "Qwen/Qwen2.5-7B-Instruct",
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
