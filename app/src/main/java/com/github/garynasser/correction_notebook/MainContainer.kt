package com.github.garynasser.correction_notebook

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.* // 必须包含这个，解决 composable 报错
import com.github.garynasser.correction_notebook.ui.screens.CommunityScreen
import com.github.garynasser.correction_notebook.ui.screens.home.HomeScreen
import com.github.garynasser.correction_notebook.ui.screens.ProfileScreen
import com.github.garynasser.correction_notebook.ui.navigation.Screen
import com.github.garynasser.correction_notebook.ui.screens.StudyCenterScreen
import com.github.garynasser.correction_notebook.ui.navigation.bottomNavItems

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContainer() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                // 这里的 bottomNavItems 是从 screens 包里导入的
                bottomNavItems.forEach { screen ->
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
            // 注意：这里调用的函数名必须和你 screens 文件夹下定义的一致
            composable(Screen.Home.route) { HomeScreen() }
            composable(Screen.StudyCenter.route) { StudyCenterScreen() }
            composable(Screen.Community.route) { CommunityScreen() }
            composable(Screen.Profile.route) { ProfileScreen() }
        }
    }
}