package com.github.garynasser.correction_notebook

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.github.garynasser.correction_notebook.navigation.Screen
import com.github.garynasser.correction_notebook.navigation.bottomNavItems
import com.github.garynasser.correction_notebook.screens.HomeScreen

@Composable
fun MainContainer() {
    val navController = rememberNavController()

    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination

                bottomNavItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(screen.title) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
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
            composable(Screen.Workbook.route) { PlaceholderScreen("错题本页面") }
            composable(Screen.Community.route) { PlaceholderScreen("社交社区") }
            composable(Screen.Profile.route) { PlaceholderScreen("个人中心") }
        }
    }
}

@Composable
fun PlaceholderScreen(text: String) {
    Surface { Text(text) }
}