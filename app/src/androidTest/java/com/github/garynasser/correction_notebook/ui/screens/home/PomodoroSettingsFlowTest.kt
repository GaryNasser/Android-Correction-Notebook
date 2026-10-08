package com.github.garynasser.correction_notebook.ui.screens.home

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Parcel
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.model.home.PomodoroSettings
import com.github.garynasser.correction_notebook.data.model.home.TimerState
import com.github.garynasser.correction_notebook.ui.components.LocalAiEnabled
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class PomodoroSettingsFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun lightRestoredSettingsStartOnlyAfterSavingAndIgnoreRepeatedClicks() = checkRestoredStart(false)
    @Test fun darkRestoredSettingsStartOnlyAfterSavingAndIgnoreRepeatedClicks() = checkRestoredStart(true)
    @Test fun lightMoreOptionsKeepSettingsAndEveryNoiseControlReachable() = checkMoreOptions(false)
    @Test fun darkMoreOptionsKeepSettingsAndEveryNoiseControlReachable() = checkMoreOptions(true)

    private fun checkMoreOptions(dark: Boolean) = withFixture { f ->
        withContext(Dispatchers.Main) {
            f.home.startPomodoro()
            f.home.selectMode(StudyMode.IMMERSIVE)
            f.home.timerManager.pause()
        }
        render(f, dark)
        compose.onNodeWithContentDescription("更多").performClick()
        listOf("无白噪音", "雨声", "海浪", "森林", "咖啡馆").forEach {
            compose.onNodeWithContentDescription(it).performScrollTo().assertIsDisplayed()
        }
        capture(if (dark) "dark-more-options" else "light-more-options")
        compose.onNodeWithText("番茄钟设置").performScrollTo().assertIsDisplayed().performClick()
        compose.onNodeWithText("学习时长").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("取消").assertIsDisplayed().performClick()
        compose.onNodeWithContentDescription("更多").assertIsDisplayed()
    }

    private fun checkRestoredStart(dark: Boolean) = withFixture { f ->
        var model = f.home
        val restoration = render(f, dark) { model }
        openFromHome()
        editValues()
        recreate(f)
        model = f.home
        restoration.emulateSavedInstanceStateRestore()
        assertDraft()
        assertTrue(f.home.uiState.value.startPomodoroAfterSettings)
        assertEquals(TimerState.Idle, f.home.timerManager.timerState.value)
        val gate = CompletableDeferred<Unit>().also { f.studyWrites.gate = it }
        compose.onNodeWithText("保存").performClick()
        f.await { f.studyWrites.attempts.get() == 1 && f.home.uiState.value.isSavingPomodoroSettings }
        compose.onNodeWithText("保存中").assertIsDisplayed().assertIsNotEnabled()
        compose.onNodeWithText("取消").assertIsNotEnabled()
        compose.onNodeWithContentDescription("学习时长").performScrollTo().assertIsNotEnabled()
        withContext(Dispatchers.Main) {
            repeat(3) { f.home.updatePomodoroSettings(EDITED) }
            f.home.hidePomodoroSettingsDialog()
        }
        assertEquals(1, f.studyWrites.attempts.get())
        assertTrue(f.home.uiState.value.showPomodoroSettingsDialog)
        assertEquals(TimerState.Idle, f.home.timerManager.timerState.value)
        gate.complete(Unit)
        f.await { !f.home.uiState.value.isSavingPomodoroSettings && f.home.uiState.value.selectedMode == StudyMode.IMMERSIVE }
        assertEquals(EDITED, f.studyPreferences.pomodoroSettings.first())
        val timer = f.home.timerManager.timerState.value as TimerState.Pomodoro
        assertEquals(EDITED, timer.state.settings)
        assertTrue(timer.state.isRunning)
        assertFalse(f.home.uiState.value.showPomodoroSettingsDialog)
        assertFalse(f.home.uiState.value.startPomodoroAfterSettings)
        compose.onNodeWithContentDescription("更多").assertIsDisplayed()
        capture(if (dark) "dark-started" else "light-started")
    }

    @Test fun failedSaveKeepsTheDraftAndDoesNotStartUntilTheRetrySucceeds() = withFixture { f ->
        render(f, true)
        openFromHome()
        editValues()
        f.studyWrites.fail = true
        compose.onNodeWithText("保存").performClick()
        f.await { !f.home.uiState.value.isSavingPomodoroSettings && f.home.uiState.value.pomodoroSettingsError != null }
        compose.onNodeWithText(ERROR).assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        assertDraft()
        assertEquals(TimerState.Idle, f.home.timerManager.timerState.value)
        assertEquals(PomodoroSettings(), f.studyPreferences.pomodoroSettings.first())
        compose.onNodeWithText(ERROR).performScrollTo()
        capture("dark-save-failure")
        f.studyWrites.fail = false
        compose.onNodeWithText("保存").performClick()
        f.await { !f.home.uiState.value.isSavingPomodoroSettings && f.home.uiState.value.selectedMode == StudyMode.IMMERSIVE }
        assertEquals(2, f.studyWrites.attempts.get())
        assertNull(f.home.uiState.value.pomodoroSettingsError)
        assertEquals(EDITED, f.studyPreferences.pomodoroSettings.first())
    }

    @Test fun cancellingARecoveredDraftClearsItsStartIntentAndDoesNotSave() = withFixture { f ->
        var model = f.home
        val restoration = render(f, false) { model }
        openFromHome()
        editValues()
        recreate(f)
        model = f.home
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("取消").performClick()
        compose.onNodeWithText("番茄钟设置").assertDoesNotExist()
        assertEquals(0, f.studyWrites.attempts.get())
        assertFalse(f.home.uiState.value.startPomodoroAfterSettings)
        assertEquals(TimerState.Idle, f.home.timerManager.timerState.value)
        withContext(Dispatchers.Main) { f.home.showPomodoroSettingsDialog() }
        compose.onNodeWithText("25 分钟").performScrollTo().assertIsDisplayed()
        editValues()
        compose.onNodeWithText("保存").performClick()
        f.await { !f.home.uiState.value.showPomodoroSettingsDialog && !f.home.uiState.value.isSavingPomodoroSettings }
        assertEquals(EDITED, f.studyPreferences.pomodoroSettings.first())
        assertEquals(TimerState.Idle, f.home.timerManager.timerState.value)
        assertNull(f.home.uiState.value.selectedMode)
    }

    @Test fun anInterruptedSaveRestoresTheDraftButNeverRetriesOrStartsAutomatically() = withFixture { f ->
        var model = f.home
        val restoration = render(f, false) { model }
        openFromHome()
        editValues()
        val gate = CompletableDeferred<Unit>().also { f.studyWrites.gate = it }
        compose.onNodeWithText("保存").performClick()
        f.await { f.studyWrites.attempts.get() == 1 && f.home.uiState.value.isSavingPomodoroSettings }
        recreate(f)
        model = f.home
        restoration.emulateSavedInstanceStateRestore()
        assertDraft()
        compose.onNodeWithText("保存").assertIsEnabled()
        assertNull(f.home.uiState.value.pomodoroSettingsError)
        assertEquals(1, f.studyWrites.attempts.get())
        assertEquals(TimerState.Idle, f.home.timerManager.timerState.value)
        gate.complete(Unit)
        assertEquals(PomodoroSettings(), f.studyPreferences.pomodoroSettings.first())
        compose.onNodeWithText("保存").performClick()
        f.await { !f.home.uiState.value.isSavingPomodoroSettings && f.home.uiState.value.selectedMode == StudyMode.IMMERSIVE }
        assertEquals(2, f.studyWrites.attempts.get())
        assertEquals(EDITED, f.studyPreferences.pomodoroSettings.first())
    }

    @Test fun immersiveSettingsRestoreAndRetryWithoutResettingTheCurrentTimer() = withFixture { f ->
        withContext(Dispatchers.Main) {
            f.home.startPomodoro()
            f.home.selectMode(StudyMode.IMMERSIVE)
            f.home.timerManager.pause()
        }
        var model = f.home
        val restoration = render(f, true) { model }
        compose.onNodeWithContentDescription("更多").performClick()
        compose.onNodeWithText("番茄钟设置").performScrollTo().assertIsDisplayed().performClick()
        editValues()
        val original = f.home.timerManager.timerState.value
        recreate(f)
        model = f.home
        restoration.emulateSavedInstanceStateRestore()
        assertDraft()
        assertEquals(original, f.home.timerManager.timerState.value)
        assertFalse(f.home.uiState.value.startPomodoroAfterSettings)
        f.studyWrites.fail = true
        compose.onNodeWithText("保存").performClick()
        f.await { !f.home.uiState.value.isSavingPomodoroSettings && f.home.uiState.value.pomodoroSettingsError != null }
        compose.onNodeWithText(ERROR).assertIsDisplayed()
        assertEquals(original, f.home.timerManager.timerState.value)
        f.studyWrites.fail = false
        compose.onNodeWithText("保存").performClick()
        f.await { !f.home.uiState.value.showPomodoroSettingsDialog && !f.home.uiState.value.isSavingPomodoroSettings }
        assertEquals(original, f.home.timerManager.timerState.value)
        assertEquals(EDITED, f.studyPreferences.pomodoroSettings.first())
        compose.onNodeWithContentDescription("更多").assertIsDisplayed()
    }

    private fun render(f: HomeFormSaveFailureTest.Fixture, dark: Boolean, model: () -> HomeViewModel = { f.home }) =
        StateRestorationTester(compose).also { restoration ->
            restoration.setContent {
                CompositionLocalProvider(LocalAiEnabled provides false,
                    LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                    CorrectionNotebookTheme(darkTheme = dark, dynamicColor = false) {
                        Surface(Modifier.fillMaxSize().safeDrawingPadding()) { HomeScreen(model(), f.statistics) }
                    }
                }
            }
        }

    private fun openFromHome() {
        compose.onNodeWithText("Study").performClick()
        compose.onNodeWithContentDescription("学习模式").performClick()
        compose.onNode(hasText("番茄钟") and hasAnyAncestor(isDialog())).performScrollTo().performClick()
        compose.onNodeWithText("番茄钟设置").assertIsDisplayed()
    }

    private fun editValues() {
        LABELS.zip(listOf(45f, 8f, 22f, 6f)).forEach { (label, value) ->
            compose.onNodeWithContentDescription(label).performScrollTo()
                .performSemanticsAction(SemanticsActions.SetProgress) { assertTrue(it(value)) }
        }
    }

    private fun assertDraft() {
        listOf("45 分钟", "8 分钟", "22 分钟", "6 轮").forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
        }
        compose.onNodeWithText("保存").assertIsDisplayed()
        compose.onNodeWithText("取消").assertIsDisplayed()
    }

    @SuppressLint("RestrictedApi")
    private suspend fun recreate(f: HomeFormSaveFailureTest.Fixture) {
        val original = f.home
        val handle = withContext(Dispatchers.Main) {
            val parcel = Parcel.obtain()
            try {
                parcel.writeBundle(f.homeSavedState.savedStateProvider().saveState())
                parcel.setDataPosition(0)
                SavedStateHandle.createHandle(parcel.readBundle(javaClass.classLoader), null)
            } finally { parcel.recycle() }
        }
        withContext(Dispatchers.Main) { f.store.clear() }
        original.viewModelScope.coroutineContext[Job]?.join()
        withContext(Dispatchers.Main) { f.create(handle) }
        f.await { !f.home.uiState.value.isRestoringStudySession && f.home.uiState.value.scheduleSections.size == 7 }
    }

    private fun withFixture(test: suspend (HomeFormSaveFailureTest.Fixture) -> Unit) = runBlocking {
        val f = HomeFormSaveFailureTest.Fixture()
        try {
            withContext(Dispatchers.Main) { f.create() }
            f.await { f.home.uiState.value.scheduleSections.size == 7 }
            withTimeout(30_000) { test(f) }
        } finally {
            withContext(NonCancellable) {
                f.studyWrites.gate?.complete(Unit)
                withContext(Dispatchers.Main) { f.store.clear() }
                f.scope.cancel()
                f.scope.coroutineContext[Job]?.join()
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
        android.os.SystemClock.sleep(350)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "qa-pomodoro-flow-$name.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    companion object {
        private val EDITED = PomodoroSettings(45, 8, 22, 6)
        private val LABELS = listOf("学习时长", "短休息", "长休息", "循环轮数")
        private const val ERROR = "番茄钟设置保存失败，请重试"
    }
}
