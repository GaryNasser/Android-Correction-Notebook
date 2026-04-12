package com.github.garynasser.correction_notebook.ui.screens.knowledgebase

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareFileDetail
import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareSearchResult
import com.github.garynasser.correction_notebook.data.model.knowledgebase.BitShareSortOption
import com.github.garynasser.correction_notebook.data.model.knowledgebase.KnowledgeBaseFileSummary
import com.github.garynasser.correction_notebook.data.model.knowledgebase.KnowledgeBaseFolderChoice
import com.github.garynasser.correction_notebook.data.model.knowledgebase.KnowledgeBaseFolderContent
import com.github.garynasser.correction_notebook.data.repository.BitShareRepository
import com.github.garynasser.correction_notebook.data.repository.KnowledgeBaseRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    val isRemoteSearching: Boolean = false,
    val isRemoteDetailLoading: Boolean = false,
    val remoteErrorMessage: String? = null,
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
    val isRemoteSearching: Boolean,
    val isRemoteDetailLoading: Boolean,
    val remoteErrorMessage: String?,
    val isLocalBusy: Boolean,
    val activeDownloadId: String?,
    val snackbarMessage: String?
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class KnowledgeBaseViewModel @Inject constructor(
    private val knowledgeBaseRepository: KnowledgeBaseRepository,
    private val bitShareRepository: BitShareRepository
) : ViewModel() {

    private val selectedTabIndex = MutableStateFlow(0)
    private val currentFolderId = MutableStateFlow<String?>(null)
    private val localSearchQuery = MutableStateFlow("")

    private val remoteQuery = MutableStateFlow("")
    private val remoteSort = MutableStateFlow(BitShareSortOption.RELEVANCE)

    private val remoteResults = MutableStateFlow<List<BitShareSearchResult>>(emptyList())
    private val selectedRemoteDetail = MutableStateFlow<BitShareFileDetail?>(null)
    private val isRemoteSearching = MutableStateFlow(false)
    private val isRemoteDetailLoading = MutableStateFlow(false)
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
        combine(
            selectedRemoteDetail,
            isRemoteSearching,
            isRemoteDetailLoading,
            remoteErrorMessage
        ) { detail, remoteSearching, detailLoading, errorMessage ->
            RemoteUiSnapshot(
                remoteQuery = "",
                remoteSort = BitShareSortOption.RELEVANCE,
                remoteResults = emptyList(),
                selectedRemoteDetail = detail,
                isRemoteSearching = remoteSearching,
                isRemoteDetailLoading = detailLoading,
                remoteErrorMessage = errorMessage,
                isLocalBusy = false,
                activeDownloadId = null,
                snackbarMessage = null
            )
        },
        combine(isLocalBusy, activeDownloadId, snackbarMessage) { localBusy, downloadId, message ->
            Triple(localBusy, downloadId, message)
        }
    ) { searchMeta, remoteMeta, localMeta ->
        RemoteUiSnapshot(
            remoteQuery = searchMeta.first,
            remoteSort = searchMeta.second,
            remoteResults = searchMeta.third,
            selectedRemoteDetail = remoteMeta.selectedRemoteDetail,
            isRemoteSearching = remoteMeta.isRemoteSearching,
            isRemoteDetailLoading = remoteMeta.isRemoteDetailLoading,
            remoteErrorMessage = remoteMeta.remoteErrorMessage,
            isLocalBusy = localMeta.first,
            activeDownloadId = localMeta.second,
            snackbarMessage = localMeta.third
        )
    }

    val uiState: StateFlow<KnowledgeBaseUiState> = combine(
        localUiSnapshot,
        remoteUiSnapshot
    ) { local, remote ->
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
            isRemoteSearching = remote.isRemoteSearching,
            isRemoteDetailLoading = remote.isRemoteDetailLoading,
            remoteErrorMessage = remote.remoteErrorMessage,
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

    fun dismissRemoteDetail() {
        selectedRemoteDetail.value = null
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

    fun deleteFile(fileId: String) {
        viewModelScope.launch {
            isLocalBusy.value = true
            knowledgeBaseRepository.deleteFile(fileId)
                .onSuccess { snackbarMessage.value = "文件已删除" }
                .onFailure { snackbarMessage.value = it.message ?: "删除失败" }
            isLocalBusy.value = false
        }
    }

    fun downloadRemoteFileToFolder(folderId: String?) {
        val detail = selectedRemoteDetail.value ?: return

        viewModelScope.launch {
            activeDownloadId.value = detail.id
            remoteErrorMessage.value = null

            val downloadResult = bitShareRepository.downloadFile(detail.id)
            downloadResult
                .mapCatching { body ->
                    knowledgeBaseRepository.importDownloadedFile(
                        detail = detail,
                        targetFolderId = folderId ?: KnowledgeBaseRepository.ROOT_FOLDER_ID,
                        inputBytes = body.bytes()
                    ).getOrThrow()
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

    fun consumeSnackbarMessage() {
        snackbarMessage.value = null
    }
}
