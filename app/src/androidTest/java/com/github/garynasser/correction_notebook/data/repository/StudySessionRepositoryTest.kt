package com.github.garynasser.correction_notebook.data.repository

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.garynasser.correction_notebook.data.model.home.SessionType
import com.github.garynasser.correction_notebook.data.model.home.StudySession
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@RunWith(AndroidJUnit4::class)
class StudySessionRepositoryTest {
    @Test
    fun retryingTheSameSessionCannotDuplicateOrChangeAnAlreadySavedRecord() = runBlocking {
        val repository = StudySessionRepository(ApplicationProvider.getApplicationContext())
        val session = StudySession(
            subject = "Timer retry fixture",
            startTime = LocalDateTime.of(2000, 1, 1, 8, 0),
            durationMinutes = 5,
            sessionType = SessionType.STOPWATCH
        )
        repository.addSession(session)
        coroutineScope {
            repeat(8) { launch { repository.addSession(session.copy(durationMinutes = 10)) } }
        }
        val stored = repository.sessions.first().filter { it.id == session.id }
        assertEquals(listOf(session), stored)
        val nextSession = session.copy(id = java.util.UUID.randomUUID().toString(), durationMinutes = 3)
        repository.addSession(nextSession)
        assertEquals(nextSession, repository.sessions.first().single { it.id == nextSession.id })
    }
}
