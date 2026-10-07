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
import com.github.garynasser.correction_notebook.utils.runCatchingCancellable
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.OffsetDateTime
import javax.inject.Inject

data class LearningContextSaveResult(
    val fileId: String,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

data class KnowledgeCardSaveResult(
    val cardId: String?,
    val studySetId: String?,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

enum class KnowledgeNameAction { CREATE_FOLDER, RENAME_FOLDER, RENAME_FILE, RENAME_STUDY_SET }

data class KnowledgeNameSaveResult(
    val action: KnowledgeNameAction,
    val targetId: String?,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

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
    val remoteTotal: Int? = null,
    val canLoadMoreRemote: Boolean = false,
    val isLoadingMoreRemote: Boolean = false,
    val remoteLoadMoreError: String? = null,
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
    val snackbarMessage: String? = null,
    val learningContextSaveResult: LearningContextSaveResult? = null,
    val knowledgeCardSaveResult: KnowledgeCardSaveResult? = null,
    val nameSaveResult: KnowledgeNameSaveResult? = null
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
    val searchPage: RemoteSearchSnapshot,
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

private data class RemoteSearchSnapshot(
    val results: List<BitShareSearchResult> = emptyList(),
    val total: Int? = null,
    val nextPage: Int? = null,
    val isLoadingMore: Boolean = false,
    val loadMoreError: String? = null
)

private data class RemoteLoadingSnapshot(
    val isRemoteSearching: Boolean,
    val isRemoteDetailLoading: Boolean,
    val isRemoteFolderLoading: Boolean,
    val isImportingLocalFile: Boolean,
    val remoteErrorMessage: String?
)

private data class StudyContentSnapshot(
    val studySets: List<StudySetSummary>,
    val knowledgeCards: List<DueReviewItem>,
    val reviewedCards: List<DueReviewItem>,
    val quizQuestions: List<StudySetQuizItem>
)

internal fun shouldCancelRemoteSearch(activeQuery: String?, editedQuery: String): Boolean =
    activeQuery != null && activeQuery != editedQuery.trim()

internal fun isLatestRemoteSearch(requestId: Long, latestRequestId: Long): Boolean =
    requestId == latestRequestId

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

    private val remoteSearch = MutableStateFlow(RemoteSearchSnapshot())
    private val selectedRemoteDetail = MutableStateFlow<BitShareFileDetail?>(null)
    private val selectedRemoteFolderDetail = MutableStateFlow<BitShareFolderDetail?>(null)
    private val isRemoteSearching = MutableStateFlow(false)
    private val isRemoteDetailLoading = MutableStateFlow(false)
    private val isRemoteFolderLoading = MutableStateFlow(false)
    private val isImportingLocalFile = MutableStateFlow(false)
    private val remoteErrorMessage = MutableStateFlow<String?>(null)
    private val isLocalBusy = MutableStateFlow(false)
    private val activeDownloadId = MutableStateFlow<String?>(null)
    private val snackbarMessage = MutableStateFlow<String?>(null)
    private val learningContextSaveResult = MutableStateFlow<LearningContextSaveResult?>(null)
    private val knowledgeCardSaveResult = MutableStateFlow<KnowledgeCardSaveResult?>(null)
    private val nameSaveResult = MutableStateFlow<KnowledgeNameSaveResult?>(null)
    private var remoteSearchJob: Job? = null
    private var remoteDetailJob: Job? = null
    private var latestRemoteDetailRequestId = 0L
    private var downloadJob: Job? = null
    private var latestRemoteSearchRequestId = 0L
    private var activeRemoteSearchQuery: String? = null
    private var appliedRemoteSearchQuery: String? = null

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
        combine(remoteQuery, remoteSort, remoteSearch) { query, sort, page ->
            Triple(query, sort, page)
        },
        combine(selectedRemoteDetail, selectedRemoteFolderDetail) { detail, folderDetail ->
            Pair(detail, folderDetail)
        },
        combine(
            isRemoteSearching,
            isRemoteDetailLoading,
            isRemoteFolderLoading,
            isImportingLocalFile,
            remoteErrorMessage
        ) { searching, detailLoading, folderLoading, importing, error ->
            RemoteLoadingSnapshot(
                isRemoteSearching = searching,
                isRemoteDetailLoading = detailLoading,
                isRemoteFolderLoading = folderLoading,
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
            remoteResults = searchMeta.third.results.let { results ->
                val folders = results.filter { it.entityType == "folder" }
                val files = results.filter { it.entityType != "folder" }
                folders + when (searchMeta.second) {
                    BitShareSortOption.RELEVANCE -> files
                    BitShareSortOption.DOWNLOADS -> files.sortedByDescending { it.downloadCount }
                    BitShareSortOption.LATEST -> files.sortedByDescending {
                        it.uploadedAt?.let { timestamp -> runCatching { OffsetDateTime.parse(timestamp).toInstant() }.getOrNull() }
                    }
                }
            },
            searchPage = searchMeta.third,
            selectedRemoteDetail = detailMeta.first,
            selectedRemoteFolderDetail = detailMeta.second,
            isRemoteSearching = loadingMeta.isRemoteSearching,
            isRemoteDetailLoading = loadingMeta.isRemoteDetailLoading,
            isRemoteFolderLoading = loadingMeta.isRemoteFolderLoading,
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
        },
        combine(learningContextSaveResult, knowledgeCardSaveResult, nameSaveResult) { context, card, name ->
            Triple(context, card, name)
        }
    ) { local, remote, study, saves ->
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
            remoteTotal = remote.searchPage.total,
            canLoadMoreRemote = remote.searchPage.nextPage != null,
            isLoadingMoreRemote = remote.searchPage.isLoadingMore,
            remoteLoadMoreError = remote.searchPage.loadMoreError,
            selectedRemoteDetail = remote.selectedRemoteDetail,
            selectedRemoteFolderDetail = remote.selectedRemoteFolderDetail,
            isRemoteSearching = remote.isRemoteSearching,
            isRemoteDetailLoading = remote.isRemoteDetailLoading,
            isRemoteFolderLoading = remote.isRemoteFolderLoading,
            isImportingLocalFile = remote.isImportingLocalFile,
            remoteErrorMessage = remote.remoteErrorMessage,
            studySets = study.studySets,
            knowledgeCards = study.knowledgeCards,
            reviewedCards = study.reviewedCards,
            quizQuestions = study.quizQuestions,
            isLocalBusy = remote.isLocalBusy,
            activeDownloadId = remote.activeDownloadId,
            snackbarMessage = remote.snackbarMessage,
            learningContextSaveResult = saves.first,
            knowledgeCardSaveResult = saves.second,
            nameSaveResult = saves.third
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = KnowledgeBaseUiState()
    )

    fun selectTab(index: Int) {
        if (index != 2) dismissRemoteDetail()
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
        val queryChanged = remoteQuery.value.trim() != query.trim()
        remoteQuery.value = query
        if (queryChanged) {
            dismissRemoteDetail()
            remoteSearch.value = RemoteSearchSnapshot()
            appliedRemoteSearchQuery = null
            remoteErrorMessage.value = null
        }
        if (shouldCancelRemoteSearch(activeRemoteSearchQuery, query)) {
            cancelRemoteSearch()
        }
    }

    fun updateRemoteSort(sortOption: BitShareSortOption) {
        remoteSort.value = sortOption
    }

    fun loadMoreRemoteResources() = searchRemoteResources(loadMore = true)

    fun searchRemoteResources(loadMore: Boolean = false) {
        val query = remoteQuery.value.trim()
        if (query.isBlank()) {
            cancelRemoteSearch()
            remoteSearch.value = RemoteSearchSnapshot()
            appliedRemoteSearchQuery = null
            remoteErrorMessage.value = null
            return
        }

        val page = if (loadMore) {
            if (isRemoteSearching.value || remoteSearch.value.isLoadingMore || query != appliedRemoteSearchQuery) return
            remoteSearch.value.nextPage ?: return
        } else 1
        val requestId = ++latestRemoteSearchRequestId
        remoteSearchJob?.cancel()
        activeRemoteSearchQuery = query
        if (loadMore) {
            remoteSearch.value = remoteSearch.value.copy(isLoadingMore = true, loadMoreError = null)
        } else {
            dismissRemoteDetail()
            appliedRemoteSearchQuery = query
            remoteSearch.value = RemoteSearchSnapshot()
            isRemoteSearching.value = true
        }
        remoteErrorMessage.value = null
        remoteSearchJob = viewModelScope.launch {
            try {
                bitShareRepository.searchFiles(query, page)
                    .onSuccess { result ->
                        if (isLatestRemoteSearch(requestId, latestRemoteSearchRequestId)) {
                            remoteSearch.value = RemoteSearchSnapshot(
                                results = ((if (loadMore) remoteSearch.value.results else emptyList()) + result.items)
                                    .distinctBy { it.entityType to it.id },
                                total = result.total,
                                nextPage = result.nextPage
                            )
                        }
                    }
                    .onFailure { error ->
                        if (isLatestRemoteSearch(requestId, latestRemoteSearchRequestId)) {
                            publishRemoteSearchError(error, loadMore)
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (isLatestRemoteSearch(requestId, latestRemoteSearchRequestId)) {
                    publishRemoteSearchError(e, loadMore)
                }
            } finally {
                if (isLatestRemoteSearch(requestId, latestRemoteSearchRequestId)) {
                    isRemoteSearching.value = false
                    remoteSearch.value = remoteSearch.value.copy(isLoadingMore = false)
                    activeRemoteSearchQuery = null
                    remoteSearchJob = null
                }
            }
        }
    }

    private fun publishRemoteSearchError(error: Throwable, loadMore: Boolean) {
        if (loadMore) {
            remoteSearch.value = remoteSearch.value.copy(loadMoreError = error.message ?: "继续加载失败")
        } else {
            remoteErrorMessage.value = error.message ?: "搜索失败"
        }
    }

    private fun cancelRemoteSearch() {
        latestRemoteSearchRequestId += 1
        remoteSearchJob?.cancel()
        remoteSearchJob = null
        activeRemoteSearchQuery = null
        isRemoteSearching.value = false
        remoteSearch.value = remoteSearch.value.copy(isLoadingMore = false)
    }

    fun loadRemoteDetail(fileId: String) {
        if (isRemoteDetailLoading.value || isRemoteFolderLoading.value) return
        val requestId = ++latestRemoteDetailRequestId
        isRemoteDetailLoading.value = true
        remoteDetailJob = viewModelScope.launch {
            remoteErrorMessage.value = null
            try {
                bitShareRepository.getFileDetail(fileId)
                    .onSuccess {
                        if (requestId == latestRemoteDetailRequestId) {
                            selectedRemoteDetail.value = it
                            remoteErrorMessage.value = null
                        }
                    }
                    .onFailure {
                        if (requestId == latestRemoteDetailRequestId) {
                            remoteErrorMessage.value = it.message ?: "加载详情失败"
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (requestId == latestRemoteDetailRequestId) {
                    remoteErrorMessage.value = e.message ?: "加载详情失败"
                }
            } finally {
                if (requestId == latestRemoteDetailRequestId) {
                    isRemoteDetailLoading.value = false
                    remoteDetailJob = null
                }
            }
        }
    }

    /**
     * 加载文件夹详情
     * 注意：由于 /api/public/files?folder_id= 接口返回 404，无法获取文件夹内的文件列表，
     * 因此只能显示文件夹信息，无法列出文件夹内容
     */
    fun loadRemoteFolderDetail(folderId: String) {
        if (isRemoteFolderLoading.value || isRemoteDetailLoading.value) return
        val requestId = ++latestRemoteDetailRequestId
        isRemoteFolderLoading.value = true
        remoteDetailJob = viewModelScope.launch {
            remoteErrorMessage.value = null
            try {
                bitShareRepository.getFolderDetail(folderId)
                    .onSuccess {
                        if (requestId == latestRemoteDetailRequestId) {
                            selectedRemoteFolderDetail.value = it
                            remoteErrorMessage.value = null
                        }
                    }
                    .onFailure {
                        if (requestId == latestRemoteDetailRequestId) {
                            remoteErrorMessage.value = it.message ?: "加载目录详情失败"
                        }
                    }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (requestId == latestRemoteDetailRequestId) {
                    remoteErrorMessage.value = e.message ?: "加载目录详情失败"
                }
            } finally {
                if (requestId == latestRemoteDetailRequestId) {
                    isRemoteFolderLoading.value = false
                    remoteDetailJob = null
                }
            }
        }
    }

    fun dismissRemoteDetail() {
        latestRemoteDetailRequestId += 1
        remoteDetailJob?.cancel()
        remoteDetailJob = null
        isRemoteDetailLoading.value = false
        isRemoteFolderLoading.value = false
        selectedRemoteDetail.value = null
        selectedRemoteFolderDetail.value = null
    }

    fun dismissRemoteFolderDetail() {
        dismissRemoteDetail()
    }

    fun createFolder(name: String) {
        val parentId = currentFolderId.value
        runNameSaveAction(KnowledgeNameAction.CREATE_FOLDER, parentId, "已创建文件夹", "创建文件夹失败") {
            knowledgeBaseRepository.createFolder(parentId, name)
        }
    }

    fun renameFolder(folderId: String, newName: String) {
        runNameSaveAction(KnowledgeNameAction.RENAME_FOLDER, folderId, "已重命名文件夹", "重命名失败") {
            knowledgeBaseRepository.renameFolder(folderId, newName)
        }
    }

    fun deleteFolder(folderId: String) {
        runLocalBusyAction("删除失败") {
            knowledgeBaseRepository.deleteFolder(folderId)
                .onSuccess { snackbarMessage.value = "已删除文件夹" }
                .onFailure { snackbarMessage.value = it.toUiMessage("删除失败") }
        }
    }

    fun renameFile(fileId: String, newName: String) {
        runNameSaveAction(KnowledgeNameAction.RENAME_FILE, fileId, "已重命名文件", "重命名失败") {
            knowledgeBaseRepository.renameFile(fileId, newName)
        }
    }

    fun moveFile(fileId: String, targetFolderId: String?) {
        runLocalBusyAction("移动失败") {
            knowledgeBaseRepository.moveFile(fileId, targetFolderId)
                .onSuccess { snackbarMessage.value = "文件已移动" }
                .onFailure { snackbarMessage.value = it.toUiMessage("移动失败") }
        }
    }

    fun moveFiles(fileIds: Set<String>, targetFolderId: String?) {
        if (fileIds.isEmpty()) return
        runLocalBusyAction("移动失败") {
            var successCount = 0
            var failureCount = 0
            var lastErrorMessage: String? = null

            fileIds.forEach { fileId ->
                knowledgeBaseRepository.moveFile(fileId, targetFolderId)
                    .onSuccess { successCount += 1 }
                    .onFailure {
                        failureCount += 1
                        lastErrorMessage = it.toUiMessage("移动失败")
                    }
            }

            snackbarMessage.value = when {
                successCount > 0 && failureCount == 0 -> "已移动 $successCount 个文件"
                successCount > 0 -> "成功移动 $successCount 个文件，$failureCount 个失败"
                else -> lastErrorMessage ?: "移动失败"
            }
        }
    }

    fun deleteFile(fileId: String) {
        runLocalBusyAction("删除失败") {
            knowledgeBaseRepository.deleteFile(fileId)
                .onSuccess { snackbarMessage.value = "文件已删除" }
                .onFailure { snackbarMessage.value = it.toUiMessage("删除失败") }
        }
    }

    fun exportFile(fileId: String, targetUri: Uri) {
        viewModelScope.launch {
            snackbarMessage.value = "正在导出文件..."
            knowledgeBaseRepository.exportFile(fileId, targetUri)
                .onSuccess { snackbarMessage.value = "已导出文件副本" }
                .onFailure { snackbarMessage.value = it.toUiMessage("导出失败，请重试") }
        }
    }

    fun updateFileLearningContext(
        fileId: String,
        courseId: Int?,
        courseName: String?,
        tags: List<String>
    ) {
        if (isLocalBusy.value) return
        learningContextSaveResult.value = LearningContextSaveResult(fileId, isSaving = true)
        runLocalBusyAction("更新失败") {
            knowledgeBaseRepository.updateFileLearningContext(fileId, courseId, courseName, tags)
                .onSuccess {
                    learningContextSaveResult.value = LearningContextSaveResult(fileId)
                    snackbarMessage.value = "已更新资料学习信息"
                }
                .onFailure {
                    val message = it.toUiMessage("更新失败")
                    learningContextSaveResult.value = LearningContextSaveResult(fileId, errorMessage = message)
                }
        }
    }

    fun clearLearningContextSaveResult() {
        learningContextSaveResult.value = null
    }

    fun markFlashcardReviewed(flashcardId: String, remembered: Boolean = true, onReviewed: () -> Unit = {}) {
        runLocalBusyAction("复习状态更新失败") {
            studySetRepository.markFlashcardReviewed(flashcardId, remembered = remembered)
                .onSuccess {
                    snackbarMessage.value = if (remembered) "已加入历史闪卡" else "已安排再次复习"
                    onReviewed()
                }
                .onFailure { snackbarMessage.value = it.toUiMessage("复习状态更新失败") }
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
        if (isLocalBusy.value) return
        knowledgeCardSaveResult.value = KnowledgeCardSaveResult(null, studySetId, isSaving = true)
        runLocalBusyAction("保存知识点失败") {
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
                .onSuccess {
                    knowledgeCardSaveResult.value = KnowledgeCardSaveResult(null, studySetId)
                    snackbarMessage.value = "知识点已保存"
                }
                .onFailure {
                    val message = it.toUiMessage("保存知识点失败")
                    knowledgeCardSaveResult.value = KnowledgeCardSaveResult(null, studySetId, errorMessage = message)
                }
        }
    }

    fun updateKnowledgeCard(card: DueReviewItem) {
        if (isLocalBusy.value) return
        knowledgeCardSaveResult.value = KnowledgeCardSaveResult(card.flashcardId, card.studySetId, isSaving = true)
        runLocalBusyAction("更新知识卡片失败") {
            studySetRepository.updateKnowledgeCard(card)
                .onSuccess {
                    knowledgeCardSaveResult.value = KnowledgeCardSaveResult(card.flashcardId, card.studySetId)
                    snackbarMessage.value = "知识卡片已更新"
                }
                .onFailure {
                    val message = it.toUiMessage("更新知识卡片失败")
                    knowledgeCardSaveResult.value = KnowledgeCardSaveResult(card.flashcardId, card.studySetId, errorMessage = message)
                }
        }
    }

    fun clearKnowledgeCardSaveResult() {
        knowledgeCardSaveResult.value = null
    }

    fun deleteKnowledgeCard(cardId: String) {
        runLocalBusyAction("删除知识卡片失败") {
            studySetRepository.deleteKnowledgeCard(cardId)
                .onSuccess { snackbarMessage.value = "知识卡片已删除" }
                .onFailure { snackbarMessage.value = it.toUiMessage("删除知识卡片失败") }
        }
    }

    fun renameStudySet(studySetId: String, title: String) {
        runNameSaveAction(KnowledgeNameAction.RENAME_STUDY_SET, studySetId, "学习集已重命名", "重命名学习集失败") {
            studySetRepository.renameStudySet(studySetId, title)
        }
    }

    fun clearNameSaveResult() {
        nameSaveResult.value = null
    }

    private fun runNameSaveAction(
        action: KnowledgeNameAction,
        targetId: String?,
        successMessage: String,
        failureMessage: String,
        save: suspend () -> Result<Unit>
    ) {
        if (isLocalBusy.value) return
        val saving = KnowledgeNameSaveResult(action, targetId, isSaving = true)
        nameSaveResult.value = saving
        runLocalBusyAction(failureMessage) {
            runCatchingCancellable { save().getOrThrow() }
                .onSuccess {
                    nameSaveResult.value = saving.copy(isSaving = false)
                    snackbarMessage.value = successMessage
                }
                .onFailure {
                    val message = it.toUiMessage(failureMessage)
                    nameSaveResult.value = saving.copy(isSaving = false, errorMessage = message)
                }
        }
    }

    fun deleteStudySet(studySetId: String) {
        runLocalBusyAction("删除学习集失败") {
            studySetRepository.deleteStudySet(studySetId)
                .onSuccess { snackbarMessage.value = "学习集已删除" }
                .onFailure { snackbarMessage.value = it.toUiMessage("删除学习集失败") }
        }
    }

    fun mergeStudySets(sourceStudySetIds: List<String>, targetStudySetId: String) {
        runLocalBusyAction("合并学习集失败") {
            studySetRepository.mergeStudySets(sourceStudySetIds, targetStudySetId)
                .onSuccess { snackbarMessage.value = "学习集已合并" }
                .onFailure { snackbarMessage.value = it.toUiMessage("合并学习集失败") }
        }
    }

    fun moveKnowledgeCard(cardId: String, targetStudySetId: String) {
        runLocalBusyAction("移动知识卡片失败") {
            studySetRepository.moveKnowledgeCard(cardId, targetStudySetId)
                .onSuccess { snackbarMessage.value = "知识卡片已移动" }
                .onFailure { snackbarMessage.value = it.toUiMessage("移动知识卡片失败") }
        }
    }

    fun deleteFiles(fileIds: Set<String>) {
        if (fileIds.isEmpty()) return
        runLocalBusyAction("删除失败") {
            var successCount = 0
            var failureCount = 0
            var lastErrorMessage: String? = null

            fileIds.forEach { fileId ->
                knowledgeBaseRepository.deleteFile(fileId)
                    .onSuccess { successCount += 1 }
                    .onFailure {
                        failureCount += 1
                        lastErrorMessage = it.toUiMessage("删除失败")
                    }
            }

            snackbarMessage.value = when {
                successCount > 0 && failureCount == 0 -> "已删除 $successCount 个文件"
                successCount > 0 -> "成功删除 $successCount 个文件，$failureCount 个失败"
                else -> lastErrorMessage ?: "删除失败"
            }
        }
    }

    fun importLocalFile(fileUri: Uri) {
        importLocalFiles(listOf(fileUri))
    }

    fun importLocalFiles(fileUris: List<Uri>) {
        if (fileUris.isEmpty()) return
        if (isImportingLocalFile.value) return
        val targetFolderId = currentFolderId.value
        isImportingLocalFile.value = true
        viewModelScope.launch {
            try {
                var successCount = 0
                var failureCount = 0
                var lastErrorMessage: String? = null

                fileUris.forEach { fileUri ->
                    knowledgeBaseRepository.importLocalFile(
                        targetFolderId = targetFolderId,
                        fileUri = fileUri
                    ).onSuccess {
                        successCount += 1
                    }.onFailure {
                        failureCount += 1
                        lastErrorMessage = it.message
                    }
                }

                val folderName = knowledgeBaseRepository.getFolderName(targetFolderId)
                snackbarMessage.value = when {
                    successCount > 0 && failureCount == 0 -> {
                        if (successCount == 1) "已导入到 $folderName" else "已导入 $successCount 个文件到 $folderName"
                    }
                    successCount > 0 -> "成功导入 $successCount 个文件，$failureCount 个失败"
                    else -> lastErrorMessage ?: "导入失败"
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbarMessage.value = e.message ?: "导入失败"
            } finally {
                isImportingLocalFile.value = false
            }
        }
    }

    fun downloadSearchResultToFolder(
        result: BitShareSearchResult,
        folderId: String?
    ) {
        if (activeDownloadId.value != null) return
        activeDownloadId.value = result.id
        downloadJob = viewModelScope.launch {
            snackbarMessage.value = "开始下载 ${result.originalName.ifBlank { result.title }}"
            remoteErrorMessage.value = null
            try {
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
            } catch (e: CancellationException) {
                snackbarMessage.value = "已取消下载"
                throw e
            } catch (e: Exception) {
                snackbarMessage.value = e.message ?: "下载失败"
            } finally {
                activeDownloadId.value = null
                downloadJob = null
            }
        }
    }

    fun downloadRemoteFileToFolder(folderId: String?) {
        val detail = selectedRemoteDetail.value ?: return
        if (activeDownloadId.value != null) return
        activeDownloadId.value = detail.id
        downloadJob = viewModelScope.launch {
            snackbarMessage.value = "开始下载 ${detail.originalName}"
            remoteErrorMessage.value = null
            try {
                performRemoteDownload(folderId, detail)
            } catch (e: CancellationException) {
                snackbarMessage.value = "已取消下载"
                throw e
            } catch (e: Exception) {
                snackbarMessage.value = e.message ?: "下载失败"
            } finally {
                activeDownloadId.value = null
                downloadJob = null
            }
        }
    }

    fun cancelRemoteDownload() {
        downloadJob?.cancel()
    }

    private suspend fun performRemoteDownload(
        folderId: String?,
        detail: BitShareFileDetail
    ) {
        val downloadResult = withContext(Dispatchers.IO) {
            bitShareRepository.downloadFile(detail.id) { body ->
                knowledgeBaseRepository.importDownloadedFile(
                    detail = detail,
                    targetFolderId = folderId ?: KnowledgeBaseRepository.ROOT_FOLDER_ID,
                    inputStream = body.byteStream()
                ).getOrThrow()
            }
        }
        downloadResult
            .onSuccess {
                val folderName = knowledgeBaseRepository.getFolderName(folderId)
                snackbarMessage.value = "已保存到 $folderName"
                currentFolderId.value = folderId.takeUnless { it == KnowledgeBaseRepository.ROOT_FOLDER_ID }
                localSearchQuery.value = ""
                selectTab(0)
            }
            .onFailure {
                if (it is CancellationException) throw it
                snackbarMessage.value = it.toUiMessage("下载失败")
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

    private fun runLocalBusyAction(
        failureMessage: String,
        action: suspend () -> Unit
    ) {
        if (isLocalBusy.value) return
        isLocalBusy.value = true
        viewModelScope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                snackbarMessage.value = e.message ?: failureMessage
            } finally {
                isLocalBusy.value = false
            }
        }
    }

    private fun Throwable.toUiMessage(fallback: String): String {
        if (this is CancellationException) throw this
        return message ?: fallback
    }
}
