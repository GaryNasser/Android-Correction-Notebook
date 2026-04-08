package com.github.garynasser.correction_notebook.utils

import java.security.MessageDigest

object SignatureUtils {
    private const val MAGIC = "1138b69dfef641d9d7ba49137d2d4875"

    private fun String.md5(): String {
        val bytes = MessageDigest.getInstance("MD5").digest(this.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun encryptURL(url: String): String {
        // 1. 计算 MD5: MD5(MAGIC + "_100")
        val input = MAGIC + "_100"
        val md = MessageDigest.getInstance("MD5")
        val hashBytes = md.digest(input.toByteArray(Charsets.UTF_8))
        val hash = hashBytes.joinToString("") { "%02x".format(it) }

        // 2. 切割 URL
        // 注意：Kotlin 的 split 会保留空字符串，这与 JS 的行为一致
        val urlList = url.split("/").toMutableList()

        // 3. 执行 splice(-1, 0, hash)
        // 在 JS 中，splice(-1, 0, hash) 是在最后一个元素之前插入
        if (urlList.isNotEmpty()) {
            val lastIndex = urlList.size - 1
            urlList.add(lastIndex, hash)
        }

        // 4. 重新拼接
        return urlList.joinToString("/")
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
}