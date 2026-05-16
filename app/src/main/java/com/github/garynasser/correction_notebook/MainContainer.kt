package com.github.garynasser.correction_notebook

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.github.garynasser.correction_notebook.data.local.AISettingsManager
import com.github.garynasser.correction_notebook.ui.navigation.ArticleDetailRoute
import com.github.garynasser.correction_notebook.ui.navigation.AITutor
import com.github.garynasser.correction_notebook.ui.navigation.CourseList
import com.github.garynasser.correction_notebook.ui.navigation.Home
import com.github.garynasser.correction_notebook.ui.navigation.KnowledgeBase
import com.github.garynasser.correction_notebook.ui.navigation.KnowledgeBaseFileViewer
import com.github.garynasser.correction_notebook.ui.navigation.Login
import com.github.garynasser.correction_notebook.ui.navigation.Profile
import com.github.garynasser.correction_notebook.ui.navigation.VideoList
import com.github.garynasser.correction_notebook.ui.navigation.VideoPlayer
import com.github.garynasser.correction_notebook.ui.navigation.bottomNavList
import com.github.garynasser.correction_notebook.ui.screens.aitutor.AITutorScreen
import com.github.garynasser.correction_notebook.ui.screens.home.ArticleDetailScreen
import com.github.garynasser.correction_notebook.ui.screens.home.HomeScreen
import com.github.garynasser.correction_notebook.ui.screens.knowledgebase.KnowledgeBaseFileViewerScreen
import com.github.garynasser.correction_notebook.ui.screens.knowledgebase.KnowledgeBaseScreen
import com.github.garynasser.correction_notebook.ui.screens.profile.ProfileScreen
import com.github.garynasser.correction_notebook.ui.screens.yanhe.CourseListScreen
import com.github.garynasser.correction_notebook.ui.screens.yanhe.CourseVideoListScreen
import com.github.garynasser.correction_notebook.ui.screens.yanhe.PlayerScreen
import com.github.garynasser.correction_notebook.ui.update.AppUpdateViewModel

@Composable
fun MainContainer(
    aiSettingsManager: AISettingsManager,
    outerNavController: NavHostController? = null
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val appUpdateViewModel: AppUpdateViewModel = hiltViewModel()
    val appUpdateUiState by appUpdateViewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val aiEnabled by aiSettingsManager.aiEnabled.collectAsState(initial = false)
    var hideBottomBar by remember { mutableStateOf(false) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination

    // 自动判断当前路由是否在底部栏列表中
    val shouldShowBottomBar = bottomNavList.any { item ->
        currentDestination?.hasRoute(item.route::class) == true
    }

    LaunchedEffect(Unit) {
        appUpdateViewModel.checkForUpdates(silent = true)
    }

    Scaffold(
        contentWindowInsets = if (hideBottomBar) {
            WindowInsets(0, 0, 0, 0)
        } else {
            ScaffoldDefaults.contentWindowInsets
        },
        snackbarHost = {
            SnackbarHost(snackbarHostState)
        },
        bottomBar = {
            // 结合了配置和隐藏逻辑
            if (!hideBottomBar && shouldShowBottomBar) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                ) {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                    tonalElevation = 0.dp
                ) {
                    bottomNavList.forEach { item ->
                        // AI 路由过滤逻辑
                        if (item.route is AITutor && !aiEnabled) return@forEach

                        val isSelected = currentDestination?.hasRoute(item.route::class) ?: false

                        NavigationBarItem(
                            icon = { Icon(item.icon, contentDescription = item.title) },
                            label = { Text(item.title) },
                            selected = isSelected,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.82f)
                            ),
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
            }
        } // 这里是 bottomBar 结束
    ) { innerPadding -> // 这里是 Scaffold 的 content 启动
        LaunchedEffect(appUpdateUiState.snackbarMessage) {
            val message = appUpdateUiState.snackbarMessage ?: return@LaunchedEffect
            snackbarHostState.showSnackbar(message)
            appUpdateViewModel.consumeSnackbarMessage()
        }
        NavHost(
            navController = navController,
            startDestination = Home,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable<Home> {
                HomeScreen(
                    onOpenArticle = { article ->
                        navController.navigate(ArticleDetailRoute(article.id))
                    },
                    onImmersiveModeChanged = { hideBottomBar = it }
                )
            }
            composable<ArticleDetailRoute> {
                ArticleDetailScreen(
                    onBack = { navController.popBackStack() }
                )
            }
            composable<CourseList> { CourseListScreen(onCourseCardClick = { courseId, courseName ->
                navController.navigate(VideoList(courseId, courseName))
            }) }
            composable<AITutor> { AITutorScreen() }
            composable<KnowledgeBase> {
                KnowledgeBaseScreen(
                    onOpenFile = { fileId ->
                        navController.navigate(KnowledgeBaseFileViewer(fileId))
                    }
                )
            }

            composable<KnowledgeBaseFileViewer> {
                KnowledgeBaseFileViewerScreen(
                    onBack = { navController.popBackStack() },
                    onDeleted = {
                        navController.popBackStack()
                    }
                )
            }

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

            composable<Profile> {
                ProfileScreen(
                    viewModel = hiltViewModel(),
                    onCheckForUpdates = {
                        appUpdateViewModel.checkForUpdates(silent = false)
                    },
                    currentVersionName = appUpdateUiState.currentVersionName,
                    onNavigateToLogin = {
                        outerNavController?.navigate(Login)
                    }
                )
            }
        }

        appUpdateUiState.availableUpdate?.let { update ->
            AlertDialog(
                onDismissRequest = {
                    if (!update.forceUpdate) {
                        appUpdateViewModel.dismissUpdateDialog()
                    }
                },
                title = { Text(update.updateTitle) },
                text = {
                    Text(
                        buildString {
                            append("最新版本 ${update.latestVersionName}\n\n")
                            append(
                                update.updateContent.ifBlank {
                                    "检测到新版本，建议尽快更新以获得更稳定的使用体验。"
                                }
                            )
                        }
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(update.downloadUrl))
                            context.startActivity(intent)
                            if (!update.forceUpdate) {
                                appUpdateViewModel.dismissUpdateDialog()
                            }
                        }
                    ) {
                        Text("立即更新")
                    }
                },
                dismissButton = {
                    if (!update.forceUpdate) {
                        TextButton(onClick = { appUpdateViewModel.dismissUpdateDialog() }) {
                            Text("稍后")
                        }
                    }
                }
            )
        }
    }
}
