package com.github.garynasser.correction_notebook.data.model.auth

import com.google.gson.annotations.SerializedName

data class RSAResponse (
    @SerializedName("keyId")
    val keyId: String,

    @SerializedName("publicKey")
    val publicKey: String
)