package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.remote.ai.AiErrorMapper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiErrorMapperTest {
    @Test
    fun plainTextUnauthorizedResponseIsMappedToApiKeyError() {
        val message = AiErrorMapper.mapHttpError(401, "Authentication Fails (governor)")

        assertTrue(message.contains("API Key"))
        assertTrue(message.contains("Authentication Fails"))
        assertFalse(message.contains("接口返回异常响应"))
    }
}
