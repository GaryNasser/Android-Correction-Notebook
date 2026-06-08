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
import kotlinx.coroutines.withTimeout
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
            uiState = VideoUIState.Loading
            try {
                val results = withTimeout(20_000) {
                    videoRepository.getCourseSession(courseId)
                }

                uiState = VideoUIState.Success(results)
                progress = courseLearningRepository.getProgressForCourse(courseId)
            } catch (e: Exception) {
                uiState = VideoUIState.Error("加载失败: ${e.message ?: "延河课堂课程资源请求超时"}")
            }
        }

    }

    fun recordWatch(section: CourseSection, videoUrl: String) {
        viewModelScope.launch {
            recordWatchInternal(section, videoUrl)
            loadProgress()
        }
    }

    fun playSection(section: CourseSection, preferScreen: Boolean) {
        viewModelScope.launch {
            playState = PlayState.Loading
            try {
                val playableSection = if (section.videos.any { it.mainUrl.isNotBlank() || it.vgaUrl.isNotBlank() }) {
                    section
                } else {
                    withTimeout(12_000) {
                        videoRepository.getCourseSessionDetail(section.id)
                    }
                }
                val videoUrl = selectVideoUrl(playableSection, preferScreen)
                    ?: throw Exception("该节次没有可播放的视频")
                recordWatchInternal(playableSection, videoUrl)
                loadProgress()
                playState = PlayState.Success(videoUrl)
            } catch (e: Exception) {
                playState = PlayState.Error("播放失败: ${e.message ?: "延河课堂视频地址获取失败"}")
            }
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

    private suspend fun recordWatchInternal(section: CourseSection, videoUrl: String) {
        val total = (uiState as? VideoUIState.Success)?.videos?.size ?: 0
        courseLearningRepository.recordWatch(
            courseId = courseId,
            courseName = courseName,
            sectionId = section.id,
            sectionTitle = section.title,
            videoUrl = videoUrl,
            totalSections = total
        )
    }

    private fun selectVideoUrl(section: CourseSection, preferScreen: Boolean): String? {
        section.videos.forEach { video ->
            val preferred = if (preferScreen) video.vgaUrl else video.mainUrl
            val fallback = if (preferScreen) video.mainUrl else video.vgaUrl
            firstNonBlank(preferred, fallback, video.room, video.path)?.let { return it }
        }
        return null
    }

    private fun firstNonBlank(vararg values: String): String? {
        return values.firstOrNull { it.isNotBlank() }
    }
}
