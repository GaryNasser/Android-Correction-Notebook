package com.github.garynasser.correction_notebook.ui.screens.knowledgebase

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.MimeTypeMap
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import androidx.compose.ui.viewinterop.AndroidView
import coil.compose.AsyncImage
import com.github.garynasser.correction_notebook.data.model.knowledgebase.KnowledgeBaseFileSummary
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeBaseFileViewerScreen(
    onBack: () -> Unit,
    onDeleted: () -> Unit,
    viewModel: KnowledgeBaseFileViewerViewModel = hiltViewModel(),
    knowledgeBaseViewModel: KnowledgeBaseViewModel = hiltViewModel()
) {
    val uiState = viewModel.uiState.value
    val context = LocalContext.current
    var fileToDelete by remember { mutableStateOf<KnowledgeBaseFileSummary?>(null) }
    var fileToExport by remember { mutableStateOf<KnowledgeBaseFileSummary?>(null) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val file = fileToExport
        if (uri != null && file != null) {
            exportFile(context, file, uri)
        }
        fileToExport = null
    }

    fileToDelete?.let { file ->
        FileViewerConfirmDialog(
            title = "删除文件",
            message = "确定删除“${file.displayName}”吗？",
            confirmText = "删除",
            onDismiss = { fileToDelete = null },
            onConfirm = {
                knowledgeBaseViewModel.deleteFile(file.id)
                fileToDelete = null
                onDeleted()
            }
        )
    }

    if (showInfoDialog && uiState.file != null) {
        FileInfoDialog(
            file = uiState.file,
            onDismiss = { showInfoDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.file?.displayName ?: "文件预览",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            uiState.file?.let { file ->
                                DropdownMenuItem(
                                    text = { Text("其他应用打开") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        openFileExternally(context, file)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("分享") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        shareViewerFile(context, file.localPath, file.mimeType, file.displayName)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("导出副本") },
                                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        fileToExport = file
                                        exportLauncher.launch(file.displayName)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("文件信息") },
                                    leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        showInfoDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("删除") },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        fileToDelete = file
                                    }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        when {
            uiState.isLoading -> ViewerLoading(modifier = Modifier.padding(innerPadding))
            uiState.file == null -> ViewerError(
                modifier = Modifier.padding(innerPadding),
                title = "文件不可用",
                message = uiState.errorMessage ?: "无法读取文件"
            )
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    uiState.errorMessage?.let { message ->
                        ElevatedCard(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = message,
                                modifier = Modifier.padding(16.dp),
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    when (uiState.previewType) {
                        KnowledgeBasePreviewType.IMAGE -> ImagePreview(uiState.file.localPath)
                        KnowledgeBasePreviewType.TEXT -> TextPreview(
                            content = uiState.textPreview.orEmpty(),
                            truncated = uiState.isTextTruncated
                        )
                        KnowledgeBasePreviewType.PDF -> PdfPreview(uiState.pdfPages)
                        KnowledgeBasePreviewType.HTML -> HtmlPreview(uiState.htmlPreviewPath)
                        KnowledgeBasePreviewType.AUDIO,
                        KnowledgeBasePreviewType.VIDEO -> MediaPreview(
                            file = uiState.file,
                            isVideo = uiState.previewType == KnowledgeBasePreviewType.VIDEO
                        )
                        KnowledgeBasePreviewType.FALLBACK,
                        null -> FallbackPreview(uiState.file)
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaPreview(file: KnowledgeBaseFileSummary, isVideo: Boolean) {
    val context = LocalContext.current
    val player = remember(file.localPath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(file.localPath))))
            prepare()
            playWhenReady = false
        }
    }

    DisposableEffect(player) {
        onDispose {
            player.release()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isVideo) 240.dp else 120.dp),
                update = { view ->
                    view.player = player
                }
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (isVideo) "可在应用内直接播放视频，也可以切换到其他应用打开。" else "可在应用内直接播放音频，也可以切换到其他应用打开。",
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun HtmlPreview(htmlPreviewPath: String?) {
    if (htmlPreviewPath.isNullOrBlank()) {
        ViewerError(title = "文档不可预览", message = "预览内容尚未生成，请稍后重试。")
        return
    }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.apply {
                    javaScriptEnabled = false
                    builtInZoomControls = true
                    displayZoomControls = false
                    allowFileAccess = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                }
                webViewClient = WebViewClient()
                loadUrl(File(htmlPreviewPath).toURI().toString())
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { webView ->
            val targetUrl = File(htmlPreviewPath).toURI().toString()
            if (webView.url != targetUrl) {
                webView.loadUrl(targetUrl)
            }
        }
    )
}

@Composable
private fun ViewerLoading(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(12.dp))
            Text("正在准备预览…")
        }
    }
}

