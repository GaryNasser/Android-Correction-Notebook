package com.github.garynasser.correction_notebook.ui.screens.knowledgebase

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KnowledgeBaseScreen() {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("我的文件", "错题库", "云端共享")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("知识库") },
                actions = {
                    IconButton(onClick = { /* TODO: 搜索 */ }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                    IconButton(onClick = { /* TODO: 更多 */ }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "更多")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { /* TODO: 添加文件 */ }
            ) {
                Icon(Icons.Default.Add, contentDescription = "添加")
            }
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
                0 -> MyFilesContent()
                1 -> WrongQuestionsContent()
                2 -> CloudSharedContent()
            }
        }
    }
}

@Composable
fun MyFilesContent() {
    var currentPath by remember { mutableStateOf("根目录") }
    val folders = remember {
        listOf(
            FolderItem("高等数学", 12, true),
            FolderItem("大学物理", 8, true),
            FolderItem("线性代数", 5, true),
            FolderItem("编程资料", 23, true)
        )
    }
    val files = remember {
        listOf(
            FileItem("第一章笔记.pdf", "2.3 MB", false),
            FileItem("课后习题答案.docx", "1.1 MB", true),
            FileItem("重点公式汇总.jpg", "856 KB", false)
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 路径导航
        ListItem(
            headlineContent = { Text(currentPath) },
            leadingContent = {
                IconButton(onClick = { /* TODO: 返回上级 */ }) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                }
            },
            trailingContent = {
                IconButton(onClick = { /* TODO: 排序方式 */ }) {
                    Icon(Icons.Default.Sort, contentDescription = "排序")
                }
            }
        )

        HorizontalDivider()

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // 文件夹
            items(folders) { folder ->
                FolderCard(folder)
            }

            item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }

            // 文件
            items(files) { file ->
                FileCard(file)
            }
        }
    }
}

@Composable
fun WrongQuestionsContent() {
    val wrongQuestions = remember {
        listOf(
            WrongQuestionItem("高数-微分方程", "2024-03-15", "未复习"),
            WrongQuestionItem("物理-电磁学", "2024-03-14", "已复习"),
            WrongQuestionItem("线代-矩阵运算", "2024-03-10", "未复习")
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 统计卡片
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatCard(
                modifier = Modifier.weight(1f),
                title = "错题总数",
                value = "45",
                color = MaterialTheme.colorScheme.primaryContainer
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "本周新增",
                value = "8",
                color = MaterialTheme.colorScheme.secondaryContainer
            )
            StatCard(
                modifier = Modifier.weight(1f),
                title = "已掌握",
                value = "12",
                color = MaterialTheme.colorScheme.tertiaryContainer
            )
        }

        HorizontalDivider()

        // 错题列表
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(wrongQuestions) { question ->
                WrongQuestionCard(question)
            }
        }
    }
}

@Composable
fun CloudSharedContent() {
    val sharedItems = remember {
        listOf(
            SharedItem("2024年期末复习资料", "张同学", "2024-03-20", true),
            SharedItem("高数重点笔记", "李同学", "2024-03-18", false),
            SharedItem("物理公式大全", "王同学", "2024-03-15", false)
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 提示
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.medium
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "校园网环境下可下载云端共享资料",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(sharedItems) { item ->
                SharedFileCard(item)
            }
        }
    }
}

data class FolderItem(
    val name: String,
    val fileCount: Int,
    val hasSubfolders: Boolean
)

@Composable
fun FolderCard(folder: FolderItem) {
    ListItem(
        headlineContent = { Text(folder.name) },
        supportingContent = { Text("${folder.fileCount} 个文件") },
        leadingContent = {
            Icon(
                if (folder.hasSubfolders) Icons.Default.Folder else Icons.Default.FolderOpen,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        trailingContent = {
            Icon(Icons.Default.ChevronRight, contentDescription = null)
        },
        modifier = Modifier.clickable { /* TODO: 进入文件夹 */ }
    )
}

data class FileItem(
    val name: String,
    val size: String,
    val hasImage: Boolean
)

@Composable
fun FileCard(file: FileItem) {
    ListItem(
        headlineContent = { Text(file.name) },
        supportingContent = { Text(file.size) },
        leadingContent = {
            Icon(
                when {
                    file.name.endsWith(".pdf") -> Icons.Default.PictureAsPdf
                    file.name.endsWith(".docx") || file.name.endsWith(".doc") -> Icons.Default.Description
                    file.name.endsWith(".jpg") || file.name.endsWith(".png") -> Icons.Default.Image
                    else -> Icons.Default.InsertDriveFile
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Row {
                if (file.hasImage) {
                    IconButton(onClick = { /* TODO: 预览 */ }) {
                        Icon(Icons.Default.Visibility, contentDescription = "预览")
                    }
                }
                IconButton(onClick = { /* TODO: 更多操作 */ }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                }
            }
        }
    )
}

data class WrongQuestionItem(
    val title: String,
    val date: String,
    val status: String
)

@Composable
fun WrongQuestionCard(question: WrongQuestionItem) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 错题图标
            Surface(
                modifier = Modifier.size(48.dp),
                shape = MaterialTheme.shapes.small,
                color = if (question.status == "已复习")
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.errorContainer
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = if (question.status == "已复习")
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = question.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = question.date,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            AssistChip(
                onClick = { /* TODO: 状态切换 */ },
                label = { Text(question.status) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (question.status == "已复习")
                        MaterialTheme.colorScheme.primaryContainer
                    else
                        MaterialTheme.colorScheme.errorContainer
                )
            )
        }
    }
}

@Composable
fun StatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    color: Color
) {
    Surface(
        modifier = modifier,
        color = color,
        shape = MaterialTheme.shapes.medium
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
            )
        }
    }
}

data class SharedItem(
    val title: String,
    val uploader: String,
    val date: String,
    val isDownloaded: Boolean
)

@Composable
fun SharedFileCard(item: SharedItem) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Cloud,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${item.uploader} · ${item.date}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }

            if (item.isDownloaded) {
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "已下载",
                    tint = MaterialTheme.colorScheme.primary
                )
            } else {
                IconButton(onClick = { /* TODO: 下载 */ }) {
                    Icon(Icons.Default.Download, contentDescription = "下载")
                }
            }
        }
    }
}
