package com.github.garynasser.correction_notebook.data.model.auth

import com.google.gson.annotations.SerializedName

data class TokenResponse(
    @SerializedName("token")
    val accessToken: String,

    @SerializedName("refreshToken")
    val refreshToken: String?
)