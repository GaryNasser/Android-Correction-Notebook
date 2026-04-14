package com.github.garynasser.correction_notebook.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import javax.inject.Inject
import javax.inject.Singleton

/**
 * BITShare 网络环境检测器
 * 用于检测当前是否在 BIT 校园网内网环境
 */
@Singleton
class BitShareNetworkDetector @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        private const val TAG = "BitShareNetwork"
        // BIT 校园网内网 IP
        private const val BITSHARE_INTRANET_IP = "10.170.35.57"
        // BITShare 外网域名
        private const val BITSHARE_INTERNET_HOST = "app.bitshare.com.cn"
        // 检测超时时间（毫秒）
        private const val PING_TIMEOUT_MS = 3000
    }

    /**
     * 当前网络环境
     */
    enum class NetworkEnvironment {
        INTRANET,   // 校园网内网
        INTERNET,   // 外网
        UNKNOWN     // 未知
    }

    /**
     * 获取当前网络环境
     */
    fun getCurrentEnvironment(): NetworkEnvironment {
        return try {
            // 首先检查是否是 WiFi 网络
            if (!isWifiConnected()) {
                Log.d(TAG, "Not connected to WiFi, assuming internet")
                return NetworkEnvironment.INTERNET
            }

            // 尝试解析内网 IP 对应的域名
            val canResolveIntranet = runCatching {
                InetAddress.getByName(BITSHARE_INTRANET_IP).isReachable(PING_TIMEOUT_MS)
            }.getOrDefault(false)

            if (canResolveIntranet) {
                Log.d(TAG, "Intranet detected - can reach 10.170.35.57")
                NetworkEnvironment.INTRANET
            } else {
                Log.d(TAG, "Internet mode - cannot reach intranet")
                NetworkEnvironment.INTERNET
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error detecting network environment", e)
            NetworkEnvironment.UNKNOWN
        }
    }

    /**
     * 异步检测网络环境（带重试）
     */
    suspend fun detectEnvironmentWithRetry(maxRetries: Int = 2): NetworkEnvironment = withContext(Dispatchers.IO) {
        repeat(maxRetries) { attempt ->
            val env = getCurrentEnvironment()
            if (env != NetworkEnvironment.UNKNOWN) {
                return@withContext env
            }
            Log.d(TAG, "Attempt ${attempt + 1} failed, retrying...")
        }
        // 如果多次检测都失败，尝试连接外网
        NetworkEnvironment.INTERNET
    }

    /**
     * 获取 BITShare 服务器地址
     * 根据网络环境自动选择内网 IP 或外网域名
     */
    fun getBitShareBaseUrl(): String {
        return when (getCurrentEnvironment()) {
            NetworkEnvironment.INTRANET -> "http://$BITSHARE_INTRANET_IP:8890/"
            NetworkEnvironment.INTERNET -> "https://$BITSHARE_INTERNET_HOST/"
            NetworkEnvironment.UNKNOWN -> "https://$BITSHARE_INTERNET_HOST/" // 默认使用外网
        }
    }

    /**
     * 获取当前使用的服务器类型描述
     */
    fun getCurrentServerType(): String {
        return when (getCurrentEnvironment()) {
            NetworkEnvironment.INTRANET -> "校园网内网 (10.170.35.57:8890)"
            NetworkEnvironment.INTERNET -> "外网 (app.bitshare.com.cn)"
            NetworkEnvironment.UNKNOWN -> "外网 (默认)"
        }
    }

    /**
     * 检查是否已连接校园网 WiFi
     * 注意：这只是一个近似检测，不能保证 100% 准确
     */
    fun isConnectedToCampusWifi(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false

            // 检查是否是 WiFi
            val isWifi = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)

            // 注意：这里不能通过 WiFi SSID 来判断是否校园网，
            // 因为应用可能没有定位权限
            // 更好的方式是通过是否能访问内网 IP 来判断
            isWifi
        } catch (e: Exception) {
            Log.e(TAG, "Error checking WiFi connection", e)
            false
        }
    }

    /**
     * 异步检测是否能访问内网
     */
    suspend fun canAccessIntranet(): Boolean = withContext(Dispatchers.IO) {
        try {
            val address = InetAddress.getByName(BITSHARE_INTRANET_IP)
            address.isReachable(PING_TIMEOUT_MS)
        } catch (e: Exception) {
            Log.e(TAG, "Intranet check failed", e)
            false
        }
    }

    private fun isWifiConnected(): Boolean {
        return try {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        } catch (e: Exception) {
            false
        }
    }
}
