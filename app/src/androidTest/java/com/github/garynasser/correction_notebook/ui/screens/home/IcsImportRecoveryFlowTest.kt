package com.github.garynasser.correction_notebook.ui.screens.home

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.core.content.FileProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.model.home.ImportDecision
import com.github.garynasser.correction_notebook.data.model.home.ScheduleEvent
import com.github.garynasser.correction_notebook.ui.components.LocalAiEnabled
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.time.LocalDate
import java.util.UUID

class IcsImportRecoveryFlowTest {
    @get:Rule val compose = createComposeRule()
    private val date = LocalDate.of(2026, 10, 8)

    @Test fun failedMergeKeepsThePreviewAndRetriesWithoutReadingTheFileAgain() = withFixture { f, file ->
        preview(f, file)
        render(f)
        val original = f.home.uiState.value.pendingIcsPreview
        f.scheduleWrites.fail = true
        compose.onNodeWithText("合并").performClick()
        f.await { f.scheduleWrites.attempts.get() == 1 && !f.home.uiState.value.isImportingSchedule }
        assertSame("A failed write must retain the reviewed import", original, f.home.uiState.value.pendingIcsPreview)
        assertNull(f.home.uiState.value.scheduleImportError)
        assertNull(f.home.uiState.value.scheduleImportMessage)
        compose.onNodeWithText("导入预览").assertIsDisplayed()
        compose.onNodeWithText("日程保存失败，请稍后再试").assertIsDisplayed()
        compose.onAllNodes(isDialog()).assertCountEquals(1)
        assertTrue(f.schedules.scheduleEvents.first().isEmpty())
        capture("merge-failure")
        f.scheduleWrites.fail = false
        val release = CompletableDeferred<Unit>().also { f.scheduleWrites.gate = it }
        compose.onNodeWithText("合并").performClick()
        f.await { f.scheduleWrites.attempts.get() == 2 && f.home.uiState.value.isImportingSchedule }
        assertNull(f.home.uiState.value.icsImportApplyError)
        compose.onNodeWithText("日程保存失败，请稍后再试").assertDoesNotExist()
        listOf("导入中", "覆盖", "取消").forEach { compose.onNodeWithText(it).assertIsNotEnabled() }
        release.complete(Unit)
        f.await { f.home.uiState.value.pendingIcsPreview == null && !f.home.uiState.value.isImportingSchedule }
        assertEquals(original!!.incomingEvents, f.schedules.scheduleEvents.first())
        assertEquals(2, f.scheduleWrites.attempts.get())
        compose.onNodeWithText("课表导入完成").assertIsDisplayed()
        compose.onNodeWithText("日程保存失败，请稍后再试").assertDoesNotExist()
        f.await { f.home.uiState.value.scheduleSections.flatMap { it.items }.size == 1 }
    }

    @Test fun failedOverwriteLeavesExistingEventsIntactAndRetryOnlyReplacesThatCalendar() = withFixture { f, file ->
        preview(f, file)
        val original = f.home.uiState.value.pendingIcsPreview!!
        val old = original.incomingEvents.single().copy(id = "old-import", title = "旧课程", sourceEventUid = "old-uid")
        val unrelated = ScheduleEvent(title = "其他日程", startAt = date.atTime(13, 20), endAt = date.atTime(14, 55))
        f.schedules.applyImportPreview(original.copy(incomingEvents = listOf(old)), ImportDecision.MERGE)
        f.schedules.addEvent(unrelated)
        val writes = f.scheduleWrites.attempts.get()
        f.scheduleWrites.fail = true
        withContext(Dispatchers.Main) { f.home.applyIcsPreview(ImportDecision.OVERWRITE) }
        f.await { f.scheduleWrites.attempts.get() == writes + 1 && !f.home.uiState.value.isImportingSchedule }
        assertSame(original, f.home.uiState.value.pendingIcsPreview)
        assertEquals(setOf(old, unrelated), f.schedules.scheduleEvents.first().toSet())
        f.scheduleWrites.fail = false
        withContext(Dispatchers.Main) { f.home.applyIcsPreview(ImportDecision.OVERWRITE) }
        f.await { f.home.uiState.value.pendingIcsPreview == null && !f.home.uiState.value.isImportingSchedule }
        assertEquals((original.incomingEvents + unrelated).toSet(), f.schedules.scheduleEvents.first().toSet())
        assertEquals(writes + 2, f.scheduleWrites.attempts.get())
    }

    @Test fun applyingCannotDismissThePreviewOrSubmitAnotherImport() = withFixture { f, file ->
        preview(f, file)
        render(f)
        val original = f.home.uiState.value.pendingIcsPreview
        val release = CompletableDeferred<Unit>().also { f.scheduleWrites.gate = it }
        compose.onNodeWithText("合并").performClick()
        f.await { f.scheduleWrites.attempts.get() == 1 }
        listOf("导入中", "覆盖", "取消").forEach { compose.onNodeWithText(it).assertIsNotEnabled() }
        withContext(Dispatchers.Main) {
            f.home.dismissIcsPreview()
            f.home.applyIcsPreview(ImportDecision.OVERWRITE)
            f.home.syncSchoolSchedule()
        }
        assertSame(original, f.home.uiState.value.pendingIcsPreview)
        assertEquals(1, f.scheduleWrites.attempts.get())
        release.complete(Unit)
        f.await { f.home.uiState.value.pendingIcsPreview == null && !f.home.uiState.value.isImportingSchedule }
        assertEquals(original!!.incomingEvents, f.schedules.scheduleEvents.first())
        withContext(Dispatchers.Main) { f.home.applyIcsPreview(ImportDecision.MERGE) }
        assertEquals(1, f.scheduleWrites.attempts.get())
    }

