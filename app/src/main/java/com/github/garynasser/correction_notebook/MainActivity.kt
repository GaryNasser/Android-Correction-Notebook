package com.github.garynasser.correction_notebook

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.navigation.compose.rememberNavController
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.garynasser.correction_notebook.data.remote.cas.CasChallengeCoordinator
import com.github.garynasser.correction_notebook.ui.components.CasChallengeDialog
import com.github.garynasser.correction_notebook.ui.navigation.NavGraph
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var casChallenges: CasChallengeCoordinator
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        setContent {
            CorrectionNotebookTheme {
                val navController = rememberNavController()

                NavGraph(
                    navController = navController
                )
                val prompt by casChallenges.prompt.collectAsStateWithLifecycle()
                prompt?.let { current ->
                    CasChallengeDialog(current,
                        onSubmit = { casChallenges.submit(current.id, it) },
                        onCancel = { casChallenges.cancel(current.id) })
                }
            }
        }
    }
}
