package com.github.garynasser.correction_notebook

import android.util.Log
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import androidx.navigation.toRoute
import com.github.garynasser.correction_notebook.ui.navigation.AITutor
import com.github.garynasser.correction_notebook.ui.navigation.CourseList
import com.github.garynasser.correction_notebook.ui.navigation.Home
import com.github.garynasser.correction_notebook.ui.navigation.KnowledgeBase
import com.github.garynasser.correction_notebook.ui.navigation.Profile
import com.github.garynasser.correction_notebook.ui.navigation.VideoList
import com.github.garynasser.correction_notebook.ui.navigation.VideoPlayer
import com.github.garynasser.correction_notebook.ui.navigation.bottomNavList
import com.github.garynasser.correction_notebook.ui.screens.aitutor.AITutorScreen
import com.github.garynasser.correction_notebook.ui.screens.home.HomeScreen
import com.github.garynasser.correction_notebook.ui.screens.knowledgebase.KnowledgeBaseScreen
import com.github.garynasser.correction_notebook.ui.screens.main.SettingsViewModel
import com.github.garynasser.correction_notebook.ui.screens.profile.ProfileScreen
import com.github.garynasser.correction_notebook.ui.screens.yanhe.CourseListScreen
import com.github.garynasser.correction_notebook.ui.screens.yanhe.CourseVideoListScreen
import com.github.garynasser.correction_notebook.ui.screens.yanhe.PlayerScreen

@Composable
fun MainContainer(
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val navController = rememberNavController()
    val aiEnabled by settingsViewModel.aiEnabled.collectAsState()
    var hideBottomBar by remember { mutableStateOf(false) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // 自动判断当前路由是否在底部栏列表中
    val shouldShowBottomBar = bottomNavList.any { item ->
        currentDestination?.hasRoute(item.route::class) == true
    }

    Scaffold(
        bottomBar = {
            // 结合了配置和隐藏逻辑
            if (!hideBottomBar && shouldShowBottomBar) {
                NavigationBar {
                    bottomNavList.forEach { item ->
                        // AI 路由过滤逻辑
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
        } // 这里是 bottomBar 结束
    ) { innerPadding -> // 这里是 Scaffold 的 content 启动
        NavHost(
            navController = navController,
            startDestination = Home,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable<Home> {
                HomeScreen(
                    onImmersiveModeChanged = { hideBottomBar = it }
                )
            }
            composable<CourseList> { CourseListScreen(onCourseCardClick = { courseId ->
                navController.navigate(VideoList(courseId))
            }) }
            composable<AITutor> { AITutorScreen() }
            composable<KnowledgeBase> { KnowledgeBaseScreen() }

            composable<VideoList> {
                CourseVideoListScreen(
                    onBackButtonClick = {
                        navController.popBackStack()
                    },
                    onNavigateToPlayer = { url ->
                        navController.navigate(VideoPlayer(url))
                    }
                )
            }

            composable<VideoPlayer> {
                PlayerScreen(onBack = { navController.popBackStack() })
            }

            composable<Profile> { ProfileScreen(settingsViewModel) }
        }
    }
}