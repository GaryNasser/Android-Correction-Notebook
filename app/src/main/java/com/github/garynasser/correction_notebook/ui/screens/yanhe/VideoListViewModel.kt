package com.github.garynasser.correction_notebook.ui.screens.yanhe

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.github.garynasser.correction_notebook.data.model.yanhe.CourseSection
import com.github.garynasser.correction_notebook.data.repository.VideoRepository
import com.github.garynasser.correction_notebook.ui.navigation.VideoList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface VideoUIState {
    object Loading : VideoUIState
    data class Success(val videos: List<CourseSection>) : VideoUIState
    data class Error(val message: String) : VideoUIState
}

@HiltViewModel
class VideoListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val videoRepository: VideoRepository
): ViewModel() {
    private val args = savedStateHandle.toRoute<VideoList>()
    val courseId = args.courseId

    var uiState : VideoUIState by mutableStateOf(VideoUIState.Loading)
        private set

    init {
        getVideoList(courseId)
    }

    fun getVideoList(courseId: Int) {
        viewModelScope.launch {
            try {
                val results = videoRepository.getCourseSession(courseId)

                uiState = VideoUIState.Success(results)
            } catch (e: Exception) {
                uiState = VideoUIState.Error("加载失败: ${e.message}")
            }
        }

    }
}