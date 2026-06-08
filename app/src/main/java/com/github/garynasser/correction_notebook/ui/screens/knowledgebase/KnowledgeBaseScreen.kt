package com.github.garynasser.correction_notebook.ui.screens.knowledgebase

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.webkit.MimeTypeMap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SortByAlpha
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.input.pointer.pointerInput
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
import com.github.garynasser.correction_notebook.data.model.studyset.DueReviewItem
import com.github.garynasser.correction_notebook.data.model.studyset.KnowledgeCardType
import com.github.garynasser.correction_notebook.data.model.studyset.StudySetQuizItem
import com.github.garynasser.correction_notebook.data.model.studyset.StudySetSummary
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

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
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isNotEmpty()) {
            viewModel.importLocalFiles(uris)
        }
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
    var fileToContext by remember { mutableStateOf<KnowledgeBaseFileSummary?>(null) }
    var showBatchMovePicker by remember { mutableStateOf(false) }
    var showBatchDeleteConfirm by remember { mutableStateOf(false) }
    var showDownloadFolderPicker by rememberSaveable { mutableStateOf(false) }
    var pendingRemoteDownload by remember { mutableStateOf<BitShareSearchResult?>(null) }
    var fileSortMode by rememberSaveable { mutableStateOf(FileSortMode.UPDATED) }
    var isSearchExpanded by rememberSaveable { mutableStateOf(false) }
    var isSelectionMode by remember { mutableStateOf(false) }
    var selectedFileIds by remember { mutableStateOf(emptySet<String>()) }
    var showManualKnowledgeDialog by rememberSaveable { mutableStateOf(false) }
    var selectedKnowledgeCard by remember { mutableStateOf<DueReviewItem?>(null) }
    var editingKnowledgeCard by remember { mutableStateOf<DueReviewItem?>(null) }
    var movingKnowledgeCard by remember { mutableStateOf<DueReviewItem?>(null) }
    var cardToDelete by remember { mutableStateOf<DueReviewItem?>(null) }
    var manualCardTargetStudySet by remember { mutableStateOf<StudySetSummary?>(null) }
    var selectedStudySetId by rememberSaveable { mutableStateOf<String?>(null) }
    var studySetToRename by remember { mutableStateOf<StudySetSummary?>(null) }
    var studySetToDelete by remember { mutableStateOf<StudySetSummary?>(null) }
    var studySetToMerge by remember { mutableStateOf<StudySetSummary?>(null) }
    var learningStudySetId by rememberSaveable { mutableStateOf<String?>(null) }
    var quizStudySetId by rememberSaveable { mutableStateOf<String?>(null) }
    var quizStartQuestionId by rememberSaveable { mutableStateOf<String?>(null) }

    val sortedFiles = remember(uiState.folderContent.files, fileSortMode) {
        when (fileSortMode) {
            FileSortMode.UPDATED -> uiState.folderContent.files.sortedByDescending { it.downloadedAt ?: 0L }
            FileSortMode.NAME -> uiState.folderContent.files.sortedBy { it.displayName.lowercase(Locale.getDefault()) }
        }
    }
    val selectedFiles = remember(sortedFiles, selectedFileIds) {
        sortedFiles.filter { it.id in selectedFileIds }
    }
    val selectedStudySet = selectedStudySetId?.let { id ->
        uiState.studySets.firstOrNull { it.id == id }
    }
    val learningStudySet = learningStudySetId?.let { id ->
        uiState.studySets.firstOrNull { it.id == id }
    }
    val learningCards = learningStudySetId?.let { id ->
        uiState.knowledgeCards.filter { it.studySetId == id }
    }.orEmpty()
    val quizStudySet = quizStudySetId?.let { id ->
        uiState.studySets.firstOrNull { it.id == id }
    }
    val quizSessionQuestions = quizStudySetId?.let { id ->
        uiState.quizQuestions.filter { it.studySetId == id }
    }.orEmpty()

    LaunchedEffect(uiState.currentFolderId, uiState.selectedTabIndex) {
        isSelectionMode = false
        selectedFileIds = emptySet()
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

    fileToContext?.let { file ->
        LearningContextDialog(
            file = file,
            onDismiss = { fileToContext = null },
            onConfirm = { courseId, courseName, tags ->
                viewModel.updateFileLearningContext(file.id, courseId, courseName, tags)
                fileToContext = null
            }
        )
    }

    if (showBatchMovePicker) {
        FolderPickerDialog(
            title = "移动 ${selectedFileIds.size} 个文件到",
            folders = uiState.folderChoices,
            onDismiss = { showBatchMovePicker = false },
            onSelect = { folderId ->
                viewModel.moveFiles(selectedFileIds, folderId)
                selectedFileIds = emptySet()
                isSelectionMode = false
                showBatchMovePicker = false
            }
        )
    }

    if (showBatchDeleteConfirm) {
        ConfirmDialog(
            title = "删除文件",
            message = "确定删除选中的 ${selectedFileIds.size} 个文件吗？",
            confirmText = "删除",
            onDismiss = { showBatchDeleteConfirm = false },
            onConfirm = {
                viewModel.deleteFiles(selectedFileIds)
                selectedFileIds = emptySet()
                isSelectionMode = false
                showBatchDeleteConfirm = false
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

    if (showManualKnowledgeDialog) {
        ManualKnowledgeCardDialog(
            targetStudySetTitle = manualCardTargetStudySet?.title,
            onDismiss = {
                manualCardTargetStudySet = null
                showManualKnowledgeDialog = false
            },
            onConfirm = { title, type, front, back, hint, courseName, explanation, example, pitfall, formula, tags ->
                viewModel.addManualKnowledgeCard(
                    title = title,
                    type = type,
                    front = front,
                    back = back,
                    hint = hint,
                    courseName = courseName,
                    explanation = explanation,
                    example = example,
                    pitfall = pitfall,
                    formula = formula,
                    tags = tags,
                    studySetId = manualCardTargetStudySet?.id
                )
                manualCardTargetStudySet = null
                showManualKnowledgeDialog = false
            }
        )
    }

    selectedKnowledgeCard?.let { card ->
        KnowledgeCardDetailDialog(
            item = card,
            onDismiss = { selectedKnowledgeCard = null },
            onEdit = {
                selectedKnowledgeCard = null
                editingKnowledgeCard = card
            },
            onDelete = {
                cardToDelete = card
                selectedKnowledgeCard = null
            },
            onReviewDone = {
                viewModel.markFlashcardReviewed(card.flashcardId)
                selectedKnowledgeCard = null
            }
        )
    }

    movingKnowledgeCard?.let { card ->
        MoveKnowledgeCardDialog(
            card = card,
            studySets = uiState.studySets,
            onDismiss = { movingKnowledgeCard = null },
            onConfirm = { targetStudySetId ->
                viewModel.moveKnowledgeCard(card.flashcardId, targetStudySetId)
                movingKnowledgeCard = null
            }
        )
    }

    cardToDelete?.let { card ->
        ConfirmDialog(
            title = "删除知识卡片",
            message = "确定删除“${card.title.ifBlank { card.front }}”吗？",
            confirmText = "删除",
            onDismiss = { cardToDelete = null },
            onConfirm = {
                viewModel.deleteKnowledgeCard(card.flashcardId)
                cardToDelete = null
            }
        )
    }

    editingKnowledgeCard?.let { card ->
        EditKnowledgeCardDialog(
            item = card,
            onDismiss = { editingKnowledgeCard = null },
            onSave = {
                viewModel.updateKnowledgeCard(it)
                editingKnowledgeCard = null
            }
        )
    }

    studySetToRename?.let { studySet ->
        NameInputDialog(
            title = "重命名学习集",
            initialValue = studySet.title,
            confirmText = "保存",
            onDismiss = { studySetToRename = null },
            onConfirm = {
                viewModel.renameStudySet(studySet.id, it)
                studySetToRename = null
            }
        )
    }

    studySetToDelete?.let { studySet ->
        ConfirmDialog(
            title = "删除学习集",
            message = "确定删除“${studySet.title}”吗？其中的知识卡片和测验题也会一起删除。",
            confirmText = "删除",
            onDismiss = { studySetToDelete = null },
            onConfirm = {
                viewModel.deleteStudySet(studySet.id)
                if (selectedStudySetId == studySet.id) selectedStudySetId = null
                if (learningStudySetId == studySet.id) learningStudySetId = null
                studySetToDelete = null
            }
        )
    }

    studySetToMerge?.let { target ->
        StudySetMergeDialog(
            target = target,
            studySets = uiState.studySets,
            onDismiss = { studySetToMerge = null },
            onConfirm = { sourceIds ->
                viewModel.mergeStudySets(sourceIds, target.id)
                studySetToMerge = null
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

    val isLearningMode = uiState.selectedTabIndex == 1 && learningStudySet != null
    val isQuizMode = uiState.selectedTabIndex == 1 && quizStudySet != null
    val isImmersiveMode = isLearningMode || isQuizMode

    Scaffold(
        topBar = {
            if (!isImmersiveMode) {
                TopAppBar(
                    title = { Text("知识库") },
                    windowInsets = WindowInsets(0, 0, 0, 0),
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                        scrolledContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    actions = {
                        if (uiState.selectedTabIndex == 2) {
                            IconButton(onClick = { viewModel.searchRemoteResources() }) {
                                Icon(Icons.Default.Refresh, contentDescription = "刷新搜索")
                            }
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (uiState.selectedTabIndex == 0 && !isImmersiveMode) {
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
            if (isLearningMode) {
                StudySetLearningPage(
                    studySet = requireNotNull(learningStudySet),
                    cards = learningCards,
                    onDismiss = { learningStudySetId = null },
                    onEditCard = { editingKnowledgeCard = it },
                    onReview = { card, remembered ->
                        viewModel.markFlashcardReviewed(card.flashcardId, remembered)
                    }
                )
            } else if (isQuizMode) {
                StudySetQuizPage(
                    studySet = requireNotNull(quizStudySet),
                    questions = quizSessionQuestions,
                    initialQuestionId = quizStartQuestionId,
                    onDismiss = {
                        quizStudySetId = null
                        quizStartQuestionId = null
                    }
                )
            } else {
                TabRow(selectedTabIndex = uiState.selectedTabIndex) {
                    listOf("文件管理", "知识空间", "BITShare 下载").forEachIndexed { index, title ->
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
                        isSelectionMode = isSelectionMode,
                        selectedFileIds = selectedFileIds,
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
                        onFolderClick = viewModel::enterFolder,
                        onFolderRename = { folderToRename = it },
                        onFolderDelete = { folderToDelete = it },
                        onFileOpen = { onOpenFile(it.id) },
                        onToggleSelectionMode = {
                            isSelectionMode = !isSelectionMode
                            selectedFileIds = emptySet()
                        },
                        onToggleFileSelection = { file ->
                            selectedFileIds = if (file.id in selectedFileIds) {
                                selectedFileIds - file.id
                            } else {
                                selectedFileIds + file.id
                            }
                        },
                        onSelectAllFiles = {
                            selectedFileIds = sortedFiles.map { it.id }.toSet()
                        },
                        onBatchMove = { showBatchMovePicker = true },
                        onBatchDelete = { showBatchDeleteConfirm = true },
                        onBatchShare = {
                            shareFiles(context, selectedFiles)
                            selectedFileIds = emptySet()
                            isSelectionMode = false
                        },
                        onFileRename = { fileToRename = it },
                        onFileDelete = { fileToDelete = it },
                        onFileMove = { fileToMove = it },
                        onFileContext = { fileToContext = it },
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

                    1 -> KnowledgeSpacePage(
                        studySets = uiState.studySets,
                        knowledgeCards = uiState.knowledgeCards,
                        reviewedCards = uiState.reviewedCards,
                        quizQuestions = uiState.quizQuestions,
                        selectedStudySet = selectedStudySet,
                        onAddManualCard = {
                            manualCardTargetStudySet = null
                            showManualKnowledgeDialog = true
                        },
                        onOpenStudySet = { selectedStudySetId = it.id },
                        onBackToStudySets = { selectedStudySetId = null },
                        onRenameStudySet = { studySetToRename = it },
                        onDeleteStudySet = { studySetToDelete = it },
                        onMergeStudySet = { studySetToMerge = it },
                        onStartStudySet = { learningStudySetId = it.id },
                        onStartQuizSet = {
                            quizStudySetId = it.id
                            quizStartQuestionId = null
                        },
                        onAddCardToStudySet = {
                            manualCardTargetStudySet = it
                            showManualKnowledgeDialog = true
                        },
                        onOpenCard = { selectedKnowledgeCard = it },
                        onEditCard = { editingKnowledgeCard = it },
                        onMoveCard = { movingKnowledgeCard = it },
                        onDeleteCard = { cardToDelete = it },
                        onOpenQuiz = {
                            quizStudySetId = it.studySetId
                            quizStartQuestionId = it.id
                        },
                        onReviewDone = { viewModel.markFlashcardReviewed(it) }
                    )

                    2 -> BitSharePage(
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
}

@Composable
private fun FileManagementPage(
    uiState: KnowledgeBaseUiState,
    fileSortMode: FileSortMode,
    files: List<KnowledgeBaseFileSummary>,
    isSelectionMode: Boolean,
    selectedFileIds: Set<String>,
    onToggleSort: () -> Unit,
    isSearchExpanded: Boolean,
    onToggleSearch: () -> Unit,
    onLocalSearchChanged: (String) -> Unit,
    onBack: () -> Unit,
    onFolderClick: (String) -> Unit,
    onFolderRename: (KnowledgeBaseFolderSummary) -> Unit,
    onFolderDelete: (KnowledgeBaseFolderSummary) -> Unit,
    onFileOpen: (KnowledgeBaseFileSummary) -> Unit,
    onToggleSelectionMode: () -> Unit,
    onToggleFileSelection: (KnowledgeBaseFileSummary) -> Unit,
    onSelectAllFiles: () -> Unit,
    onBatchMove: () -> Unit,
    onBatchDelete: () -> Unit,
    onBatchShare: () -> Unit,
    onFileRename: (KnowledgeBaseFileSummary) -> Unit,
    onFileDelete: (KnowledgeBaseFileSummary) -> Unit,
    onFileMove: (KnowledgeBaseFileSummary) -> Unit,
    onFileContext: (KnowledgeBaseFileSummary) -> Unit,
    onFileExport: (KnowledgeBaseFileSummary) -> Unit,
    onImportLocalFile: () -> Unit,
    onFileShare: (KnowledgeBaseFileSummary) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val currentFolderPath = uiState.folderContent.breadcrumbs
        .joinToString(" / ") { it.name }
        .ifBlank { "知识库" }

    Column(modifier = Modifier.fillMaxSize()) {
        Surface(color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
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
                    OutlinedButton(
                        onClick = onToggleSelectionMode,
                        enabled = files.isNotEmpty() && !uiState.isLocalBusy
                    ) {
                        Icon(
                            if (isSelectionMode) Icons.Default.Close else Icons.Default.CheckCircle,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(if (isSelectionMode) "取消" else "多选")
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
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = uiState.localSearchQuery,
                        onValueChange = onLocalSearchChanged,
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        label = { Text("搜索当前目录") }
                    )
                }

                if (isSelectionMode) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "已选 ${selectedFileIds.size} 个",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onSelectAllFiles, enabled = files.isNotEmpty()) {
                            Text("全选")
                        }
                        TextButton(onClick = onBatchMove, enabled = selectedFileIds.isNotEmpty() && !uiState.isLocalBusy) {
                            Text("移动")
                        }
                        TextButton(onClick = onBatchShare, enabled = selectedFileIds.isNotEmpty()) {
                            Text("分享")
                        }
                        TextButton(onClick = onBatchDelete, enabled = selectedFileIds.isNotEmpty() && !uiState.isLocalBusy) {
                            Text("删除")
                        }
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 88.dp)
        ) {
            item {
                SectionTitle(currentFolderPath)
            }

            if (uiState.folderContent.folders.isEmpty() && files.isEmpty()) {
                item {
                    EmptyStateCard(
                        title = "目录是空的",
                        description = "先创建文件夹，或者去 BITShare 页把资料下载进来。"
                    )
                }
            }

            if (uiState.folderContent.folders.isNotEmpty()) {
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
                items(files, key = { it.id }) { file ->
                    FileRow(
                        file = file,
                        isSelectionMode = isSelectionMode,
                        isSelected = file.id in selectedFileIds,
                        onOpen = { onFileOpen(file) },
                        onToggleSelected = { onToggleFileSelection(file) },
                        onRename = { onFileRename(file) },
                        onMove = { onFileMove(file) },
                        onContext = { onFileContext(file) },
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
private fun KnowledgeSpacePage(
    studySets: List<StudySetSummary>,
    knowledgeCards: List<DueReviewItem>,
    reviewedCards: List<DueReviewItem>,
    quizQuestions: List<StudySetQuizItem>,
    selectedStudySet: StudySetSummary?,
    onAddManualCard: () -> Unit,
    onOpenStudySet: (StudySetSummary) -> Unit,
    onBackToStudySets: () -> Unit,
    onRenameStudySet: (StudySetSummary) -> Unit,
    onDeleteStudySet: (StudySetSummary) -> Unit,
    onMergeStudySet: (StudySetSummary) -> Unit,
    onStartStudySet: (StudySetSummary) -> Unit,
    onStartQuizSet: (StudySetSummary) -> Unit,
    onAddCardToStudySet: (StudySetSummary) -> Unit,
    onOpenCard: (DueReviewItem) -> Unit,
    onEditCard: (DueReviewItem) -> Unit,
    onMoveCard: (DueReviewItem) -> Unit,
    onDeleteCard: (DueReviewItem) -> Unit,
    onOpenQuiz: (StudySetQuizItem) -> Unit,
    onReviewDone: (String) -> Unit
) {
    if (selectedStudySet != null) {
        StudySetDetailPage(
            studySet = selectedStudySet,
            cards = knowledgeCards.filter { it.studySetId == selectedStudySet.id },
            reviewedCards = reviewedCards.filter { it.studySetId == selectedStudySet.id },
            quizQuestions = quizQuestions.filter { it.studySetId == selectedStudySet.id },
            onBack = onBackToStudySets,
            onRename = { onRenameStudySet(selectedStudySet) },
            onDelete = { onDeleteStudySet(selectedStudySet) },
            onMerge = { onMergeStudySet(selectedStudySet) },
            onStart = { onStartStudySet(selectedStudySet) },
            onStartQuiz = { onStartQuizSet(selectedStudySet) },
            onAddCard = { onAddCardToStudySet(selectedStudySet) },
            onOpenCard = onOpenCard,
            onEditCard = onEditCard,
            onMoveCard = onMoveCard,
            onDeleteCard = onDeleteCard,
            onOpenQuiz = onOpenQuiz,
            onReviewDone = onReviewDone
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("知识空间", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "按学习集管理知识卡片，进入后再学习和复习",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(
                    onClick = onAddManualCard,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                }
            }
        }

        item {
            KnowledgeSpaceStats(studySets = studySets, cards = knowledgeCards, reviewedCards = reviewedCards)
        }

        item {
            Text(
                "学习集",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
        }

        if (studySets.isEmpty()) {
            item { EmptyStateCard("暂无学习集", "从资料页生成学习集，或手动添加第一组知识点。") }
        } else {
            items(studySets, key = { it.id }) { set ->
                StudySetRow(
                    set = set,
                    onClick = { onOpenStudySet(set) },
                    onRename = { onRenameStudySet(set) },
                    onDelete = { onDeleteStudySet(set) },
                    onMerge = { onMergeStudySet(set) }
                )
            }
        }
    }
}

@Composable
private fun StudySetDetailPage(
    studySet: StudySetSummary,
    cards: List<DueReviewItem>,
    reviewedCards: List<DueReviewItem>,
    quizQuestions: List<StudySetQuizItem>,
    onBack: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMerge: () -> Unit,
    onStart: () -> Unit,
    onStartQuiz: () -> Unit,
    onAddCard: () -> Unit,
    onOpenCard: (DueReviewItem) -> Unit,
    onEditCard: (DueReviewItem) -> Unit,
    onMoveCard: (DueReviewItem) -> Unit,
    onDeleteCard: (DueReviewItem) -> Unit,
    onOpenQuiz: (StudySetQuizItem) -> Unit,
    onReviewDone: (String) -> Unit
) {
    var filter by rememberSaveable(studySet.id) { mutableStateOf("ALL") }
    val now = System.currentTimeMillis()
    val dueCards = cards.filter {
        it.type == KnowledgeCardType.QA_FLASHCARD && (it.lastReviewedAt == null || it.nextReviewAt <= now)
    }
    val visibleCards = when (filter) {
        "DUE" -> dueCards
        "QA" -> cards.filter { it.type == KnowledgeCardType.QA_FLASHCARD }
        "KNOWLEDGE" -> cards.filter { it.type == KnowledgeCardType.KNOWLEDGE_CARD }
        "HISTORY" -> reviewedCards
        "QUIZ" -> emptyList()
        else -> cards
    }
    val visibleQuizzes = when (filter) {
        "ALL", "QUIZ" -> quizQuestions
        else -> emptyList()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(studySet.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(
                            studySet.courseName,
                            "${cards.size} 张卡",
                            if (quizQuestions.isNotEmpty()) "${quizQuestions.size} 道测验" else null,
                            if (dueCards.isNotEmpty()) "${dueCards.size} 待复习" else null
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onRename) {
                    Icon(Icons.Default.Edit, contentDescription = "编辑学习集")
                }
                IconButton(onClick = onMerge) {
                    Icon(Icons.Default.Link, contentDescription = "合并学习集")
                }
                IconButton(onClick = onAddCard) {
                    Icon(Icons.Default.Add, contentDescription = "添加知识卡片")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "删除学习集")
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(
                    onClick = onStart,
                    enabled = cards.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Style, contentDescription = null)
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("学习")
                }
                OutlinedButton(
                    onClick = onStartQuiz,
                    enabled = quizQuestions.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.Description, contentDescription = null)
                    Spacer(modifier = Modifier.size(6.dp))
                    Text("测验")
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "ALL" to "全部",
                        "DUE" to "待复习",
                        "QA" to "问答",
                        "KNOWLEDGE" to "知识点",
                        "QUIZ" to "测验",
                        "HISTORY" to "历史"
                    ).forEach { (key, label) ->
                        FilterChip(
                            selected = filter == key,
                            onClick = { filter = key },
                            label = { Text(label) }
                        )
                    }
                }
            }
        }

        if (visibleCards.isEmpty() && visibleQuizzes.isEmpty()) {
            item {
                EmptyStateCard(
                    if (filter == "QUIZ") "这里还没有测验" else "这里还没有内容",
                    "可以从资料生成学习集，或手动添加知识点。"
                )
            }
        } else {
            if (visibleCards.isNotEmpty()) {
                item { SectionTitle("知识卡片") }
                items(visibleCards, key = { "${filter}-${it.flashcardId}" }) { card ->
                    KnowledgeCardRow(
                        card = card,
                        onClick = { onOpenCard(card) },
                        trailing = {
                            KnowledgeCardMenu(
                                card = card,
                                showReview = card.type == KnowledgeCardType.QA_FLASHCARD && filter != "HISTORY",
                                onReview = { onReviewDone(card.flashcardId) },
                                onEdit = { onEditCard(card) },
                                onMove = { onMoveCard(card) },
                                onDelete = { onDeleteCard(card) }
                            )
                        }
                    )
                }
            }
            if (visibleQuizzes.isNotEmpty()) {
                item { SectionTitle("测验题") }
                items(visibleQuizzes, key = { it.id }) { quiz ->
                    QuizQuestionRow(
                        quiz = quiz,
                        onClick = { onOpenQuiz(quiz) }
                    )
                }
            }
        }
    }
}

@Composable
private fun KnowledgeSpaceStats(
    studySets: List<StudySetSummary>,
    cards: List<DueReviewItem>,
    reviewedCards: List<DueReviewItem>
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        KnowledgeMetric("学习集", studySets.size.toString(), Modifier.weight(1f))
        KnowledgeMetric("知识卡", cards.size.toString(), Modifier.weight(1f))
        KnowledgeMetric("历史", reviewedCards.size.toString(), Modifier.weight(1f))
    }
}

@Composable
private fun KnowledgeMetric(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f),
        shape = MaterialTheme.shapes.medium
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun KnowledgeCardRow(
    card: DueReviewItem,
    onClick: () -> Unit,
    trailing: @Composable () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        ListItem(
            headlineContent = {
                Text(
                    if (card.type == KnowledgeCardType.QA_FLASHCARD) card.front else card.title,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Text(
                    listOfNotNull(
                        if (card.type == KnowledgeCardType.QA_FLASHCARD) "问答" else "知识点",
                        card.courseName,
                        card.studySetTitle,
                        if (card.type == KnowledgeCardType.QA_FLASHCARD) {
                            card.hint.takeIf { it.isNotBlank() }
                        } else {
                            card.explanation.take(48).takeIf { it.isNotBlank() }
                        }
                    )
                        .joinToString(" · "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingContent = { Icon(Icons.Default.Style, contentDescription = null) },
            trailingContent = trailing
        )
    }
}

@Composable
private fun QuizQuestionRow(
    quiz: StudySetQuizItem,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        ListItem(
            headlineContent = { Text(quiz.question, maxLines = 2, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Text(
                    listOfNotNull(
                        if (quiz.type == "MULTIPLE_CHOICE") "选择题" else "简答题",
                        quiz.options.takeIf { it.isNotEmpty() }?.let { "${it.size} 个选项" },
                        "点击开始测验"
                    ).joinToString(" · "),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            leadingContent = { Icon(Icons.Default.Description, contentDescription = null) }
        )
    }
}

@Composable
private fun KnowledgeCardMenu(
    card: DueReviewItem,
    showReview: Boolean,
    onReview: () -> Unit,
    onEdit: () -> Unit,
    onMove: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (showReview) {
            TextButton(onClick = onReview) { Text("复习") }
        } else {
            Text(
                if (card.reviewCount > 0) "${card.reviewCount} 次" else "查看",
                style = MaterialTheme.typography.labelMedium
            )
        }
        Box {
            IconButton(onClick = { expanded = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "知识卡片操作")
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("编辑") },
                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onEdit()
                    }
                )
                DropdownMenuItem(
                    text = { Text("移动到其他学习集") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onMove()
                    }
                )
                DropdownMenuItem(
                    text = { Text("删除") },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                    onClick = {
                        expanded = false
                        onDelete()
                    }
                )
            }
        }
    }
}

@Composable
private fun StudySetRow(
    set: StudySetSummary,
    onClick: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMerge: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        ListItem(
            headlineContent = { Text(set.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            supportingContent = {
                Text(
                    listOfNotNull(
                        set.courseName,
                        "${set.flashcardCount} 张卡",
                        "${set.quizCount} 道测验",
                        if (set.dueFlashcardCount > 0) "${set.dueFlashcardCount} 张待复习" else null
                    ).joinToString(" · ")
                )
            },
            leadingContent = { Icon(Icons.Default.History, contentDescription = null) }
            ,
            trailingContent = {
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "学习集操作")
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
                            text = { Text("合并到这里") },
                            leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onMerge()
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
private fun KnowledgeCardDetailDialog(
    item: DueReviewItem,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onReviewDone: () -> Unit
) {
    var showAnswer by remember(item.flashcardId) { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.title.ifBlank { item.studySetTitle }) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item.courseName?.let {
                    Text("课程：$it", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (item.type == KnowledgeCardType.QA_FLASHCARD) {
                    DetailBlock("问题", item.front)
                    DetailBlock("提示", item.hint)
                    if (showAnswer) {
                        DetailBlock("答案", item.back)
                    } else {
                        OutlinedButton(onClick = { showAnswer = true }) { Text("显示答案") }
                    }
                } else {
                    DetailBlock("解释", item.explanation.ifBlank { item.back })
                    DetailBlock("例子", item.example)
                    DetailBlock("易错点", item.pitfall)
                    DetailBlock("公式/术语", item.formula)
                }
                DetailBlock("来源", listOf(item.sourceLocation, item.sourceQuote).filter { it.isNotBlank() }.joinToString("\n"))
                if (item.tags.isNotEmpty()) {
                    Text(item.tags.joinToString(" ") { "#$it" }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                Text("复习 ${item.reviewCount} 次 · 置信度 ${(item.confidence * 100).toInt()}%", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onEdit) { Text("编辑") }
                TextButton(onClick = onReviewDone) { Text("已复习") }
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onDelete) { Text("删除") }
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    )
}

@Composable
private fun DetailBlock(label: String, content: String) {
    if (content.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Text(content, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ManualKnowledgeCardDialog(
    targetStudySetTitle: String?,
    onDismiss: () -> Unit,
    onConfirm: (
        title: String,
        type: KnowledgeCardType,
        front: String,
        back: String,
        hint: String,
        courseName: String?,
        explanation: String,
        example: String,
        pitfall: String,
        formula: String,
        tags: List<String>
    ) -> Unit
) {
    var type by rememberSaveable { mutableStateOf(KnowledgeCardType.QA_FLASHCARD) }
    var title by rememberSaveable { mutableStateOf("") }
    var courseName by rememberSaveable { mutableStateOf("") }
    var front by rememberSaveable { mutableStateOf("") }
    var back by rememberSaveable { mutableStateOf("") }
    var hint by rememberSaveable { mutableStateOf("") }
    var explanation by rememberSaveable { mutableStateOf("") }
    var example by rememberSaveable { mutableStateOf("") }
    var pitfall by rememberSaveable { mutableStateOf("") }
    var formula by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (targetStudySetTitle == null) "新建学习集卡片" else "添加知识卡片") },
        text = {
            LazyColumn(
                modifier = Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        targetStudySetTitle?.let { "保存到：$it" } ?: "保存后会自动创建一个新的学习集",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState())
                    ) {
                        FilterChip(
                            selected = type == KnowledgeCardType.QA_FLASHCARD,
                            onClick = { type = KnowledgeCardType.QA_FLASHCARD },
                            label = { Text("问答闪卡") }
                        )
                        FilterChip(
                            selected = type == KnowledgeCardType.KNOWLEDGE_CARD,
                            onClick = { type = KnowledgeCardType.KNOWLEDGE_CARD },
                            label = { Text("知识点卡") }
                        )
                    }
                }
                if (type == KnowledgeCardType.QA_FLASHCARD) {
                    item { OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("标题，可留空") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                    if (targetStudySetTitle == null) {
                        item { OutlinedTextField(value = courseName, onValueChange = { courseName = it }, label = { Text("课程，可留空") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                    }
                    item { OutlinedTextField(value = front, onValueChange = { front = it }, label = { Text("问题") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
                    item { OutlinedTextField(value = back, onValueChange = { back = it }, label = { Text("答案") }, modifier = Modifier.fillMaxWidth(), minLines = 3) }
                    item { OutlinedTextField(value = hint, onValueChange = { hint = it }, label = { Text("提示关键词，可留空") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                } else {
                    item { OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("知识点标题") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                    if (targetStudySetTitle == null) {
                        item { OutlinedTextField(value = courseName, onValueChange = { courseName = it }, label = { Text("课程，可留空") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                    }
                    item { OutlinedTextField(value = explanation, onValueChange = { explanation = it }, label = { Text("核心解释") }, modifier = Modifier.fillMaxWidth(), minLines = 3) }
                    item { OutlinedTextField(value = example, onValueChange = { example = it }, label = { Text("例子，可留空") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
                    item { OutlinedTextField(value = pitfall, onValueChange = { pitfall = it }, label = { Text("易错点，可留空") }, modifier = Modifier.fillMaxWidth(), minLines = 2) }
                    item { OutlinedTextField(value = formula, onValueChange = { formula = it }, label = { Text("公式/术语，可留空") }, modifier = Modifier.fillMaxWidth()) }
                }
                item { OutlinedTextField(value = tags, onValueChange = { tags = it }, label = { Text("标签，用逗号分隔") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        title,
                        type,
                        front,
                        back,
                        hint,
                        courseName.takeIf { it.isNotBlank() },
                        explanation,
                        example,
                        pitfall,
                        formula,
                        tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    )
                },
                enabled = if (type == KnowledgeCardType.QA_FLASHCARD) {
                    front.isNotBlank() && back.isNotBlank()
                } else {
                    title.isNotBlank() && explanation.isNotBlank()
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun EditKnowledgeCardDialog(
    item: DueReviewItem,
    onDismiss: () -> Unit,
    onSave: (DueReviewItem) -> Unit
) {
    var title by rememberSaveable(item.flashcardId) { mutableStateOf(item.title) }
    var front by rememberSaveable(item.flashcardId) { mutableStateOf(item.front) }
    var back by rememberSaveable(item.flashcardId) { mutableStateOf(item.back) }
    var hint by rememberSaveable(item.flashcardId) { mutableStateOf(item.hint) }
    var explanation by rememberSaveable(item.flashcardId) { mutableStateOf(item.explanation) }
    var example by rememberSaveable(item.flashcardId) { mutableStateOf(item.example) }
    var pitfall by rememberSaveable(item.flashcardId) { mutableStateOf(item.pitfall) }
    var formula by rememberSaveable(item.flashcardId) { mutableStateOf(item.formula) }
    var tags by rememberSaveable(item.flashcardId) { mutableStateOf(item.tags.joinToString(", ")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑知识卡片") },
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.heightIn(max = 520.dp)) {
                item {
                    OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("标题") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
                if (item.type == KnowledgeCardType.QA_FLASHCARD) {
                    item { OutlinedTextField(value = front, onValueChange = { front = it }, label = { Text("问题") }, modifier = Modifier.fillMaxWidth()) }
                    item { OutlinedTextField(value = back, onValueChange = { back = it }, label = { Text("答案") }, modifier = Modifier.fillMaxWidth()) }
                    item { OutlinedTextField(value = hint, onValueChange = { hint = it }, label = { Text("提示") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
                } else {
                    item { OutlinedTextField(value = explanation, onValueChange = { explanation = it }, label = { Text("解释") }, modifier = Modifier.fillMaxWidth()) }
                    item { OutlinedTextField(value = example, onValueChange = { example = it }, label = { Text("例子") }, modifier = Modifier.fillMaxWidth()) }
                    item { OutlinedTextField(value = pitfall, onValueChange = { pitfall = it }, label = { Text("易错点") }, modifier = Modifier.fillMaxWidth()) }
                    item { OutlinedTextField(value = formula, onValueChange = { formula = it }, label = { Text("公式/术语") }, modifier = Modifier.fillMaxWidth()) }
                }
                item { OutlinedTextField(value = tags, onValueChange = { tags = it }, label = { Text("标签，用逗号分隔") }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(
                        item.copy(
                            title = title,
                            front = front,
                            back = back,
                            hint = hint,
                            explanation = explanation,
                            example = example,
                            pitfall = pitfall,
                            formula = formula,
                            tags = tags.split(",").map { it.trim() }.filter { it.isNotBlank() },
                            editedByUser = true,
                            updatedAt = System.currentTimeMillis()
                        )
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun StudySetMergeDialog(
    target: StudySetSummary,
    studySets: List<StudySetSummary>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var selectedIds by remember(target.id) { mutableStateOf(emptySet<String>()) }
    val candidates = studySets.filter { it.id != target.id }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("合并学习集") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "合并到：${target.title}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                if (candidates.isEmpty()) {
                    Text("暂无其他学习集可合并。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(candidates, key = { it.id }) { set ->
                            ListItem(
                                modifier = Modifier.clickable {
                                    selectedIds = if (set.id in selectedIds) selectedIds - set.id else selectedIds + set.id
                                },
                                headlineContent = { Text(set.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = {
                                    Text("${set.flashcardCount} 张卡 · ${set.quizCount} 道测验")
                                },
                                leadingContent = {
                                    Checkbox(
                                        checked = set.id in selectedIds,
                                        onCheckedChange = { checked ->
                                            selectedIds = if (checked) selectedIds + set.id else selectedIds - set.id
                                        }
                                    )
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(selectedIds.toList()) },
                enabled = selectedIds.isNotEmpty()
            ) { Text("合并") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun MoveKnowledgeCardDialog(
    card: DueReviewItem,
    studySets: List<StudySetSummary>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val candidates = studySets.filter { it.id != card.studySetId }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("移动知识卡片") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    card.title.ifBlank { card.front },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (candidates.isEmpty()) {
                    Text("暂无其他学习集可移动。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 360.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(candidates, key = { it.id }) { set ->
                            ListItem(
                                modifier = Modifier.clickable { onConfirm(set.id) },
                                headlineContent = { Text(set.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text("${set.flashcardCount} 张卡 · ${set.quizCount} 道测验") },
                                leadingContent = { Icon(Icons.Default.History, contentDescription = null) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

@Composable
private fun StudySetQuizPage(
    studySet: StudySetSummary,
    questions: List<StudySetQuizItem>,
    initialQuestionId: String?,
    onDismiss: () -> Unit
) {
    val initialIndex = remember(initialQuestionId, questions) {
        questions.indexOfFirst { it.id == initialQuestionId }.takeIf { it >= 0 } ?: 0
    }
    var index by rememberSaveable(studySet.id, questions.size, initialQuestionId) { mutableStateOf(initialIndex) }
    var selectedOption by rememberSaveable(studySet.id, index) { mutableStateOf<String?>(null) }
    var showAnswer by rememberSaveable(studySet.id, index) { mutableStateOf(false) }
    val safeIndex = index.coerceIn(0, (questions.size - 1).coerceAtLeast(0))
    val current = questions.getOrNull(safeIndex)

    LaunchedEffect(questions.size) {
        if (index >= questions.size) index = (questions.size - 1).coerceAtLeast(0)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "退出测验")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(studySet.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                    Text(
                        if (questions.isEmpty()) "0 / 0" else "${safeIndex + 1} / ${questions.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (current == null) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    EmptyStateCard("没有测验题", "这个学习集里还没有测验题。")
                }
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.extraLarge,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                if (current.type == "MULTIPLE_CHOICE") "选择题" else "简答题",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                current.question,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold
                            )
                            if (current.options.isNotEmpty()) {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    current.options.forEach { option ->
                                        val isSelected = selectedOption == option
                                        OutlinedButton(
                                            onClick = {
                                                selectedOption = option
                                                showAnswer = true
                                            },
                                            modifier = Modifier.fillMaxWidth(),
                                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                                        ) {
                                            Text(
                                                option,
                                                modifier = Modifier.weight(1f),
                                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }
                                }
                            } else if (!showAnswer) {
                                OutlinedButton(
                                    onClick = { showAnswer = true },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                ) {
                                    Text("显示答案")
                                }
                            }

                            if (showAnswer) {
                                DetailBlock("答案", current.answer)
                                DetailBlock("解析", current.explanation)
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    selectedOption = null
                                    showAnswer = false
                                    index = (safeIndex - 1).coerceAtLeast(0)
                                },
                                enabled = safeIndex > 0
                            ) { Text("上一题") }
                            Text(
                                selectedOption?.let { "已选择" } ?: if (showAnswer) "已显示答案" else "作答后显示答案",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            TextButton(
                                onClick = {
                                    selectedOption = null
                                    showAnswer = false
                                    index = (safeIndex + 1).coerceAtMost(questions.lastIndex)
                                },
                                enabled = safeIndex < questions.lastIndex
                            ) { Text("下一题") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StudySetLearningPage(
    studySet: StudySetSummary,
    cards: List<DueReviewItem>,
    onDismiss: () -> Unit,
    onEditCard: (DueReviewItem) -> Unit,
    onReview: (DueReviewItem, Boolean) -> Unit
) {
    var index by rememberSaveable(studySet.id, cards.size) { mutableStateOf(0) }
    var showAnswer by rememberSaveable(studySet.id, index) { mutableStateOf(false) }
    var dragX by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }
    val safeIndex = index.coerceIn(0, (cards.size - 1).coerceAtLeast(0))
    val current = cards.getOrNull(safeIndex)

    LaunchedEffect(cards.size) {
        if (index >= cards.size) index = (cards.size - 1).coerceAtLeast(0)
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onDismiss) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "退出学习")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(studySet.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                    Text(
                        if (cards.isEmpty()) "0 / 0" else "${safeIndex + 1} / ${cards.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (current != null) {
                    IconButton(onClick = { onEditCard(current) }) {
                        Icon(Icons.Default.Edit, contentDescription = "编辑卡片")
                    }
                }
            }

            if (current == null) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    EmptyStateCard("没有可学习的卡片", "这个学习集里还没有知识卡片。")
                }
            } else {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .pointerInput(current.flashcardId) {
                            detectDragGestures(
                                onDragStart = {
                                    dragX = 0f
                                    dragY = 0f
                                },
                                onDrag = { _, dragAmount ->
                                    dragX += dragAmount.x
                                    dragY += dragAmount.y
                                },
                                onDragEnd = {
                                    when {
                                        abs(dragX) > abs(dragY) && abs(dragX) > 120f -> {
                                            onReview(current, dragX > 0)
                                            if (safeIndex < cards.lastIndex) index = safeIndex + 1
                                        }
                                        abs(dragY) > 90f -> {
                                            index = if (dragY > 0) {
                                                (safeIndex - 1).coerceAtLeast(0)
                                            } else {
                                                (safeIndex + 1).coerceAtMost(cards.lastIndex)
                                            }
                                        }
                                    }
                                    dragX = 0f
                                    dragY = 0f
                                }
                            )
                        },
                    color = MaterialTheme.colorScheme.surface,
                    shape = MaterialTheme.shapes.extraLarge,
                    tonalElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                if (current.type == KnowledgeCardType.QA_FLASHCARD) "问答闪卡" else "知识点卡",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                current.title.ifBlank { current.front },
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                            if (current.type == KnowledgeCardType.QA_FLASHCARD) {
                                DetailBlock("问题", current.front)
                                DetailBlock("提示", current.hint)
                                if (showAnswer) {
                                    DetailBlock("答案", current.back)
                                } else {
                                    OutlinedButton(
                                        onClick = { showAnswer = true },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("显示答案")
                                    }
                                }
                            } else {
                                DetailBlock("解释", current.explanation.ifBlank { current.back })
                                DetailBlock("例子", current.example)
                                DetailBlock("易错点", current.pitfall)
                                DetailBlock("公式/术语", current.formula)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {
                                onReview(current, false)
                                if (safeIndex < cards.lastIndex) index = safeIndex + 1
                            }) {
                                Text("没记清")
                            }
                            Text(
                                current.tags.take(2).joinToString(" ") { "#$it" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            TextButton(onClick = {
                                onReview(current, true)
                                if (safeIndex < cards.lastIndex) index = safeIndex + 1
                            }) {
                                Text("记住了")
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = { index = (safeIndex - 1).coerceAtLeast(0) },
                    enabled = safeIndex > 0
                ) { Text("上一张") }
                Text(
                    "左滑没记清 · 右滑记住 · 上下切换",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                TextButton(
                    onClick = { index = (safeIndex + 1).coerceAtMost(cards.lastIndex) },
                    enabled = current != null && safeIndex < cards.lastIndex
                ) { Text("下一张") }
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
                color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.58f),
                shape = MaterialTheme.shapes.large
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                    Text(
                        text = "仅支持 BIT 校园网内网访问，请在连接校园 WiFi 或 VPN 后使用",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onOpen: () -> Unit,
    onToggleSelected: () -> Unit,
    onRename: () -> Unit,
    onMove: () -> Unit,
    onContext: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = if (isSelectionMode) onToggleSelected else onOpen),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.76f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
            }
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        ListItem(
            headlineContent = {
                Text(file.displayName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            supportingContent = {
                Text(
                    listOfNotNull(
                        formatFileSize(file.sizeBytes),
                        file.courseName?.let { "课程: $it" },
                        file.tags.takeIf { it.isNotEmpty() }?.joinToString(", ") { "#$it" },
                        file.sourceTitle?.takeIf { file.sourceType == "bitshare" }?.let { "来源: BITShare" }
                    ).joinToString(" · ")
                )
            },
            leadingContent = {
                if (isSelectionMode) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleSelected() }
                    )
                } else {
                    Icon(Icons.Default.Description, contentDescription = null)
                }
            },
            trailingContent = {
                if (!isSelectionMode) {
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
                                text = { Text("关联课程/标签") },
                                leadingIcon = { Icon(Icons.Default.Link, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onContext()
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

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
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
private fun LearningContextDialog(
    file: KnowledgeBaseFileSummary,
    onDismiss: () -> Unit,
    onConfirm: (Int?, String?, List<String>) -> Unit
) {
    var courseIdText by rememberSaveable(file.id) { mutableStateOf(file.courseId?.toString().orEmpty()) }
    var courseName by rememberSaveable(file.id) { mutableStateOf(file.courseName.orEmpty()) }
    var tagsText by rememberSaveable(file.id) { mutableStateOf(file.tags.joinToString(", ")) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("关联课程/标签") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    file.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                OutlinedTextField(
                    value = courseIdText,
                    onValueChange = { courseIdText = it.filter(Char::isDigit) },
                    label = { Text("课程 ID，可留空") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = courseName,
                    onValueChange = { courseName = it },
                    label = { Text("课程名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tagsText,
                    onValueChange = { tagsText = it },
                    label = { Text("标签，用逗号分隔") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        courseIdText.toIntOrNull(),
                        courseName.trim().takeIf { it.isNotBlank() },
                        tagsText.split(",").map { it.trim() }.filter { it.isNotBlank() }
                    )
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
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
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
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

private fun shareFiles(
    context: android.content.Context,
    files: List<KnowledgeBaseFileSummary>
) {
    val existingFiles = files.mapNotNull { summary ->
        File(summary.localPath).takeIf { it.exists() }
    }
    if (existingFiles.isEmpty()) {
        Toast.makeText(context, "文件不存在，无法分享", Toast.LENGTH_SHORT).show()
        return
    }

    runCatching {
        val uris = ArrayList(
            existingFiles.map { file ->
                FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )
            }
        )

        val shareIntent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = ClipData.newUri(context.contentResolver, "shared-files", uris.first()).apply {
                uris.drop(1).forEach { uri ->
                    addItem(ClipData.Item(uri))
                }
            }
        }

        context.startActivity(Intent.createChooser(shareIntent, "分享文件"))
    }.onFailure {
        Toast.makeText(context, "当前设备无法分享这些文件", Toast.LENGTH_SHORT).show()
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
