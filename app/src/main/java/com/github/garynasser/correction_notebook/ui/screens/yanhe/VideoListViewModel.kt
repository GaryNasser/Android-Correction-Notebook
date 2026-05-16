package com.github.garynasser.correction_notebook.ui.screens.yanhe

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.github.garynasser.correction_notebook.data.model.yanhe.CourseSection
import com.github.garynasser.correction_notebook.data.model.yanhe.CourseProgress
import com.github.garynasser.correction_notebook.data.repository.CourseLearningRepository
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

sealed class PlayState {
    object Idle : PlayState()
    object Loading : PlayState()
    data class Success(val url: String) : PlayState()
    data class Error(val message: String) : PlayState()
}
@HiltViewModel
class VideoListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val videoRepository: VideoRepository,
    private val courseLearningRepository: CourseLearningRepository
): ViewModel() {
    private val args = savedStateHandle.toRoute<VideoList>()
    val courseId = args.courseId
    val courseName = args.courseName

    var playState by mutableStateOf<PlayState>(PlayState.Idle)
        private set

    var uiState : VideoUIState by mutableStateOf(VideoUIState.Loading)
        private set

    var progress by mutableStateOf<CourseProgress?>(null)
        private set

    init {
        getVideoList(courseId)
        loadProgress()
    }

    fun resetPlayState() {
        playState = PlayState.Idle
    }

    fun getVideoList(courseId: Int) {
        viewModelScope.launch {
            try {
                val results = videoRepository.getCourseSession(courseId)

                uiState = VideoUIState.Success(results)
                progress = courseLearningRepository.getProgressForCourse(courseId)
            } catch (e: Exception) {
                uiState = VideoUIState.Error("加载失败: ${e.message}")
            }
        }

    }

    fun recordWatch(section: CourseSection, videoUrl: String) {
        viewModelScope.launch {
            val total = (uiState as? VideoUIState.Success)?.videos?.size ?: 0
            courseLearningRepository.recordWatch(
                courseId = courseId,
                courseName = courseName,
                sectionId = section.id,
                sectionTitle = section.title,
                videoUrl = videoUrl,
                totalSections = total
            )
            loadProgress()
        }
    }

    fun setSectionCompleted(section: CourseSection, completed: Boolean) {
        viewModelScope.launch {
            val total = (uiState as? VideoUIState.Success)?.videos?.size ?: 0
            courseLearningRepository.setSectionCompleted(
                courseId = courseId,
                courseName = courseName,
                sectionId = section.id,
                sectionTitle = section.title,
                totalSections = total,
                completed = completed
            )
            loadProgress()
        }
    }

    private fun loadProgress() {
        viewModelScope.launch {
            progress = courseLearningRepository.getProgressForCourse(courseId)
        }
    }
}
