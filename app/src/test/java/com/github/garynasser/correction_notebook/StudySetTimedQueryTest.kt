package com.github.garynasser.correction_notebook

import com.github.garynasser.correction_notebook.data.repository.observeTimedQuery
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Test

class StudySetTimedQueryTest {
    @Test
    fun timedQueryRefreshesItsTimeBoundary() = runBlocking {
        var now = 100L

        val observed = withTimeout(1_000L) {
            observeTimedQuery(
                refreshIntervalMillis = 1L,
                currentTimeMillis = { now++ },
                query = { queryTime -> flowOf(queryTime) }
            ).take(3).toList()
        }

        assertEquals(listOf(100L, 101L, 102L), observed)
    }
}
