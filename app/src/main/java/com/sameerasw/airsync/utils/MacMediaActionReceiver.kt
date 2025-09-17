package com.sameerasw.airsync.utils

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class MacMediaActionReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "MacMediaActionReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = when (intent.action) {
            "com.sameerasw.airsync.MAC_MEDIA_play" -> "play"
            "com.sameerasw.airsync.MAC_MEDIA_pause" -> "pause"
            "com.sameerasw.airsync.MAC_MEDIA_previous" -> "previous"
            "com.sameerasw.airsync.MAC_MEDIA_next" -> "next"
            "com.sameerasw.airsync.MAC_MEDIA_stop" -> "stop"
            else -> {
                Log.w(TAG, "Unknown action: ${intent.action}")
                return
            }
        }

        try {
            // Send media control command to Mac via WebSocket
            val controlJson = """{"type":"macMediaControl","data":{"action":"$action"}}"""
            val success = WebSocketUtil.sendMessage(controlJson)

            if (success) {
                Log.d(TAG, "Sent Mac media control: $action")
            } else {
                Log.w(TAG, "Failed to send Mac media control: $action")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sending Mac media control: ${e.message}")
        }
    }
}
