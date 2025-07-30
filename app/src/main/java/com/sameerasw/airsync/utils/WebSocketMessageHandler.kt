package com.sameerasw.airsync.utils

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

object WebSocketMessageHandler {
    private const val TAG = "WebSocketMessageHandler"

    /**
     * Handle incoming WebSocket messages from Mac
     */
    fun handleIncomingMessage(context: Context, message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val json = JSONObject(message)
                val type = json.getString("type")
                val data = json.getJSONObject("data")

                Log.d(TAG, "Received message type: $type")

                when (type) {
                    "dismissNotification" -> handleNotificationDismissal(context, data)
                    "mediaControl" -> handleMediaControl(context, data)
                    "volumeControl" -> handleVolumeControl(context, data)
                    else -> {
                        Log.w(TAG, "Unknown message type: $type")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error parsing incoming message: ${e.message}")
                Log.e(TAG, "Message was: $message")
            }
        }
    }

    /**
     * Handle notification dismissal request from Mac
     */
    private suspend fun handleNotificationDismissal(context: Context, data: JSONObject) {
        try {
            val notificationId = data.getString("id")
            Log.d(TAG, "Dismissing notification: $notificationId")

            val success = NotificationDismissalUtil.dismissNotification(context, notificationId)

            // Send response back to Mac
            val response = JsonUtil.createNotificationDismissalResponse(
                id = notificationId,
                success = success,
                message = if (success) "Notification dismissed successfully" else "Failed to dismiss notification"
            )

            WebSocketUtil.sendMessage(response)
            Log.d(TAG, "Sent dismissal response: $success for notification $notificationId")

        } catch (e: Exception) {
            Log.e(TAG, "Error handling notification dismissal: ${e.message}")
            // Send error response
            val errorResponse = JsonUtil.createNotificationDismissalResponse(
                id = "unknown",
                success = false,
                message = "Error: ${e.message}"
            )
            WebSocketUtil.sendMessage(errorResponse)
        }
    }

    /**
     * Handle media control request from Mac
     */
    private suspend fun handleMediaControl(context: Context, data: JSONObject) {
        try {
            val action = data.getString("action")
            Log.d(TAG, "Performing media action: $action")

            val success = when (action) {
                "play" -> MediaControlUtil.playPause(context)
                "pause" -> MediaControlUtil.playPause(context)
                "playPause" -> MediaControlUtil.playPause(context)
                "next" -> MediaControlUtil.skipNext(context)
                "previous" -> MediaControlUtil.skipPrevious(context)
                "stop" -> MediaControlUtil.stop(context)
                else -> {
                    Log.w(TAG, "Unknown media action: $action")
                    false
                }
            }

            // Send response back to Mac
            val response = JsonUtil.createMediaControlResponse(
                action = action,
                success = success,
                message = if (success) "Media action executed successfully" else "Failed to execute media action"
            )

            WebSocketUtil.sendMessage(response)
            Log.d(TAG, "Sent media control response: $success for action $action")

            // If successful, trigger a device status sync to update the Mac with new state
            if (success) {
                SyncManager.onMediaStateChanged(context)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error handling media control: ${e.message}")
            // Send error response
            val errorResponse = JsonUtil.createMediaControlResponse(
                action = "unknown",
                success = false,
                message = "Error: ${e.message}"
            )
            WebSocketUtil.sendMessage(errorResponse)
        }
    }

    /**
     * Handle volume control request from Mac
     */
    private suspend fun handleVolumeControl(context: Context, data: JSONObject) {
        try {
            val action = data.getString("action")
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager

            Log.d(TAG, "Performing volume action: $action")

            val success = when (action) {
                "volumeUp" -> {
                    audioManager.adjustStreamVolume(
                        android.media.AudioManager.STREAM_MUSIC,
                        android.media.AudioManager.ADJUST_RAISE,
                        0
                    )
                    true
                }
                "volumeDown" -> {
                    audioManager.adjustStreamVolume(
                        android.media.AudioManager.STREAM_MUSIC,
                        android.media.AudioManager.ADJUST_LOWER,
                        0
                    )
                    true
                }
                "mute" -> {
                    audioManager.adjustStreamVolume(
                        android.media.AudioManager.STREAM_MUSIC,
                        android.media.AudioManager.ADJUST_MUTE,
                        0
                    )
                    true
                }
                "unmute" -> {
                    audioManager.adjustStreamVolume(
                        android.media.AudioManager.STREAM_MUSIC,
                        android.media.AudioManager.ADJUST_UNMUTE,
                        0
                    )
                    true
                }
                "setVolume" -> {
                    val volume = data.getInt("volume")
                    val maxVolume = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                    val targetVolume = (volume * maxVolume / 100).coerceIn(0, maxVolume)
                    audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, targetVolume, 0)
                    true
                }
                else -> {
                    Log.w(TAG, "Unknown volume action: $action")
                    false
                }
            }

            // Send response back to Mac
            val response = JsonUtil.createMediaControlResponse(
                action = action,
                success = success,
                message = if (success) "Volume action executed successfully" else "Failed to execute volume action"
            )

            WebSocketUtil.sendMessage(response)
            Log.d(TAG, "Sent volume control response: $success for action $action")

            // If successful, trigger a device status sync to update the Mac with new volume
            if (success) {
                SyncManager.onVolumeChanged(context)
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error handling volume control: ${e.message}")
            // Send error response
            val errorResponse = JsonUtil.createMediaControlResponse(
                action = "unknown",
                success = false,
                message = "Error: ${e.message}"
            )
            WebSocketUtil.sendMessage(errorResponse)
        }
    }
}
