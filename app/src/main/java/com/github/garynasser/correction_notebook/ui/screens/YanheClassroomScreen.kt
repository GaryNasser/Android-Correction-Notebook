package com.github.garynasser.correction_notebook.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YanheClassroomScreen() {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("录播课程", "直播课堂")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("延河课堂") },
                actions = {
                    IconButton(onClick = { /* TODO: 刷新 */ }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Tab 选择器
            TabRow(selectedTabIndex = selectedTab) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(title) }
                    )
                }
            }

            // 内容区域
            when (selectedTab) {
                0 -> RecordedCoursesContent()
                1 -> LiveClassContent()
            }
        }
    }
}

@Composable
fun RecordedCoursesContent() {
    val courses = remember {
        listOf(
            CourseItem("高等数学（下）", "张教授", "第12周 已更新", true),
            CourseItem("大学物理", "李教授", "第11周", false),
            CourseItem("线性代数", "王教授", "第10周 已更新", true),
            CourseItem("概率论与数理统计", "陈教授", "第9周", false)
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 搜索框
        item {
            OutlinedTextField(
                value = "",
                onValueChange = {},
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("搜索课程...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        items(courses) { course ->
            CourseCard(course)
        }
    }
}

@Composable
fun LiveClassContent() {
    val liveClasses = remember {
        listOf(
            LiveClassItem("计算机网络", "明天 14:00", "刘教授", true),
            LiveClassItem("操作系统", "后天 10:00", "赵教授", false)
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 直播预告标题
        ListItem(
            headlineContent = { Text("直播预告") },
            supportingContent = { Text("共 ${liveClasses.size} 场直播") }
        )

        HorizontalDivider()

        if (liveClasses.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.LiveTv,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "暂无直播安排",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            LazyColumn {
                items(liveClasses) { liveClass ->
                    LiveClassCard(liveClass)
                }
            }
        }
    }
}

data class CourseItem(
    val name: String,
    val teacher: String,
    val updateInfo: String,
    val hasUpdate: Boolean
)

@Composable
fun CourseCard(course: CourseItem) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 课程图标
            Surface(
                modifier = Modifier.size(56.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = course.teacher,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (course.hasUpdate) {
                        Badge(
                            containerColor = MaterialTheme.colorScheme.error
                        ) {
                            Text("新")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = course.updateInfo,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            IconButton(onClick = { /* TODO: 进入课程 */ }) {
                Icon(Icons.Default.ChevronRight, contentDescription = "进入")
            }
        }
    }
}

data class LiveClassItem(
    val name: String,
    val time: String,
    val teacher: String,
    val canRemind: Boolean
)

@Composable
fun LiveClassCard(liveClass: LiveClassItem) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 直播图标
            Surface(
                modifier = Modifier.size(56.dp),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.LiveTv,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = liveClass.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = liveClass.teacher,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = liveClass.time,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }

            if (liveClass.canRemind) {
                IconButton(onClick = { /* TODO: 设置提醒 */ }) {
                    Icon(Icons.Default.Notifications, contentDescription = "提醒")
                }
            }
        }
    }
}
