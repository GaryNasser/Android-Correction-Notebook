package com.github.garynasser.correction_notebook.ui.screens.knowledgebase

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareFileDetail
import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareSearchResult
import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareSortOption
import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareFolderDetail
import com.github.garynasser.correction_notebook.data.model.knowledgebase.KnowledgeBaseFileSummary
import com.github.garynasser.correction_notebook.data.model.knowledgebase.KnowledgeBaseFolderChoice
import com.github.garynasser.correction_notebook.data.model.knowledgebase.KnowledgeBaseFolderSummary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class FileSortMode {
    UPDATED,
    NAME
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeBaseScreen(
    onOpenFile: (String) -> Unit,
    viewModel: KnowledgeBaseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var fileToExport by remember { mutableStateOf<KnowledgeBaseFileSummary?>(null) }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let(viewModel::importLocalFile)
    }
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri: Uri? ->
        val file = fileToExport
        if (uri != null && file != null) {
            exportFile(context, file, uri)
        }
        fileToExport = null
    }

    var showCreateFolderDialog by rememberSaveable { mutableStateOf(false) }
    var folderToRename by remember { mutableStateOf<KnowledgeBaseFolderSummary?>(null) }
    var folderToDelete by remember { mutableStateOf<KnowledgeBaseFolderSummary?>(null) }
    var fileToRename by remember { mutableStateOf<KnowledgeBaseFileSummary?>(null) }
    var fileToDelete by remember { mutableStateOf<KnowledgeBaseFileSummary?>(null) }
    var fileToMove by remember { mutableStateOf<KnowledgeBaseFileSummary?>(null) }
    var showDownloadFolderPicker by rememberSaveable { mutableStateOf(false) }
    var pendingRemoteDownload by remember { mutableStateOf<BitShareSearchResult?>(null) }
    var fileSortMode by rememberSaveable { mutableStateOf(FileSortMode.UPDATED) }
    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }

    val sortedFiles = remember(uiState.folderContent.files, fileSortMode) {
        when (fileSortMode) {
            FileSortMode.UPDATED -> uiState.folderContent.files.sortedByDescending { it.downloadedAt ?: 0L }
            FileSortMode.NAME -> uiState.folderContent.files.sortedBy { it.displayName.lowercase(Locale.getDefault()) }
        }
    }

        LaunchedEffect(uiState.snackbarMessage) {
        val message = uiState.snackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.consumeSnackbarMessage()
    }

    if (showCreateFolderDialog) {
        NameInputDialog(
            title = "新建文件夹",
            initialValue = "",
            confirmText = "创建",
            onDismiss = { showCreateFolderDialog = false },
            onConfirm = {
                viewModel.createFolder(it)
                showCreateFolderDialog = false
            }
        )
    }

    folderToRename?.let { folder ->
        NameInputDialog(
            title = "重命名文件夹",
            initialValue = folder.name,
            confirmText = "保存",
            onDismiss = { folderToRename = null },
            onConfirm = {
                viewModel.renameFolder(folder.id, it)
                folderToRename = null
            }
        )
    }

    fileToRename?.let { file ->
        NameInputDialog(
            title = "重命名文件",
            initialValue = file.displayName,
            confirmText = "保存",
            onDismiss = { fileToRename = null },
            onConfirm = {
                viewModel.renameFile(file.id, it)
                fileToRename = null
            }
        )
    }

    folderToDelete?.let { folder ->
        ConfirmDialog(
            title = "删除文件夹",
            message = "仅支持删除空文件夹。确定删除“${folder.name}”吗？",
            confirmText = "删除",
            onDismiss = { folderToDelete = null },
            onConfirm = {
                viewModel.deleteFolder(folder.id)
                folderToDelete = null
            }
        )
    }

    fileToDelete?.let { file ->
        ConfirmDialog(
            title = "删除文件",
            message = "确定删除“${file.displayName}”吗？",
            confirmText = "删除",
            onDismiss = { fileToDelete = null },
            onConfirm = {
                viewModel.deleteFile(file.id)
                fileToDelete = null
            }
        )
    }

    fileToMove?.let { file ->
        FolderPickerDialog(
            title = "移动到",
            folders = uiState.folderChoices,
            onDismiss = { fileToMove = null },
            onSelect = { folderId ->
                viewModel.moveFile(file.id, folderId)
                fileToMove = null
            }
        )
    }

    if (showDownloadFolderPicker && pendingRemoteDownload != null) {
        FolderPickerDialog(
            title = "下载到知识库",
            folders = uiState.folderChoices,
            onDismiss = {
                showDownloadFolderPicker = false
                pendingRemoteDownload = null
                viewModel.dismissRemoteDetail()
            },
            onSelect = { folderId ->
                pendingRemoteDownload?.let { result ->
                    viewModel.downloadSearchResultToFolder(result, folderId)
                }
                showDownloadFolderPicker = false
                pendingRemoteDownload = null
            }
        )
    }

    uiState.selectedRemoteDetail?.takeIf { !showDownloadFolderPicker && pendingRemoteDownload == null }?.let { detail ->
        RemoteDetailDialog(
            detail = detail,
            isDownloading = uiState.activeDownloadId == detail.id,
            onDismiss = { viewModel.dismissRemoteDetail() },
            onDownloadClick = { showDownloadFolderPicker = true }
        )
    }

    uiState.selectedRemoteFolderDetail?.let { folderDetail ->
        RemoteFolderDetailDialog(
            detail = folderDetail,
            onDismiss = { viewModel.dismissRemoteFolderDetail() }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("知识库") },
                windowInsets = WindowInsets(0, 0, 0, 0),
                actions = {
                    if (uiState.selectedTabIndex == 1) {
                        IconButton(onClick = { viewModel.searchRemoteResources() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "刷新搜索")
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (uiState.selectedTabIndex == 0) {
                FloatingActionButton(onClick = { showCreateFolderDialog = true }) {
                    Icon(Icons.Default.CreateNewFolder, contentDescription = "新建文件夹")
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            TabRow(selectedTabIndex = uiState.selectedTabIndex) {
                listOf("文件管理", "BITShare 下载").forEachIndexed { index, title ->
                    Tab(
                        selected = uiState.selectedTabIndex == index,
                        onClick = { viewModel.selectTab(index) },
                        text = { Text(title) }
                    )
                }
            }

            when (uiState.selectedTabIndex) {
                0 -> FileManagementPage(
                    uiState = uiState,
                    fileSortMode = fileSortMode,
                    onToggleSort = {
                        fileSortMode = when (fileSortMode) {
                            FileSortMode.UPDATED -> FileSortMode.NAME
                            FileSortMode.NAME -> FileSortMode.UPDATED
                        }
                    },
                    isSearchExpanded = isSearchExpanded,
                    onToggleSearch = {
                        isSearchExpanded = !isSearchExpanded
                        if (!isSearchExpanded) {
                            viewModel.updateLocalSearchQuery("")
                        }
                    },
                    files = sortedFiles,
                    onLocalSearchChanged = viewModel::updateLocalSearchQuery,
                    onBack = viewModel::navigateBack,
                    onBreadcrumbClick = viewModel::navigateToBreadcrumb,
                    onFolderClick = viewModel::enterFolder,
                    onFolderRename = { folderToRename = it },
                    onFolderDelete = { folderToDelete = it },
                    onFileOpen = { onOpenFile(it.id) },
                    onFileRename = { fileToRename = it },
                    onFileDelete = { fileToDelete = it },
                    onFileMove = { fileToMove = it },
                    onFileExport = {
                        fileToExport = it
                        exportLauncher.launch(it.displayName)
                    },
                    onImportLocalFile = { importLauncher.launch(arrayOf("*/*")) },
                    onFileShare = {
                        shareFile(
                            context = context,
                            localPath = it.localPath,
                            mimeType = it.mimeType,
                            title = it.displayName
                        )
                    }
                )

                1 -> BitSharePage(
                    uiState = uiState,
                    onQueryChanged = viewModel::updateRemoteQuery,
                    onSortChanged = {
                        viewModel.updateRemoteSort(it)
                        if (uiState.remoteQuery.isNotBlank()) {
                            viewModel.searchRemoteResources()
                        }
                    },
                    onSearchClick = viewModel::searchRemoteResources,
                    onOpenDetail = {
                        pendingRemoteDownload = it
                        showDownloadFolderPicker = true
                    },
                    onOpenFolderDetail = viewModel::loadRemoteFolderDetail
                )
            }
        }
    }
}

@Composable
private fun FileManagementPage(
    uiState: KnowledgeBaseUiState,
    fileSortMode: FileSortMode,
    files: List<KnowledgeBaseFileSummary>,
    onToggleSort: () -> Unit,
    isSearchExpanded: Boolean,
    onToggleSearch: () -> Unit,
    onLocalSearchChanged: (String) -> Unit,
    onBack: () -> Unit,
    onBreadcrumbClick: (String?) -> Unit,
    onFolderClick: (String) -> Unit,
    onFolderRename: (KnowledgeBaseFolderSummary) -> Unit,
    onFolderDelete: (KnowledgeBaseFolderSummary) -> Unit,
    onFileOpen: (KnowledgeBaseFileSummary) -> Unit,
    onFileRename: (KnowledgeBaseFileSummary) -> Unit,
    onFileDelete: (KnowledgeBaseFileSummary) -> Unit,
    onFileMove: (KnowledgeBaseFileSummary) -> Unit,
    onFileExport: (KnowledgeBaseFileSummary) -> Unit,
    onImportLocalFile: () -> Unit,
    onFileShare: (KnowledgeBaseFileSummary) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.currentFolderId != null) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回上一级")
                        }
                    }
                    val breadcrumbs = uiState.folderContent.breadcrumbs
                        .drop(if (uiState.currentFolderId == null) 1 else 0)
                    breadcrumbs.forEachIndexed { index, breadcrumb ->
                        TextButton(onClick = { onBreadcrumbClick(breadcrumb.id) }) {
                            Text(breadcrumb.name)
                        }
                        if (index != breadcrumbs.lastIndex) {
                            Text("/", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    Spacer(modifier = Modifier.size(12.dp))
                    OutlinedButton(
                        onClick = onImportLocalFile,
                        enabled = !uiState.isLocalBusy && !uiState.isImportingLocalFile
                    ) {
                        if (uiState.isImportingLocalFile) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.FileOpen, contentDescription = null)
                        }
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("导入")
                    }
                    OutlinedButton(onClick = onToggleSearch) {
                        Icon(Icons.Default.Search, contentDescription = null)
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(if (isSearchExpanded) "收起" else "搜索")
                    }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (fileSortMode == FileSortMode.UPDATED) "当前: 最近更新" else "当前: 名称") },
                                enabled = false,
                                onClick = {}
                            )
                            DropdownMenuItem(
                                text = { Text("按最近更新") },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    if (fileSortMode != FileSortMode.UPDATED) onToggleSort()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("按名称") },
                                leadingIcon = { Icon(Icons.Default.SortByAlpha, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    if (fileSortMode != FileSortMode.NAME) onToggleSort()
                                }
                            )
                        }
                    }
                }

                if (isSearchExpanded) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = uiState.localSearchQuery,
                        onValueChange = onLocalSearchChanged,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        label = { Text("搜索当前目录") }
                    )
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (uiState.folderContent.folders.isEmpty() && files.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "目录是空的",
                        description = "先创建文件夹，或者去 BITShare 页把资料下载进来。"
                    )
                }
            }

            if (uiState.folderContent.folders.isNotEmpty()) {
                item {
                    SectionTitle("文件夹")
                }
                items(uiState.folderContent.folders, key = { it.id }) { folder ->
                    FolderRow(
                        folder = folder,
                        onClick = { onFolderClick(folder.id) },
                        onRename = { onFolderRename(folder) },
                        onDelete = { onFolderDelete(folder) }
                    )
                }
            }

            if (files.isNotEmpty()) {
                item {
                    SectionTitle("文件")
                }
                items(files, key = { it.id }) { file ->
                    FileRow(
                        file = file,
                        onOpen = { onFileOpen(file) },
                        onRename = { onFileRename(file) },
                        onMove = { onFileMove(file) },
                        onExport = { onFileExport(file) },
                        onDelete = { onFileDelete(file) },
                        onShare = { onFileShare(file) }
                    )
                }
            }
        }
    }
}

