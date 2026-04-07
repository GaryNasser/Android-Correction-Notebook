package com.github.garynasser.correction_notebook.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.ui.graphics.vector.ImageVector
import com.github.garynasser.correction_notebook.ui.screens.yanhe.CourseListScreen

data class BottomNavItem <T : Any> (
    val route: T,
    val title: String,
    val icon: ImageVector
)


val bottomNavList = listOf(
    BottomNavItem(Home, "首页", Icons.Default.AutoAwesome),
    BottomNavItem(CourseList, "延河课堂", Icons.Default.School)
)