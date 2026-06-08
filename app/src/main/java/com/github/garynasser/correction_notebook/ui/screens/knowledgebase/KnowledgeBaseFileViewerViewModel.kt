package com.github.garynasser.correction_notebook.ui.screens.knowledgebase

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.core.graphics.createBitmap
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.github.garynasser.correction_notebook.data.model.knowledgebase.KnowledgeBaseFileSummary
import com.github.garynasser.correction_notebook.data.model.studyset.StudySetDraft
import com.github.garynasser.correction_notebook.data.repository.KnowledgeBasePreviewRenderer
import com.github.garynasser.correction_notebook.data.repository.KnowledgeBaseAiRepository
import com.github.garynasser.correction_notebook.data.repository.KnowledgeBaseRepository
import com.github.garynasser.correction_notebook.data.repository.StudySetRepository
import com.github.garynasser.correction_notebook.domain.usecase.AiStudyUseCase
import com.github.garynasser.correction_notebook.domain.usecase.KnowledgeAiMode
import com.github.garynasser.correction_notebook.ui.navigation.KnowledgeBaseFileViewer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

enum class KnowledgeBasePreviewType {
    IMAGE,
    TEXT,
    PDF,
    AUDIO,
    VIDEO,
    HTML,
    FALLBACK
}

data class KnowledgeBaseFileViewerUiState(
    val isLoading: Boolean = true,
    val file: KnowledgeBaseFileSummary? = null,
    val previewType: KnowledgeBasePreviewType? = null,
    val textPreview: String? = null,
    val isTextTruncated: Boolean = false,
    val pdfPages: List<Bitmap> = emptyList(),
    val htmlPreviewPath: String? = null,
    val indexChunkCount: Int? = null,
    val isIndexing: Boolean = false,
    val aiResult: String? = null,
    val studySetDraft: StudySetDraft? = null,
    val isAiLoading: Boolean = false,
    val errorMessage: String? = null
)

