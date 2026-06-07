package com.github.garynasser.correction_notebook.domain.usecase

import com.github.garynasser.correction_notebook.data.local.ai.UserMemoryEntity
import com.github.garynasser.correction_notebook.data.model.ai.AiActionResult
import com.github.garynasser.correction_notebook.data.model.ai.MemoryCategory
import com.github.garynasser.correction_notebook.data.model.ai.NormalizedChatMessage
import com.github.garynasser.correction_notebook.data.model.home.ScheduleRange
import com.github.garynasser.correction_notebook.data.remote.ai.AiActionParser
import com.github.garynasser.correction_notebook.data.repository.AIRepository
import com.github.garynasser.correction_notebook.data.repository.CourseLearningRepository
import com.github.garynasser.correction_notebook.data.repository.KnowledgeContextChunk
import com.github.garynasser.correction_notebook.data.repository.KnowledgeBaseAiRepository
import com.github.garynasser.correction_notebook.data.repository.KnowledgeBaseRepository
import com.github.garynasser.correction_notebook.data.repository.MemoryRepository
import com.github.garynasser.correction_notebook.data.repository.ScheduleRepository
import com.github.garynasser.correction_notebook.data.repository.StudySessionRepository
import com.github.garynasser.correction_notebook.data.repository.TodoRepository
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiStudyUseCase @Inject constructor(
    private val aiRepository: AIRepository,
    private val knowledgeBaseAiRepository: KnowledgeBaseAiRepository,
    private val memoryRepository: MemoryRepository,
    private val todoRepository: TodoRepository,
    private val scheduleRepository: ScheduleRepository,
    private val studySessionRepository: StudySessionRepository,
    private val courseLearningRepository: CourseLearningRepository,
    private val knowledgeBaseRepository: KnowledgeBaseRepository
) {
    suspend fun chat(
        messages: List<NormalizedChatMessage>,
        systemPrompt: String = DEFAULT_TUTOR_PROMPT
    ): Result<String> {
        return aiRepository.sendChat(
            messages = messages,
            systemPrompt = systemPrompt,
            memorySummary = memorySummary()
        )
    }

    suspend fun askKnowledgeBase(
        question: String,
        folderId: String? = null,
        fileId: String? = null,
        courseId: Int? = null
    ): Result<String> {
        fileId?.let { knowledgeBaseAiRepository.ensureIndexed(it) }
        val chunks = knowledgeBaseAiRepository.searchContext(question, folderId, fileId, courseId)
        if (chunks.isEmpty()) {
            return Result.failure(Exception("没有检索到可用资料，请先导入 txt/md/docx/pptx，或尝试换一种问法"))
        }
        val context = chunks.toPromptContext()
        val prompt = """
            你是 BITStudy 的资料库学习助手。请只基于给定资料回答。
            如果资料不足，先说明不足，再给出可以继续查找的方向。
            回答末尾用“参考资料”列出用到的文件名。

            资料：
            $context
        """.trimIndent()
        return aiRepository.sendChat(
            messages = listOf(NormalizedChatMessage("user", question)),
            systemPrompt = prompt,
            memorySummary = memorySummary()
        )
    }

    suspend fun summarizeKnowledgeFile(fileId: String, mode: KnowledgeAiMode): Result<String> {
        val chunks = knowledgeBaseAiRepository.contextForFile(fileId)
        if (chunks.isEmpty()) {
            return Result.failure(Exception("当前文件暂不支持抽取文本，PDF 第一版不做 OCR"))
        }
        val context = chunks.toPromptContext()
        val instruction = when (mode) {
            KnowledgeAiMode.SUMMARY -> "总结这份资料，输出核心内容、关键概念和适合复习的顺序，控制在 800 字以内。"
            KnowledgeAiMode.KEY_POINTS -> "提取这份资料的高频考点、易错点和必须记住的公式/定义，用条目输出，控制在 800 字以内。"
            KnowledgeAiMode.QUIZ -> "基于这份资料生成 6 道复习题，包含答案和简短解析，避免过长。"
            KnowledgeAiMode.GLOSSARY -> "提取这份资料中的关键术语，输出术语、定义、适用场景和易混点，控制在 800 字以内。"
            KnowledgeAiMode.FORMULA_SHEET -> "提取这份资料中的公式、变量含义、适用条件和常见错误，控制在 800 字以内。"
            KnowledgeAiMode.REVIEW_CHECKLIST -> "基于这份资料生成复习清单，按必须掌握、需要练习、考前回看分组，控制在 800 字以内。"
        }
        return aiRepository.sendChat(
            messages = listOf(NormalizedChatMessage("user", "$instruction\n\n资料内容：\n$context")),
            systemPrompt = DEFAULT_TUTOR_PROMPT,
            memorySummary = memorySummary()
        )
    }

    suspend fun summarizeKnowledgeFileStructured(fileId: String, mode: KnowledgeAiMode): Result<AiActionResult> {
        return summarizeKnowledgeFile(fileId, mode).map { raw ->
            AiActionParser.parse(raw)
        }
    }

    suspend fun generateTodayPlan(targetDate: LocalDate = LocalDate.now()): Result<AiActionResult> {
        val context = buildTodayLearningContext(targetDate)
        val dateLabel = formatTargetDate(targetDate)
        val prompt = """
            请根据 BITStudy 当前数据生成$dateLabel 的学习计划。
            输出 3-5 个 planBlocks，并给出可以由用户确认执行的 actions。
            只能建议，不要假装已经创建任务。

            ${AiActionParser.instruction()}

            当前学习上下文：
            $context
        """.trimIndent()
        return aiRepository.sendChat(
            messages = listOf(NormalizedChatMessage("user", prompt)),
            systemPrompt = DEFAULT_TUTOR_PROMPT,
            memorySummary = memorySummary()
        ).map(AiActionParser::parse)
    }

    suspend fun generateTodayAdvice(targetDate: LocalDate = LocalDate.now()): Result<String> {
        val dateLabel = formatTargetDate(targetDate)
        val todos = todoRepository.todoItems.first()
            .filterNot { it.isCompleted }
            .take(8)
            .joinToString("\n") { "- ${it.title}（${it.priority}，截止：${it.dueDate ?: "无"}）" }
        val schedule = scheduleRepository.getEventsForRange(ScheduleRange.TODAY, today = targetDate)
            .flatMap { it.items }
            .take(8)
            .joinToString("\n") { "- ${it.title} ${it.startAt.toLocalTime()}-${it.endAt.toLocalTime()}" }
        val stats = studySessionRepository.getTodayStats()
        val weekMinutes = studySessionRepository.getWeekSessions().sumOf { it.durationMinutes }
        val prompt = """
            请根据 BITStudy 当前数据生成$dateLabel 的学习建议，给出 3-5 条可执行建议。
            不要自动创建任务，只输出建议。

            今日已学习：${stats.totalStudyMinutes} 分钟，番茄钟：${stats.completedPomodoros} 个
            近 7 日学习：$weekMinutes 分钟
            $dateLabel 课程/日程：
            ${schedule.ifBlank { "暂无" }}
            未完成待办：
            ${todos.ifBlank { "暂无" }}
        """.trimIndent()
        return aiRepository.sendChat(
            messages = listOf(NormalizedChatMessage("user", prompt)),
            systemPrompt = DEFAULT_TUTOR_PROMPT,
            memorySummary = memorySummary()
        )
    }

    suspend fun breakDownTodo(title: String, description: String): Result<String> {
        return aiRepository.sendChat(
            messages = listOf(
                NormalizedChatMessage(
                    "user",
                    "请把这个学习待办拆成 3-6 个可执行小任务，并建议优先级和预计耗时：\n$title\n$description"
                )
            ),
            systemPrompt = DEFAULT_TUTOR_PROMPT,
            memorySummary = memorySummary()
        )
    }

    suspend fun breakDownTodoStructured(title: String, description: String): Result<AiActionResult> {
        val prompt = """
            请把这个学习待办拆成 3-6 个可执行小任务，并为每个小任务生成 CREATE_TODO action。
            ${AiActionParser.instruction()}

            待办标题：$title
            说明：${description.ifBlank { "无" }}
        """.trimIndent()
        return aiRepository.sendChat(
            messages = listOf(NormalizedChatMessage("user", prompt)),
            systemPrompt = DEFAULT_TUTOR_PROMPT,
            memorySummary = memorySummary()
        ).map(AiActionParser::parse)
    }

    suspend fun summarizeCourseSection(sectionTitle: String, note: String): Result<String> {
        return aiRepository.sendChat(
            messages = listOf(
                NormalizedChatMessage(
                    "user",
                    """
                    请为这节课程生成学习辅助内容：
                    课程片段：$sectionTitle
                    我的笔记：${note.ifBlank { "暂无" }}

                    输出：本节摘要、关键概念、可能考点、课后复习任务。
                    """.trimIndent()
                )
            ),
            systemPrompt = DEFAULT_TUTOR_PROMPT,
            memorySummary = memorySummary()
        )
    }

    suspend fun summarizeCourseSectionStructured(
        courseId: Int,
        courseName: String,
        sectionId: Int,
        sectionTitle: String,
        note: String
    ): Result<AiActionResult> {
        val prompt = """
            请为这节课生成“AI 学习包”，包含 summary、SAVE_COURSE_NOTE action 和 2-4 个 CREATE_TODO action。
            ${AiActionParser.instruction()}

            课程 ID：$courseId
            课程名称：${courseName.ifBlank { "未知课程" }}
            章节 ID：$sectionId
            章节标题：$sectionTitle
            我的笔记：${note.ifBlank { "暂无" }}
        """.trimIndent()
        return aiRepository.sendChat(
            messages = listOf(NormalizedChatMessage("user", prompt)),
            systemPrompt = DEFAULT_TUTOR_PROMPT,
            memorySummary = memorySummary()
        ).map(AiActionParser::parse)
    }

    suspend fun generateStatsInsight(): Result<String> {
        val today = studySessionRepository.getTodayStats()
        val weekSessions = studySessionRepository.getWeekSessions()
        val courseProgress = courseLearningRepository.progressItems.first()
            .sortedByDescending { it.lastAccessedAt }
            .take(8)
            .joinToString("\n") {
                "- ${it.courseName.ifBlank { "课程 ${it.courseId}" }}：完成 ${it.completedCount}/${it.totalSections}，最近：${it.lastSectionTitle.ifBlank { "暂无" }}"
            }
        val prompt = """
            请只读以下数据，生成 BITStudy 学习统计解读。不要自动创建任务。
            输出：本周趋势、课程投入、可能拖延项、下周调整建议。控制在 600 字以内。

            今日学习：${today.totalStudyMinutes} 分钟，番茄钟：${today.completedPomodoros}
            近 7 日学习会话：
            ${weekSessions.joinToString("\n") { "- ${it.subject} ${it.durationMinutes} 分钟 ${it.startTime.toLocalDate()}" }.ifBlank { "暂无" }}
            课程进度：
            ${courseProgress.ifBlank { "暂无" }}
        """.trimIndent()
        return aiRepository.sendChat(
            messages = listOf(NormalizedChatMessage("user", prompt)),
            systemPrompt = DEFAULT_TUTOR_PROMPT,
            memorySummary = memorySummary()
        )
    }

    suspend fun saveMemory(category: String, content: String): Result<Unit> = runCatching {
        memoryRepository.saveMemory(
            UserMemoryEntity(
                category = normalizeMemoryCategory(category),
                content = content.trim(),
                confidence = 0.8f
            )
        )
    }

    private fun normalizeMemoryCategory(category: String): String {
        val trimmed = category.trim()
        return MemoryCategory.entries.firstOrNull {
            it.name == trimmed || it.label == trimmed
        }?.label ?: MemoryCategory.LEARNING_PREFERENCE.label
    }

    private suspend fun buildTodayLearningContext(targetDate: LocalDate): String {
        val dateLabel = formatTargetDate(targetDate)
        val todos = todoRepository.todoItems.first()
            .filterNot { it.isCompleted }
            .take(8)
            .joinToString("\n") { "- id=${it.id} ${it.title}（${it.priority}，截止：${it.dueDate ?: "无"}）" }
        val schedule = scheduleRepository.getEventsForRange(ScheduleRange.TODAY, today = targetDate)
            .flatMap { it.items }
            .take(8)
            .joinToString("\n") { "- ${it.title} ${it.startAt.toLocalTime()}-${it.endAt.toLocalTime()} ${it.location}" }
        val stats = studySessionRepository.getTodayStats()
        val weekMinutes = studySessionRepository.getWeekSessions().sumOf { it.durationMinutes }
        val recentCourses = courseLearningRepository.getRecentProgress(5).joinToString("\n") {
            "- courseId=${it.courseId} ${it.courseName.ifBlank { "未知课程" }}，进度 ${it.completedCount}/${it.totalSections}，最近 ${it.lastSectionTitle}"
        }
        val recentFiles = knowledgeBaseRepository.observeRecentFiles(5).first().joinToString("\n") {
            "- fileId=${it.id} ${it.displayName} ${it.courseName?.let { name -> "课程：$name" } ?: ""}"
        }
        return """
            目标日期：$dateLabel
            今日已学习：${stats.totalStudyMinutes} 分钟，番茄钟：${stats.completedPomodoros} 个
            近 7 日学习：$weekMinutes 分钟
            $dateLabel 课程/日程：
            ${schedule.ifBlank { "暂无" }}
            未完成待办：
            ${todos.ifBlank { "暂无" }}
            最近课程：
            ${recentCourses.ifBlank { "暂无" }}
            最近资料：
            ${recentFiles.ifBlank { "暂无" }}
        """.trimIndent()
    }

    private fun formatTargetDate(date: LocalDate): String {
        val formatted = date.format(DateTimeFormatter.ofPattern("yyyy年M月d日"))
        return if (date == LocalDate.now()) "今天（$formatted）" else formatted
    }

    private suspend fun memorySummary(): String {
        return memoryRepository.observeMemories().first()
            .take(12)
            .joinToString("\n") { "- [${it.category}] ${it.content}" }
    }

    private fun List<KnowledgeContextChunk>.toPromptContext(maxChars: Int = MAX_KNOWLEDGE_CONTEXT_CHARS): String {
        val builder = StringBuilder()
        for (chunk in this) {
            val block = "来源：${chunk.title}\n路径：${chunk.path}\n内容：${chunk.content}\n\n"
            if (builder.length + block.length > maxChars) {
                val remaining = maxChars - builder.length
                if (remaining > 200) {
                    builder.append(block.take(remaining))
                    builder.append("\n（后续资料已因上下文长度自动截断）")
                }
                break
            }
            builder.append(block)
        }
        return builder.toString().trim()
    }

    companion object {
        private const val MAX_KNOWLEDGE_CONTEXT_CHARS = 10_000
        private const val DEFAULT_TUTOR_PROMPT = """
            你是 BITStudy 内置 AI 学习导师。
            你的目标是帮助用户理解资料、制定计划、复习巩固。
            回答要具体、可执行，默认使用中文。
        """
    }
}

enum class KnowledgeAiMode {
    SUMMARY,
    KEY_POINTS,
    QUIZ,
    GLOSSARY,
    FORMULA_SHEET,
    REVIEW_CHECKLIST
}
