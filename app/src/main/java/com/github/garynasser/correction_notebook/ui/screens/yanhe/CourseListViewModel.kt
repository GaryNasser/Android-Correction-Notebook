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
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.combine
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
    private var personalCoursesLoaded = false
    private var hasSelectedSemester = false
    private var appliedPublicKeyword: String? = null
    var isRefreshingSchedule by mutableStateOf(false)
        private set
    var loadMoreErrorMessage by mutableStateOf<String?>(null)
        private set
    private var courseLoadJob: Job? = null
    private var courseRequest = 0L
    private var observedSessionVersion = yanheRepository.sessionVersion

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
            combine(authStateManager.authState, yanheRepository.sessionRevision) { state, version -> state to version }
                .collect { (state, version) ->
                    val changed = observedSessionVersion != version
                    observedSessionVersion = version
                    if (changed) clearPersonalCourseState(clearVisibleCourses = true)
                    when (state) {
                        is AuthState.Authenticated -> {
                            if (yanheRepository.isSignedOut) {
                                clearPersonalCourseState()
                            } else if (isPersonalCoursesMode && personalCourses.isEmpty()) {
                                refreshMySchedule()
                            } else if (changed && !isPersonalCoursesMode) {
                                loadCourses()
                            }
                        }
                        is AuthState.Unauthenticated -> clearPersonalCourseState()
                        else -> Unit
                    }
                }
        }
    }

    fun loadCourses(isNextPage: Boolean = false) {
        if (isPersonalCoursesMode) {
            if (!isNextPage && !isRefreshingSchedule) {
                if (personalCoursesLoaded) applyPersonalCourseFilters() else refreshMySchedule()
            }
            return
        }
        if (isNextPage && (isLoadingMore || isEndReached || courseLoadJob?.isActive == true)) return

        val request = ++courseRequest
        if (!isNextPage) {
            courseLoadJob?.cancel()
            isRefreshingSchedule = false
            isLoadingMore = false
            appliedPublicKeyword = searchQuery.trim().ifBlank { null }
        }
        val keyword = appliedPublicKeyword
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
                val result = awaitYanheResource(30_000, "课程请求超时，请重试") {
                    videoRepository.getCourse(null, currentPage, 16, keyword)
                }
                if (request != courseRequest) return@launch

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
                if (request == courseRequest && currentCoroutineContext().isActive) {
                    uiState = CourseUiState.Error("登录状态已变化，请重新加载课程")
                }
                throw e
            } catch (e: Exception) {
                if (request != courseRequest) return@launch
                if (!isNextPage) {
                    uiState = CourseUiState.Error("加载失败: ${e.message ?: "课程资源请求失败"}")
                } else {
                    loadMoreErrorMessage = "继续加载失败：${e.message ?: "请稍后再试"}"
                }
            } finally {
                if (request == courseRequest) isLoadingMore = false
            }
        }
    }

    fun toggleCourseMode() {
        courseRequest++
        courseLoadJob?.cancel()
        courseLoadJob = null
        isRefreshingSchedule = false
        isLoadingMore = false
        loadMoreErrorMessage = null
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
        val request = ++courseRequest
        courseLoadJob = viewModelScope.launch {
            uiState = CourseUiState.Loading
            loadMoreErrorMessage = null
            isPersonalCoursesMode = true
            isLoadingMore = false
            isEndReached = true

            try {
                runCatching {
                    awaitYanheResource(30_000, "课程同步超时，请重试") {
                        videoRepository.getAllPersonalCourses()
                    }
                }.onSuccess { loadedCourses ->
                    if (request != courseRequest) return@onSuccess
                    personalCourses = loadedCourses
                    personalCoursesLoaded = true
                    semesters = buildCourseSemesters(personalCourses)
                    selectedSemester = if (!hasSelectedSemester) {
                        pickLatestSemester(semesters)
                    } else {
                        selectedSemester.takeIf { it in semesters } ?: ALL_SEMESTERS
                    }
                    applyPersonalCourseFilters()
                }.onFailure { throwable ->
                    if (throwable is CancellationException) throw throwable
                    if (request != courseRequest) return@onFailure
                    personalCourses = emptyList()
                    personalCoursesLoaded = false
                    courses.clear()
                    semesters = listOf(ALL_SEMESTERS)
                    selectedSemester = ALL_SEMESTERS
                    uiState = CourseUiState.Error(formatYanheError(throwable))
                }
            } catch (e: CancellationException) {
                if (request == courseRequest && currentCoroutineContext().isActive) {
                    uiState = CourseUiState.Error("登录状态已变化，请重新加载课程")
                }
                throw e
            } catch (e: Exception) {
                if (request != courseRequest) return@launch
                personalCourses = emptyList()
                personalCoursesLoaded = false
                courses.clear()
                semesters = listOf(ALL_SEMESTERS)
                selectedSemester = ALL_SEMESTERS
                uiState = CourseUiState.Error(formatYanheError(e))
            } finally {
                if (request == courseRequest) isRefreshingSchedule = false
            }
        }
    }

    fun selectSemester(semester: String) {
        hasSelectedSemester = true
        selectedSemester = semester
        expanded = false
        loadCourses(isNextPage = false)
    }

    fun updateSearchQuery(query: String) {
        searchQuery = query
        if (isPersonalCoursesMode && personalCoursesLoaded && !isRefreshingSchedule) {
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

    private fun clearPersonalCourseState(clearVisibleCourses: Boolean = isPersonalCoursesMode) {
        courseRequest++
        courseLoadJob?.cancel()
        courseLoadJob = null
        isRefreshingSchedule = false
        isLoadingMore = false
        loadMoreErrorMessage = null
        personalCourses = emptyList()
        personalCoursesLoaded = false
        hasSelectedSemester = false
        semesters = listOf(ALL_SEMESTERS)
        selectedSemester = ALL_SEMESTERS
        if (clearVisibleCourses) courses.clear()
        uiState = CourseUiState.Success(courses.toList())
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
    val academicYears = Regex("(\\d{4})\\D+(\\d{4})").find(semester)
    val term = Regex("第\\s*(\\d+)\\s*学期").find(semester)
        ?.groupValues
        ?.getOrNull(1)
        ?.toIntOrNull()
        ?: when {
            semester.contains("春") -> 2
            semester.contains("秋") -> 1
            else -> 0
        }
    val year = academicYears?.groupValues?.getOrNull(if (term == 1) 1 else 2)?.toIntOrNull()
        ?: Regex("\\d{4}").find(semester)?.value?.toIntOrNull()
        ?: 0
    val seasonOrder = when (term) {
        1 -> 2
        2 -> 1
        else -> term
    }
    return year * 10 + seasonOrder
}
