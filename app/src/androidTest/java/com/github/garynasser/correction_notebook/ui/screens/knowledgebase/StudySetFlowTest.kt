package com.github.garynasser.correction_notebook.ui.screens.knowledgebase

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.github.garynasser.correction_notebook.data.local.knowledgebase.*
import com.github.garynasser.correction_notebook.data.model.studyset.*
import com.github.garynasser.correction_notebook.data.remote.api.BitShareApiService
import com.github.garynasser.correction_notebook.data.repository.*
import com.github.garynasser.correction_notebook.ui.theme.CorrectionNotebookTheme
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.OkHttpClient
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import retrofit2.Retrofit
import java.io.File
import java.util.concurrent.atomic.AtomicReference

class StudySetFlowTest {
    @get:Rule val compose = createComposeRule()

    @Test fun olderStudySetStillExposesAllItsCardsHistoryAndQuestions() = withFixture { fixture ->
        fixture.dao.insertStudySet(set("a-new", "新学习集", 20))
        fixture.dao.insertFlashcards(List(81) { card("new-$it", "a-new").copy(lastReviewedAt = 1, reviewCount = 1) } + card("old-card", "z-old").copy(lastReviewedAt = 1))
        fixture.dao.insertQuizQuestions(List(201) { question("new-$it", "a-new") } + question("old-quiz", "z-old"))
        fixture.await { it.studySets.sumOf { set -> set.flashcardCount } == 82 && it.quizQuestions.isNotEmpty() && it.reviewedCards.isNotEmpty() }
        assertEquals(82, fixture.vm.uiState.value.knowledgeCards.size)
        assertEquals(82, fixture.vm.uiState.value.reviewedCards.size)
        assertEquals(202, fixture.vm.uiState.value.quizQuestions.size)
        compose.setContent { CorrectionNotebookTheme { KnowledgeBaseScreen({}, viewModel = fixture.vm) } }
        compose.onNodeWithText("知识空间").performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText(setTitle))
        compose.onNodeWithText(setTitle).performClick()
        compose.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("问题 old-card"))
        compose.onNodeWithText("问题 old-card").assertIsDisplayed()
    }

    @Test fun newCardsAndReorderingDoNotResetTheCurrentLearningCardOrItsAnswer() = withFixture { fixture ->
        fixture.dao.insertFlashcards(listOf(card("a", "z-old"), card("b", "z-old")))
        fixture.await { it.knowledgeCards.size == 2 }
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val state by fixture.vm.uiState.collectAsState()
            CorrectionNotebookTheme { StudySetLearningPage(summary(), state.knowledgeCards, {}, {}, { _, _, _ -> }) }
        }
        compose.onNodeWithContentDescription("下一张").performClick()
        compose.onNodeWithText("显示答案").performClick()
        compose.onNodeWithText("答案 b").assertExists()
        fixture.dao.insertFlashcards(listOf(card("0-new", "z-old")))
        fixture.await { it.knowledgeCards.size == 3 }
        compose.onNodeWithText("答案 b").assertExists()
        compose.onNodeWithText("问题 b").assertExists()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("答案 b").assertExists()
    }

    @Test fun quizAnswerAndSelectionStayWithQuestionIdWhenDatabaseOrderingChanges() = withFixture { fixture ->
        fixture.dao.insertQuizQuestions(listOf(question("a", "z-old"), question("b", "z-old")))
        fixture.await { it.quizQuestions.size == 2 }
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val state by fixture.vm.uiState.collectAsState()
            CorrectionNotebookTheme { StudySetQuizPage(summary(), state.quizQuestions, "a", {}) }
        }
        compose.onNodeWithText("显示答案").performClick()
        compose.onNodeWithText("答案 a").assertExists()
        fixture.dao.insertQuizQuestions(listOf(question("a", "z-old").copy(question = "问题 z-a")))
        fixture.await { it.quizQuestions.last().id == "a" }
        compose.onNodeWithText("问题 z-a").assertExists()
        compose.onNodeWithText("答案 a").assertExists()
        compose.onNodeWithText("答案 b").assertDoesNotExist()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("问题 z-a").assertExists()
        compose.onNodeWithText("答案 a").assertExists()
    }

    @Test fun detailTitleAndToolsFitOnNarrowLightScreen() = checkDetail(false)
    @Test fun detailTitleAndToolsFitOnNarrowDarkScreen() = checkDetail(true)
    @Test fun learningContentFitsNarrowLightScreen() = checkSession(false, false)
    @Test fun learningContentFitsNarrowDarkScreen() = checkSession(true, false)
    @Test fun quizContentFitsNarrowLightScreen() = checkSession(false, true)
    @Test fun quizContentFitsNarrowDarkScreen() = checkSession(true, true)
    @Test fun switchingLearningCardsStartsAtTheTopButRestorationKeepsTheReadingPosition() = checkContentNavigation(false)
    @Test fun switchingQuizQuestionsStartsAtTheTopButRestorationKeepsTheReadingPosition() = checkContentNavigation(true)

    private fun checkContentNavigation(quiz: Boolean) {
        val headers = if (quiz) listOf("第一题：矩阵的特征值", "第二题：矩阵的特征向量")
            else listOf("第一张卡片：矩阵的特征值", "第二张卡片：矩阵的特征向量")
        val longContent = "理解特征值和特征向量，需要先区分矩阵作用前后向量的方向与长度。".repeat(50)
        val tails = listOf("第一项正文末尾", "第二项正文末尾")
        val restoration = StateRestorationTester(compose)
        var reviews = 0
        restoration.setContent {
            CorrectionNotebookTheme(darkTheme = InstrumentationRegistry.getArguments().getString("qaDark") == "true",
                dynamicColor = false) {
                if (quiz) {
                    StudySetQuizPage(summary(), headers.mapIndexed { index, title ->
                        StudySetQuizItem("question-$index", "z-old", "MULTIPLE_CHOICE", title,
                            options = listOf("A. $longContent", "B. ${tails[index]}"), answer = "A")
                    }, null, {})
                } else {
                    StudySetLearningPage(summary(), headers.mapIndexed { index, title ->
                        DueReviewItem("card-$index", "z-old", setTitle, null, type = KnowledgeCardType.KNOWLEDGE_CARD,
                            title = title, front = "", back = "", explanation = longContent,
                            example = tails[index], hint = "", nextReviewAt = 0)
                    }, {}, {}, { _, _, _ -> reviews++ })
                }
            }
        }
        val previousLabel = if (quiz) "上一题" else "上一张"
        val nextLabel = if (quiz) "下一题" else "下一张"
        compose.onNodeWithContentDescription(previousLabel).assertIsNotEnabled()
        compose.onNodeWithContentDescription(nextLabel).assertIsEnabled()
        listOf(previousLabel, nextLabel).forEach {
            val bounds = compose.onNodeWithContentDescription(it).assertIsDisplayed().fetchSemanticsNode().touchBoundsInRoot
            val minimum = 48f * InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.density
            assertTrue("Navigation must retain a 48dp touch target", bounds.width + 1f >= minimum && bounds.height + 1f >= minimum)
        }
        val firstTail = if (quiz) "B. ${tails[0]}" else tails[0]
        compose.onNodeWithText(firstTail).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(headers[0], useUnmergedTree = true).assertIsNotDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(firstTail).assertIsDisplayed()
        compose.onNodeWithText(headers[0], useUnmergedTree = true).assertIsNotDisplayed()
        compose.onNodeWithContentDescription(nextLabel).performClick()
        compose.onNodeWithText(headers[0], useUnmergedTree = true).assertDoesNotExist()
        screenshot(if (quiz) "quiz-next-content" else "learning-next-content")
        assertHeaderAtTop(headers[1])
        compose.onNodeWithContentDescription(nextLabel).assertIsNotEnabled()
        compose.onNodeWithContentDescription(previousLabel).assertIsEnabled()
        val secondTail = if (quiz) "B. ${tails[1]}" else tails[1]
        compose.onNodeWithText(secondTail).performScrollTo().assertIsDisplayed()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText(secondTail).assertIsDisplayed()
        compose.onNodeWithText(headers[1], useUnmergedTree = true).assertIsNotDisplayed()
        compose.onNodeWithContentDescription(previousLabel).performClick()
        assertHeaderAtTop(headers[0])
        compose.runOnIdle { assertEquals(0, reviews) }
    }

    private fun assertHeaderAtTop(title: String) {
        val titleBounds = compose.onNodeWithText(title, useUnmergedTree = true).assertIsDisplayed().fetchSemanticsNode().boundsInRoot
        val viewport = compose.onNode(hasScrollAction()).fetchSemanticsNode().boundsInRoot
        assertTrue("A new item must start at its heading", titleBounds.top >= viewport.top && titleBounds.bottom <= viewport.bottom)
        assertFullText(title)
    }

    @Test fun failedReviewStaysOnCurrentCardAndSuccessfulRetryAdvancesOnlyOnce() = withFixture { fixture ->
        fixture.dao.insertFlashcards(listOf(card("a", "z-old"), card("b", "z-old")))
        fixture.await { it.knowledgeCards.size == 2 }
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val state by fixture.vm.uiState.collectAsState()
            CorrectionNotebookTheme {
                StudySetLearningPage(summary(), state.knowledgeCards, {}, {}, { card, remembered, onReviewed ->
                    fixture.vm.markFlashcardReviewed(card.flashcardId, remembered, onReviewed)
                }, isReviewing = state.isLocalBusy)
            }
        }
        val gate = ReviewGate(fail = true)
        fixture.updates.next.set(gate)
        compose.onNodeWithText("记住了").performClick()
        gate.entered.await()
        compose.onNodeWithText("问题 a").assertExists()
        compose.onNodeWithText("记住了").assertIsNotEnabled()
        compose.onNodeWithText("没记清").assertIsNotEnabled()
        assertEquals(0, fixture.dao.getFlashcardById("a")!!.reviewCount)
        gate.release.complete(Unit)
        fixture.await { !it.isLocalBusy && it.snackbarMessage == "复习记录保存失败" }
        compose.onNodeWithText("问题 a").assertExists()
        compose.onNodeWithText("记住了").performClick()
        fixture.await { it.knowledgeCards.first { card -> card.flashcardId == "a" }.reviewCount == 1 }
        compose.onNodeWithText("问题 b").assertExists()
        assertEquals(1, fixture.dao.getFlashcardById("a")!!.reviewCount)
        compose.onNodeWithText("没记清").performClick()
        fixture.await { it.knowledgeCards.first { card -> card.flashcardId == "b" }.reviewCount == 1 }
        compose.onNodeWithText("本轮学习完成").assertExists()
        compose.onNodeWithText("记住了").assertDoesNotExist()
        assertEquals(1, fixture.dao.getFlashcardById("b")!!.reviewCount)
        val stored = fixture.dao.getFlashcardById("b")!!
        assertEquals(86_400_000, stored.nextReviewAt - stored.lastReviewedAt!!)
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("本轮学习完成").assertExists()
        assertEquals(1, fixture.dao.getFlashcardById("b")!!.reviewCount)
    }

    private fun checkDetail(dark: Boolean) {
        val actions = mutableListOf<String>()
        compose.setContent {
            CompositionLocalProvider(LocalDensity provides Density(3.375f, 1.3f)) {
                CorrectionNotebookTheme(darkTheme = dark, dynamicColor = false) {
                    Box(Modifier.statusBarsPadding().navigationBarsPadding()) {
                        StudySetDetailPage(summary(), emptyList(), emptyList(), emptyList(), {},
                            { actions += "rename" }, { actions += "delete" }, { actions += "merge" }, {}, {},
                            { actions += "add" }, {}, {}, {}, {}, {}, {})
                    }
                }
            }
        }
        assertFullText(setTitle)
        compose.onNodeWithContentDescription("添加知识卡片").assertIsDisplayed()
        listOf("编辑学习集" to "rename", "合并学习集" to "merge", "删除学习集" to "delete").forEach { (label, key) ->
            compose.onNodeWithContentDescription("学习集操作").performClick()
            compose.onNodeWithText(label).performClick()
            assertEquals(key, actions.last())
        }
        compose.onNodeWithContentDescription("添加知识卡片").performClick()
        assertEquals("add", actions.last())
        screenshot(if (dark) "dark-detail" else "light-detail")
    }

    private fun checkSession(dark: Boolean, quiz: Boolean) {
        val question = "为什么最短路径中的松弛操作能够更新到达顶点的最优距离？"
        val answer = "松弛操作比较原有距离与经过当前顶点的候选路径，只有候选路径更短时才更新距离。".repeat(8)
        lateinit var view: android.view.View
        compose.setContent {
            view = LocalView.current
            CompositionLocalProvider(LocalDensity provides Density(3.375f, 1.3f)) {
                CorrectionNotebookTheme(darkTheme = dark, dynamicColor = false) {
                    Box(Modifier.navigationBarsPadding()) {
                        if (quiz) {
                            StudySetQuizPage(summary(), listOf(StudySetQuizItem("qa", "z-old", "SHORT_ANSWER", question, answer = answer)), null, {})
                        } else {
                            val item = DueReviewItem("qa", "z-old", setTitle, null, title = "最短路径中的松弛操作", front = question,
                                back = answer, hint = "比较两条路径的长度", nextReviewAt = 0)
                            StudySetLearningPage(summary(), listOf(item), {}, {}, { _, _, _ -> })
                        }
                    }
                }
            }
        }
        assertFullText(setTitle)
        val titleBounds = compose.onNodeWithText(setTitle, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val footer = compose.onNodeWithContentDescription(if (quiz) "下一题" else "下一张").fetchSemanticsNode().boundsInRoot
        compose.runOnIdle {
            val insets = view.rootWindowInsets.getInsets(android.view.WindowInsets.Type.systemBars() or android.view.WindowInsets.Type.displayCutout())
            assertTrue("Title overlaps the status bar", titleBounds.top >= insets.top)
            assertTrue("Actions overlap the navigation bar", footer.bottom <= view.rootView.height - insets.bottom)
        }
        assertFullText(question)
        compose.onNodeWithText("显示答案").performScrollTo().performClick()
        compose.onNodeWithText(answer).performScrollTo()
        assertFullText(answer)
        screenshot("${if (dark) "dark" else "light"}-${if (quiz) "quiz" else "learning"}")
    }

    private fun assertFullText(text: String) {
        val layouts = mutableListOf<TextLayoutResult>()
        compose.onNodeWithText(text, useUnmergedTree = true).performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
        assertTrue(layouts.isNotEmpty())
        layouts.forEach {
            assertFalse("Clipped content: $text", it.didOverflowHeight)
            assertEquals(text.length, it.getLineEnd(it.lineCount - 1))
            repeat(it.lineCount) { line -> assertFalse(it.isLineEllipsized(line)); assertTrue(it.getLineRight(line) <= it.size.width + 1) }
        }
    }

    private fun screenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        android.os.SystemClock.sleep(350)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        try {
            File(instrumentation.targetContext.getExternalFilesDir(null), "qa-study-set-$name.png").outputStream()
                .use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        } finally { bitmap.recycle() }
    }

    private fun withFixture(test: suspend (Fixture) -> Unit) = runBlocking {
        val fixture = Fixture()
        var observer: Job? = null
        try {
            withTimeout(30_000) {
                fixture.dao.insertStudySet(set("z-old", setTitle, 10))
                withContext(Dispatchers.Main) { fixture.create() }
                observer = launch { fixture.vm.uiState.collect {} }
                try { fixture.await { it.studySets.size == 1 }; test(fixture) } finally { observer?.cancel() }
            }
        } finally {
            observer?.cancel()
            withContext(Dispatchers.Main) { fixture.store.clear() }
            fixture.database.close()
            fixture.network.dispatcher.executorService.shutdownNow()
            fixture.network.connectionPool.evictAll()
        }
    }

    private class Fixture {
        private val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, KnowledgeBaseDatabase::class.java).build()
        val dao = database.knowledgeBaseDao()
        val updates = GatedUpdates(dao)
        val repository = StudySetRepository(updates)
        val store = ViewModelStore()
        val network = OkHttpClient.Builder().addInterceptor { error("Unexpected network call") }.build()
        lateinit var vm: KnowledgeBaseViewModel
        fun create() {
            val api = Retrofit.Builder().baseUrl("https://unused.invalid/").client(network).build().create(BitShareApiService::class.java)
            vm = KnowledgeBaseViewModel(KnowledgeBaseRepository(dao, KnowledgeBaseFileStorage(context), context),
                BitShareRepository(api, network), repository)
            store.put("study-set", vm)
        }
        suspend fun await(predicate: (KnowledgeBaseUiState) -> Boolean) = withTimeout(5_000) {
            while (!predicate(vm.uiState.value)) delay(10)
        }
    }

    private class ReviewGate(val fail: Boolean) {
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
    }

    private class GatedUpdates(private val delegate: KnowledgeBaseDao) : KnowledgeBaseDao by delegate {
        val next = AtomicReference<ReviewGate?>()
        override suspend fun updateFlashcard(card: FlashcardEntity) {
            next.getAndSet(null)?.let {
                it.entered.complete(Unit)
                it.release.await()
                if (it.fail) throw IllegalStateException("复习记录保存失败")
            }
            delegate.updateFlashcard(card)
        }
    }

    companion object {
        private const val setTitle = "计算理论与算法分析设计第三章全部知识点及课后习题复习集"
        private fun summary() = StudySetSummary("z-old", null, "计算理论与算法分析设计", setTitle, "manual", null, false, 2, 2, 2, 10, 10)
        private fun set(id: String, title: String, time: Long) = StudySetEntity(id, null, null, title, "manual", null, false, time, time)
        private fun question(id: String, setId: String) = QuizQuestionEntity(id, setId, "SHORT_ANSWER", "问题 $id", "", "答案 $id", "解析 $id", null)
        private fun card(id: String, setId: String) = FlashcardEntity(
            id = id, studySetId = setId, type = "QA_FLASHCARD", title = "卡片 $id", front = "问题 $id", back = "答案 $id",
            explanation = "", example = "", pitfall = "", formula = "", tags = "算法,复习", sourceLocation = "", sourceQuote = "", hint = "",
            difficulty = "MEDIUM", confidence = 1f, createdByAi = false, editedByUser = true, nextReviewAt = 0, lastReviewedAt = null,
            reviewCount = 0, createdAt = 10, updatedAt = 10)
    }
}
