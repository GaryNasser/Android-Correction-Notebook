package com.github.garynasser.correction_notebook.ui.navigation

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.github.garynasser.correction_notebook.ui.screens.main.MainViewModel
import com.github.garynasser.correction_notebook.MainContainer
import com.github.garynasser.correction_notebook.data.model.auth.AuthEvent
import com.github.garynasser.correction_notebook.ui.screens.register.CasScreen
import com.github.garynasser.correction_notebook.ui.screens.register.RegistrationViewModel


@Composable
fun NavGraph(
    navController: NavHostController,
    mainViewModel: MainViewModel = hiltViewModel(),
) {
    val authStateManager = mainViewModel.authStateManager
    val aiSettingsManager = mainViewModel.aiSettingsManager

    NavHost(
        navController = navController,
        startDestination = Home
    ) {
        composable<Home> {
            MainContainer(
                aiSettingsManager = aiSettingsManager,
                outerNavController = navController
            )
        }

        composable<CasAuth> { backStackEntry ->
            val authViewModel: RegistrationViewModel = hiltViewModel(backStackEntry)

            CasScreen(
                viewModel = authViewModel,
                onBackButtonClick = { navController.popBackStack() },
                onConfirm = {
                    authViewModel.submitYanheLogin(
                        onSuccess = { navController.popBackStack() }
                    )
                }
            )
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