@Composable
private fun BitSharePage(
    uiState: KnowledgeBaseUiState,
    onQueryChanged: (String) -> Unit,
    onSortChanged: (BitShareSortOption) -> Unit,
    onSearchClick: () -> Unit,
    onOpenDetail: (BitShareSearchResult) -> Unit,
    onOpenFolderDetail: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = uiState.remoteQuery,
                onValueChange = onQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("搜索课程资料") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    IconButton(onClick = onSearchClick) {
                        Icon(Icons.Default.CloudDownload, contentDescription = "搜索")
                    }
                }
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    BitShareSortOption.RELEVANCE to "综合",
                    BitShareSortOption.DOWNLOADS to "下载量",
                    BitShareSortOption.LATEST to "最新"
                ).forEach { (option, label) ->
                    FilterChip(
                        selected = uiState.remoteSort == option,
                        onClick = { onSortChanged(option) },
                        label = { Text(label) }
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "⚠️ 仅支持 BIT 校园网内网访问，请在连接校园 WiFi 或 VPN 后使用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        when {
            uiState.isRemoteSearching -> {
                LoadingState("正在搜索资料…")
            }

            uiState.remoteErrorMessage != null && uiState.remoteResults.isEmpty() -> {
                EmptyStateCard(
                    title = "搜索失败",
                    description = uiState.remoteErrorMessage
                )
            }

            uiState.remoteQuery.isBlank() -> {
                EmptyStateCard(
                    title = "搜索公开资料",
                    description = "⚠️ 请确保已连接 BIT 校园网（内网 WiFi 或 VPN）\n输入课程名、老师名或关键词后搜索"
                )
            }

            uiState.remoteResults.isEmpty() -> {
                EmptyStateCard(
                    title = "没有找到结果",
                    description = "换个关键词试试，或者缩短搜索条件。"
                )
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.remoteResults, key = { it.id }) { result ->
                        RemoteResultRow(
                            result = result,
                            onClick = {
                                if (result.entityType == "folder") {
                                    onOpenFolderDetail(result.id)
                                } else {
                                    onOpenDetail(result)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderRow(
    folder: KnowledgeBaseFolderSummary,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick)
    ) {
        ListItem(
            headlineContent = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = { Text("${folder.directFileCount} 个文件") },
            leadingContent = {
                Icon(Icons.Default.Folder, contentDescription = null)
            },
            trailingContent = {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("重命名") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun FileRow(
    file: KnowledgeBaseFileSummary,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onOpen)
    ) {
        ListItem(
            headlineContent = {
                Text(file.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                Text(
                    listOfNotNull(
                        formatFileSize(file.sizeBytes),
                        file.sourceTitle?.takeIf { file.sourceType == "bitshare" }?.let { "来源: BITShare" }
                    ).joinToString(" · ")
                )
            },
            leadingContent = { Icon(Icons.Default.Description, contentDescription = null) },
            trailingContent = {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多操作")
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("重命名") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("移动") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onMove()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("导出") },
                            leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onExport()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("分享") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.Send, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onShare()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        )
    }
}

@Composable
private fun RemoteResultRow(
    result: BitShareSearchResult,
    onClick: () -> Unit
) {
    val isFolder = result.entityType == "folder"

    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick)
    ) {
        ListItem(
            headlineContent = {
                Text(result.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                if (isFolder) {
                    Text(
                        "目录 · ${result.downloadCount} 次下载",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "${result.originalName} · ${formatFileSize(result.sizeBytes)} · 下载 ${result.downloadCount}"
                    )
                }
            },
            leadingContent = {
                Icon(
                    if (isFolder) Icons.Default.Folder else Icons.Default.Description,
                    contentDescription = if (isFolder) "目录" else "文件"
                )
            },
            trailingContent = {
                if (isFolder) {
                    TextButton(onClick = onClick) {
                        Text("查看", style = MaterialTheme.typography.labelSmall)
                    }
                } else {
                    Icon(Icons.Default.CloudDownload, contentDescription = "下载")
                }
            }
        )
    }
}

@Composable
private fun RemoteDetailDialog(
    detail: BitShareFileDetail,
    isDownloading: Boolean,
    onDismiss: () -> Unit,
    onDownloadClick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(detail.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailLine("原始文件名", detail.originalName)
                DetailLine("文件大小", formatFileSize(detail.sizeBytes))
                DetailLine("下载量", detail.downloadCount.toString())
                DetailLine("上传时间", detail.uploadedAt ?: "未知")
                DetailLine("来源路径", detail.path ?: "未提供")
                detail.description?.takeIf { it.isNotBlank() }?.let {
                    DetailLine("说明", it)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDownloadClick,
                enabled = !isDownloading
            ) {
                if (isDownloading) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp
                        )
                        Text("下载中…")
                    }
                } else {
                    Text("下载到知识库")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun RemoteFolderDetailDialog(
    detail: BitShareFolderDetail,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(detail.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!detail.description.isNullOrBlank()) {
                    DetailLine("目录描述", detail.description)
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "目录内容统计",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("文件数量: ${detail.fileCount}")
                        Text("下载次数: ${detail.downloadCount}")
                        Text("总大小: ${formatFileSize(detail.totalSize)}")
                    }
                }

                if (detail.breadcrumbs.isNotEmpty()) {
                    DetailLine("路径", detail.breadcrumbs.joinToString(" > ") { it.name })
                }

                // 提示用户无法浏览目录内文件
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error
                        )
                        Text(
                            text = "无法浏览目录内文件，请通过搜索找到具体文件后下载",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

@Composable
private fun FolderPickerDialog(
    title: String,
    folders: List<KnowledgeBaseFolderChoice>,
    onDismiss: () -> Unit,
    onSelect: (String?) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(folders, key = { it.path }) { folder ->
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(folder.id) }
                    ) {
                        ListItem(
                            headlineContent = { Text(folder.name) },
                            supportingContent = { Text(folder.path) },
                            leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        }
    )
}

@Composable
private fun NameInputDialog(
    title: String,
    initialValue: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember(initialValue) { mutableStateOf(initialValue) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) {
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

@Composable
private fun ConfirmDialog(
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

@Composable
private fun DetailLine(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun LoadingState(message: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(modifier = Modifier.height(12.dp))
            Text(message)
        }
    }
}

@Composable
private fun EmptyStateCard(
    title: String,
    description: String
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 240.dp)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        ElevatedCard(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(text = description, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
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

private fun formatTimestamp(timestamp: Long?): String {
    if (timestamp == null || timestamp <= 0) return "刚刚"
    val formatter = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}

private fun shareFile(
    context: android.content.Context,
    localPath: String,
    mimeType: String,
    title: String
) {
    val file = File(localPath)
    if (!file.exists()) {
        Toast.makeText(context, "文件不存在，无法导出", Toast.LENGTH_SHORT).show()
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

        context.startActivity(Intent.createChooser(shareIntent, "导出文件"))
    }.onFailure {
        Toast.makeText(context, "当前设备无法导出该文件", Toast.LENGTH_SHORT).show()
    }
}

private fun exportFile(
    context: android.content.Context,
    file: KnowledgeBaseFileSummary,
    targetUri: Uri
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
