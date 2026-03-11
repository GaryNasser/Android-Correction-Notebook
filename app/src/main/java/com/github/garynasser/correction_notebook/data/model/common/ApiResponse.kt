package com.github.garynasser.correction_notebook.data.model.common

data class ApiResponse <T> (
    val code: Int,
    val message: String,
    val data: T?
)