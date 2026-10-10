package com.github.garynasser.correction_notebook.data.remote.cas

import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

enum class CasCodeKind { CAPTCHA, SMS }

class CasCodePrompt(
    val kind: CasCodeKind,
    val maskedPhone: String = "",
    val image: ByteArray? = null,
    val error: String? = null,
    val id: String = UUID.randomUUID().toString(),
) {
    internal val answer = CompletableDeferred<String>()
}

@Singleton
class CasChallengeCoordinator @Inject constructor() {
    private val mutex = Mutex()
    private val generationLock = Any()
    @Volatile internal var generation = 0L
        private set
    private val mutablePrompt = MutableStateFlow<CasCodePrompt?>(null)
    val prompt = mutablePrompt.asStateFlow()

    suspend fun requestCode(prompt: CasCodePrompt, expectedGeneration: Long = generation): String = mutex.withLock {
        synchronized(generationLock) {
            if (expectedGeneration != generation) throw CancellationException("统一认证会话已变更")
            mutablePrompt.value = prompt
        }
        try {
            withTimeoutOrNull(180_000) { prompt.answer.await() }
                ?: throw CasAuthException("验证码输入已超时，请重新登录")
        } finally {
            if (mutablePrompt.value === prompt) mutablePrompt.value = null
        }
    }

    fun submit(id: String, code: String) {
        val current = mutablePrompt.value ?: return
        if (current.id == id && code.trim().isNotEmpty()) current.answer.complete(code.trim())
    }

    fun cancel(id: String) {
        val current = mutablePrompt.value ?: return
        if (current.id == id) current.answer.completeExceptionally(CasAuthException("已取消统一认证"))
    }

    internal fun cancelPending() {
        synchronized(generationLock) {
            generation++
            mutablePrompt.value?.let { cancel(it.id) }
        }
    }
}
