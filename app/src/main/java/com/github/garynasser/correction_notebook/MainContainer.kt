package com.github.garynasser.correction_notebook

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.*
import com.github.garynasser.correction_notebook.data.local.AISettingsManager
import com.github.garynasser.correction_notebook.ui.components.BottomBarTab
import com.github.garynasser.correction_notebook.ui.components.LocalAiEnabled
import com.github.garynasser.correction_notebook.ui.navigation.ArticleDetailRoute
import com.github.garynasser.correction_notebook.ui.navigation.AITutor
import com.github.garynasser.correction_notebook.ui.navigation.CourseList
import com.github.garynasser.correction_notebook.ui.navigation.Home
import com.github.garynasser.correction_notebook.ui.navigation.KnowledgeBase
import com.github.garynasser.correction_notebook.ui.navigation.KnowledgeBaseFileViewer
import com.github.garynasser.correction_notebook.ui.navigation.CasAuth
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
import com.github.garynasser.correction_notebook.ui.update.AppUpdateDialog
import com.github.garynasser.correction_notebook.ui.update.openUpdateDownload

@Composable
fun MainContainer(
    aiSettingsManager: AISettingsManager,
    outerNavController: NavHostController? = null
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val appUpdateViewModel: AppUpdateViewModel = hiltViewModel()
    val appUpdateUiState by appUpdateViewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val aiEnabledSetting by aiSettingsManager.aiEnabled.collectAsStateWithLifecycle(initialValue = null)
    val aiEnabled = aiEnabledSetting == true
    var fullscreenEntryId by remember { mutableStateOf<String?>(null) }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val hideBottomBar = fullscreenEntryId != null && fullscreenEntryId == navBackStackEntry?.id

    fun updateFullscreen(entryId: String, fullscreen: Boolean) {
        if (fullscreen) fullscreenEntryId = entryId
        else if (fullscreenEntryId == entryId) fullscreenEntryId = null
    }

    // 自动判断当前路由是否在底部栏列表中
    val shouldShowBottomBar = bottomNavList.any { item ->
        currentDestination?.hasRoute(item.route::class) == true
    }

    LaunchedEffect(Unit) {
        appUpdateViewModel.checkForUpdates(silent = true)
    }

    LaunchedEffect(aiEnabledSetting, currentDestination) {
        if (aiEnabledSetting == false && currentDestination?.hasRoute(AITutor::class) == true) {
            navController.navigate(Home) {
                popUpTo(navController.graph.findStartDestination().id)
                launchSingleTop = true
            }
        }
    }

    CompositionLocalProvider(LocalAiEnabled provides aiEnabled) {
    Scaffold(
        contentWindowInsets = if (usesZeroContentInsets(
                hideBottomBar = hideBottomBar,
                shouldShowBottomBar = shouldShowBottomBar
            )
        ) {
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
                        .navigationBarsPadding(),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                    ) {
                        bottomNavList.forEach { item ->
                            // AI 路由过滤逻辑
                            if (item.route is AITutor && !aiEnabled) return@forEach

                            val isSelected = currentDestination?.hasRoute(item.route::class) ?: false

                            BottomBarTab(
                                modifier = Modifier.weight(1f),
                                label = item.title,
                                icon = item.icon,
                                isSelected = isSelected,
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
            composable<Home> { entry ->
                HomeScreen(
                    onOpenArticle = { article ->
                        navController.navigate(ArticleDetailRoute(article.id, article.url))
                    },
                    onNavigateToCourses = { progress ->
                        if (progress != null) {
                            navController.navigate(VideoList(progress.courseId, progress.courseName))
                        } else {
                            navController.navigate(CourseList) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    },
                    onOpenCourse = { courseId, courseName ->
                        navController.navigate(VideoList(courseId, courseName))
                    },
                    onNavigateToKnowledgeBase = {
                        navController.navigate(KnowledgeBase) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onOpenKnowledgeFile = { fileId ->
                        navController.navigate(KnowledgeBaseFileViewer(fileId))
                    },
                    onFullscreenModeChanged = { updateFullscreen(entry.id, it) }
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
            composable<KnowledgeBase> { entry ->
                KnowledgeBaseScreen(
                    onOpenFile = { fileId ->
                        navController.navigate(KnowledgeBaseFileViewer(fileId))
                    },
                    onFullscreenModeChanged = { updateFullscreen(entry.id, it) }
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
                    onNavigateToPlayer = { url, videoTitle, courseName ->
                        navController.navigate(VideoPlayer(url, videoTitle, courseName))
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
                    isCheckingForUpdates = appUpdateUiState.isChecking,
                    onNavigateToLogin = {
                        outerNavController?.navigate(CasAuth)
                    }
                )
            }
        }

        appUpdateUiState.availableUpdate?.let { update ->
            AppUpdateDialog(
                update = update,
                currentVersionName = appUpdateUiState.currentVersionName,
                errorMessage = appUpdateUiState.downloadErrorMessage,
                onDismiss = appUpdateViewModel::dismissUpdateDialog,
                onUpdate = {
                    val error = openUpdateDownload(context, update.downloadUrl)
                    if (error != null) appUpdateViewModel.reportDownloadFailure(error)
                    else appUpdateViewModel.dismissUpdateDialog()
                }
            )
        }
    }
    }
}

internal fun usesZeroContentInsets(
    hideBottomBar: Boolean,
    shouldShowBottomBar: Boolean
): Boolean {
    return hideBottomBar || shouldShowBottomBar
}
