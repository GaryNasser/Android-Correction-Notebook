package com.github.garynasser.correction_notebook.data.remote.network

import java.io.IOException
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response

// Consume the response here so cancellation cannot leak a body handed to another coroutine.
internal suspend fun <T> Call.awaitResponse(read: (Response) -> T): T = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel() }
    enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            continuation.resumeWith(Result.failure(e))
        }

        override fun onResponse(call: Call, response: Response) {
            if (!continuation.isActive) {
                response.close()
                return
            }
            continuation.resumeWith(runCatching { response.use(read) })
        }
    })
}
