package com.github.garynasser.correction_notebook.data.model.yanhe

import com.google.gson.annotations.SerializedName
data class VideoTokenResponse(
    @SerializedName("token")
    val token: String = "",

    @SerializedName("expired_at")
    val expiredAt: Long = 0,

    @SerializedName("now")
    val now: Long = 0
)