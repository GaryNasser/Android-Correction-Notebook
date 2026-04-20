package com.github.garynasser.correction_notebook.data.model.auth

import com.google.gson.annotations.SerializedName

data class RefreshRequest (
    @SerializedName("refresh_token")
    val refreshToken: String
)