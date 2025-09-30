package com.sameerasw.airsync.utils

import android.content.Context
import android.util.Log
import com.sameerasw.airsync.data.local.DataStoreManager
import kotlinx.coroutines.flow.first
import org.json.JSONObject

/**
 * Utility class for sharing Android device connection information
 * This helps the Mac know where to send wake-up requests
 */
object DeviceConnectionInfo {
    private const val TAG = "DeviceConnectionInfo"
    
    // Standard ports for the wake-up service
    const val HTTP_WAKEUP_PORT = 8888
    const val UDP_WAKEUP_PORT = 8889
    
    /**
     * Generate a connection info JSON that can be sent to Mac during handshake
     * This tells the Mac where to send wake-up requests
     */
    suspend fun generateConnectionInfo(context: Context): String {
        return try {
            val dataStoreManager = DataStoreManager(context)
            val deviceName = dataStoreManager.getDeviceName().first()
            val androidIp = DeviceInfoUtil.getWifiIpAddress(context)
            
            val connectionInfo = JSONObject().apply {
                put("androidDeviceName", deviceName)
                put("androidIp", androidIp ?: "unknown")
                put("httpWakeupPort", HTTP_WAKEUP_PORT)
                put("udpWakeupPort", UDP_WAKEUP_PORT)
                put("supportsWakeup", true)
                put("timestamp", System.currentTimeMillis())
            }
            
            connectionInfo.toString()
        } catch (e: Exception) {
            Log.e(TAG, "Error generating connection info", e)
            "{\"supportsWakeup\": false}"
        }
    }
    
    /**
     * Extract wake-up information from a Mac's connection request
     * This is used when parsing wake-up requests to understand Mac's details
     */
    fun parseMacConnectionInfo(jsonString: String): MacConnectionInfo? {
        return try {
            val json = JSONObject(jsonString)
            MacConnectionInfo(
                macIp = json.optString("macIp", ""),
                macPort = json.optInt("macPort", 6996),
                macName = json.optString("macName", "Mac"),
                encryptionKey = json.optString("encryptionKey", ""),
                timestamp = json.optLong("timestamp", System.currentTimeMillis())
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing Mac connection info", e)
            null
        }
    }
}

/**
 * Data class representing Mac connection information for wake-up requests
 */
data class MacConnectionInfo(
    val macIp: String,
    val macPort: Int,
    val macName: String,
    val encryptionKey: String,
    val timestamp: Long
) {
    fun isValid(): Boolean {
        return macIp.isNotEmpty() && macPort > 0 && encryptionKey.isNotEmpty()
    }
}