@HiltViewModel
class KnowledgeBaseFileViewerViewModel @Inject constructor(
    private val knowledgeBaseRepository: KnowledgeBaseRepository,
    private val knowledgeBaseAiRepository: KnowledgeBaseAiRepository,
    private val previewRenderer: KnowledgeBasePreviewRenderer,
    private val aiStudyUseCase: AiStudyUseCase,
    private val studySetRepository: StudySetRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val args = savedStateHandle.toRoute<KnowledgeBaseFileViewer>()

    var uiState = androidx.compose.runtime.mutableStateOf(KnowledgeBaseFileViewerUiState())
        private set

    init {
        loadFile()
    }

    fun refresh() {
        loadFile()
    }

    fun runAiAction(mode: KnowledgeAiMode) {
        val fileId = uiState.value.file?.id ?: return
        val fileName = uiState.value.file?.displayName ?: "资料"
        viewModelScope.launch {
            uiState.value = uiState.value.copy(isAiLoading = true, aiResult = null, errorMessage = null)
            val cached = studySetRepository.getCachedAiResult(fileId, mode.name)
            if (!cached.isNullOrBlank()) {
                uiState.value = uiState.value.copy(isAiLoading = false, aiResult = cached)
                return@launch
            }
            aiStudyUseCase.summarizeKnowledgeFile(fileId, mode)
                .onSuccess { result ->
                    studySetRepository.saveAiResult(
                        fileId = fileId,
                        mode = mode.name,
                        title = "${fileName} · ${mode.displayName()}",
                        content = result
                    )
                    uiState.value = uiState.value.copy(isAiLoading = false, aiResult = result)
                }
                .onFailure { throwable ->
                    uiState.value = uiState.value.copy(
                        isAiLoading = false,
                        errorMessage = throwable.message ?: "AI 处理失败"
                    )
                }
        }
    }

    fun generateStudySet() {
        val fileId = uiState.value.file?.id ?: return
        viewModelScope.launch {
            uiState.value = uiState.value.copy(
                isAiLoading = true,
                aiResult = null,
                studySetDraft = null,
                errorMessage = null
            )
            aiStudyUseCase.generateStudySetFromKnowledgeFile(fileId)
                .onSuccess { draft ->
                    uiState.value = uiState.value.copy(
                        isAiLoading = false,
                        studySetDraft = draft
                    )
                }
                .onFailure { throwable ->
                    uiState.value = uiState.value.copy(
                        isAiLoading = false,
                        errorMessage = throwable.message ?: "学习集生成失败"
                    )
                }
        }
    }

    fun saveStudySetDraft() {
        val file = uiState.value.file ?: return
        val draft = uiState.value.studySetDraft ?: return
        viewModelScope.launch {
            studySetRepository.saveDraftFromFile(file, draft)
                .onSuccess {
                    uiState.value = uiState.value.copy(
                        studySetDraft = null,
                        errorMessage = null
                    )
                }
                .onFailure { throwable ->
                    uiState.value = uiState.value.copy(errorMessage = throwable.message ?: "学习集保存失败")
                }
        }
    }

    fun rebuildIndex() {
        val fileId = uiState.value.file?.id ?: return
        viewModelScope.launch {
            uiState.value = uiState.value.copy(isIndexing = true, errorMessage = null)
            knowledgeBaseAiRepository.rebuildIndexForFile(fileId)
                .onSuccess { count ->
                    uiState.value = uiState.value.copy(isIndexing = false, indexChunkCount = count)
                }
                .onFailure { throwable ->
                    uiState.value = uiState.value.copy(
                        isIndexing = false,
                        errorMessage = throwable.message ?: "索引重建失败"
                    )
                }
        }
    }

    fun clearAiResult() {
        uiState.value = uiState.value.copy(aiResult = null, studySetDraft = null)
    }

    private fun loadFile() {
        viewModelScope.launch {
            uiState.value = KnowledgeBaseFileViewerUiState(isLoading = true)

            val file = knowledgeBaseRepository.getFileSummary(args.fileId)
            if (file == null) {
                uiState.value = KnowledgeBaseFileViewerUiState(
                    isLoading = false,
                    errorMessage = "文件不存在或已被删除"
                )
                return@launch
            }

            val exists = knowledgeBaseRepository.fileExists(args.fileId)
            if (!exists) {
                uiState.value = KnowledgeBaseFileViewerUiState(
                    isLoading = false,
                    file = file,
                    errorMessage = "本地文件不存在，可能已被移除"
                )
                return@launch
            }

            val previewType = resolvePreviewType(file)
            val chunkCount = knowledgeBaseAiRepository.indexStatus(args.fileId).getOrNull()
            when (previewType) {
                KnowledgeBasePreviewType.TEXT -> loadTextPreview(file, previewType)
                KnowledgeBasePreviewType.PDF -> loadPdfPreview(file, previewType)
                KnowledgeBasePreviewType.HTML -> loadHtmlPreview(file, previewType)
                else -> {
                    uiState.value = KnowledgeBaseFileViewerUiState(
                        isLoading = false,
                        file = file,
                        previewType = previewType,
                        indexChunkCount = chunkCount
                    )
                }
            }
            uiState.value = uiState.value.copy(indexChunkCount = chunkCount)
        }
    }

    private suspend fun loadTextPreview(
        file: KnowledgeBaseFileSummary,
        previewType: KnowledgeBasePreviewType
    ) {
        val previewResult = withContext(Dispatchers.IO) {
            runCatching {
                val content = File(file.localPath).reader().use { reader ->
                    val buffer = CharArray(TEXT_PREVIEW_LIMIT + 1)
                    val read = reader.read(buffer)
                    if (read <= 0) {
                        "" to false
                    } else {
                        val text = String(buffer, 0, minOf(read, TEXT_PREVIEW_LIMIT))
                        text to (read > TEXT_PREVIEW_LIMIT)
                    }
                }
                content
            }
        }

        uiState.value = previewResult.fold(
            onSuccess = { (text, truncated) ->
                KnowledgeBaseFileViewerUiState(
                    isLoading = false,
                    file = file,
                    previewType = previewType,
                    textPreview = text,
                    isTextTruncated = truncated
                )
            },
            onFailure = {
                KnowledgeBaseFileViewerUiState(
                    isLoading = false,
                    file = file,
                    previewType = KnowledgeBasePreviewType.FALLBACK,
                    errorMessage = "文本预览失败，请尝试用其他应用打开"
                )
            }
        )
    }

    private suspend fun loadHtmlPreview(
        file: KnowledgeBaseFileSummary,
        previewType: KnowledgeBasePreviewType
    ) {
        val previewResult = previewRenderer.render(file)
        uiState.value = previewResult.fold(
            onSuccess = { rendered ->
                KnowledgeBaseFileViewerUiState(
                    isLoading = false,
                    file = file,
                    previewType = previewType,
                    htmlPreviewPath = rendered.htmlPath
                )
            },
            onFailure = {
                KnowledgeBaseFileViewerUiState(
                    isLoading = false,
                    file = file,
                    previewType = KnowledgeBasePreviewType.FALLBACK,
                    errorMessage = "文档预览失败，请尝试用其他应用打开"
                )
            }
        )
    }

    private suspend fun loadPdfPreview(
        file: KnowledgeBaseFileSummary,
        previewType: KnowledgeBasePreviewType
    ) {
        val previewResult = withContext(Dispatchers.IO) {
            runCatching {
                ParcelFileDescriptor.open(File(file.localPath), ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                    PdfRenderer(descriptor).use { renderer ->
                        buildList {
                            for (pageIndex in 0 until renderer.pageCount) {
                                renderer.openPage(pageIndex).use { page ->
                                    val scale = PDF_RENDER_WIDTH.toFloat() / page.width.toFloat()
                                    val bitmap = createBitmap(
                                        PDF_RENDER_WIDTH,
                                        (page.height * scale).toInt().coerceAtLeast(1)
                                    )
                                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                    add(bitmap)
                                }
                            }
                        }
                    }
                }
            }
        }

        uiState.value = previewResult.fold(
            onSuccess = { pages ->
                KnowledgeBaseFileViewerUiState(
                    isLoading = false,
                    file = file,
                    previewType = previewType,
                    pdfPages = pages
                )
            },
            onFailure = {
                KnowledgeBaseFileViewerUiState(
                    isLoading = false,
                    file = file,
                    previewType = KnowledgeBasePreviewType.FALLBACK,
                    errorMessage = "PDF 预览失败，请尝试用其他应用打开"
                )
            }
        )
    }

    private fun resolvePreviewType(file: KnowledgeBaseFileSummary): KnowledgeBasePreviewType {
        val mimeType = file.mimeType.lowercase()
        val extension = file.localPath.substringAfterLast('.', "").lowercase()

        return when {
            mimeType.startsWith("image/") || extension in IMAGE_EXTENSIONS -> KnowledgeBasePreviewType.IMAGE
            mimeType.startsWith("audio/") -> KnowledgeBasePreviewType.AUDIO
            mimeType.startsWith("video/") -> KnowledgeBasePreviewType.VIDEO
            mimeType == "application/pdf" || extension == "pdf" -> KnowledgeBasePreviewType.PDF
            extension in HTML_PREVIEW_EXTENSIONS -> KnowledgeBasePreviewType.HTML
            mimeType.startsWith("text/") || extension in TEXT_EXTENSIONS -> KnowledgeBasePreviewType.TEXT
            else -> KnowledgeBasePreviewType.FALLBACK
        }
    }

    companion object {
        private const val TEXT_PREVIEW_LIMIT = 24_000
        private const val PDF_RENDER_WIDTH = 1440

        private val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")
        private val TEXT_EXTENSIONS = setOf("txt", "md", "json", "csv", "log", "xml", "yaml", "yml", "kt", "java")
        private val HTML_PREVIEW_EXTENSIONS = setOf("docx", "pptx")
    }
}

private fun KnowledgeAiMode.displayName(): String {
    return when (this) {
        KnowledgeAiMode.SUMMARY -> "总结"
        KnowledgeAiMode.KEY_POINTS -> "重点"
        KnowledgeAiMode.QUIZ -> "复习题"
        KnowledgeAiMode.GLOSSARY -> "术语表"
        KnowledgeAiMode.FORMULA_SHEET -> "公式表"
        KnowledgeAiMode.REVIEW_CHECKLIST -> "复习清单"
    }
}
