package com.github.garynasser.correction_notebook.data.local

import android.content.Context
import androidx.datastore.core.IOException
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.garynasser.correction_notebook.data.model.home.PomodoroSettings
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class StudyPreferencesRecoveryTest {
    @Test fun corruptedSettingsAreBackedUpAndNewValuesSurviveANewStore() = withFiles { directory ->
        val file = File(directory, "study.preferences_pb")
        val corrupt = ByteArray(109)
        file.writeBytes(corrupt)
        withStore(file) { manager ->
            assertEquals(PomodoroSettings(), manager.pomodoroSettings.first())
            assertTrue(manager.soundEnabled.first())
            assertTrue(manager.vibrationEnabled.first())
            assertArrayEquals(corrupt, backups(directory).single().readBytes())
            val initialValueObserved = CompletableDeferred<Unit>()
            val updates = async(start = CoroutineStart.UNDISPATCHED) {
                manager.soundEnabled.onEach { initialValueObserved.complete(Unit) }.take(2).toList()
            }
            initialValueObserved.await()
            manager.setSoundEnabled(false)
            assertEquals(listOf(true, false), updates.await())
            manager.setVibrationEnabled(false)
            manager.updatePomodoroSettings(PomodoroSettings(45, 8, 20, 6))
        }
        withStore(file) { manager ->
            assertFalse(manager.soundEnabled.first())
            assertFalse(manager.vibrationEnabled.first())
            assertEquals(PomodoroSettings(45, 8, 20, 6), manager.pomodoroSettings.first())
        }
        assertEquals(1, backups(directory).size)
        assertArrayEquals(corrupt, backups(directory).single().readBytes())
    }

    @Test fun healthySettingsKeepTheirValuesAndUnknownKeysWithoutABackup() = withFiles { directory ->
        val file = File(directory, "study.preferences_pb")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { file }
            val manager = StudyPreferencesManager(store)
            manager.updatePomodoroSettings(PomodoroSettings(30, 6, 18, 5))
            manager.setSoundEnabled(false)
            store.edit { it[stringPreferencesKey("future_setting")] = "preserved" }
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
        val scope2 = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val store = createStudyPreferencesDataStore(file, scope2)
            val manager = StudyPreferencesManager(store)
            assertEquals(PomodoroSettings(30, 6, 18, 5), manager.pomodoroSettings.first())
            assertFalse(manager.soundEnabled.first())
            manager.setVibrationEnabled(false)
            assertEquals("preserved", store.data.first()[stringPreferencesKey("future_setting")])
        } finally { scope2.coroutineContext[Job]!!.cancelAndJoin() }
        assertTrue(backups(directory).isEmpty())
    }

    @Test fun missingSettingsCreateDefaultsAndAllowSavingWithoutABackup() = withFiles { directory ->
        val file = File(directory, "study.preferences_pb")
        withStore(file) { manager ->
            assertEquals(PomodoroSettings(), manager.pomodoroSettings.first())
            manager.setSoundEnabled(false)
            assertFalse(manager.soundEnabled.first())
        }
        assertTrue(file.isFile)
        assertTrue(backups(directory).isEmpty())
    }

    @Test fun failureToPreserveCorruptBytesLeavesTheOriginalUntouchedAndCanBeRetried() = withFiles { directory ->
        val file = File(directory, "study.preferences_pb")
        val corrupt = ByteArray(109)
        file.writeBytes(corrupt)
        assertTrue(directory.setWritable(false, false))
        try {
            withStore(file) { manager ->
                assertTrue(manager.soundEnabled.first())
                expectIoFailure { manager.setSoundEnabled(false) }
                assertArrayEquals(corrupt, file.readBytes())
                assertTrue(backups(directory).isEmpty())
                assertTrue(directory.setWritable(true, true))
                manager.setSoundEnabled(false)
                assertFalse(manager.soundEnabled.first())
                assertArrayEquals(corrupt, backups(directory).single().readBytes())
            }
        } finally { directory.setWritable(true, true) }
    }

    @Test fun ordinaryReadErrorsDoNotTriggerRecoveryOrRemoveOtherData() = withFiles { directory ->
        val file = File(directory, "study.preferences_pb").apply { mkdir() }
        val unrelated = File(file, "keep.txt").apply { writeText("do not remove") }
        withStore(file) { manager ->
            assertTrue(manager.soundEnabled.first())
            expectIoFailure { manager.setSoundEnabled(false) }
        }
        assertTrue(file.isDirectory)
        assertEquals("do not remove", unrelated.readText())
        assertTrue(backups(directory).isEmpty())
    }

    private suspend fun expectIoFailure(action: suspend () -> Unit) {
        try {
            action()
            fail("Expected an IO failure")
        } catch (_: IOException) { }
    }

    private suspend fun <T> withStore(file: File, test: suspend CoroutineScope.(StudyPreferencesManager) -> T): T = coroutineScope {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            test(StudyPreferencesManager(createStudyPreferencesDataStore(file, scope)))
        } finally { scope.coroutineContext[Job]!!.cancelAndJoin() }
    }

    private fun withFiles(test: suspend (File) -> Unit) = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val directory = File(context.cacheDir, "qa-study-recovery-${UUID.randomUUID()}").apply { mkdirs() }
        try { withTimeout(15_000) { test(directory) } }
        finally { directory.deleteRecursively() }
    }

    private fun backups(directory: File) = directory.listFiles()!!.filter { it.name.startsWith("study-prefs-corrupt-") }
}
