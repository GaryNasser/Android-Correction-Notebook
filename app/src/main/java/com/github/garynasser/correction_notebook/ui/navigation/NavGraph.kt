package com.github.garynasser.correction_notebook.ui.navigation

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.github.garynasser.correction_notebook.data.model.auth.AuthState
import com.github.garynasser.correction_notebook.ui.screens.login.UsernameLoginScreen
import com.github.garynasser.correction_notebook.ui.screens.main.MainViewModel
import com.github.garynasser.correction_notebook.MainContainer
import com.github.garynasser.correction_notebook.data.model.auth.AuthEvent
import com.github.garynasser.correction_notebook.data.repository.AuthStateManager
import com.github.garynasser.correction_notebook.ui.screens.register.CasScreen
import com.github.garynasser.correction_notebook.ui.screens.register.RegistrationViewModel
import com.github.garynasser.correction_notebook.ui.screens.register.RegisterScreen
import com.github.garynasser.correction_notebook.ui.screens.yanhe.CourseVideoListScreen


@Composable
fun NavGraph(
    modifier: Modifier,
    navController: NavHostController,
    mainViewModel: MainViewModel = hiltViewModel(),
) {
    val authStateManager = mainViewModel.authStateManager
    val authState by mainViewModel.authState.collectAsState()

    when(val state = authState) {
        is AuthState.Loading -> {
            LoadingSplashScreen()
        }
        is AuthState.Authenticated, is AuthState.Unauthenticated -> {
            NavHost(
                navController = navController,
                startDestination = if (state is AuthState.Authenticated) Home else Login
            ) {
                composable<Home> {
                    MainContainer(
                        outerNavController = navController
                    )
                }

                composable<Login> {
                    UsernameLoginScreen(
                        onNavigateToRegister = { navController.navigate(Register) }
                    )
                }

                composable<Register> { backStackEntry ->
                    val registerViewModel: RegistrationViewModel = hiltViewModel(backStackEntry)

                    RegisterScreen(
                        onNext = { navController.navigate(CasAuth) },
                        viewModel = registerViewModel,
                        onNavigateToLogin = { navController.navigate(Login) }
                    )
                }

                composable<CasAuth> { backStackEntry ->
                    val registerEntry = remember(backStackEntry) {
                        try {
                            navController.getBackStackEntry<Register>()
                        } catch (e: Exception) {
                            null
                        }
                    }

                    val authViewModel: RegistrationViewModel = if (registerEntry != null) {
                        hiltViewModel(registerEntry)
                    } else {
                        hiltViewModel(backStackEntry)
                    }

                    CasScreen(
                        viewModel = authViewModel,
                        onBackButtonClick = { navController.popBackStack() },
                        onConfirm = {
                            if (registerEntry != null) {
                                authViewModel.submit()
                            } else {
                                authViewModel.submitReauthentication(
                                    onConfirmClick = { navController.popBackStack() }
                                )
                            }
                        }
                    )
                }
            }
        }
    }

    LaunchedEffect(authState) {
        if (authState is AuthState.Unauthenticated) {
            navController.navigate(Login) {
                popUpTo(0) { inclusive = true }
            }
        } else if (authState is AuthState.Authenticated) {
            navController.navigate(Home) {
                launchSingleTop = true
                popUpTo(Login) { inclusive = true }
            }
        }
    }

    LaunchedEffect(Unit) {
        authStateManager.authEvents.collect { event ->
            when (event) {
                AuthEvent.NEEDS_LOGIN -> {
                    Log.d("NEEDS_LOGIN", "接收到信号")
                    navController.navigate(CasAuth) {
                        popUpTo(navController.graph.startDestinationId) {
                            saveState = true
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@Composable
fun LoadingSplashScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}