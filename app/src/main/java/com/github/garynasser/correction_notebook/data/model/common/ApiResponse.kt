package com.github.garynasser.correction_notebook.data.model.common

import com.google.gson.annotations.SerializedName

data class ApiResponse <T> (
    val code: Int,
    @SerializedName("msg")
    val message: String,
    val data: T?
)