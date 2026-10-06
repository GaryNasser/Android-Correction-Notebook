package com.github.garynasser.correction_notebook.ui.update

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import java.net.URI

internal fun validatedUpdateUrl(value: String): String? = runCatching {
    URI(value.trim()).takeIf {
        (it.scheme.equals("http", ignoreCase = true) || it.scheme.equals("https", ignoreCase = true)) &&
            !it.host.isNullOrBlank() && (it.port == -1 || it.port in 1..65535)
    }?.toASCIIString()
}.getOrNull()

internal fun openUpdateDownload(context: Context, downloadUrl: String): String? {
    if (downloadUrl.isBlank()) return "没有可用的下载地址"
    val url = validatedUpdateUrl(downloadUrl) ?: return "下载链接格式不正确"
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri().normalizeScheme()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        null
    } catch (_: ActivityNotFoundException) {
        "没有可打开下载链接的应用"
    } catch (_: IllegalArgumentException) {
        "下载链接格式不正确"
    } catch (_: SecurityException) {
        "无法打开下载链接"
    }
}
