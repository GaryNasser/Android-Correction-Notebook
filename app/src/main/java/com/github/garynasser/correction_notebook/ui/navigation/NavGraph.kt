package com.github.garynasser.correction_notebook.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.github.garynasser.correction_notebook.data.model.auth.AuthState
import com.github.garynasser.correction_notebook.ui.screens.home.HomeScreen
import com.github.garynasser.correction_notebook.ui.screens.login.UsernameLoginScreen
import com.github.garynasser.correction_notebook.ui.screens.main.MainViewModel

@Composable
fun NavGraph(
    modifier: Modifier,
    navController: NavHostController,
    mainViewModel: MainViewModel = hiltViewModel()
) {
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
                    HomeScreen()
                }

                composable<Login> {
                    UsernameLoginScreen()
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
                popUpTo(Home)
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