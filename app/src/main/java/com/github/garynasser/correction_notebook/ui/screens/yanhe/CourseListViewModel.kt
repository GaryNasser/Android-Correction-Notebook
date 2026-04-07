package com.github.garynasser.correction_notebook.ui.screens.yanhe

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.garynasser.correction_notebook.data.model.yanhe.Course
import com.github.garynasser.correction_notebook.data.repository.AuthStateManager
import com.github.garynasser.correction_notebook.data.repository.VideoRepository
import com.github.garynasser.correction_notebook.data.repository.YanheRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface CourseUiState {
    object Loading : CourseUiState
    data class Success(val courses: List<Course>) : CourseUiState
    data class Error(val message: String) : CourseUiState
}

@HiltViewModel
class CourseListViewModel @Inject constructor(
    private val yanheRepository: YanheRepository,
    private val authStateManager: AuthStateManager,
    private val videoRepository: VideoRepository
) : ViewModel() {

    // 搜索与筛选状态
    var searchQuery by mutableStateOf("")
    var selectedSemester by mutableStateOf("全部学期")
    var expanded by mutableStateOf(false)
    var isPersonalCoursesMode by mutableStateOf(false)
        private set

    // 分页控制
    private var currentPage = 1
    private var isEndReached = false
    var isLoadingMore by mutableStateOf(false)
    val courses = mutableStateListOf<Course>()

    // UI 状态
    var uiState: CourseUiState by mutableStateOf(CourseUiState.Loading)
        private set

    // 学期映射表：注意 "全部学期" 映射为 null
    val semesters = listOf("全部学期", "2023-2024 秋季", "2023-2024 春季")
    private val semesterToIdMap = mapOf(
        "全部学期" to null,
        "2023-2024 秋季" to 1,
        "2023-2024 春季" to 2
    )

    init {
        loadCourses(isNextPage = false)
    }

    fun loadCourses(isNextPage: Boolean = false) {
        if (isNextPage && (isLoadingMore || isEndReached)) return

        viewModelScope.launch {
            if (!isNextPage) {
                currentPage = 1
                isEndReached = false
                courses.clear()
                uiState = CourseUiState.Loading
            } else {
                isLoadingMore = true
            }

            try {
                val semesterId = semesterToIdMap[selectedSemester]
                val keywordParam = searchQuery.ifBlank { null }

                val result = if (isPersonalCoursesMode) {
                    videoRepository.getPersonalCourse(semesterId, currentPage, 16, keywordParam)
                } else {
                    videoRepository.getCourse(semesterId, currentPage, 16, keywordParam)
                }

                if (result.isEmpty()) {
                    isEndReached = true
                } else {
                    courses.addAll(result)
                    currentPage++
                }

                uiState = CourseUiState.Success(courses.toList())
            } catch (e: Exception) {
                if (!isNextPage) {
                    uiState = CourseUiState.Error("加载失败: ${e.message}")
                }
            } finally {
                isLoadingMore = false
            }
        }
    }

    fun toggleCourseMode() {
        isPersonalCoursesMode = !isPersonalCoursesMode
        loadCourses(isNextPage = false)
    }

    suspend fun logToYanhe() {
        yanheRepository.getYanheLoginToken()
            .onSuccess { loadCourses(isNextPage = false) }
            .onFailure { authStateManager.onCasLoginRequired() }
    }
}