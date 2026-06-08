package com.github.garynasser.correction_notebook.data.model.course

import java.time.LocalDate

data class CourseGoal(
    val courseId: Int,
    val examDate: LocalDate? = null,
    val targetScore: Float? = null,
    val weight: Float? = null,
    val weaknessLevel: Int = 0,
    val notes: String = ""
)
