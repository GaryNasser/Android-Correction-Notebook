package com.github.garynasser.correction_notebook.ui.screens.yanhe

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.SubcomposeAsyncImage
import com.github.garynasser.correction_notebook.data.model.yanhe.Course
import com.github.garynasser.correction_notebook.data.model.yanhe.CourseProgress
import com.github.garynasser.correction_notebook.ui.components.FreshScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseListScreen(
    viewModel: CourseListViewModel = hiltViewModel(),
    onCourseCardClick: (Int, String) -> Unit
) {
    val gridState = rememberLazyGridState()

    // 监听是否滑动到底部
    val shouldLoadMore = remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val totalItems = layoutInfo.totalItemsCount
            val lastVisibleItemIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            // 距离底部还有 2 个 item 时就开始加载
            lastVisibleItemIndex >= totalItems - 2 && totalItems > 0
        }
    }

    // 触发加载更多
    LaunchedEffect(shouldLoadMore.value) {
        if (shouldLoadMore.value) {
            viewModel.loadCourses(isNextPage = true)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(text = "课程资源", fontWeight = FontWeight.Bold) },
                windowInsets = WindowInsets(0, 0, 0, 0),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                actions = {
                    FilledIconButton(
                        onClick = { viewModel.refreshMySchedule() },
                        enabled = !viewModel.isRefreshingSchedule,
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .size(38.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (viewModel.isRefreshingSchedule) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                Icons.Default.CalendarMonth,
                                contentDescription = "同步我的课程",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        FreshScreen(modifier = Modifier.padding(innerPadding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                // 搜索与下拉框区域
                SearchAndFilterSection(viewModel)

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (val state = viewModel.uiState) {
                        is CourseUiState.Loading -> {
                            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        }
                        is CourseUiState.Error -> {
                            CourseListMessageState(
                                title = "课程加载失败",
                                message = state.message,
                                actionText = "重试",
                                onAction = viewModel::retryLoadCourses,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(horizontal = 24.dp)
                            )
                        }
                        is CourseUiState.Success -> {
                            LazyVerticalGrid(
                                state = gridState,
                                columns = GridCells.Adaptive(minSize = 152.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                if (viewModel.recentProgress.isNotEmpty()) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        RecentLearningSection(
                                            items = viewModel.recentProgress,
                                            onCourseClick = onCourseCardClick
                                        )
                                    }
                                }

                                if (state.courses.isEmpty()) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        EmptyCourseState(
                                            isPersonalMode = viewModel.isPersonalCoursesMode,
                                            onRefresh = { viewModel.refreshMySchedule() }
                                        )
                                    }
                                }

                                items(state.courses, key = { it.id }) { course ->
                                    CourseCard(
                                        course = course,
                                        onCourseCardClick = onCourseCardClick
                                    )
                                }

                                // 底部加载指示器
                                if (viewModel.isLoadingMore) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        CourseListInlineStatus(message = "正在加载更多课程...", isLoading = true)
                                    }
                                } else if (viewModel.loadMoreErrorMessage != null) {
                                    item(span = { GridItemSpan(maxLineSpan) }) {
                                        CourseListInlineStatus(
                                            message = viewModel.loadMoreErrorMessage ?: "继续加载失败",
                                            isError = true,
                                            actionText = "重试",
                                            onAction = { viewModel.loadCourses(isNextPage = true) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseListInlineStatus(
    message: String,
    isLoading: Boolean = false,
    isError: Boolean = false,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (isError) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.68f)
        } else {
            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
        },
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = if (isError) Icons.Default.CloudOff else Icons.Default.Info,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.bodySmall,
                color = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
            )
            if (actionText != null && onAction != null) {
                TextButton(onClick = onAction, contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp)) {
                    Text(actionText)
                }
            }
        }
    }
}

@Composable
private fun CourseListMessageState(
    title: String,
    message: String,
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                Icons.Default.CloudOff,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = onAction,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(actionText)
            }
        }
    }
}

@Composable
private fun EmptyCourseState(
    isPersonalMode: Boolean,
    onRefresh: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.86f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.CalendarMonth,
                contentDescription = null,
                modifier = Modifier.size(26.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = if (isPersonalMode) "还没有显示我的课程" else "没有找到课程",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = if (isPersonalMode) {
                    "点击右上角同步，登录并刷新延河课堂我的课程。"
                } else {
                    "换个关键词或学期再试试。"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
            if (isPersonalMode) {
                TextButton(
                    onClick = onRefresh,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("刷新课表")
                }
            }
        }
    }
}

