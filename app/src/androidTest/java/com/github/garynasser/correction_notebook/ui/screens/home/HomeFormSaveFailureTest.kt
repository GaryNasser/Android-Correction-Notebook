package com.github.garynasser.correction_notebook.ui.screens.home

import android.graphics.Bitmap
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.local.*
import com.github.garynasser.correction_notebook.data.local.ai.AiCredentialCipher
import com.github.garynasser.correction_notebook.data.local.ai.AiDatabase
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseDatabase
import com.github.garynasser.correction_notebook.data.local.knowledgebase.KnowledgeBaseFileStorage
import com.github.garynasser.correction_notebook.data.model.common.ApiResponse
import com.github.garynasser.correction_notebook.data.model.home.ScheduleEvent
import com.github.garynasser.correction_notebook.data.model.home.TodoItem
import com.github.garynasser.correction_notebook.data.remote.ai.AnthropicCompatibleAdapter
import com.github.garynasser.correction_notebook.data.remote.ai.OpenAiCompatibleAdapter
import com.github.garynasser.correction_notebook.data.remote.api.AIApiService
import com.github.garynasser.correction_notebook.data.remote.api.ArticleApiService
import com.github.garynasser.correction_notebook.data.remote.cas.BitCasClient
import com.github.garynasser.correction_notebook.data.remote.model.ArticleDto
import com.github.garynasser.correction_notebook.data.remote.school.SchoolScheduleRemoteDataSource
import com.github.garynasser.correction_notebook.data.repository.*
import com.github.garynasser.correction_notebook.data.repository.school.SchoolScheduleMapper
import com.github.garynasser.correction_notebook.domain.usecase.AiStudyUseCase
import com.github.garynasser.correction_notebook.ui.components.LocalAiEnabled
import com.github.garynasser.correction_notebook.ui.screens.statistics.StatisticsViewModel
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import com.google.gson.Gson
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

class HomeFormSaveFailureTest {
    @get:Rule val compose = createComposeRule()

    @Test fun todoFailureIsVisibleInsideTheFormAndRetrySavesTheOriginalDraft() = withFixture { f ->
        render(f)
        openTodo()
        compose.onNodeWithText("标题").performTextReplacement(TITLE)
        compose.onNodeWithText("备注（可选）").performTextReplacement(NOTES)
        f.todoWrites.fail = true
        compose.onNodeWithContentDescription("添加").performClick()
        f.await { f.todoWrites.attempts.get() == 1 && !f.home.uiState.value.isAddingTodo }
        withContext(Dispatchers.Main) { f.home.consumeTodoActionMessage() }
        capture("todo-failure")
        assertFormError(TODO_ERROR)
        compose.onNodeWithText(TITLE).assertExists()
        compose.onNodeWithText(NOTES).assertExists()
        assertTrue(f.todos.todoItems.first().isEmpty())
        f.todoWrites.fail = false
        val release = CompletableDeferred<Unit>().also { f.todoWrites.gate = it }
        compose.onNodeWithContentDescription("添加").performClick()
        f.await { f.todoWrites.attempts.get() == 2 && f.home.uiState.value.isAddingTodo }
        compose.onNodeWithText(TODO_ERROR).assertDoesNotExist()
        compose.onNodeWithText("标题").assertIsNotEnabled()
        compose.onNodeWithContentDescription("取消").assertIsNotEnabled()
        withContext(Dispatchers.Main) {
            f.home.addTodo(TodoItem(title = "重复提交"))
            f.home.hideAddTodoDialog()
        }
        assertTrue(f.home.uiState.value.showAddTodoDialog)
        assertEquals(2, f.todoWrites.attempts.get())
        release.complete(Unit)
        f.await { !f.home.uiState.value.showAddTodoDialog }
        val saved = f.todos.todoItems.first().single()
        assertEquals(TITLE, saved.title)
        assertEquals(NOTES, saved.description)
        assertEquals(2, f.todoWrites.attempts.get())
        compose.onNodeWithText(TODO_ERROR).assertDoesNotExist()
    }

