package com.github.garynasser.correction_notebook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import com.github.garynasser.correction_notebook.ui.navigation.NavGraph
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CorrectionNotebookTheme {
                val navController = rememberNavController()

                NavGraph(
                    navController = navController
                )
            }
        }
    }
}
