package com.github.garynasser.correction_notebook.data.remote.cas

import android.annotation.SuppressLint
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.X509EncodedKeySpec
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

internal object BitSsoCrypto {
    private val random = SecureRandom()
    private val encoder = Base64.getEncoder()
    private val decoder = Base64.getDecoder()

    fun encrypt(value: String, encodedKey: String): String = encoder.encodeToString(
        aes(Cipher.ENCRYPT_MODE, decoder.decode(encodedKey)).doFinal(value.toByteArray(Charsets.UTF_8))
    )

    fun csrfHeaders(): Map<String, String> {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"
        val key = buildString { repeat(32) { append(alphabet[random.nextInt(alphabet.length)]) } }
        val encoded = encoder.encodeToString(key.toByteArray(Charsets.US_ASCII))
        val mixed = encoded.take(encoded.length / 2) + encoded + encoded.drop(encoded.length / 2)
        return mapOf("Csrf-Key" to key, "Csrf-Value" to digest("MD5", mixed))
    }

    class PhoneRequest(val body: String, val encryptedKey: String, val key: ByteArray)

    fun phoneRequest(userId: String): PhoneRequest {
        val key = ByteArray(16).also(random::nextBytes)
        val json = JsonObject().apply { addProperty("userId", userId) }
        val rsa = Cipher.getInstance("RSA/ECB/PKCS1Padding").apply {
            val encoded = PUBLIC_KEY.replace(Regex("\\s"), "")
            init(Cipher.ENCRYPT_MODE, KeyFactory.getInstance("RSA")
                .generatePublic(X509EncodedKeySpec(decoder.decode(encoded))))
        }
        return PhoneRequest(
            encoder.encodeToString(aes(Cipher.ENCRYPT_MODE, key).doFinal(json.toString().toByteArray(Charsets.UTF_8))),
            encoder.encodeToString(rsa.doFinal(encoder.encode(key))), key
        )
    }

    fun phoneResponse(body: String, key: ByteArray): JsonObject {
        var current = body.trim()
        repeat(4) {
            val parsed = runCatching { JsonParser.parseString(current) }.getOrNull()
            if (parsed?.isJsonObject == true) return parsed.asJsonObject
            current = if (parsed?.isJsonPrimitive == true && parsed.asJsonPrimitive.isString) {
                parsed.asString
            } else {
                String(aes(Cipher.DECRYPT_MODE, key).doFinal(decoder.decode(current)), Charsets.UTF_8)
            }
        }
        throw CasAuthException("学校短信验证响应格式异常")
    }

    fun digest(algorithm: String, value: String): String = MessageDigest.getInstance(algorithm)
        .digest(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    @SuppressLint("GetInstance")
    private fun aes(mode: Int, key: ByteArray): Cipher {
        require(key.size in setOf(16, 24, 32)) { "统一认证加密参数无效" }
        return Cipher.getInstance("AES/ECB/PKCS5Padding").apply { init(mode, SecretKeySpec(key, "AES")) }
    }

    // School URL-crypto public key, as documented by BIT101-dev/BIT-Login.
    private const val PUBLIC_KEY = """
MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAjVr1zKwohU3xA0afprWLSQvIymaSH/V27MedFc+CecXSnORIFMAp4uEIb4taDq/2X4eMeTI66Mu/rB5GKSFDbExF2Gu4NaO/CNDpf1gHMScUrIFCh4CDqzBnx17kclvezLkIK0T8FVa4cRsINvzjbnA6jUSMaf6Fm1n9wTAtW6QYBjssGOEtCj+c38PTBdFMmJbXp3brt1tEBesz6lb3Fjp76FGvDZ08xtYG8fxYPuiMwKU04eS+mcX/BunwgpU3zwekHYB+PWRIvq0lBry9Wms25sJE5T/RAv5fEuMLbBkfcZK3+7ivSZthTmPpr2Ap/ji70ZZ6u2jvR5VJq+LJHQIDAQAB
"""
}
