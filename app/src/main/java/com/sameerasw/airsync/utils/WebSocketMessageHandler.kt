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
        try {
            val json = JSONObject(message)
            val type = json.optString("type")
            val data = json.optJSONObject("data")

            Log.d(TAG, "Handling message type: $type")

            when (type) {
                "volumeControl" -> handleVolumeControl(context, data)
                "mediaControl" -> handleMediaControl(context, data)
                "dismissNotification" -> handleNotificationDismissal(data)
                "disconnectRequest" -> handleDisconnectRequest()
                "ping" -> handlePing(context)
                else -> {
                    Log.w(TAG, "Unknown message type: $type")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming message: ${e.message}")
        }
    }

    private fun handleVolumeControl(context: Context, data: JSONObject?) {
        try {
            if (data == null) {
                Log.e(TAG, "Volume control data is null")
                sendVolumeControlResponse("setVolume", false, "No data provided")
                return
            }

            val action = data.optString("action")
            when (action) {
                "setVolume" -> {
                    val volume = data.optInt("volume", -1)
                    if (volume in 0..100) {
                        val success = VolumeControlUtil.setVolume(context, volume)
                        sendVolumeControlResponse(action, success, if (success) "Volume set to $volume%" else "Failed to set volume")

                        // Send updated device status after volume change
                        if (success) {
                            SyncManager.onVolumeChanged(context)
                        }
                    } else {
                        sendVolumeControlResponse(action, false, "Invalid volume value: $volume")
                    }
                }
                "increaseVolume" -> {
                    val increment = data.optInt("increment", 10)
                    val success = VolumeControlUtil.increaseVolume(context, increment)
                    sendVolumeControlResponse(action, success, if (success) "Volume increased by $increment%" else "Failed to increase volume")

                    if (success) {
                        SyncManager.onVolumeChanged(context)
                    }
                }
                "decreaseVolume" -> {
                    val decrement = data.optInt("decrement", 10)
                    val success = VolumeControlUtil.decreaseVolume(context, decrement)
                    sendVolumeControlResponse(action, success, if (success) "Volume decreased by $decrement%" else "Failed to decrease volume")

                    if (success) {
                        SyncManager.onVolumeChanged(context)
                    }
                }
                "toggleMute" -> {
                    val success = VolumeControlUtil.toggleMute(context)
                    sendVolumeControlResponse(action, success, if (success) "Mute toggled" else "Failed to toggle mute")

                    if (success) {
                        SyncManager.onVolumeChanged(context)
                    }
                }
                else -> {
                    Log.w(TAG, "Unknown volume control action: $action")
                    sendVolumeControlResponse(action, false, "Unknown action")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling volume control: ${e.message}")
            sendVolumeControlResponse("unknown", false, "Error: ${e.message}")
        }
    }

    private fun handleMediaControl(context: Context, data: JSONObject?) {
        try {
            if (data == null) {
                Log.e(TAG, "Media control data is null")
                sendMediaControlResponse("unknown", false, "No data provided")
                return
            }

            val action = data.optString("action")
            var success = false
            var message = ""

            when (action) {
                "playPause" -> {
                    success = MediaControlUtil.playPause(context)
                    message = if (success) "Play/pause toggled" else "Failed to toggle play/pause"
                }
                "play" -> {
                    success = MediaControlUtil.playPause(context) // Will play if not playing
                    message = if (success) "Playback started" else "Failed to start playback"
                }
                "pause" -> {
                    success = MediaControlUtil.playPause(context) // Will pause if playing
                    message = if (success) "Playback paused" else "Failed to pause playback"
                }
                "next" -> {
                    success = MediaControlUtil.skipNext(context)
                    message = if (success) "Skipped to next track" else "Failed to skip to next track"
                }
                "previous" -> {
                    success = MediaControlUtil.skipPrevious(context)
                    message = if (success) "Skipped to previous track" else "Failed to skip to previous track"
                }
                "stop" -> {
                    success = MediaControlUtil.stop(context)
                    message = if (success) "Playback stopped" else "Failed to stop playback"
                }
                else -> {
                    Log.w(TAG, "Unknown media control action: $action")
                    message = "Unknown action: $action"
                }
            }

            sendMediaControlResponse(action, success, message)

            // Send updated media state after successful control
            if (success) {
                SyncManager.onMediaStateChanged(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling media control: ${e.message}")
            sendMediaControlResponse("unknown", false, "Error: ${e.message}")
        }
    }

    private fun handleNotificationDismissal(data: JSONObject?) {
        try {
            if (data == null) {
                Log.e(TAG, "Notification dismissal data is null")
                sendNotificationDismissalResponse("unknown", false, "No data provided")
                return
            }

            val notificationId = data.optString("id")
            if (notificationId.isEmpty()) {
                sendNotificationDismissalResponse(notificationId, false, "No notification ID provided")
                return
            }

            val success = NotificationDismissalUtil.dismissNotification(notificationId)
            val message = if (success) "Notification dismissed" else "Failed to dismiss notification or notification not found"

            sendNotificationDismissalResponse(notificationId, success, message)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling notification dismissal: ${e.message}")
            sendNotificationDismissalResponse("unknown", false, "Error: ${e.message}")
        }
    }

    private fun handlePing(context: Context) {
        try {
            // Respond to ping with current device status
            SyncManager.checkAndSyncDeviceStatus(context, forceSync = true)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling ping: ${e.message}")
        }
    }

    private fun handleDisconnectRequest() {
        try {
            // Immediately disconnect the WebSocket
            WebSocketUtil.disconnect()
            Log.d(TAG, "WebSocket disconnected as per request")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling disconnect request: ${e.message}")
        }
    }

    private fun sendVolumeControlResponse(action: String, success: Boolean, message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val response = JsonUtil.createVolumeControlResponse(action, success, message)
            WebSocketUtil.sendMessage(response)
        }
    }

    private fun sendMediaControlResponse(action: String, success: Boolean, message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val response = JsonUtil.createMediaControlResponse(action, success, message)
            WebSocketUtil.sendMessage(response)
        }
    }

    private fun sendNotificationDismissalResponse(id: String, success: Boolean, message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val response = JsonUtil.createNotificationDismissalResponse(id, success, message)
            WebSocketUtil.sendMessage(response)
        }
    }
}
