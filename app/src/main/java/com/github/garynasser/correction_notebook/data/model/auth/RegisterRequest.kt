package com.github.garynasser.correction_notebook.data.model.auth

import com.google.gson.annotations.SerializedName

data class RegisterRequest(
    @SerializedName("username")
    val username: String,
    @SerializedName("password")
    val password: String,
    @SerializedName("studentCredential")
    val credentialAuthRequest: CredentialAuthRequest
)
