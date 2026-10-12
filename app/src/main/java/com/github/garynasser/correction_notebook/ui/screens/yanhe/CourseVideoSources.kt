package com.github.garynasser.correction_notebook.ui.screens.yanhe

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

internal fun originalScreenVideoUrl(cameraUrl: String?, screenUrl: String?): String? {
    val camera = cameraUrl?.toHttpUrlOrNull() ?: return null
    val screen = screenUrl?.toHttpUrlOrNull() ?: return null
    val sameOrigin = listOf(camera, screen).all {
        it.scheme == "https" && it.host == "aita.yanhekt.cn" && it.port == 443 &&
            it.username.isEmpty() && it.password.isEmpty()
    }
    if (!sameOrigin || !screen.encodedPath.startsWith("/play/frim_files/ppt") ||
        !camera.encodedPath.matches(Regex("/play/\\d+/vod\\d+/\\d{4}/\\d{2}/\\d{2}/\\d+/\\d+/Video1/Video1\\.m3u8"))) return null
    return camera.resolve("../VGA/VGA.m3u8")?.newBuilder()?.query(null)?.build()?.toString()
}
