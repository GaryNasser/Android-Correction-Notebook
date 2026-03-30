package com.github.garynasser.correction_notebook

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.github.garynasser.correction_notebook.ui.screens.AITutorScreen
import com.github.garynasser.correction_notebook.ui.screens.home.HomeScreen
import com.github.garynasser.correction_notebook.ui.navigation.Screen
import com.github.garynasser.correction_notebook.ui.screens.KnowledgeBaseScreen
import com.github.garynasser.correction_notebook.ui.screens.ProfileScreen
import com.github.garynasser.correction_notebook.ui.screens.YanheClassroomScreen
import com.github.garynasser.correction_notebook.ui.navigation.bottomNavItems
import com.github.garynasser.correction_notebook.ui.navigation.bottomNavItemsWithAI
import com.github.garynasser.correction_notebook.ui.screens.main.SettingsViewModel

@Composable
fun MainContainer(
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val aiEnabled by settingsViewModel.aiEnabled.collectAsState()
    val navItems = if (aiEnabled) bottomNavItemsWithAI else bottomNavItems

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                navItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
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
            startDestination = Screen.Home.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Screen.Home.route) { HomeScreen() }
            composable(Screen.YanheClassroom.route) { YanheClassroomScreen() }
            composable(Screen.KnowledgeBase.route) { KnowledgeBaseScreen() }
            composable(Screen.AITutor.route) { AITutorScreen() }
            composable(Screen.Profile.route) {
                ProfileScreen(settingsViewModel = settingsViewModel)
            }
        }
    }
}
