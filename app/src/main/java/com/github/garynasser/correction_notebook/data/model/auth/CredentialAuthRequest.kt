package com.github.garynasser.correction_notebook.data.model.auth

import com.google.gson.annotations.SerializedName

data class CredentialAuthRequest(
    @SerializedName("keyId")
    val keyId: String,

    @SerializedName("studentId")
    val studentId: String,

    @SerializedName("encryptStudentPassword")
    val encryptStudentPassword: String
)
