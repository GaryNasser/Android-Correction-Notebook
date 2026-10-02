package com.github.garynasser.correction_notebook.ui.screens.yanhe

import java.io.IOException
import kotlinx.coroutines.withTimeoutOrNull

internal suspend fun <T : Any> awaitYanheResource(
    timeoutMillis: Long,
    timeoutMessage: String,
    request: suspend () -> T
): T = withTimeoutOrNull(timeoutMillis) { request() }
    ?: throw IOException(timeoutMessage)
