package com.github.garynasser.correction_notebook.data.local.ai

import android.content.Context
import android.util.Base64
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiCredentialCipher @Inject constructor(
    @ApplicationContext context: Context
) {
    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "ai_secret_key_store",
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val secretKey: SecretKey by lazy {
        val existing = sharedPreferences.getString(KEY_ALIAS, null)
        if (existing != null) {
            javax.crypto.spec.SecretKeySpec(Base64.decode(existing, Base64.DEFAULT), ALGORITHM)
        } else {
            val generated = javax.crypto.KeyGenerator.getInstance(ALGORITHM).apply {
                init(KEY_SIZE_BITS)
            }.generateKey()
            sharedPreferences.edit()
                .putString(KEY_ALIAS, Base64.encodeToString(generated.encoded, Base64.NO_WRAP))
                .apply()
            generated
        }
    }

    fun encrypt(rawValue: String): String {
        if (rawValue.isBlank()) return ""

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val encrypted = cipher.doFinal(rawValue.toByteArray(Charsets.UTF_8))

        return Base64.encodeToString(iv + encrypted, Base64.NO_WRAP)
    }

    fun decrypt(encryptedValue: String): String {
        if (encryptedValue.isBlank()) return ""

        val decoded = Base64.decode(encryptedValue, Base64.DEFAULT)
        val iv = decoded.copyOfRange(0, GCM_IV_SIZE_BYTES)
        val cipherText = decoded.copyOfRange(GCM_IV_SIZE_BYTES, decoded.size)

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(cipherText).toString(Charsets.UTF_8)
    }

    companion object {
        private const val KEY_ALIAS = "ai_database_secret_key"
        private const val ALGORITHM = "AES"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val KEY_SIZE_BITS = 256
        private const val GCM_IV_SIZE_BYTES = 12
        private const val GCM_TAG_LENGTH_BITS = 128
    }
}
