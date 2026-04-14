package com.github.garynasser.correction_notebook.utils

import androidx.annotation.OptIn
import androidx.media3.common.util.Log
import androidx.media3.common.util.UnstableApi
import java.security.MessageDigest

object SignatureUtils {
    private const val MAGIC = "1138b69dfef641d9d7ba49137d2d4875"

    private fun String.md5(): String {
        val bytes = MessageDigest.getInstance("MD5").digest(this.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    @OptIn(UnstableApi::class)
    fun encryptURL(url: String): String {
        // 1. 计算 MD5: MD5(MAGIC + "_100")
        val input = MAGIC + "_100"
        val md = MessageDigest.getInstance("MD5")
        val hashBytes = md.digest(input.toByteArray(Charsets.UTF_8))
        val hash = hashBytes.joinToString("") { "%02x".format(it) }

        // 2. 切割 URL
        val urlList = url.split("/").toMutableList()

        // 3. 关键修正：检查是否已经加密过
        if (urlList.isNotEmpty()) {
            val lastIndex = urlList.size - 1

            // 检查倒数第二个元素（即我们要插入的位置）是否已经是这个 hash
            // 或者检查整个 URL 是否已经包含了这个路径段
            if (lastIndex >= 1 && urlList[lastIndex - 1] == hash) {
                // 如果已经存在该 hash 段，直接返回原 URL，不再重复插入
                Log.d("VIDEO", "检测到路径已加密，跳过: $url")
                return url
            }

            // 执行插入逻辑
            urlList.add(lastIndex, hash)
        }

        // 4. 重新拼接
        val finalUrl = urlList.joinToString("/")
        Log.d("VIDEO", "加密后的 URL: $finalUrl")
        return finalUrl
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