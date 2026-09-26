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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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
    var selectedSemester by mutableStateOf(ALL_SEMESTERS)
    var expanded by mutableStateOf(false)
    var isPersonalCoursesMode by mutableStateOf(true)
        private set
    var semesters by mutableStateOf(listOf(ALL_SEMESTERS))
        private set

    // 分页控制
    private var currentPage = 1
    private var isEndReached = false
    var isLoadingMore by mutableStateOf(false)
    val courses = mutableStateListOf<Course>()
    private var personalCourses: List<Course> = emptyList()
    var isRefreshingSchedule by mutableStateOf(false)
        private set
    var loadMoreErrorMessage by mutableStateOf<String?>(null)
        private set
    private var courseLoadJob: Job? = null

    // UI 状态
    var uiState: CourseUiState by mutableStateOf(CourseUiState.Loading)
        private set
    var recentProgress by mutableStateOf<List<CourseProgress>>(emptyList())
        private set

    private val publicSemesters = listOf(ALL_SEMESTERS)

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
                when (state) {
                    is AuthState.Authenticated -> {
                        if (isPersonalCoursesMode && personalCourses.isEmpty()) {
                            refreshMySchedule()
                        }
                    }
                    is AuthState.Unauthenticated -> clearPersonalCourseState()
                    else -> Unit
                }
            }
        }
    }

    fun loadCourses(isNextPage: Boolean = false) {
        if (isNextPage && (isLoadingMore || isEndReached || courseLoadJob?.isActive == true)) return
        if (!isNextPage) {
            courseLoadJob?.cancel()
        }
        if (isPersonalCoursesMode) {
            if (!isNextPage) {
                applyPersonalCourseFilters()
            }
            return
        }

        courseLoadJob = viewModelScope.launch {
            if (!isNextPage) {
                currentPage = 1
                isEndReached = false
                loadMoreErrorMessage = null
                courses.clear()
                uiState = CourseUiState.Loading
            } else {
                isLoadingMore = true
                loadMoreErrorMessage = null
            }

            try {
                val keywordParam = searchQuery.ifBlank { null }

                val result = if (isPersonalCoursesMode) {
                    videoRepository.getPersonalCourse(null, currentPage, 16, keywordParam)
                } else {
                    videoRepository.getCourse(null, currentPage, 16, keywordParam)
                }

                if (result.isEmpty()) {
                    isEndReached = true
                } else {
                    val existingIds = courses.mapTo(mutableSetOf()) { it.id }
                    courses.addAll(
                        result
                            .distinctBy { it.id }
                            .filterNot { it.id in existingIds }
                    )
                    currentPage++
                }

                uiState = CourseUiState.Success(courses.toList())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (!isNextPage) {
                    uiState = CourseUiState.Error("加载失败: ${e.message ?: "课程资源请求失败"}")
                } else {
                    loadMoreErrorMessage = "继续加载失败：${e.message ?: "请稍后再试"}"
                }
            } finally {
                isLoadingMore = false
            }
        }
    }

    fun toggleCourseMode() {
        isPersonalCoursesMode = !isPersonalCoursesMode
        semesters = if (isPersonalCoursesMode) buildCourseSemesters(personalCourses) else publicSemesters
        selectedSemester = semesters.firstOrNull { it == selectedSemester } ?: semesters.first()
        loadCourses(isNextPage = false)
    }

    fun retryLoadCourses() {
        if (isPersonalCoursesMode) {
            refreshMySchedule()
        } else {
            loadCourses(isNextPage = false)
        }
    }

    fun refreshMySchedule() {
        if (isRefreshingSchedule) return
        isRefreshingSchedule = true
        courseLoadJob?.cancel()
        courseLoadJob = viewModelScope.launch {
            uiState = CourseUiState.Loading
            loadMoreErrorMessage = null
            isPersonalCoursesMode = true
            isLoadingMore = false
            isEndReached = true

            try {
                runCatching {
                    withTimeout(30_000) {
                        videoRepository.getAllPersonalCourses()
                    }
                }.onSuccess { loadedCourses ->
                    personalCourses = loadedCourses
                    semesters = buildCourseSemesters(personalCourses)
                    selectedSemester = pickLatestSemester(semesters)
                    applyPersonalCourseFilters()
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    personalCourses = emptyList()
                    semesters = listOf(ALL_SEMESTERS)
                    selectedSemester = ALL_SEMESTERS
                    uiState = CourseUiState.Error(formatYanheError(throwable))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                personalCourses = emptyList()
                courses.clear()
                semesters = listOf(ALL_SEMESTERS)
                selectedSemester = ALL_SEMESTERS
                uiState = CourseUiState.Error(formatYanheError(e))
            } finally {
                isRefreshingSchedule = false
            }
        }
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
            .filter { selectedSemester == ALL_SEMESTERS || it.semester == selectedSemester }
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

    private fun clearPersonalCourseState() {
        personalCourses = emptyList()
        semesters = listOf(ALL_SEMESTERS)
        selectedSemester = ALL_SEMESTERS
        if (isPersonalCoursesMode) {
            courseLoadJob?.cancel()
            courses.clear()
            uiState = CourseUiState.Success(emptyList())
        }
    }

    private fun pickLatestSemester(options: List<String>): String {
        return options.firstOrNull { it != ALL_SEMESTERS } ?: ALL_SEMESTERS
    }

    private fun formatYanheError(throwable: Throwable): String {
        val raw = throwable.message.orEmpty()
        return raw
            .replace(Regex("^java\\.lang\\.[A-Za-z]+Exception:\\s*"), "")
            .replace(Regex("^com\\.google\\.gson\\.[A-Za-z]+Exception:\\s*"), "")
            .ifBlank { "延河课堂课程拉取失败" }
    }
}

internal const val ALL_SEMESTERS = "全部学期"

internal fun buildCourseSemesters(courses: List<Course>): List<String> {
    val courseSemesters = courses
        .map { it.semester.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .sortedByDescending(::semesterSortKey)
    return listOf(ALL_SEMESTERS) + courseSemesters
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
