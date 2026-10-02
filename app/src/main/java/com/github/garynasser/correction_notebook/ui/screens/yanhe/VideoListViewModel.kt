package com.github.garynasser.correction_notebook.ui.screens.yanhe

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.github.garynasser.correction_notebook.data.model.yanhe.CourseSection
import com.github.garynasser.correction_notebook.data.model.yanhe.CourseProgress
import com.github.garynasser.correction_notebook.data.model.yanhe.Video
import com.github.garynasser.correction_notebook.data.repository.CourseLearningRepository
import com.github.garynasser.correction_notebook.data.repository.VideoRepository
import com.github.garynasser.correction_notebook.ui.navigation.VideoList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    data class Success(
        val url: String,
        val videoTitle: String = "",
        val courseName: String = ""
    ) : PlayState()
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
    var sectionActionMessage by mutableStateOf<String?>(null)
        private set
    var updatingCompletionSectionIds by mutableStateOf<Set<Int>>(emptySet())
        private set

    private var videoListJob: Job? = null
    private var playJob: Job? = null

    init {
        getVideoList(courseId)
    }

    fun resetPlayState() {
        playState = PlayState.Idle
    }

    fun cancelPlayback() {
        playJob?.cancel()
        playJob = null
        playState = PlayState.Idle
    }

    fun consumeSectionActionMessage() {
        sectionActionMessage = null
    }

    fun getVideoList(courseId: Int) {
        cancelPlayback()
        videoListJob?.cancel()
        videoListJob = viewModelScope.launch {
            uiState = VideoUIState.Loading
            playState = PlayState.Idle
            try {
                val results = awaitYanheResource(20_000, "视频列表请求超时，请重试") {
                    videoRepository.getCourseSession(courseId)
                }

                uiState = VideoUIState.Success(results)
                loadProgress()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                uiState = VideoUIState.Error("加载失败: ${e.message ?: "延河课堂课程资源请求超时"}")
            }
        }

    }

    fun playSection(section: CourseSection, preferScreen: Boolean) {
        if (playJob?.isActive == true) return
        playJob = viewModelScope.launch {
            playState = PlayState.Loading
            try {
                val playableSection = if (selectCourseVideoUrl(section, preferScreen) != null) {
                    section
                } else {
                    awaitYanheResource(12_000, "视频地址获取超时，请重新选择播放") {
                        videoRepository.getCourseSessionDetail(section.id)
                    }
                }
                val videoUrl = selectCourseVideoUrl(playableSection, preferScreen)
                    ?: throw Exception("该节次没有可播放的视频")
                playState = PlayState.Success(
                    url = videoUrl,
                    videoTitle = playableSection.displayTitle(),
                    courseName = courseName
                )
                viewModelScope.launch {
                    try {
                        recordWatchInternal(playableSection, videoUrl)
                        progress = courseLearningRepository.getProgressForCourse(courseId)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        sectionActionMessage = "视频已打开，但学习进度记录失败：${e.message ?: "请稍后再试"}"
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                playState = PlayState.Error("播放失败: ${e.message ?: "延河课堂视频地址获取失败"}")
            }
        }
    }

    fun setSectionCompleted(section: CourseSection, completed: Boolean) {
        if (section.id in updatingCompletionSectionIds) return
        updatingCompletionSectionIds = updatingCompletionSectionIds + section.id
        viewModelScope.launch {
            try {
                val total = (uiState as? VideoUIState.Success)?.videos?.size ?: 0
                courseLearningRepository.setSectionCompleted(
                    courseId = courseId,
                    courseName = courseName,
                    sectionId = section.id,
                    sectionTitle = section.title,
                    totalSections = total,
                    completed = completed
                )
                progress = courseLearningRepository.getProgressForCourse(courseId)
                sectionActionMessage = if (completed) "已标记完成" else "已取消完成"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                sectionActionMessage = "进度更新失败：${e.message ?: "请稍后再试"}"
            } finally {
                updatingCompletionSectionIds = updatingCompletionSectionIds - section.id
            }
        }
    }

    private suspend fun loadProgress() {
        try {
            progress = courseLearningRepository.getProgressForCourse(courseId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            sectionActionMessage = "学习进度加载失败：${e.message ?: "请稍后再试"}"
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

    private fun CourseSection.displayTitle(): String {
        return title.ifBlank {
            if (sectionBigStart == sectionBigEnd) {
                "第 $sectionBigStart 大节"
            } else {
                "第 $sectionBigStart-$sectionBigEnd 大节"
            }
        }
    }
}

internal fun selectCourseVideoUrl(section: CourseSection, preferScreen: Boolean): String? {
    val sourceOrder = if (preferScreen) {
        listOf(Video::vgaUrl, Video::mainUrl, Video::room, Video::path)
    } else {
        listOf(Video::mainUrl, Video::vgaUrl, Video::room, Video::path)
    }
    return sourceOrder.firstNotNullOfOrNull { source ->
        section.videos.firstNotNullOfOrNull { source(it).trim().takeIf(String::isNotEmpty) }
    }
}
