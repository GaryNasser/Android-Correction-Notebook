package com.github.garynasser.correction_notebook.ui.screens.yanhe

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.ui.PlayerView

@Composable
fun PlayerScreen(
    onBack: () -> Unit,
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val playState = viewModel.playState
    val controller by viewModel.controller // 监听 MediaController 的变化
    val lifecycleOwner = LocalLifecycleOwner.current

    // 生命周期管理：当用户切离界面时自动暂停，回来时尝试播放
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> controller?.pause()
                Lifecycle.Event.ON_RESUME -> controller?.play()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (controller != null) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = controller
                        useController = true
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f) // 保持视频比例，或者使用 fillMaxSize()
                    .align(Alignment.Center),
                update = { view ->
                    view.player = controller
                }
            )
        } else {
            PlayerPlaceholder(modifier = Modifier.align(Alignment.Center))
        }

        if (playState is PlayState.Loading) {
            PlayerLoadingOverlay(modifier = Modifier.fillMaxSize())
        }

        if (playState is PlayState.Error) {
            PlayerErrorOverlay(
                message = playState.message,
                onRetry = viewModel::retryPlayback,
                modifier = Modifier.fillMaxSize()
            )
        }

        Surface(
            modifier = Modifier
                .statusBarsPadding()
                .padding(top = 8.dp, start = 8.dp)
                .align(Alignment.TopStart),
            shape = CircleShape,
            color = Color.Black.copy(alpha = 0.44f)
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun PlayerPlaceholder(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        color = Color(0xFF151515)
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun PlayerLoadingOverlay(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(Color.Black.copy(alpha = 0.56f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = Color.Black.copy(alpha = 0.62f),
            shape = RoundedCornerShape(8.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
                Text("流媒体缓冲中...", color = Color.White)
            }
        }
    }
}

@Composable
private fun PlayerErrorOverlay(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(Color.Black.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            color = Color(0xFF181818),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.16f))
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFFFD2D2), modifier = Modifier.size(30.dp))
                Text("播放失败", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = message,
                    color = Color(0xFFC9C9C9),
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )
                Button(onClick = onRetry, shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("重试")
                }
            }
        }
    }
}