@Composable
private fun ViewerError(
    modifier: Modifier = Modifier,
    title: String,
    message: String
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(modifier = Modifier.padding(24.dp)) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(message)
            }
        }
    }
}

@Composable
private fun FileInfoDialog(
    file: KnowledgeBaseFileSummary,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("文件信息") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("名称: ${file.displayName}")
                Text("大小: ${formatFileSize(file.sizeBytes)}")
                Text("类型: ${file.mimeType.ifBlank { "未知类型" }}")
                file.sourceTitle?.let { Text("来源: $it") }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun ImagePreview(localPath: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
        contentAlignment = Alignment.Center
    ) {
        AsyncImage(
            model = File(localPath),
            contentDescription = "图片预览",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit
        )
    }
}

@Composable
private fun TextPreview(
    content: String,
    truncated: Boolean
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        if (truncated) {
            ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "当前仅预览文件前一部分内容，完整内容可通过“其他应用打开”或“导出副本”查看。",
                    modifier = Modifier.padding(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
        Text(
            text = content.ifBlank { "文件内容为空" },
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun PdfPreview(pages: List<Bitmap>) {
    if (pages.isEmpty()) {
        ViewerError(title = "PDF 不可预览", message = "暂时无法渲染这个 PDF，请尝试用其他应用打开。")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        itemsIndexed(pages) { index, bitmap ->
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text(
                        text = "第 ${index + 1} 页",
                        modifier = Modifier.padding(8.dp),
                        style = MaterialTheme.typography.labelMedium
                    )
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "PDF 第 ${index + 1} 页",
                        modifier = Modifier.fillMaxWidth(),
                        contentScale = ContentScale.FillWidth
                    )
                }
            }
        }
    }
}

@Composable
private fun FallbackPreview(file: KnowledgeBaseFileSummary) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Icon(Icons.Default.Description, contentDescription = null)
                Spacer(modifier = Modifier.height(12.dp))
                Text("当前格式暂不支持应用内深度预览", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("你仍然可以使用“其他应用打开”、“分享”或“导出副本”处理 ${file.displayName}。")
            }
        }
    }
}

private fun openFileExternally(
    context: Context,
    file: KnowledgeBaseFileSummary
) {
    val targetFile = File(file.localPath)
    if (!targetFile.exists()) {
        Toast.makeText(context, "文件不存在", Toast.LENGTH_SHORT).show()
        return
    }

    runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            targetFile
        )

        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                uri,
                file.mimeType.ifBlank {
                    MimeTypeMap.getSingleton().getMimeTypeFromExtension(targetFile.extension) ?: "*/*"
                }
            )
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(viewIntent, "选择应用打开"))
    }.onFailure {
        Toast.makeText(context, "设备上没有可打开此文件的应用", Toast.LENGTH_SHORT).show()
    }
}

private fun exportFile(
    context: Context,
    file: KnowledgeBaseFileSummary,
    targetUri: android.net.Uri
) {
    val sourceFile = File(file.localPath)
    if (!sourceFile.exists()) {
        Toast.makeText(context, "文件不存在，无法导出", Toast.LENGTH_SHORT).show()
        return
    }

    runCatching {
        context.contentResolver.openOutputStream(targetUri)?.use { output ->
            sourceFile.inputStream().use { input ->
                input.copyTo(output)
            }
        } ?: error("无法写入导出位置")
        Toast.makeText(context, "已导出文件副本", Toast.LENGTH_SHORT).show()
    }.onFailure {
        Toast.makeText(context, "导出失败，请重试", Toast.LENGTH_SHORT).show()
    }
}

private fun shareViewerFile(
    context: Context,
    localPath: String,
    mimeType: String,
    title: String
) {
    val file = File(localPath)
    if (!file.exists()) {
        Toast.makeText(context, "文件不存在，无法分享", Toast.LENGTH_SHORT).show()
        return
    }

    runCatching {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType.ifBlank {
                MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(file.extension)
                    ?: "*/*"
            }
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, title)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "分享文件"))
    }.onFailure {
        Toast.makeText(context, "当前设备无法分享该文件", Toast.LENGTH_SHORT).show()
    }
}

private fun formatFileSize(sizeBytes: Long): String {
    if (sizeBytes <= 0) return "未知大小"
    val units = listOf("B", "KB", "MB", "GB")
    var value = sizeBytes.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024
        unitIndex++
    }
    return String.format(Locale.getDefault(), "%.1f %s", value, units[unitIndex])
}

@Composable
private fun FileViewerConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}