    @Test fun scheduleFailureIsVisibleInsideTheFormAndRetrySavesEveryField() = withFixture { f ->
        render(f)
        compose.onNodeWithContentDescription("添加日程").performClick()
        compose.onNodeWithText("活动标题").performTextReplacement(TITLE)
        compose.onNodeWithText("地点").performTextReplacement(LOCATION)
        compose.onNodeWithText("备注").performTextReplacement(NOTES)
        f.scheduleWrites.fail = true
        compose.onNodeWithContentDescription("保存").performClick()
        f.await { f.scheduleWrites.attempts.get() == 1 && !f.home.uiState.value.isEditingSchedule }
        withContext(Dispatchers.Main) { f.home.consumeScheduleActionMessage() }
        capture("schedule-failure")
        assertFormError(SCHEDULE_ERROR)
        compose.onNodeWithText(TITLE).assertExists()
        compose.onNodeWithText(LOCATION).assertExists()
        compose.onNodeWithText(NOTES).assertExists()
        assertTrue(f.schedules.scheduleEvents.first().isEmpty())
        f.scheduleWrites.fail = false
        val release = CompletableDeferred<Unit>().also { f.scheduleWrites.gate = it }
        compose.onNodeWithContentDescription("保存").performClick()
        f.await { f.scheduleWrites.attempts.get() == 2 && f.home.uiState.value.isEditingSchedule }
        compose.onNodeWithText(SCHEDULE_ERROR).assertDoesNotExist()
        compose.onNodeWithText("活动标题").assertIsNotEnabled()
        compose.onNodeWithContentDescription("取消").assertIsNotEnabled()
        withContext(Dispatchers.Main) {
            val start = f.home.uiState.value.selectedDate.atTime(9, 0)
            f.home.addSchedule(ScheduleEvent(title = "重复提交", startAt = start, endAt = start.plusHours(1)))
            f.home.hideAddScheduleDialog()
        }
        assertTrue(f.home.uiState.value.showAddScheduleDialog)
        assertEquals(2, f.scheduleWrites.attempts.get())
        release.complete(Unit)
        f.await { !f.home.uiState.value.showAddScheduleDialog }
        val saved = f.schedules.scheduleEvents.first().single()
        assertEquals(TITLE, saved.title)
        assertEquals(LOCATION, saved.location)
        assertEquals(NOTES, saved.description)
        assertEquals(f.home.uiState.value.selectedDate.atTime(9, 0), saved.startAt)
        assertEquals(f.home.uiState.value.selectedDate.atTime(10, 0), saved.endAt)
        assertEquals(2, f.scheduleWrites.attempts.get())
        compose.onNodeWithText(SCHEDULE_ERROR).assertDoesNotExist()
    }

