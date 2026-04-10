package com.github.garynasser.correction_notebook.data.model.home

import java.time.LocalDate

data class DailyStats(
    val date: LocalDate,
    val totalStudyMinutes: Int = 0,
    val completedPomodoros: Int = 0,
    val subjectDistribution: Map<String, Int> = emptyMap()
)

data class WeeklyStats(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val dailyStats: List<DailyStats>,
    val totalStudyMinutes: Int,
    val averageDailyMinutes: Int
)

data class MonthlyStats(
    val year: Int,
    val month: Int,
    val dailyStats: List<DailyStats>,
    val totalStudyMinutes: Int,
    val averageDailyMinutes: Int
)
