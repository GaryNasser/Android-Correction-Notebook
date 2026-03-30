package com.github.garynasser.correction_notebook.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.LibraryBooks
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "主页", Icons.Default.AutoAwesome)
    object YanheClassroom : Screen("yanhe_classroom", "延河课堂", Icons.Default.School)
    object KnowledgeBase : Screen("knowledge_base", "知识库", Icons.Default.LibraryBooks)
    object AITutor : Screen("ai_tutor", "AI导师", Icons.Default.SmartToy)
    object Profile : Screen("profile", "个人中心", Icons.Default.Person)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.YanheClassroom,
    Screen.KnowledgeBase,
    Screen.Profile
)

// 包含AI导师的完整导航项（当AI功能开启时使用）
val bottomNavItemsWithAI = listOf(
    Screen.Home,
    Screen.YanheClassroom,
    Screen.KnowledgeBase,
    Screen.AITutor,
    Screen.Profile
)
