package com.github.garynasser.correction_notebook.ui.screens.knowledgebase

import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareFileDetail
import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareSearchResult
import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareSortOption
import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareFolderDetail
import com.github.garynasser.correction_notebook.data.model.knowledgebase.KnowledgeBaseFileSummary
import com.github.garynasser.correction_notebook.data.model.knowledgebase.KnowledgeBaseFolderChoice
import com.github.garynasser.correction_notebook.data.model.knowledgebase.KnowledgeBaseFolderContent
import com.github.garynasser.correction_notebook.data.model.studyset.DueReviewItem
import com.github.garynasser.correction_notebook.data.model.studyset.KnowledgeCardType
import com.github.garynasser.correction_notebook.data.model.studyset.StudySetQuizItem
import com.github.garynasser.correction_notebook.data.model.studyset.StudySetSummary
import com.github.garynasser.correction_notebook.data.repository.BitShareRepository
import com.github.garynasser.correction_notebook.data.repository.KnowledgeBaseRepository
import com.github.garynasser.correction_notebook.data.repository.StudySetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class KnowledgeBaseUiState(
    val selectedTabIndex: Int = 0,
    val currentFolderId: String? = null,
    val localSearchQuery: String = "",
    val folderContent: KnowledgeBaseFolderContent = KnowledgeBaseFolderContent(
        breadcrumbs = emptyList(),
        folders = emptyList(),
        files = emptyList()
    ),
    val recentFiles: List<KnowledgeBaseFileSummary> = emptyList(),
    val folderChoices: List<KnowledgeBaseFolderChoice> = emptyList(),
    val remoteQuery: String = "",
    val remoteSort: BitShareSortOption = BitShareSortOption.RELEVANCE,
    val remoteResults: List<BitShareSearchResult> = emptyList(),
    val selectedRemoteDetail: BitShareFileDetail? = null,
    val selectedRemoteFolderDetail: BitShareFolderDetail? = null,
    val isRemoteSearching: Boolean = false,
    val isRemoteDetailLoading: Boolean = false,
    val isRemoteFolderLoading: Boolean = false,
    val isImportingLocalFile: Boolean = false,
    val remoteErrorMessage: String? = null,
    val studySets: List<StudySetSummary> = emptyList(),
    val knowledgeCards: List<DueReviewItem> = emptyList(),
    val reviewedCards: List<DueReviewItem> = emptyList(),
    val quizQuestions: List<StudySetQuizItem> = emptyList(),
    val isLocalBusy: Boolean = false,
    val activeDownloadId: String? = null,
    val snackbarMessage: String? = null
)

private data class LocalUiSnapshot(
    val selectedTabIndex: Int,
    val currentFolderId: String?,
    val localSearchQuery: String,
    val folderContent: KnowledgeBaseFolderContent,
    val recentFiles: List<KnowledgeBaseFileSummary>,
    val folderChoices: List<KnowledgeBaseFolderChoice>
)

private data class RemoteUiSnapshot(
    val remoteQuery: String,
    val remoteSort: BitShareSortOption,
    val remoteResults: List<BitShareSearchResult>,
    val selectedRemoteDetail: BitShareFileDetail?,
    val selectedRemoteFolderDetail: BitShareFolderDetail?,
    val isRemoteSearching: Boolean,
    val isRemoteDetailLoading: Boolean,
    val isRemoteFolderLoading: Boolean,
    val isImportingLocalFile: Boolean,
    val remoteErrorMessage: String?,
    val isLocalBusy: Boolean,
    val activeDownloadId: String?,
    val snackbarMessage: String?
)

private data class RemoteLoadingSnapshot(
    val isRemoteSearching: Boolean,
    val isRemoteDetailLoading: Boolean,
    val isImportingLocalFile: Boolean,
    val remoteErrorMessage: String?
)

