package com.github.garynasser.correction_notebook.utils

import androidx.annotation.OptIn
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import java.security.MessageDigest

object SignatureUtils {
    private const val MAGIC = "1138b69dfef641d9d7ba49137d2d4875"
    private const val DEFAULT_PLATFORM = "100"
    private const val DIRECT_MEDIA_HASH = "3af07e494c9f18694d1025e3584865f0"

    private fun String.md5(): String {
        val bytes = MessageDigest.getInstance("MD5").digest(this.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    @OptIn(UnstableApi::class)
    fun encryptURL(url: String): String {
        val queryStart = url.indexOf('?')
        val baseUrl = if (queryStart >= 0) url.substring(0, queryStart) else url
        val suffix = if (queryStart >= 0) url.substring(queryStart) else ""
        val normalizedBaseUrl = if (baseUrl.contains(".ts", ignoreCase = true)) {
            removeExistingHashSegment(baseUrl)
        } else {
            baseUrl
        }
        val hash = if (isDirectMediaFile(normalizedBaseUrl)) {
            DIRECT_MEDIA_HASH
        } else {
            "${MAGIC}_$DEFAULT_PLATFORM".md5()
        }

        val urlList = normalizedBaseUrl.split("/").toMutableList()
        if (urlList.isNotEmpty()) {
            val lastIndex = urlList.size - 1
            if (lastIndex >= 1 && urlList[lastIndex - 1] == hash) {
                Log.d("VIDEO", "检测到路径已加密，跳过: $url")
                return normalizedBaseUrl + suffix
            }
            urlList.add(lastIndex, hash)
        }

        val finalUrl = urlList.joinToString("/") + suffix
        Log.d("VIDEO", "加密后的 URL: $finalUrl")
        return finalUrl
    }

    private fun removeExistingHashSegment(url: String): String {
        val parts = url.split("/").toMutableList()
        if (parts.size > 2 && parts[parts.size - 2].matches(Regex("^[0-9a-fA-F]{32}$"))) {
            parts.removeAt(parts.size - 2)
        }
        return parts.joinToString("/")
    }

    private fun isDirectMediaFile(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#')
        return listOf(".mp4", ".mpg", ".mov", ".aac")
            .any { path.endsWith(it, ignoreCase = true) }
    }

    fun getSignature(): Map<String, String> {
        val timestamp = (System.currentTimeMillis() / 1000).toString()

        val signSource = "${MAGIC}_v1_undefined"
        val signature = signSource.md5()

        return mapOf(
            "Xclient-Timestamp" to timestamp,
            "Xclient-Signature" to signature
        )
    }

    /**
     * 计算任意字符串的 MD5 哈希值
     * @param input 要计算 MD5 的输入字符串
     * @return 32 位小写 MD5 哈希值
     */
    fun getmd5(input: String): String {
        return input.md5()
    }
}
