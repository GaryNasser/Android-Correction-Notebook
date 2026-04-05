package com.github.garynasser.correction_notebook.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.github.garynasser.correction_notebook.data.model.auth.UserCredential
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CredentialManager @Inject constructor(
    @ApplicationContext private val context: Context
){
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "bit_student_credential", // 文件名
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveCredentials(credential: UserCredential) {
        sharedPreferences.edit().apply {
            putString("KEY_STUDENT_ID", credential.studentId)
            putString("KEY_PASSWORD", credential.password)
            apply()
        }
    }

    fun removeCredentials() {
        sharedPreferences.edit { clear() }
    }
    fun getCredentials(): UserCredential? {
        val id = sharedPreferences.getString("KEY_STUDENT_ID", null)
        val pass = sharedPreferences.getString("KEY_PASSWORD", null)

        return if (id != null && pass != null) {
            UserCredential(id, pass)
        } else {
            null
        }
    }
}