@Composable
private fun RecentLearningSection(
    items: List<CourseProgress>,
    onCourseClick: (Int, String) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.34f),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.History, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "最近学习",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "继续上次进度",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items.forEach { progress ->
                RecentLearningRow(
                    progress = progress,
                    onClick = { onCourseClick(progress.courseId, progress.courseName) }
                )
            }
        }
    }
}

@Composable
private fun RecentLearningRow(
    progress: CourseProgress,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                Icons.Default.PlayCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = progress.courseName.ifBlank { "课程 ${progress.courseId}" },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = progress.lastSectionTitle.ifBlank { "继续查看课程章节" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            AssistChip(
                onClick = onClick,
                label = { Text("${progress.progressPercent}%") },
                leadingIcon = {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            )
        }
    }
}

@Composable
fun SearchAndFilterSection(viewModel: CourseListViewModel) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(7.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = viewModel.isPersonalCoursesMode,
                    onClick = {
                        if (!viewModel.isPersonalCoursesMode) {
                            viewModel.toggleCourseMode()
                        }
                    },
                    label = { Text("我的课程") },
                    leadingIcon = {
                        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(15.dp))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 34.dp)
                )
                FilterChip(
                    selected = !viewModel.isPersonalCoursesMode,
                    onClick = {
                        if (viewModel.isPersonalCoursesMode) {
                            viewModel.toggleCourseMode()
                        }
                    },
                    label = { Text("全校课程") },
                    leadingIcon = {
                        Icon(Icons.Default.Public, contentDescription = null, modifier = Modifier.size(15.dp))
                    },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 34.dp)
                )
                SemesterMenu(
                    selectedSemester = viewModel.selectedSemester,
                    semesters = viewModel.semesters,
                    expanded = viewModel.expanded,
                    onExpandedChange = { viewModel.expanded = it },
                    onSemesterSelected = viewModel::selectSemester,
                    modifier = Modifier.weight(1.25f)
                )
            }

            OutlinedTextField(
                value = viewModel.searchQuery,
                onValueChange = { viewModel.updateSearchQuery(it) },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索课程名称或老师") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                trailingIcon = {
                    Row {
                        if (viewModel.searchQuery.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    viewModel.updateSearchQuery("")
                                    if (!viewModel.isPersonalCoursesMode) {
                                        viewModel.loadCourses(false)
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "清空搜索",
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                viewModel.loadCourses(false)
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "搜索课程",
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                },
                shape = RoundedCornerShape(8.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.loadCourses(false) })
            )
        }
    }
}

@Composable
private fun SemesterMenu(
    selectedSemester: String,
    semesters: List<String>,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSemesterSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 34.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable { onExpandedChange(!expanded) },
            shape = RoundedCornerShape(8.dp),
            color = if (expanded) {
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.62f)
            } else {
                MaterialTheme.colorScheme.surface
            },
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f))
        ) {
            Row(
                modifier = Modifier.padding(start = 9.dp, top = 6.dp, end = 7.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = selectedSemester,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = "选择学期",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            semesters.forEach { semester ->
                DropdownMenuItem(
                    text = { Text(semester, style = MaterialTheme.typography.bodyMedium) },
                    onClick = {
                        onSemesterSelected(semester)
                        onExpandedChange(false)
                    }
                )
            }
        }
    }
}

@Composable
fun CourseCard(
    course: Course,
    onCourseCardClick: (Int, String) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        onClick = { onCourseCardClick(course.id, course.nameZh) }
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f),
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.62f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                SubcomposeAsyncImage(
                    model = course.imageUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    loading = {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        }
                    },
                    error = {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.48f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                Icons.Default.PlayCircleOutline,
                                contentDescription = null,
                                modifier = Modifier
                                    .padding(11.dp)
                                    .size(24.dp),
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.62f)
                            )
                        }
                    }
                )
            }

            Column(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = course.nameZh,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = course.professors.joinToString(", ").ifEmpty { "未知讲师" },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.32f))
                ) {
                    Text(
                        text = course.semester.ifBlank { "学期未知" },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}
