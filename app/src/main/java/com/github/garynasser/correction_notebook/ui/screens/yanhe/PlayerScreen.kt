package com.github.garynasser.correction_notebook.ui.screens.yanhe

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SegmentedButtonDefaults.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.ui.PlayerView

@Composable
fun PlayerScreen(
    onBack: () -> Unit, // 增加返回按钮的 lambda 表达式
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val playState = viewModel.playState

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 1. 播放器
        AndroidView<PlayerView>(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = viewModel.player
                    useController = true // 建议开启控制条，方便用户操作
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(260.dp)
                .align(Alignment.TopCenter),
            update = { view ->
                view.player = viewModel.player
            }
        )

        // 2. 加载层 (转圈圈 + 百分比)
        if (playState is PlayState.Loading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "资源准备中... ${(viewModel.downloadProgress * 100).toInt()}%",
                        color = Color.White
                    )
                }
            }
        }

        // 3. 错误显示
        if (playState is PlayState.Error) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = playState.message,
                    color = Color.Red,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .statusBarsPadding() // 避免挡住状态栏
                .padding(top = 8.dp, start = 8.dp)
                .align(Alignment.TopStart) // 对齐到左上角
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack, // 使用 Material 自动镜像的返回图标
                contentDescription = "返回",
                tint = Color.White // 白色图标在黑色背景上更清晰
            )
        }
    }
}