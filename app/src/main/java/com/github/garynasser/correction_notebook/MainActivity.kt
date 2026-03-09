package com.github.garynasser.correction_notebook

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme // 注意这里根据你的项目名可能会有变化

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            // 确保这里调用的是你 ui.theme 包里的主题名
            CorrectionNotebookTheme {
                MainContainer()
            }
        }
    }
}