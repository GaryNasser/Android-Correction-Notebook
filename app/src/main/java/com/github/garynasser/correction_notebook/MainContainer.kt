package com.github.garynasser.correction_notebook

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.github.garynasser.correction_notebook.ui.navigation.AITutor
import com.github.garynasser.correction_notebook.ui.navigation.CourseList
import com.github.garynasser.correction_notebook.ui.navigation.Home
import com.github.garynasser.correction_notebook.ui.navigation.KnowledgeBase
import com.github.garynasser.correction_notebook.ui.navigation.Profile
import com.github.garynasser.correction_notebook.ui.navigation.bottomNavList
import com.github.garynasser.correction_notebook.ui.screens.aitutor.AITutorScreen
import com.github.garynasser.correction_notebook.ui.screens.home.HomeScreen
import com.github.garynasser.correction_notebook.ui.screens.knowledgebase.KnowledgeBaseScreen
import com.github.garynasser.correction_notebook.ui.screens.main.SettingsViewModel
import com.github.garynasser.correction_notebook.ui.screens.profile.ProfileScreen
import com.github.garynasser.correction_notebook.ui.screens.yanhe.CourseListScreen

@Composable
fun MainContainer(
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val navController = rememberNavController()

    val aiEnabled by settingsViewModel.aiEnabled.collectAsState()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                bottomNavList.forEach { item ->
                    if (item.route is AITutor && !aiEnabled) return@forEach

                    val isSelected = currentDestination?.hasRoute(item.route::class) ?: false

                    NavigationBarItem(
                        icon = { Icon(item.icon, contentDescription = item.title) },
                        label = { Text(item.title) },
                        selected = isSelected,
                        onClick = {
                            navController.navigate(item.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Home,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable<Home> { HomeScreen() }
            composable<CourseList> { CourseListScreen() }
            composable<AITutor> { AITutorScreen() }
            composable<KnowledgeBase> { KnowledgeBaseScreen() }
            composable<Profile> { ProfileScreen(settingsViewModel) }
        }
    }
}