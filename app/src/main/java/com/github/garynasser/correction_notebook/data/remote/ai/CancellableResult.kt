package com.github.garynasser.correction_notebook.data.remote.ai

import kotlinx.coroutines.CancellationException

internal suspend inline fun <T> runCatchingCancellable(block: suspend () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (throwable: Throwable) {
        Result.failure(throwable)
    }
}

internal inline fun <T> Result<T>.recoverCancellable(crossinline transform: (Throwable) -> T): Result<T> {
    return recoverCatching { throwable ->
        if (throwable is CancellationException) throw throwable
        transform(throwable)
    }
}
