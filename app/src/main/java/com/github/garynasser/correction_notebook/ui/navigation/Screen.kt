package com.github.garynasser.correction_notebook.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.filled.MenuBook

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "首页", Icons.Default.AutoAwesome)
    object StudyCenter : Screen("study_center", "学习中心", Icons.Default.MenuBook)
    object Community : Screen("community", "社区", Icons.Default.Group)
    object Profile : Screen("profile", "我的", Icons.Default.Person)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.StudyCenter,
    Screen.Community,
    Screen.Profile
)