package com.github.garynasser.correction_notebook.ui.screens.statistics

import android.graphics.Bitmap
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.local.AISettingsManager
import com.github.garynasser.correction_notebook.data.local.ai.AiCredentialCipher
import com.github.garynasser.correction_notebook.data.local.ai.AiDatabase
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseDatabase
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseFileStorage
import com.github.garynasser.correction_notebook.data.model.home.StudySession
import com.github.garynasser.correction_notebook.data.model.ai.AiProviderForm
import com.github.garynasser.correction_notebook.data.remote.ai.AnthropicCompatibleAdapter
import com.github.garynasser.correction_notebook.data.remote.ai.OpenAiCompatibleAdapter
import com.github.garynasser.correction_notebook.data.remote.api.AIApiService
import com.github.garynasser.correction_notebook.data.repository.*
import com.github.garynasser.correction_notebook.domain.usecase.AiStudyUseCase
import com.github.garynasser.correction_notebook.ui.components.LocalAiEnabled
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.RequestBody
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import retrofit2.Response
import java.io.File
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

class StatisticsFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun changingPeriodNeverLabelsPreviousTotalsAsNewPeriod() = withFixture { fixture ->
        fixture.load()
        assertEquals(780, fixture.vm.uiState.value.totalStudyMinutes)
        val gate = ReadGate()
        fixture.reads.next.set(gate)
        withContext(Dispatchers.Main) { fixture.vm.setPeriod(StatsPeriod.DAY) }
        gate.entered.await()
        assertUnloaded(fixture.vm.uiState.value)
        gate.release.complete(Unit)
        fixture.await { !it.isStatsLoading }
        assertEquals(123, fixture.vm.uiState.value.totalStudyMinutes)
        assertEquals(listOf(123), fixture.vm.uiState.value.dailyMinutes)
        assertEquals(5, fixture.vm.uiState.value.completedPomodoros)
        assertEquals(mapOf(longSubject to 123), fixture.vm.uiState.value.subjectDistribution)
    }

    @Test fun failedRefreshDoesNotPresentOldDataAndRetryRecovers() = withFixture { fixture ->
        compose.setContent {
            CompositionLocalProvider(LocalAiEnabled provides true) {
                CorrectionNotebookTheme { StatisticsScreen(fixture.vm, {}) }
            }
        }
        fixture.await { !it.isStatsLoading && it.totalStudyMinutes == 780 }
        fixture.reads.next.set(ReadGate(fail = true).also { it.release.complete(Unit) })
        withContext(Dispatchers.Main) { fixture.vm.refreshStats() }
        fixture.await { !it.isStatsLoading && it.statsError != null }
        assertUnloaded(fixture.vm.uiState.value)
        compose.onNodeWithText("统计加载失败").assertIsDisplayed()
        compose.onNodeWithText("AI 解读").assertIsNotEnabled()
        compose.onNodeWithText("13h").assertDoesNotExist()
        compose.onNodeWithText("还没有科目分布").assertDoesNotExist()
        compose.onNodeWithText("重试").performClick()
        fixture.await { !it.isStatsLoading && it.statsError == null && it.totalStudyMinutes == 780 }
        compose.onNodeWithText("统计加载失败").assertDoesNotExist()
    }

    @Test fun rapidPeriodChangesOnlyPublishTheLatestDates() = withFixture { fixture ->
        fixture.load()
        val gate = ReadGate()
        fixture.reads.next.set(gate)
        withContext(Dispatchers.Main) { fixture.vm.setPeriod(StatsPeriod.DAY) }
        gate.entered.await()
        withContext(Dispatchers.Main) { fixture.vm.setPeriod(StatsPeriod.MONTH) }
        fixture.await { !it.isStatsLoading && it.period == StatsPeriod.MONTH }
        gate.release.complete(Unit)
        val (start, end) = statsDateRange(StatsPeriod.MONTH, LocalDate.now())
        assertEquals(fixture.sessions.getSessionsBetween(start, end).sumOf { it.durationMinutes }, fixture.vm.uiState.value.totalStudyMinutes)
        assertEquals(end.dayOfMonth, fixture.vm.uiState.value.dailyMinutes.size)
        assertEquals(end.dayOfMonth, fixture.vm.uiState.value.chartLabels.size)
    }

    @Test fun aiInsightCannotStartWithPendingOrFailedStatistics() = withFixture { fixture ->
        fixture.load()
        val gate = ReadGate(fail = true)
        fixture.reads.next.set(gate)
        withContext(Dispatchers.Main) { fixture.vm.refreshStats() }
        gate.entered.await()
        withContext(Dispatchers.Main) {
            fixture.vm.generateAiInsight()
            assertFalse(fixture.vm.uiState.value.isAiInsightLoading)
        }
        gate.release.complete(Unit)
        fixture.await { !it.isStatsLoading && it.statsError != null }
        withContext(Dispatchers.Main) {
            fixture.vm.generateAiInsight()
            assertFalse(fixture.vm.uiState.value.isAiInsightLoading)
        }
    }

    @Test fun recentWeekExcludesFutureSessions() = withFixture { fixture ->
        assertEquals(780, fixture.sessions.getWeekSessions().sumOf { it.durationMinutes })
    }

    @Test fun refreshCancelsTheOldInsightAndNextInsightUsesUpdatedStatistics() = withFixture { fixture ->
        val wasEnabled = fixture.settings.aiEnabled.first()
        try {
            fixture.settings.setAiEnabled(true)
            fixture.providers.activateProvider(fixture.providers.saveProvider(fixture.ai.normalizeProviderRecord(
                AiProviderForm(name = "Statistics QA", baseUrl = "https://statistics.invalid/v1", apiKey = "qa", model = "qa-model"))))
            fixture.load()
            val gate = ReadGate()
            fixture.service.gate = gate
            withContext(Dispatchers.Main) { fixture.vm.generateAiInsight() }
            gate.entered.await()
            fixture.sessions.addSession(StudySession(subject = longSubject, startTime = LocalDate.now().atTime(12, 0), durationMinutes = 77))
            withContext(Dispatchers.Main) { fixture.vm.refreshStats() }
            fixture.service.cancelled.await()
            fixture.await { !it.isStatsLoading && it.totalStudyMinutes == 857 }
            assertNull(fixture.vm.uiState.value.aiInsight)
            assertNull(fixture.vm.uiState.value.aiInsightError)
            fixture.service.gate = null
            withContext(Dispatchers.Main) { fixture.vm.generateAiInsight() }
            fixture.await { !it.isAiInsightLoading && it.aiInsight != null }
            assertEquals("新的统计解读", fixture.vm.uiState.value.aiInsight)
            assertTrue(fixture.service.lastRequest.contains("857"))
        } finally {
            fixture.service.gate?.release?.complete(Unit)
            fixture.settings.setAiEnabled(wasEnabled)
        }
    }

    @Test fun lightStatisticsFitsNarrowScreenAndLargeFont() = checkLayout(false)
    @Test fun darkStatisticsFitsNarrowScreenAndLargeFont() = checkLayout(true)

    @Test fun monthChartLabelsFitWithoutClippingOrOverlapping() {
        val labels = (1..31).map { if (it == 1 || it % 5 == 0 || it == 31) "$it" else "" }
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3.375f, 1.3f)) {
                CorrectionNotebookTheme {
                    BarChart(List(31) { 20 + it }, labels, Modifier.fillMaxWidth().padding(12.dp).height(180.dp))
                }
            }
        }
        val bounds = labels.filter { it.isNotEmpty() }.map { label ->
            assertFullText(label)
            compose.onNodeWithText(label).fetchSemanticsNode().boundsInRoot
        }
        bounds.zipWithNext().forEach { (previous, next) -> assertTrue(previous.right <= next.left) }
        saveScreenshot("month-chart")
    }

    private fun checkLayout(dark: Boolean) = withFixture { fixture ->
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3.375f, 1.3f), LocalAiEnabled provides false) {
                CorrectionNotebookTheme(darkTheme = dark, dynamicColor = false) {
                    Box(Modifier.navigationBarsPadding()) { StatisticsScreen(fixture.vm, {}) }
                }
            }
        }
        fixture.await { !it.isStatsLoading && it.totalStudyMinutes == 780 }
        assertFullText("总学习时长")
        assertFullText("13h")
        assertFullText("31个")
        assertFullText("1h51m")
        saveScreenshot("${if (dark) "dark" else "light"}-summary")
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(longSubject))
        assertFullText(longSubject)
        saveScreenshot(if (dark) "dark" else "light")
    }

    private fun saveScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(350)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "qa-statistics-$name.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    private fun assertFullText(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        layouts.forEach {
            assertFalse("Clipped text: $text", it.didOverflowHeight)
            assertEquals(text.length, it.getLineEnd(it.lineCount - 1))
            repeat(it.lineCount) { line ->
                assertFalse(it.isLineEllipsized(line))
                assertTrue("Text extends outside its slot: $text", it.getLineRight(line) <= it.size.width + 1)
            }
        }
    }

    private fun assertUnloaded(state: StatsUiState) {
        assertEquals(0, state.totalStudyMinutes)
        assertEquals(0, state.averageDailyMinutes)
        assertEquals(0, state.completedPomodoros)
        assertTrue(state.dailyMinutes.isEmpty())
        assertTrue(state.chartLabels.isEmpty())
        assertTrue(state.subjectDistribution.isEmpty())
    }

    private fun withFixture(test: suspend (Fixture) -> Unit) = runBlocking {
        val fixture = Fixture()
        try {
            withTimeout(30_000) {
                val today = LocalDate.now()
                fixture.sessions.addSession(StudySession(subject = longSubject, startTime = today.atTime(8, 0), durationMinutes = 123, pomodoroCount = 5))
                fixture.sessions.addSession(StudySession(subject = "矩阵分析", startTime = today.minusDays(1).atTime(8, 0), durationMinutes = 657, pomodoroCount = 26))
                fixture.sessions.addSession(StudySession(subject = "过去的学习", startTime = today.minusDays(7).atTime(8, 0), durationMinutes = 47))
                fixture.sessions.addSession(StudySession(subject = "未来的记录", startTime = today.plusDays(1).atTime(8, 0), durationMinutes = 600))
                withContext(Dispatchers.Main) { fixture.create() }
                test(fixture)
            }
        } finally {
            fixture.reads.next.get()?.release?.complete(Unit)
            withContext(Dispatchers.Main) { fixture.store.clear() }
            fixture.scope.cancel()
            fixture.scope.coroutineContext[Job]?.join()
            fixture.database.close()
            fixture.knowledge.close()
            fixture.file.delete()
        }
    }

    private class ReadGate(val fail: Boolean = false) {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
    }

    private class GatedStore(private val delegate: DataStore<Preferences>) : DataStore<Preferences> {
        val next = AtomicReference<ReadGate?>()
        override val data: Flow<Preferences> = flow {
            next.getAndSet(null)?.let {
                it.entered.complete(Unit)
                it.release.await()
                if (it.fail) throw IllegalStateException("统计存储暂时不可用")
            }
            emitAll(delegate.data)
        }
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences) = delegate.updateData(transform)
    }

    private class Fixture {
        private val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val file = File(context.cacheDir, "statistics-${UUID.randomUUID()}.preferences_pb")
        val reads = GatedStore(PreferenceDataStoreFactory.create(scope = scope) { file })
        val sessions = StudySessionRepository(reads)
        val database = Room.inMemoryDatabaseBuilder(context, AiDatabase::class.java).build()
        val knowledge = Room.inMemoryDatabaseBuilder(context, KnowledgeBaseDatabase::class.java).build()
        val store = ViewModelStore()
        val settings = AISettingsManager(context)
        val providers = ProviderRepository(database.aiProviderDao(), AiCredentialCipher(context))
        val service = InsightService()
        private val gson = Gson()
        val ai = AIRepository(settings, providers, OpenAiCompatibleAdapter(service, gson), AnthropicCompatibleAdapter(service, gson), gson)
        lateinit var vm: StatisticsViewModel

        fun create() {
            val dao = knowledge.knowledgeBaseDao()
            val useCase = AiStudyUseCase(ai, KnowledgeBaseAiRepository(dao), MemoryRepository(database.userMemoryDao()),
                TodoRepository(context), ScheduleRepository(context), sessions, CourseLearningRepository(context),
                KnowledgeBaseRepository(dao, KnowledgeBaseFileStorage(context), context))
            vm = StatisticsViewModel(sessions, useCase)
            store.put("statistics", vm)
        }

        suspend fun load() {
            withContext(Dispatchers.Main) { vm.refreshStats() }
            await { !it.isStatsLoading && it.totalStudyMinutes == 780 }
        }

        suspend fun await(predicate: (StatsUiState) -> Boolean) = withTimeout(5_000) {
            while (!predicate(vm.uiState.value)) delay(10)
        }
    }

    private class InsightService : AIApiService {
        @Volatile var gate: ReadGate? = null
        @Volatile var lastRequest = ""
        val cancelled = CompletableDeferred<Unit>()
        override suspend fun getJson(url: String, headers: Map<String, String>): Response<ResponseBody> = error("Unexpected network call")
        override suspend fun postJson(url: String, headers: Map<String, String>, request: RequestBody): Response<ResponseBody> {
            lastRequest = Buffer().also { request.writeTo(it) }.readUtf8()
            gate?.let {
                it.entered.complete(Unit)
                try { it.release.await() } catch (e: CancellationException) { cancelled.complete(Unit); throw e }
            }
            return Response.success("""{"choices":[{"message":{"content":"新的统计解读"}}]}""".toResponseBody())
        }
    }

    companion object { private const val longSubject = "计算理论与算法分析设计" }
}