private data class StudyContentSnapshot(
    val studySets: List<StudySetSummary>,
    val knowledgeCards: List<DueReviewItem>,
    val reviewedCards: List<DueReviewItem>,
    val quizQuestions: List<StudySetQuizItem>
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class KnowledgeBaseViewModel @Inject constructor(
    private val knowledgeBaseRepository: KnowledgeBaseRepository,
    private val bitShareRepository: BitShareRepository,
    private val studySetRepository: StudySetRepository
) : ViewModel() {

    private val selectedTabIndex = MutableStateFlow(0)
    private val currentFolderId = MutableStateFlow<String?>(null)
    private val localSearchQuery = MutableStateFlow("")

    private val remoteQuery = MutableStateFlow("")
    private val remoteSort = MutableStateFlow(BitShareSortOption.RELEVANCE)

    private val remoteResults = MutableStateFlow<List<BitShareSearchResult>>(emptyList())
    private val selectedRemoteDetail = MutableStateFlow<BitShareFileDetail?>(null)
    private val selectedRemoteFolderDetail = MutableStateFlow<BitShareFolderDetail?>(null)
    private val isRemoteSearching = MutableStateFlow(false)
    private val isRemoteDetailLoading = MutableStateFlow(false)
    private val isImportingLocalFile = MutableStateFlow(false)
    private val remoteErrorMessage = MutableStateFlow<String?>(null)
    private val isLocalBusy = MutableStateFlow(false)
    private val activeDownloadId = MutableStateFlow<String?>(null)
    private val snackbarMessage = MutableStateFlow<String?>(null)

    private val folderContent: StateFlow<KnowledgeBaseFolderContent> = combine(
        currentFolderId,
        localSearchQuery
    ) { folderId, query ->
        folderId to query
    }.flatMapLatest { (folderId, query) ->
        knowledgeBaseRepository.observeFolderContent(folderId, query)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = KnowledgeBaseFolderContent(
            breadcrumbs = emptyList(),
            folders = emptyList(),
            files = emptyList()
        )
    )

    private val recentFiles = knowledgeBaseRepository.observeRecentFiles().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val folderChoices = knowledgeBaseRepository.observeFolderChoices().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val studySets = studySetRepository.observeStudySets().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val knowledgeCards = studySetRepository.observeKnowledgeCards().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val reviewedCards = studySetRepository.observeReviewedCards().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val quizQuestions = studySetRepository.observeQuizQuestions().stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    private val localUiSnapshot = combine(
        combine(selectedTabIndex, currentFolderId, localSearchQuery) { tabIndex, folderId, localQuery ->
            Triple(tabIndex, folderId, localQuery)
        },
        combine(folderContent, recentFiles, folderChoices) { content, recent, choices ->
            Triple(content, recent, choices)
        }
    ) { meta, data ->
        LocalUiSnapshot(
            selectedTabIndex = meta.first,
            currentFolderId = meta.second,
            localSearchQuery = meta.third,
            folderContent = data.first,
            recentFiles = data.second,
            folderChoices = data.third
        )
    }

    private val remoteUiSnapshot = combine(
        combine(remoteQuery, remoteSort, remoteResults) { query, sort, results ->
            Triple(query, sort, results)
        },
        combine(selectedRemoteDetail, selectedRemoteFolderDetail) { detail, folderDetail ->
            Pair(detail, folderDetail)
        },
        combine(isRemoteSearching, isRemoteDetailLoading, isImportingLocalFile, remoteErrorMessage) { searching, detailLoading, importing, error ->
            RemoteLoadingSnapshot(
                isRemoteSearching = searching,
                isRemoteDetailLoading = detailLoading,
                isImportingLocalFile = importing,
                remoteErrorMessage = error
            )
        },
        combine(isLocalBusy, activeDownloadId, snackbarMessage) { localBusy, downloadId, message ->
            Triple(localBusy, downloadId, message)
        }
    ) { searchMeta, detailMeta, loadingMeta, localMeta ->
        RemoteUiSnapshot(
            remoteQuery = searchMeta.first,
            remoteSort = searchMeta.second,
            remoteResults = searchMeta.third,
            selectedRemoteDetail = detailMeta.first,
            selectedRemoteFolderDetail = detailMeta.second,
            isRemoteSearching = loadingMeta.isRemoteSearching,
            isRemoteDetailLoading = loadingMeta.isRemoteDetailLoading,
            isRemoteFolderLoading = false,
            isImportingLocalFile = loadingMeta.isImportingLocalFile,
            remoteErrorMessage = loadingMeta.remoteErrorMessage,
            isLocalBusy = localMeta.first,
            activeDownloadId = localMeta.second,
            snackbarMessage = localMeta.third
        )
    }

    val uiState: StateFlow<KnowledgeBaseUiState> = combine(
        localUiSnapshot,
        remoteUiSnapshot,
        combine(studySets, knowledgeCards, reviewedCards, quizQuestions) { sets, cards, reviewed, quizzes ->
            StudyContentSnapshot(sets, cards, reviewed, quizzes)
        }
    ) { local, remote, study ->
        KnowledgeBaseUiState(
            selectedTabIndex = local.selectedTabIndex,
            currentFolderId = local.currentFolderId,
            localSearchQuery = local.localSearchQuery,
            folderContent = local.folderContent,
            recentFiles = local.recentFiles,
            folderChoices = local.folderChoices,
            remoteQuery = remote.remoteQuery,
            remoteSort = remote.remoteSort,
            remoteResults = remote.remoteResults,
            selectedRemoteDetail = remote.selectedRemoteDetail,
            selectedRemoteFolderDetail = remote.selectedRemoteFolderDetail,
            isRemoteSearching = remote.isRemoteSearching,
            isRemoteDetailLoading = remote.isRemoteDetailLoading,
            isImportingLocalFile = remote.isImportingLocalFile,
            remoteErrorMessage = remote.remoteErrorMessage,
            studySets = study.studySets,
            knowledgeCards = study.knowledgeCards,
            reviewedCards = study.reviewedCards,
            quizQuestions = study.quizQuestions,
            isLocalBusy = remote.isLocalBusy,
            activeDownloadId = remote.activeDownloadId,
            snackbarMessage = remote.snackbarMessage
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = KnowledgeBaseUiState()
    )

    fun selectTab(index: Int) {
        selectedTabIndex.value = index
    }

    fun updateLocalSearchQuery(query: String) {
        localSearchQuery.value = query
    }

    fun enterFolder(folderId: String) {
        currentFolderId.value = folderId
    }

    fun navigateBack() {
        val breadcrumbs = folderContent.value.breadcrumbs
        currentFolderId.value = breadcrumbs
            .dropLast(1)
            .lastOrNull()
            ?.id
    }

    fun navigateToBreadcrumb(folderId: String?) {
        currentFolderId.value = folderId
    }

    fun updateRemoteQuery(query: String) {
        remoteQuery.value = query
    }

    fun updateRemoteSort(sortOption: BitShareSortOption) {
        remoteSort.value = sortOption
    }

    fun searchRemoteResources() {
        if (remoteQuery.value.isBlank()) {
            remoteResults.value = emptyList()
            remoteErrorMessage.value = null
            return
        }

        viewModelScope.launch {
            isRemoteSearching.value = true
            remoteErrorMessage.value = null
            bitShareRepository.searchFiles(remoteQuery.value, remoteSort.value)
                .onSuccess {
                    remoteResults.value = it
                }
                .onFailure {
                    remoteErrorMessage.value = it.message ?: "搜索失败"
                }
            isRemoteSearching.value = false
        }
    }

    fun loadRemoteDetail(fileId: String) {
        viewModelScope.launch {
            isRemoteDetailLoading.value = true
            bitShareRepository.getFileDetail(fileId)
                .onSuccess {
                    selectedRemoteDetail.value = it
                    remoteErrorMessage.value = null
                }
                .onFailure {
                    remoteErrorMessage.value = it.message ?: "加载详情失败"
                }
            isRemoteDetailLoading.value = false
        }
    }

    /**
     * 加载文件夹详情
     * 注意：由于 /api/public/files?folder_id= 接口返回 404，无法获取文件夹内的文件列表，
     * 因此只能显示文件夹信息，无法列出文件夹内容
     */
    fun loadRemoteFolderDetail(folderId: String) {
        viewModelScope.launch {
            isRemoteDetailLoading.value = true
            bitShareRepository.getFolderDetail(folderId)
                .onSuccess {
                    selectedRemoteFolderDetail.value = it
                    remoteErrorMessage.value = null
                }
                .onFailure {
                    remoteErrorMessage.value = it.message ?: "加载目录详情失败"
                }
            isRemoteDetailLoading.value = false
        }
    }

    fun dismissRemoteDetail() {
        selectedRemoteDetail.value = null
    }

    fun dismissRemoteFolderDetail() {
        selectedRemoteFolderDetail.value = null
    }

    fun createFolder(name: String) {
        viewModelScope.launch {
            isLocalBusy.value = true
            knowledgeBaseRepository.createFolder(currentFolderId.value, name)
                .onSuccess {
                    snackbarMessage.value = "已创建文件夹"
                }
                .onFailure {
                    snackbarMessage.value = it.message ?: "创建文件夹失败"
                }
            isLocalBusy.value = false
        }
    }

    fun renameFolder(folderId: String, newName: String) {
        viewModelScope.launch {
            isLocalBusy.value = true
            knowledgeBaseRepository.renameFolder(folderId, newName)
                .onSuccess { snackbarMessage.value = "已重命名文件夹" }
                .onFailure { snackbarMessage.value = it.message ?: "重命名失败" }
            isLocalBusy.value = false
        }
    }

    fun deleteFolder(folderId: String) {
        viewModelScope.launch {
            isLocalBusy.value = true
            knowledgeBaseRepository.deleteFolder(folderId)
                .onSuccess { snackbarMessage.value = "已删除文件夹" }
                .onFailure { snackbarMessage.value = it.message ?: "删除失败" }
            isLocalBusy.value = false
        }
    }

    fun renameFile(fileId: String, newName: String) {
        viewModelScope.launch {
            isLocalBusy.value = true
            knowledgeBaseRepository.renameFile(fileId, newName)
                .onSuccess { snackbarMessage.value = "已重命名文件" }
                .onFailure { snackbarMessage.value = it.message ?: "重命名失败" }
            isLocalBusy.value = false
        }
    }

    fun moveFile(fileId: String, targetFolderId: String?) {
        viewModelScope.launch {
            isLocalBusy.value = true
            knowledgeBaseRepository.moveFile(fileId, targetFolderId)
                .onSuccess {
                    snackbarMessage.value = "文件已移动"
                }
                .onFailure {
                    snackbarMessage.value = it.message ?: "移动失败"
                }
            isLocalBusy.value = false
        }
    }

    fun moveFiles(fileIds: Set<String>, targetFolderId: String?) {
        if (fileIds.isEmpty()) return
        viewModelScope.launch {
            isLocalBusy.value = true
            var successCount = 0
            var failureCount = 0
            var lastErrorMessage: String? = null

            fileIds.forEach { fileId ->
                knowledgeBaseRepository.moveFile(fileId, targetFolderId)
                    .onSuccess { successCount += 1 }
                    .onFailure {
                        failureCount += 1
                        lastErrorMessage = it.message
                    }
            }

            snackbarMessage.value = when {
                successCount > 0 && failureCount == 0 -> "已移动 $successCount 个文件"
                successCount > 0 -> "成功移动 $successCount 个文件，$failureCount 个失败"
                else -> lastErrorMessage ?: "移动失败"
            }
            isLocalBusy.value = false
        }
    }

    fun deleteFile(fileId: String) {
        viewModelScope.launch {
            isLocalBusy.value = true
            knowledgeBaseRepository.deleteFile(fileId)
                .onSuccess { snackbarMessage.value = "文件已删除" }
                .onFailure { snackbarMessage.value = it.message ?: "删除失败" }
            isLocalBusy.value = false
        }
    }

    fun updateFileLearningContext(
        fileId: String,
        courseId: Int?,
        courseName: String?,
        tags: List<String>
    ) {
        viewModelScope.launch {
            isLocalBusy.value = true
            knowledgeBaseRepository.updateFileLearningContext(fileId, courseId, courseName, tags)
                .onSuccess { snackbarMessage.value = "已更新资料学习信息" }
                .onFailure { snackbarMessage.value = it.message ?: "更新失败" }
            isLocalBusy.value = false
        }
    }

    fun markFlashcardReviewed(flashcardId: String, remembered: Boolean = true) {
        viewModelScope.launch {
            studySetRepository.markFlashcardReviewed(flashcardId, remembered = remembered)
                .onSuccess { snackbarMessage.value = if (remembered) "已加入历史闪卡" else "已安排再次复习" }
                .onFailure { snackbarMessage.value = it.message ?: "复习状态更新失败" }
        }
    }

    fun addManualKnowledgeCard(
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
        tags: List<String>,
        studySetId: String? = null
    ) {
        viewModelScope.launch {
            isLocalBusy.value = true
            studySetRepository.saveManualCard(
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
                studySetId = studySetId
            )
                .onSuccess { snackbarMessage.value = "知识点已保存" }
                .onFailure { snackbarMessage.value = it.message ?: "保存知识点失败" }
            isLocalBusy.value = false
        }
    }

    fun updateKnowledgeCard(card: DueReviewItem) {
        viewModelScope.launch {
            studySetRepository.updateKnowledgeCard(card)
                .onSuccess { snackbarMessage.value = "知识卡片已更新" }
                .onFailure { snackbarMessage.value = it.message ?: "更新知识卡片失败" }
        }
    }

    fun deleteKnowledgeCard(cardId: String) {
        viewModelScope.launch {
            studySetRepository.deleteKnowledgeCard(cardId)
                .onSuccess { snackbarMessage.value = "知识卡片已删除" }
                .onFailure { snackbarMessage.value = it.message ?: "删除知识卡片失败" }
        }
    }

    fun renameStudySet(studySetId: String, title: String) {
        viewModelScope.launch {
            studySetRepository.renameStudySet(studySetId, title)
                .onSuccess { snackbarMessage.value = "学习集已重命名" }
                .onFailure { snackbarMessage.value = it.message ?: "重命名学习集失败" }
        }
    }

    fun deleteStudySet(studySetId: String) {
        viewModelScope.launch {
            studySetRepository.deleteStudySet(studySetId)
                .onSuccess { snackbarMessage.value = "学习集已删除" }
                .onFailure { snackbarMessage.value = it.message ?: "删除学习集失败" }
        }
    }

    fun mergeStudySets(sourceStudySetIds: List<String>, targetStudySetId: String) {
        viewModelScope.launch {
            studySetRepository.mergeStudySets(sourceStudySetIds, targetStudySetId)
                .onSuccess { snackbarMessage.value = "学习集已合并" }
                .onFailure { snackbarMessage.value = it.message ?: "合并学习集失败" }
        }
    }

    fun moveKnowledgeCard(cardId: String, targetStudySetId: String) {
        viewModelScope.launch {
            studySetRepository.moveKnowledgeCard(cardId, targetStudySetId)
                .onSuccess { snackbarMessage.value = "知识卡片已移动" }
                .onFailure { snackbarMessage.value = it.message ?: "移动知识卡片失败" }
        }
    }

    fun deleteFiles(fileIds: Set<String>) {
        if (fileIds.isEmpty()) return
        viewModelScope.launch {
            isLocalBusy.value = true
            var successCount = 0
            var failureCount = 0
            var lastErrorMessage: String? = null

            fileIds.forEach { fileId ->
                knowledgeBaseRepository.deleteFile(fileId)
                    .onSuccess { successCount += 1 }
                    .onFailure {
                        failureCount += 1
                        lastErrorMessage = it.message
                    }
            }

            snackbarMessage.value = when {
                successCount > 0 && failureCount == 0 -> "已删除 $successCount 个文件"
                successCount > 0 -> "成功删除 $successCount 个文件，$failureCount 个失败"
                else -> lastErrorMessage ?: "删除失败"
            }
            isLocalBusy.value = false
        }
    }

    fun importLocalFile(fileUri: Uri) {
        importLocalFiles(listOf(fileUri))
    }

    fun importLocalFiles(fileUris: List<Uri>) {
        if (fileUris.isEmpty()) return
        viewModelScope.launch {
            isImportingLocalFile.value = true
            var successCount = 0
            var failureCount = 0
            var lastErrorMessage: String? = null

            fileUris.forEach { fileUri ->
                knowledgeBaseRepository.importLocalFile(
                    targetFolderId = currentFolderId.value,
                    fileUri = fileUri
                ).onSuccess {
                    successCount += 1
                }.onFailure {
                    failureCount += 1
                    lastErrorMessage = it.message
                }
            }

            val folderName = knowledgeBaseRepository.getFolderName(currentFolderId.value)
            snackbarMessage.value = when {
                successCount > 0 && failureCount == 0 -> {
                    if (successCount == 1) "已导入到 $folderName" else "已导入 $successCount 个文件到 $folderName"
                }
                successCount > 0 -> "成功导入 $successCount 个文件，$failureCount 个失败"
                else -> lastErrorMessage ?: "导入失败"
            }
            isImportingLocalFile.value = false
        }
    }

    fun downloadSearchResultToFolder(
        result: BitShareSearchResult,
        folderId: String?
    ) {
        viewModelScope.launch {
            val detail = bitShareRepository.getFileDetail(result.id).getOrElse {
                BitShareFileDetail(
                    id = result.id,
                    title = result.title,
                    originalName = result.originalName.ifBlank { result.title },
                    extension = result.extension,
                    path = null,
                    description = null,
                    mimeType = guessMimeType(result.extension),
                    sizeBytes = result.sizeBytes,
                    uploadedAt = result.uploadedAt,
                    downloadCount = result.downloadCount
                )
            }
            selectedRemoteDetail.value = detail
            performRemoteDownload(folderId, detail)
        }
    }

    fun downloadRemoteFileToFolder(folderId: String?) {
        val detail = selectedRemoteDetail.value ?: return
        performRemoteDownload(folderId, detail)
    }

    private fun performRemoteDownload(
        folderId: String?,
        detail: BitShareFileDetail
    ) {
        viewModelScope.launch {
            activeDownloadId.value = detail.id
            snackbarMessage.value = "开始下载 ${detail.originalName}"
            remoteErrorMessage.value = null

            val downloadResult = bitShareRepository.downloadFile(detail.id)
            downloadResult
                .mapCatching { body ->
                    withContext(Dispatchers.IO) {
                        body.use { responseBody ->
                            knowledgeBaseRepository.importDownloadedFile(
                                detail = detail,
                                targetFolderId = folderId ?: KnowledgeBaseRepository.ROOT_FOLDER_ID,
                                inputBytes = responseBody.bytes()
                            ).getOrThrow()
                        }
                    }
                }
                .onSuccess {
                    val folderName = knowledgeBaseRepository.getFolderName(folderId)
                    snackbarMessage.value = "已保存到 $folderName"
                    selectedTabIndex.value = 0
                    selectedRemoteDetail.value = null
                }
                .onFailure {
                    snackbarMessage.value = it.message ?: "下载失败"
                }

            activeDownloadId.value = null
        }
    }

    private fun guessMimeType(extension: String): String {
        return when (extension.lowercase()) {
            "pdf" -> "application/pdf"
            "ppt" -> "application/vnd.ms-powerpoint"
            "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            "png" -> "image/png"
            "jpg", "jpeg" -> "image/jpeg"
            "txt", "md" -> "text/plain"
            else -> "application/octet-stream"
        }
    }

    fun consumeSnackbarMessage() {
        snackbarMessage.value = null
    }
}
