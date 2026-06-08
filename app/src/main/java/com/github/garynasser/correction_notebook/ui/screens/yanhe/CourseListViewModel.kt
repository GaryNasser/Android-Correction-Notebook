package com.github.garynasser.correction_notebook.ui.screens.yanhe

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.github.garynasser.correction_notebook.data.model.auth.AuthState
import com.github.garynasser.correction_notebook.data.model.yanhe.Course
import com.github.garynasser.correction_notebook.data.model.yanhe.CourseProgress
import com.github.garynasser.correction_notebook.data.repository.AuthStateManager
import com.github.garynasser.correction_notebook.data.repository.CourseLearningRepository
import com.github.garynasser.correction_notebook.data.repository.VideoRepository
import com.github.garynasser.correction_notebook.data.repository.YanheRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
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
    private val videoRepository: VideoRepository,
    private val courseLearningRepository: CourseLearningRepository
) : ViewModel() {

    // 搜索与筛选状态
    var searchQuery by mutableStateOf("")
    var selectedSemester by mutableStateOf("全部学期")
    var expanded by mutableStateOf(false)
    var isPersonalCoursesMode by mutableStateOf(true)
        private set
    var semesters by mutableStateOf(listOf("全部学期"))
        private set

    // 分页控制
    private var currentPage = 1
    private var isEndReached = false
    var isLoadingMore by mutableStateOf(false)
    val courses = mutableStateListOf<Course>()
    private var personalCourses: List<Course> = emptyList()
    private var isRefreshingSchedule = false

    // UI 状态
    var uiState: CourseUiState by mutableStateOf(CourseUiState.Loading)
        private set
    var recentProgress by mutableStateOf<List<CourseProgress>>(emptyList())
        private set

    // 全校课程筛选仍保留旧参数；我的课程学期来自接口返回的真实课程数据。
    private val publicSemesters = listOf("全部学期", "2023-2024 秋季", "2023-2024 春季")
    private val semesterToIdMap = mapOf(
        "全部学期" to null,
        "2023-2024 秋季" to 1,
        "2023-2024 春季" to 2
    )

    init {
        if (yanheRepository.getStudentCredential() != null) {
            refreshMySchedule()
        } else {
            uiState = CourseUiState.Success(emptyList())
        }
        viewModelScope.launch {
            courseLearningRepository.progressItems.collect { items ->
                recentProgress = items
                    .sortedByDescending { it.lastAccessedAt }
                    .take(3)
            }
        }
        viewModelScope.launch {
            authStateManager.authState.collect { state ->
                if (state is AuthState.Authenticated && isPersonalCoursesMode && personalCourses.isEmpty()) {
                    refreshMySchedule()
                }
            }
        }
    }

    fun loadCourses(isNextPage: Boolean = false) {
        if (isNextPage && (isLoadingMore || isEndReached)) return
        if (isPersonalCoursesMode) {
            if (!isNextPage) {
                applyPersonalCourseFilters()
            }
            return
        }

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
        semesters = if (isPersonalCoursesMode) buildPersonalSemesters() else publicSemesters
        selectedSemester = semesters.firstOrNull { it == selectedSemester } ?: semesters.first()
        loadCourses(isNextPage = false)
    }

    fun refreshMySchedule() {
        if (isRefreshingSchedule) return
        viewModelScope.launch {
            isRefreshingSchedule = true
            uiState = CourseUiState.Loading
            isPersonalCoursesMode = true
            isLoadingMore = false
            isEndReached = true

            try {
                val loginResult = yanheRepository.getYanheLoginToken()
                if (loginResult.isFailure) {
                    uiState = CourseUiState.Success(emptyList())
                    authStateManager.onCasLoginRequired()
                    return@launch
                }

                runCatching {
                    withTimeout(30_000) {
                        videoRepository.getAllPersonalCourses()
                    }
                }.onSuccess { loadedCourses ->
                    personalCourses = loadedCourses
                    semesters = buildPersonalSemesters()
                    selectedSemester = pickLatestSemester(semesters)
                    if (loadedCourses.isEmpty()) {
                        courses.clear()
                        uiState = CourseUiState.Error("已登录延河课堂，但“我的课程”接口返回 0 门课程。请重新登录延河课堂后再刷新；如果仍为空，请把日志里的 VIDEO_REPO 课程响应摘要发给我继续定位。")
                    } else {
                        applyPersonalCourseFilters()
                    }
                }.onFailure { throwable ->
                    personalCourses = emptyList()
                    semesters = listOf("全部学期")
                    selectedSemester = "全部学期"
                    uiState = CourseUiState.Error(formatYanheError(throwable))
                }
            } finally {
                isRefreshingSchedule = false
            }
        }
    }

    suspend fun logToYanhe() {
        refreshMySchedule()
    }

    fun selectSemester(semester: String) {
        selectedSemester = semester
        expanded = false
        loadCourses(isNextPage = false)
    }

    fun updateSearchQuery(query: String) {
        searchQuery = query
        if (isPersonalCoursesMode) {
            applyPersonalCourseFilters()
        }
    }

    private fun applyPersonalCourseFilters() {
        courses.clear()
        val keyword = searchQuery.trim()
        val filtered = personalCourses
            .asSequence()
            .filter { selectedSemester == "全部学期" || it.semester == selectedSemester }
            .filter { course ->
                keyword.isBlank() ||
                    course.nameZh.contains(keyword, ignoreCase = true) ||
                    course.nameEn.contains(keyword, ignoreCase = true) ||
                    course.professors.any { it.contains(keyword, ignoreCase = true) }
            }
            .toList()
        courses.addAll(filtered)
        uiState = CourseUiState.Success(filtered)
    }

    private fun buildPersonalSemesters(): List<String> {
        val courseSemesters = personalCourses
            .map { it.semester.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sortedByDescending { semesterSortKey(it) }
        return if (courseSemesters.isEmpty()) listOf("全部学期") else courseSemesters
    }

    private fun pickLatestSemester(options: List<String>): String {
        return options.firstOrNull { it != "全部学期" } ?: "全部学期"
    }

    private fun semesterSortKey(semester: String): Int {
        val year = Regex("(\\d{4})\\D+(\\d{4})").find(semester)
            ?.groupValues
            ?.getOrNull(2)
            ?.toIntOrNull()
            ?: 0
        val term = Regex("第\\s*(\\d+)\\s*学期").find(semester)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
            ?: when {
                semester.contains("春") -> 2
                semester.contains("秋") -> 1
                else -> 0
            }
        return year * 10 + term
    }

    private fun formatYanheError(throwable: Throwable): String {
        val raw = throwable.message.orEmpty()
        return raw
            .replace(Regex("^java\\.lang\\.[A-Za-z]+Exception:\\s*"), "")
            .replace(Regex("^com\\.google\\.gson\\.[A-Za-z]+Exception:\\s*"), "")
            .ifBlank { "延河课堂课程拉取失败" }
    }
}
