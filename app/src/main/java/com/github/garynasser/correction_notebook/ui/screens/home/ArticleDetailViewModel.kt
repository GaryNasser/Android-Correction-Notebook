package com.github.garynasser.correction_notebook.ui.screens.home

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.github.garynasser.correction_notebook.data.model.home.ArticleDetail
import com.github.garynasser.correction_notebook.data.repository.ArticleRepository
import com.github.garynasser.correction_notebook.ui.navigation.ArticleDetailRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArticleDetailUiState(
    val isLoading: Boolean = true,
    val articleDetail: ArticleDetail? = null,
    val errorMessage: String? = null
)

@HiltViewModel
class ArticleDetailViewModel @Inject constructor(
    private val articleRepository: ArticleRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val args = savedStateHandle.toRoute<ArticleDetailRoute>()

    private val _uiState = MutableStateFlow(ArticleDetailUiState())
    val uiState: StateFlow<ArticleDetailUiState> = _uiState.asStateFlow()

    init {
        loadArticleDetail()
    }

    fun refresh() {
        loadArticleDetail(forceRefresh = true)
    }

    private fun loadArticleDetail(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isLoading = true,
                errorMessage = null
            )
            runCatching {
                articleRepository.getArticleDetail(
                    articleId = args.articleId,
                    forceRefresh = forceRefresh
                )
            }.onSuccess { detail ->
                _uiState.value = ArticleDetailUiState(
                    isLoading = false,
                    articleDetail = detail
                )
            }.onFailure { throwable ->
                _uiState.value = ArticleDetailUiState(
                    isLoading = false,
                    errorMessage = throwable.message?.takeIf { it.isNotBlank() } ?: "文章加载失败"
                )
            }
        }
    }
}
