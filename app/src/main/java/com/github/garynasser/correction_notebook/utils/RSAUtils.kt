package com.github.garynasser.correction_notebook.utils

import android.util.Base64
import java.security.KeyFactory
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher

object RSAUtils {

    private const val RSA_TRANSFORMATION = "RSA/ECB/PKCS1Padding"
    private const val ALGORITHM_RSA = "RSA"

    fun encrypt(plainText: String, publicKeyBase64: String): String {
        return try {
            val publicBytes = Base64.decode(publicKeyBase64, Base64.DEFAULT)
            val keySpec = X509EncodedKeySpec(publicBytes)
            val keyFactory = KeyFactory.getInstance(ALGORITHM_RSA)
            val publicKey = keyFactory.generatePublic(keySpec)

            val cipher = Cipher.getInstance(RSA_TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, publicKey)

            val encryptedBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))

            Base64.encodeToString(encryptedBytes, Base64.NO_WRAP)
        } catch (e: Exception) {
            e.printStackTrace()
            throw RuntimeException("RSA加密失败: ${e.message}")
        }
    }
}