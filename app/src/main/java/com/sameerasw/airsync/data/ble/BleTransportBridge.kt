package com.sameerasw.airsync.data.ble

import android.util.Log
import com.sameerasw.airsync.domain.model.BatteryInfo
import com.sameerasw.airsync.domain.model.AudioInfo
import java.security.MessageDigest
import java.util.*

object BleTransportBridge {
    private const val TAG = "BleTransportBridge"
    
    private var gattServer: BleGattServer? = null

    fun initialize(server: BleGattServer) {
        gattServer = server
    }

    fun deriveAuthToken(symmetricKey: String): String {
        return try {
            val md = MessageDigest.getInstance("SHA-256")
            val hash = md.digest(symmetricKey.toByteArray(Charsets.UTF_8))
            Base64.getEncoder().encodeToString(hash.copyOf(16))
        } catch (e: Exception) {
            Log.e(TAG, "Error deriving auth token: ${e.message}")
            ""
        }
    }

    // --- Outbound (Android -> Mac) ---

    fun sendNotification(pkg: String, appName: String, title: String, text: String) {
        val payload = listOf(pkg, appName, title, text).joinToString(BleConstants.DELIMITER)
        gattServer?.sendChunkedNotification(BleConstants.CHAR_NOTIFICATION_DATA, payload)
    }

    fun sendBatteryStatus(battery: BatteryInfo) {
        val level = battery.level.toByte()
        gattServer?.sendNotification(BleConstants.CHAR_BATTERY_LEVEL, byteArrayOf(level))
    }

    fun sendMediaState(audio: AudioInfo) {
        val payload = listOf(
            if (audio.isPlaying) "1" else "0",
            audio.title,
            audio.artist,
            audio.volume.toString(),
            if (audio.isMuted) "1" else "0",
            audio.likeStatus,
            audio.albumArtLite ?: ""
        ).joinToString(BleConstants.DELIMITER)
        
        gattServer?.sendChunkedNotification(BleConstants.CHAR_MEDIA_STATE, payload)
    }

    fun sendSystemState(isDnd: Boolean, isPowerSave: Boolean) {
        val payload = listOf(
            if (isDnd) "1" else "0",
            if (isPowerSave) "1" else "0"
        ).joinToString(BleConstants.DELIMITER)
        
        gattServer?.sendNotification(BleConstants.CHAR_SYSTEM_STATE, payload.toByteArray())
    }

    fun sendClipboard(text: String) {
        gattServer?.sendChunkedNotification(BleConstants.CHAR_CLIPBOARD_DATA_NOTIFY, text)
    }

    fun sendNotificationDismissal(id: String) {
        gattServer?.sendChunkedNotification(BleConstants.CHAR_NOTIFICATION_DISMISS_NOTIFY, id)
    }

    fun sendDeviceName() {
        val name = android.os.Build.MODEL
        gattServer?.sendChunkedNotification(BleConstants.CHAR_DEVICE_NAME, name)
    }

    // --- Inbound (Mac -> Android) ---

    fun handleMediaControl(action: String, context: android.content.Context) {
        Log.d(TAG, "Media control from BLE: $action")
        when (action) {
            "playPause" -> com.sameerasw.airsync.utils.MediaControlUtil.playPause(context)
            "next" -> com.sameerasw.airsync.utils.MediaControlUtil.skipNext(context)
            "previous" -> com.sameerasw.airsync.utils.MediaControlUtil.skipPrevious(context)
        }
    }

    fun handleNotificationAction(data: String, context: android.content.Context) {
        Log.d(TAG, "Notification action from BLE: $data")
        val parts = data.split(BleConstants.DELIMITER)
        if (parts.size >= 2) {
            val id = parts[0]
            val actionName = parts[1]
            com.sameerasw.airsync.utils.NotificationDismissalUtil.performNotificationAction(id, actionName)
        }
    }
}
