package com.github.garynasser.correction_notebook.data.repository

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.github.garynasser.correction_notebook.data.local.CredentialManager
import com.github.garynasser.correction_notebook.data.model.auth.UserCredential
import com.github.garynasser.correction_notebook.data.model.home.ScheduleEvent
import com.github.garynasser.correction_notebook.data.model.home.ScheduleSourceType
import com.github.garynasser.correction_notebook.data.model.school.SchoolScheduleException
import com.github.garynasser.correction_notebook.data.remote.cas.BitCasClient
import com.github.garynasser.correction_notebook.data.remote.school.SchoolScheduleRemoteDataSource
import com.github.garynasser.correction_notebook.data.repository.school.SchoolScheduleMapper
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.util.UUID

class SchoolScheduleSyncTest {
    @Test fun anUnparseableCourseCannotReplaceTheCompleteLocalSchedule() = withFixture { fixture ->
        val invalidRows = listOf(
            """{"KCMC":"Missing weekday","JC":"3-4","ZC":"1-2"}""",
            """{"KCMC":"Missing sections","XQ":"2","ZC":"1-2"}""",
            """{"KCMC":"Missing weeks","XQ":"2","JC":"3-4"}""",
            """{"KCMC":" ","XQ":"2","JC":"3-4","ZC":"1-2"}""",
            "null"
        )
        for (row in invalidRows) {
            fixture.rows = "$VALID_ROW,$row"
            fixture.assertRejectedWithoutChanges()
        }
        fixture.rows = VALID_ROW
        fixture.syncAndAssertComplete()
    }

    @Test fun anInvalidCourseTimeCannotReplaceTheCompleteLocalSchedule() = withFixture { fixture ->
        val invalidRows = listOf(
            """{"KCMC":"Unsupported section","XQ":"2","JC":"14-15","ZC":"1-2"}""",
            """{"KCMC":"Reversed section","XQ":"2","JC":"4-3","ZC":"1-2"}""",
            """{"KCMC":"Invalid weekday","XQ":"8","JC":"3-4","ZC":"1-2"}""",
            """{"KCMC":"Negative weekday","XQ":"-1","JC":"3-4","ZC":"1-2"}""",
            """{"KCMC":"Invalid week","XQ":"2","JC":"3-4","ZC":"0,1"}"""
        )
        for (row in invalidRows) {
            fixture.rows = "$VALID_ROW,$row"
            fixture.assertRejectedWithoutChanges()
        }
        fixture.rows = VALID_ROW
        fixture.syncAndAssertComplete()
    }

    @Test fun repeatedSyncUpdatesOnlyTheSelectedSchoolTerm() = withFixture { fixture ->
        val beforeCount = fixture.schedule.scheduleEvents.first().size
        fixture.rows = VALID_ROW
        fixture.syncAndAssertComplete()
        fixture.rows = VALID_ROW.replace("Matrix analysis", "Updated matrix analysis")
        fixture.syncAndAssertComplete(expectedTitle = "Updated matrix analysis")
        assertEquals(beforeCount, fixture.schedule.scheduleEvents.first().size)
    }

