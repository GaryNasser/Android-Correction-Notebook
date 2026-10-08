package com.github.garynasser.correction_notebook.ui.screens.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Parcel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.model.home.PomodoroSettings
import com.github.garynasser.correction_notebook.ui.components.LocalAiEnabled
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.io.File

class AlertSettingsFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun aFailedSoundSaveKeepsTheSettingAndAllowsRetry() = withFixture { f ->
        render(f)
        compose.onNodeWithContentDescription("更多").performClick()
        f.studyWrites.fail = true
        compose.onAllNodes(isToggleable())[0].performClick()
        f.await { !f.home.uiState.value.isSavingSoundSetting && f.home.uiState.value.soundSettingError != null }
        compose.onNodeWithText("提醒声音保存失败，请重试").assertIsDisplayed()
        compose.onAllNodes(isToggleable())[0].assertIsOn().assertIsEnabled()
        assertTrue(f.studyPreferences.soundEnabled.first())
        f.studyWrites.fail = false
        compose.onAllNodes(isToggleable())[0].performClick()
        f.await { !f.home.uiState.value.soundEnabled }
        compose.onAllNodes(isToggleable())[0].assertIsOff()
        assertFalse(f.studyPreferences.soundEnabled.first())
        compose.onNodeWithText("提醒声音保存失败，请重试").assertDoesNotExist()
    }

    @Test fun aFailedVibrationSaveKeepsTheSettingAndAllowsRetry() = withFixture { f ->
        render(f, dark = true)
        compose.onNodeWithContentDescription("更多").performClick()
        f.studyWrites.fail = true
        control("振动提醒").performClick()
        f.await { !f.home.uiState.value.isSavingVibrationSetting && f.home.uiState.value.vibrationSettingError != null }
        compose.onNodeWithText(VIBRATION_ERROR).performScrollTo().assertIsDisplayed()
        control("振动提醒").assertIsOn().assertIsEnabled()
        assertTrue(f.studyPreferences.vibrationEnabled.first())
        f.studyWrites.fail = false
        control("振动提醒").performClick()
        f.await { !f.home.uiState.value.vibrationEnabled && !f.home.uiState.value.isSavingVibrationSetting }
        control("振动提醒").assertIsOff()
        assertFalse(f.studyPreferences.vibrationEnabled.first())
        compose.onNodeWithText(VIBRATION_ERROR).assertDoesNotExist()
    }

    @Test fun lightIndependentErrorsRemainVisibleUntilEachSettingIsRetried() = checkErrors(false)
    @Test fun darkIndependentErrorsRemainVisibleUntilEachSettingIsRetried() = checkErrors(true)
    @Test fun lightModeChooserUsesTheCurrentSavedPomodoroSettings() = checkModeSettings(false)
    @Test fun darkModeChooserUsesTheCurrentSavedPomodoroSettings() = checkModeSettings(true)

    private fun checkErrors(dark: Boolean) = withFixture { f ->
        render(f, dark)
        val timer = f.home.timerManager.timerState.value
        compose.onNodeWithContentDescription("更多").performClick()
        f.studyWrites.fail = true
        control("提醒声音").performClick()
        f.await { !f.home.uiState.value.isSavingSoundSetting && f.home.uiState.value.soundSettingError != null }
        control("振动提醒").performClick()
        f.await { !f.home.uiState.value.isSavingVibrationSetting && f.home.uiState.value.vibrationSettingError != null }
        listOf(SOUND_ERROR, VIBRATION_ERROR).forEach {
            compose.onNodeWithText(it).performScrollTo().assertIsDisplayed()
                .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        }
        control("提醒声音").performScrollTo().assertIsOn().assertIsEnabled().assertHeightIsAtLeast(48.dp)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
        control("振动提醒").assertIsOn().assertIsEnabled().assertHeightIsAtLeast(48.dp)
        capture(if (dark) "dark-failures" else "light-failures")
        listOf("无白噪音", "雨声", "海浪", "森林", "咖啡馆").forEach {
            compose.onNodeWithContentDescription(it).performScrollTo().assertIsDisplayed()
        }
        f.studyWrites.fail = false
        control("提醒声音").performScrollTo().performClick()
        f.await { !f.home.uiState.value.soundEnabled && !f.home.uiState.value.isSavingSoundSetting }
        compose.onNodeWithText(SOUND_ERROR).assertDoesNotExist()
        compose.onNodeWithText(VIBRATION_ERROR).assertExists()
        assertTrue(f.studyPreferences.vibrationEnabled.first())
        control("振动提醒").performClick()
        f.await { !f.home.uiState.value.vibrationEnabled && !f.home.uiState.value.isSavingVibrationSetting }
        compose.onNodeWithText(VIBRATION_ERROR).assertDoesNotExist()
        assertEquals(timer, f.home.timerManager.timerState.value)
        assertEquals(4, f.studyWrites.attempts.get())
    }

    @Test fun pendingWritesDisableOnlyTheirOwnControlAndIgnoreRepeatedRequests() = withFixture { f ->
        render(f)
        compose.onNodeWithContentDescription("更多").performClick()
        val timer = f.home.timerManager.timerState.value
        val gate = CompletableDeferred<Unit>().also { f.studyWrites.gate = it }
        control("提醒声音").performClick()
        f.await { f.home.uiState.value.isSavingSoundSetting && f.studyWrites.attempts.get() == 1 }
        control("提醒声音").assertIsOn().assertIsNotEnabled()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "保存中，当前已开启"))
        control("振动提醒").assertIsEnabled().performClick()
        f.await { f.home.uiState.value.isSavingVibrationSetting && f.studyWrites.attempts.get() == 2 }
        control("振动提醒").assertIsOn().assertIsNotEnabled()
        withContext(Dispatchers.Main) {
            repeat(3) { f.home.setSoundEnabled(false); f.home.setVibrationEnabled(false) }
        }
        assertEquals(2, f.studyWrites.attempts.get())
        assertTrue(f.studyPreferences.soundEnabled.first())
        assertTrue(f.studyPreferences.vibrationEnabled.first())
        capture("pending-writes")
        gate.complete(Unit)
        f.await { !f.home.uiState.value.isSavingSoundSetting && !f.home.uiState.value.isSavingVibrationSetting }
        control("提醒声音").assertIsOff().assertIsEnabled()
        control("振动提醒").assertIsOff().assertIsEnabled()
        assertEquals(timer, f.home.timerManager.timerState.value)
    }

    @Test fun cancellingAPendingWriteDoesNotRestoreBusyStateOrRetryAutomatically() = withFixture { f ->
        var model = f.home
        val restoration = render(f, model = { model })
        compose.onNodeWithContentDescription("更多").performClick()
        val gate = CompletableDeferred<Unit>().also { f.studyWrites.gate = it }
        control("提醒声音").performClick()
        f.await { f.home.uiState.value.isSavingSoundSetting && f.studyWrites.attempts.get() == 1 }
        recreate(f)
        model = f.home
        restoration.emulateSavedInstanceStateRestore()
        assertFalse(f.home.uiState.value.isSavingSoundSetting)
        assertNull(f.home.uiState.value.soundSettingError)
        compose.onNodeWithContentDescription("更多").performClick()
        control("提醒声音").assertIsOn().assertIsEnabled()
        gate.complete(Unit)
        assertTrue(f.studyPreferences.soundEnabled.first())
        assertEquals(1, f.studyWrites.attempts.get())
        control("提醒声音").performClick()
        f.await { !f.home.uiState.value.soundEnabled && !f.home.uiState.value.isSavingSoundSetting }
        assertEquals(2, f.studyWrites.attempts.get())
    }

    @Test fun savedSwitchValuesReloadInANewOwnerWithoutResettingThePausedTimer() = withFixture { f ->
        var model = f.home
        val restoration = render(f, model = { model })
        compose.onNodeWithContentDescription("更多").performClick()
        val timer = f.home.timerManager.timerState.value
        control("提醒声音").performClick()
        f.await { !f.home.uiState.value.soundEnabled && !f.home.uiState.value.isSavingSoundSetting }
        control("振动提醒").performClick()
        f.await { !f.home.uiState.value.vibrationEnabled && !f.home.uiState.value.isSavingVibrationSetting }
        recreate(f)
        model = f.home
        f.await { !f.home.uiState.value.soundEnabled && !f.home.uiState.value.vibrationEnabled }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithContentDescription("更多").performClick()
        control("提醒声音").assertIsOff().assertIsEnabled()
        control("振动提醒").assertIsOff().assertIsEnabled()
        assertEquals(timer, f.home.timerManager.timerState.value)
    }

    private fun checkModeSettings(dark: Boolean) = withFixture(startImmersive = false) { f ->
        val settings = PomodoroSettings(45, 8, 22, 6)
        f.studyPreferences.updatePomodoroSettings(settings)
        f.await { f.home.uiState.value.pomodoroSettings == settings }
        render(f, dark)
        compose.onNodeWithText("Study").performClick()
        compose.onNodeWithContentDescription("学习模式").performClick()
        val label = "45 分钟专注，8 分钟休息"
        compose.onNodeWithText(label, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        capture(if (dark) "dark-mode-settings" else "light-mode-settings")
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(label, useUnmergedTree = true)
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        val layout = layouts.single()
        assertFalse("$label must fit: ${layout.size}, width overflow=${layout.didOverflowWidth}, height overflow=${layout.didOverflowHeight}, paragraph=${layout.multiParagraph.width}",
            layout.hasVisualOverflow)
        compose.onNode(hasText("番茄钟") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("45 分钟").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("8 分钟").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("取消").performClick()
    }

    private fun control(label: String) = compose.onNode(hasText(label) and isToggleable())

    private fun render(f: HomeFormSaveFailureTest.Fixture, dark: Boolean = false, model: () -> HomeViewModel = { f.home }) =
        StateRestorationTester(compose).also { restoration -> restoration.setContent {
            CompositionLocalProvider(LocalAiEnabled provides false,
                LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                CorrectionNotebookTheme(darkTheme = dark, dynamicColor = false) {
                    Surface(Modifier.fillMaxSize().safeDrawingPadding()) { HomeScreen(model(), f.statistics) }
                }
            }
        } }

    private fun withFixture(startImmersive: Boolean = true, test: suspend (HomeFormSaveFailureTest.Fixture) -> Unit) = runBlocking {
        val f = HomeFormSaveFailureTest.Fixture()
        try {
            withContext(Dispatchers.Main) {
                f.create()
                if (startImmersive) {
                    f.home.startPomodoro()
                    f.home.selectMode(StudyMode.IMMERSIVE)
                    f.home.timerManager.pause()
                }
            }
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

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(350)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "qa-alert-settings-$name.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    companion object {
        private const val SOUND_ERROR = "提醒声音保存失败，请重试"
        private const val VIBRATION_ERROR = "振动提醒保存失败，请重试"
    }
}
