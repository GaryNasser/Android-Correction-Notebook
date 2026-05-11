package com.github.garynasser.correction_notebook.domain.usecase

import com.github.garynasser.correction_notebook.data.local.ai.UserMemoryEntity
import com.github.garynasser.correction_notebook.data.model.ai.NormalizedChatMessage
import com.github.garynasser.correction_notebook.data.model.home.ScheduleRange
import com.github.garynasser.correction_notebook.data.repository.AIRepository
import com.github.garynasser.correction_notebook.data.repository.KnowledgeBaseAiRepository
import com.github.garynasser.correction_notebook.data.repository.MemoryRepository
import com.github.garynasser.correction_notebook.data.repository.ScheduleRepository
import com.github.garynasser.correction_notebook.data.repository.StudySessionRepository
import com.github.garynasser.correction_notebook.data.repository.TodoRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiStudyUseCase @Inject constructor(
    private val aiRepository: AIRepository,
    private val knowledgeBaseAiRepository: KnowledgeBaseAiRepository,
    private val memoryRepository: MemoryRepository,
    private val todoRepository: TodoRepository,
    private val scheduleRepository: ScheduleRepository,
    private val studySessionRepository: StudySessionRepository
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
        fileId: String? = null
    ): Result<String> {
        fileId?.let { knowledgeBaseAiRepository.ensureIndexed(it) }
        val chunks = knowledgeBaseAiRepository.searchContext(question, folderId, fileId)
        if (chunks.isEmpty()) {
            return Result.failure(Exception("没有检索到可用资料，请先导入 txt/md/docx/pptx，或尝试换一种问法"))
        }
        val context = chunks.joinToString("\n\n") { chunk ->
            "来源：${chunk.title}\n路径：${chunk.path}\n内容：${chunk.content}"
        }
        val prompt = """
            你是 StudyBIT 的资料库学习助手。请只基于给定资料回答。
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
        val context = chunks.joinToString("\n\n") { it.content }
        val instruction = when (mode) {
            KnowledgeAiMode.SUMMARY -> "总结这份资料，输出核心内容、关键概念和适合复习的顺序。"
            KnowledgeAiMode.KEY_POINTS -> "提取这份资料的高频考点、易错点和必须记住的公式/定义。"
            KnowledgeAiMode.QUIZ -> "基于这份资料生成 8 道复习题，包含答案和简短解析。"
        }
        return aiRepository.sendChat(
            messages = listOf(NormalizedChatMessage("user", "$instruction\n\n资料内容：\n$context")),
            systemPrompt = DEFAULT_TUTOR_PROMPT,
            memorySummary = memorySummary()
        )
    }

    suspend fun generateTodayAdvice(): Result<String> {
        val todos = todoRepository.todoItems.first()
            .filterNot { it.isCompleted }
            .take(8)
            .joinToString("\n") { "- ${it.title}（${it.priority}，截止：${it.dueDate ?: "无"}）" }
        val schedule = scheduleRepository.getEventsForRange(ScheduleRange.TODAY)
            .flatMap { it.items }
            .take(8)
            .joinToString("\n") { "- ${it.title} ${it.startAt.toLocalTime()}-${it.endAt.toLocalTime()}" }
        val stats = studySessionRepository.getTodayStats()
        val weekMinutes = studySessionRepository.getWeekSessions().sumOf { it.durationMinutes }
        val prompt = """
            请根据 StudyBIT 当前数据生成今天的学习建议，给出 3-5 条可执行建议。
            不要自动创建任务，只输出建议。

            今日已学习：${stats.totalStudyMinutes} 分钟，番茄钟：${stats.completedPomodoros} 个
            近 7 日学习：$weekMinutes 分钟
            今日课程/日程：
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

    suspend fun saveMemory(category: String, content: String): Result<Unit> = runCatching {
        memoryRepository.saveMemory(
            UserMemoryEntity(
                category = category.trim().ifBlank { "学习偏好" },
                content = content.trim(),
                confidence = 0.8f
            )
        )
    }

    private suspend fun memorySummary(): String {
        return memoryRepository.observeMemories().first()
            .take(12)
            .joinToString("\n") { "- [${it.category}] ${it.content}" }
    }

    companion object {
        private const val DEFAULT_TUTOR_PROMPT = """
            你是 StudyBIT 内置 AI 学习导师。
            你的目标是帮助用户理解资料、制定计划、复习巩固。
            回答要具体、可执行，默认使用中文。
        """
    }
}

enum class KnowledgeAiMode {
    SUMMARY,
    KEY_POINTS,
    QUIZ
}