    @Test fun cancellingFailedTodoStartsANewBlankDraftWithoutTheOldError() = withFixture { f ->
        render(f)
        openTodo()
        compose.onNodeWithText("标题").performTextReplacement(TITLE)
        f.todoWrites.fail = true
        compose.onNodeWithContentDescription("添加").performClick()
        f.await { f.todoWrites.attempts.get() == 1 && !f.home.uiState.value.isAddingTodo }
        compose.onNode(hasText(TODO_ERROR) and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.onNodeWithContentDescription("取消").performClick()
        openTodo()
        compose.onNodeWithText(TODO_ERROR).assertDoesNotExist()
        compose.onNodeWithText(TITLE).assertDoesNotExist()
        compose.onNodeWithContentDescription("添加").assertIsNotEnabled()
    }

    @Test fun cancellingFailedScheduleStartsANewBlankDraftWithoutTheOldError() = withFixture { f ->
        render(f)
        compose.onNodeWithContentDescription("添加日程").performClick()
        compose.onNodeWithText("活动标题").performTextReplacement(TITLE)
        f.scheduleWrites.fail = true
        compose.onNodeWithContentDescription("保存").performClick()
        f.await { f.scheduleWrites.attempts.get() == 1 && !f.home.uiState.value.isEditingSchedule }
        compose.onNode(hasText(SCHEDULE_ERROR) and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.onNodeWithContentDescription("取消").performClick()
        compose.onNodeWithContentDescription("添加日程").performClick()
        compose.onNodeWithText(SCHEDULE_ERROR).assertDoesNotExist()
        compose.onNodeWithText(TITLE).assertDoesNotExist()
        compose.onNodeWithContentDescription("保存").assertIsNotEnabled()
    }

    private fun render(f: Fixture) {
        val dark = InstrumentationRegistry.getArguments().getString("qaDark") == "true"
        compose.setContent {
            CompositionLocalProvider(LocalAiEnabled provides false,
                LocalDensity provides Density(LocalDensity.current.density, 1.3f)) {
                CorrectionNotebookTheme(darkTheme = dark) { HomeScreen(f.home, f.statistics) }
            }
        }
    }

    private fun assertFormError(message: String) {
        compose.onNode(hasText(message) and hasAnyAncestor(isDialog())).assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
            .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { action ->
                val layouts = mutableListOf<TextLayoutResult>()
                assertTrue(action(layouts))
                assertTrue(layouts.isNotEmpty())
                layouts.forEach { layout ->
                    assertFalse(layout.didOverflowHeight)
                    assertEquals(message.length, layout.getLineEnd(layout.lineCount - 1))
                    repeat(layout.lineCount) { line ->
                        assertFalse(layout.isLineEllipsized(line))
                        assertTrue(layout.getLineRight(line) <= layout.size.width + 1f)
                    }
                }
            }
    }

    private fun openTodo() {
        compose.onNodeWithText("Study").performClick()
        compose.onNode(hasScrollAction() and SemanticsMatcher.keyIsDefined(androidx.compose.ui.semantics.SemanticsProperties.VerticalScrollAxisRange))
            .performScrollToNode(hasContentDescription("添加待办"))
        compose.onNodeWithContentDescription("添加待办").performClick()
    }

    private fun withFixture(test: suspend (Fixture) -> Unit) = runBlocking {
        val f = Fixture()
        try {
            withContext(Dispatchers.Main) { f.create() }
            withTimeout(25_000) { test(f) }
        } finally {
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

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        Thread.sleep(300)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "qa-home-form-$name.png")
                .outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    private class ControlledWrites(private val delegate: DataStore<Preferences>) : DataStore<Preferences> by delegate {
        @Volatile var fail = false
        @Volatile var gate: CompletableDeferred<Unit>? = null
        val attempts = AtomicInteger()
        override suspend fun updateData(transform: suspend (Preferences) -> Preferences): Preferences {
            attempts.incrementAndGet()
            gate?.await()
            if (fail) throw IOException(SCHEDULE_ERROR)
            return delegate.updateData(transform)
        }
    }

    private class Fixture {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val files = listOf("todo", "schedule").map { File(context.cacheDir, "home-form-$it-${UUID.randomUUID()}.preferences_pb") }
        val todoWrites = ControlledWrites(PreferenceDataStoreFactory.create(scope = scope) { files[0] })
        val scheduleWrites = ControlledWrites(PreferenceDataStoreFactory.create(scope = scope) { files[1] })
        val todos = TodoRepository(todoWrites)
        val schedules = ScheduleRepository(scheduleWrites)
        val database = Room.inMemoryDatabaseBuilder(context, AiDatabase::class.java).build()
        val knowledge = Room.inMemoryDatabaseBuilder(context, KnowledgeBaseDatabase::class.java).build()
        val network = OkHttpClient.Builder().addInterceptor { error("Unexpected external request") }.build()
        val store = ViewModelStore()
        lateinit var home: HomeViewModel
        lateinit var statistics: StatisticsViewModel
        fun create() {
            val retrofit = Retrofit.Builder().baseUrl("https://unused.invalid/").client(network)
                .addConverterFactory(GsonConverterFactory.create()).build()
            val service = retrofit.create(AIApiService::class.java)
            val gson = Gson()
            val ai = AIRepository(AISettingsManager(context), ProviderRepository(database.aiProviderDao(), AiCredentialCipher(context)),
                OpenAiCompatibleAdapter(service, gson), AnthropicCompatibleAdapter(service, gson), gson)
            val dao = knowledge.knowledgeBaseDao()
            val repository = KnowledgeBaseRepository(dao, KnowledgeBaseFileStorage(context), context)
            val learning = CourseLearningRepository(context)
            val sessions = StudySessionRepository(context)
            val useCase = AiStudyUseCase(ai, KnowledgeBaseAiRepository(dao), MemoryRepository(database.userMemoryDao()),
                todos, schedules, sessions, learning, repository)
            val articles = object : ArticleApiService by retrofit.create(ArticleApiService::class.java) {
                override suspend fun getRecommendedArticles() = ApiResponse<List<ArticleDto>>(200, data = emptyList())
            }
            home = HomeViewModel(todos, ArticleRepository(articles), StudyPreferencesManager(context), sessions, TodoHistoryRepository(context),
                schedules, IcsImportRepository(context, schedules), SchoolScheduleRepository(CredentialManager(context),
                    SchoolScheduleRemoteDataSource(BitCasClient(network), network), schedules, SchoolScheduleMapper()),
                learning, repository, StudySetRepository(dao), useCase, context, SavedStateHandle())
            statistics = StatisticsViewModel(sessions, useCase)
            store.put("home", home)
            store.put("statistics", statistics)
        }
        suspend fun await(predicate: () -> Boolean) = withTimeout(5_000) { while (!predicate()) delay(10) }
    }

    companion object {
        private const val TITLE = "复习计算理论与算法分析设计"
        private const val NOTES = "带上课堂笔记\n核对第三章证明步骤"
        private const val LOCATION = "文萃楼 M134"
        private const val TODO_ERROR = "待办添加失败，请稍后再试"
        private const val SCHEDULE_ERROR = "日程保存失败，请稍后再试"
    }
}