    private fun withFixture(block: suspend (Fixture) -> Unit) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val credentials = CredentialManager(context)
        val previousCredential = credentials.getCredentials()
        val fixture = Fixture(ScheduleRepository(context), credentials)
        val previousEvents = fixture.schedule.scheduleEvents.first()
        try {
            credentials.saveCredentials(UserCredential("qa-student", "qa-password"))
            fixture.originals.forEach { fixture.schedule.addEvent(it) }
            block(fixture)
        } finally {
            fixture.schedule.scheduleEvents.first().filter { event ->
                event.sourceCalendarId == ScheduleRepository.schoolCalendarId(fixture.termId) ||
                    event.id in fixture.originals.map { it.id }
            }.forEach { fixture.schedule.deleteEvent(it.id) }
            if (previousCredential == null) credentials.removeCredentials() else credentials.saveCredentials(previousCredential)
            fixture.client.dispatcher.executorService.shutdown()
            fixture.client.connectionPool.evictAll()
            assertEquals(previousEvents.toSet(), fixture.schedule.scheduleEvents.first().toSet())
        }
    }

    private class Fixture(val schedule: ScheduleRepository, credentials: CredentialManager) {
        val termId = "qa_${UUID.randomUUID()}"
        var rows = VALID_ROW
        val client = OkHttpClient.Builder().addInterceptor { chain ->
            val request = chain.request()
            val body = when (request.url.encodedPath) {
                "/cas/v1/tickets" -> "<form action='/cas/v1/tickets/TGT-qa'></form>"
                "/cas/v1/tickets/TGT-qa" -> "ST-qa"
                "/jwapp/sys/wdkbby/*default/index.do" -> "QA callback"
                "/jwapp/sys/wdkbby/modules/jshkcb/dqxnxq.do" ->
                    """{"dqxnxq":{"XNXQDM":"$termId","XNXQMC":"QA term","KSRQ":"2026-09-07"}}"""
                "/jwapp/sys/wdkbby/modules/xskcb/cxxszhxqkb.do" -> {
                    val form = request.body as FormBody
                    assertEquals(termId, form.value(0))
                    """{"cxxszhxqkb":[$rows]}"""
                }
                else -> throw AssertionError("No real school request is allowed: ${request.url}")
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                .body(body.toResponseBody()).build()
        }.build()
        val repository = SchoolScheduleRepository(credentials,
            SchoolScheduleRemoteDataSource(BitCasClient(client), client), schedule, SchoolScheduleMapper())
        private val start = LocalDateTime.of(2026, 9, 7, 8, 0)
        val originals = listOf(
            ScheduleEvent(title = "Old school course", startAt = start, endAt = start.plusHours(1),
                sourceType = ScheduleSourceType.SCHOOL_IMPORT, sourceCalendarId = ScheduleRepository.schoolCalendarId(termId)),
            ScheduleEvent(title = "Other term", startAt = start, endAt = start.plusHours(1),
                sourceType = ScheduleSourceType.SCHOOL_IMPORT, sourceCalendarId = "${termId}_other"),
            ScheduleEvent(title = "Manual event", startAt = start, endAt = start.plusHours(1)),
            ScheduleEvent(title = "ICS course", startAt = start, endAt = start.plusHours(1),
                sourceType = ScheduleSourceType.ICS_IMPORT, sourceCalendarId = "${termId}_ics")
        )

        suspend fun assertRejectedWithoutChanges() {
            val before = schedule.scheduleEvents.first()
            val failure = try { repository.syncCurrentTerm(); null } catch (error: SchoolScheduleException) { error }
            assertTrue("Incomplete school response must fail instead of silently replacing local courses: $rows", failure != null)
            assertTrue(failure!!.message.orEmpty().contains("已保留本地"))
            assertEquals(before.toSet(), schedule.scheduleEvents.first().toSet())
        }

        suspend fun syncAndAssertComplete(expectedTitle: String = "Matrix analysis") {
            val result = repository.syncCurrentTerm()
            assertEquals(1, result.importedCount)
            val stored = schedule.scheduleEvents.first()
            val imported = stored.single { it.sourceCalendarId == ScheduleRepository.schoolCalendarId(termId) }
            assertEquals(expectedTitle, imported.title)
            assertEquals(LocalDateTime.of(2026, 9, 8, 9, 55), imported.startAt)
            assertEquals(LocalDateTime.of(2026, 9, 8, 11, 30), imported.endAt)
            assertEquals("Building A 101", imported.location)
            originals.drop(1).forEach { assertEquals(it, schedule.getEventById(it.id)) }
        }
    }

    companion object {
        private const val VALID_ROW = """{"KCMC":"Matrix analysis","XQ":"2","JC":"3-4","ZC":"1","JASMC":"Building A 101"}"""
    }
}