    @Test fun cancellingAFailedPreviewDoesNotLeaveAnErrorDialogOrWriteAnything() = withFixture { f, file ->
        preview(f, file)
        render(f)
        f.scheduleWrites.fail = true
        compose.onNodeWithText("合并").performClick()
        f.await { f.scheduleWrites.attempts.get() == 1 && !f.home.uiState.value.isImportingSchedule }
        compose.onNodeWithText("取消").performClick()
        assertNull(f.home.uiState.value.pendingIcsPreview)
        assertNull(f.home.uiState.value.icsImportApplyError)
        compose.onNodeWithText("导入预览").assertDoesNotExist()
        compose.onNodeWithText("课表导入失败").assertDoesNotExist()
        compose.onNodeWithText("日程保存失败，请稍后再试").assertDoesNotExist()
        assertEquals(1, f.scheduleWrites.attempts.get())
        assertTrue(f.schedules.scheduleEvents.first().isEmpty())
    }

    @Test fun cancelledWriteReleasesTheBusyFlagAndCanBeRetried() = withFixture { f, file ->
        preview(f, file)
        val original = f.home.uiState.value.pendingIcsPreview
        val release = CompletableDeferred<Unit>().also { f.scheduleWrites.gate = it }
        withContext(Dispatchers.Main) { f.home.applyIcsPreview(ImportDecision.MERGE) }
        f.await { f.scheduleWrites.attempts.get() == 1 }
        release.completeExceptionally(CancellationException("Cancelled write"))
        f.await { !f.home.uiState.value.isImportingSchedule }
        assertSame(original, f.home.uiState.value.pendingIcsPreview)
        assertTrue(f.schedules.scheduleEvents.first().isEmpty())
        f.scheduleWrites.gate = null
        withContext(Dispatchers.Main) { f.home.applyIcsPreview(ImportDecision.MERGE) }
        f.await { f.home.uiState.value.pendingIcsPreview == null && !f.home.uiState.value.isImportingSchedule }
        assertEquals(original!!.incomingEvents, f.schedules.scheduleEvents.first())
    }

    @Test fun rebuildingTheCompositionKeepsTheFailedPreviewReadyForRetry() = withFixture { f, file ->
        preview(f, file)
        val restoration = render(f)
        f.scheduleWrites.fail = true
        compose.onNodeWithText("合并").performClick()
        f.await { f.scheduleWrites.attempts.get() == 1 && !f.home.uiState.value.isImportingSchedule }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("导入预览").assertIsDisplayed()
        compose.onNodeWithText("日程保存失败，请稍后再试").assertIsDisplayed()
        listOf("合并", "覆盖", "取消").forEach { compose.onNodeWithText(it).assertIsEnabled().assertIsDisplayed() }
        assertEquals(1, f.scheduleWrites.attempts.get())
    }

    private suspend fun preview(f: HomeFormSaveFailureTest.Fixture, file: File) {
        file.writeText(listOf("BEGIN:VCALENDAR", "VERSION:2.0", "X-WR-CALNAME:${file.name}", "BEGIN:VEVENT", "UID:course",
            "DTSTART:20261008T080000", "DTEND:20261008T093500", "SUMMARY:计算理论与算法分析设计",
            "LOCATION:文萃楼 M134\\n良乡校区", "DESCRIPTION:带上课堂笔记\\n核对证明步骤", "END:VEVENT", "END:VCALENDAR").joinToString("\r\n"))
        val uri = FileProvider.getUriForFile(f.context, "${f.context.packageName}.fileprovider", file)
        withContext(Dispatchers.Main) { f.home.setSelectedWeek(date); f.home.importIcs(uri) }
        f.await { f.home.uiState.value.pendingIcsPreview != null && !f.home.uiState.value.isImportingSchedule }
        assertEquals("文萃楼 M134\n良乡校区", f.home.uiState.value.pendingIcsPreview!!.incomingEvents.single().location)
        assertTrue(file.delete())
    }

    private fun render(f: HomeFormSaveFailureTest.Fixture) = StateRestorationTester(compose).also { it.setContent {
        CompositionLocalProvider(LocalAiEnabled provides false, LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
            CorrectionNotebookTheme(dynamicColor = false) { HomeScreen(f.home, f.statistics) }
        }
    } }

    private fun withFixture(test: suspend (HomeFormSaveFailureTest.Fixture, File) -> Unit) = runBlocking {
        val f = HomeFormSaveFailureTest.Fixture()
        val file = File(File(f.context.filesDir, "knowledge_base").apply { mkdirs() }, "qa-import-${UUID.randomUUID()}.ics")
        try {
            withContext(Dispatchers.Main) { f.create() }
            f.await { f.home.uiState.value.scheduleSections.size == 7 }
            withTimeout(30_000) { test(f, file) }
        } finally {
            withContext(NonCancellable) {
                withContext(Dispatchers.Main) { f.store.clear() }
                f.scope.cancel()
                f.scope.coroutineContext[Job]?.join()
                file.delete()
                f.files.forEach { it.delete() }
                f.database.close()
                f.knowledge.close()
                f.network.dispatcher.executorService.shutdownNow()
                f.network.connectionPool.evictAll()
            }
        }
    }

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "qa-ics-$name.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }
}
