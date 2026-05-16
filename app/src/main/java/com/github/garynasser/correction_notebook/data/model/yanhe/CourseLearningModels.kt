package com.github.garynasser.correction_notebook.data.model.yanhe

data class CourseProgress(
    val courseId: Int,
    val courseName: String = "",
    val lastSectionId: Int = 0,
    val lastSectionTitle: String = "",
    val lastVideoUrl: String = "",
    val completedSectionIds: Set<Int> = emptySet(),
    val totalSections: Int = 0,
    val watchedMinutes: Int = 0,
    val lastAccessedAt: Long = System.currentTimeMillis()
) {
    val completedCount: Int get() = completedSectionIds.size
    val progressPercent: Int
        get() = if (totalSections <= 0) 0 else ((completedCount * 100f) / totalSections).toInt().coerceIn(0, 100)
}

data class CourseNote(
    val id: String = java.util.UUID.randomUUID().toString(),
    val courseId: Int,
    val courseName: String = "",
    val sectionId: Int,
    val sectionTitle: String,
    val content: String,
    val aiGenerated: Boolean = false,
    val createdAt: Long = System.currentTimeMillis()
)
