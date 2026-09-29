package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.repository.authenticateBeforeCommit
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class YanheAuthenticationCommitTest {
    @Test
    fun successfulAuthenticationCommitsTheNewToken() = runBlocking {
        var committedToken: String? = null

        val result = authenticateBeforeCommit(
            requestToken = { "new-token" },
            commit = { committedToken = it }
        )

        assertTrue(result.isSuccess)
        assertEquals("new-token", committedToken)
    }

    @Test
    fun failedAuthenticationKeepsTheExistingSessionUntouched() = runBlocking {
        var commitCalled = false

        val result = authenticateBeforeCommit(
            requestToken = { throw IllegalArgumentException("密码错误") },
            commit = { commitCalled = true }
        )

        assertTrue(result.isFailure)
        assertFalse(commitCalled)
    }
}
