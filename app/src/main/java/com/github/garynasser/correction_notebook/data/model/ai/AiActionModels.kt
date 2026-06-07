package com.github.garynasser.correction_notebook.data.model.ai

enum class AiActionType {
    CREATE_TODO,
    SAVE_COURSE_NOTE,
    CREATE_REVIEW_PLAN,
    SAVE_MEMORY,
    OPEN_COURSE,
    OPEN_FILE
}

enum class MemoryCategory(val label: String) {
    LEARNING_PREFERENCE("学习偏好"),
    WEAK_COURSE("薄弱课程"),
    TIME_HABIT("时间习惯"),
    EXAM_GOAL("目标考试"),
    RESOURCE_PREFERENCE("常用资料")
}

enum class KnowledgeAiScope {
    ALL,
    FOLDER,
    FILE,
    COURSE
}

data class AiAction(
    val id: String = java.util.UUID.randomUUID().toString(),
    val type: AiActionType,
    val title: String,
    val description: String = "",
    val payload: Map<String, String> = emptyMap(),
    val source: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class AiPlanBlock(
    val title: String,
    val reason: String = "",
    val estimatedMinutes: Int = 25,
    val courseId: Int? = null,
    val fileId: String? = null,
    val todoId: String? = null,
    val priority: String = "MEDIUM"
)

data class AiActionResult(
    val rawText: String,
    val summary: String = "",
    val actions: List<AiAction> = emptyList(),
    val planBlocks: List<AiPlanBlock> = emptyList(),
    val referencedMemories: List<String> = emptyList(),
    val parsed: Boolean = false
)
