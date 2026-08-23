package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.remote.ai.runCatchingCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Assert.assertTrue
import org.junit.Test

class CancellableResultTest {
    @Test
    fun regularExceptionBecomesFailureResult() = runBlocking {
        val result = runCatchingCancellable {
            throw IllegalStateException("bad request")
        }

        assertTrue(result.isFailure)
        assertEquals("bad request", result.exceptionOrNull()?.message)
    }

    @Test
    fun cancellationExceptionIsRethrown() {
        runBlocking {
            try {
                runCatchingCancellable {
                    throw CancellationException("cancelled")
                }
                fail("CancellationException should be rethrown")
            } catch (expected: CancellationException) {
                assertEquals("cancelled", expected.message)
            }
        }
    }
}
