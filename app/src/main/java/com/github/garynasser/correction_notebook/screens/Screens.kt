package com.github.garynasser.correction_notebook.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Home : Screen("home", "首页", Icons.Default.AutoAwesome)
    object Workbook : Screen("workbook", "错题本", Icons.Default.Book)
    object Community : Screen("community", "社区", Icons.Default.Group)
    object Profile : Screen("profile", "我的", Icons.Default.Person)
}

val bottomNavItems = listOf(
    Screen.Home,
    Screen.Workbook,
    Screen.Community,
    Screen.Profile
)