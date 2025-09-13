package com.sameerasw.airsync.utils

import android.content.Context
import android.util.Log
import com.sameerasw.airsync.data.local.DataStoreManager
import com.sameerasw.airsync.data.repository.AirSyncRepositoryImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
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
                "clipboardUpdate" -> handleClipboardUpdate(context, data)
                "fileTransferInit" -> handleFileTransferInit(context, data)
                "fileChunk" -> handleFileChunk(context, data)
                "fileTransferComplete" -> handleFileTransferComplete(context, data)
                "volumeControl" -> handleVolumeControl(context, data)
                "mediaControl" -> handleMediaControl(context, data)
                "dismissNotification" -> handleNotificationDismissal(data)
                "notificationAction" -> handleNotificationAction(data)
                "disconnectRequest" -> handleDisconnectRequest(context)
                "toggleAppNotif" -> handleToggleAppNotification(context, data)
                "ping" -> handlePing(context)
                else -> {
                    Log.w(TAG, "Unknown message type: $type")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming message: ${e.message}")
        }
    }

    private fun handleFileTransferInit(context: Context, data: JSONObject?) {
        try {
            if (data == null) return
            val id = data.optString("id", java.util.UUID.randomUUID().toString())
            val name = data.optString("name")
            val size = data.optInt("size", 0)
            val mime = data.optString("mime", "application/octet-stream")
            val checksumVal = data.optString("checksum", "")

            FileReceiver.handleInit(context, id, name, size, mime, if (checksumVal.isBlank()) null else checksumVal)
            Log.d(TAG, "Started incoming file transfer: $name ($size bytes)")
        } catch (e: Exception) {
            Log.e(TAG, "Error in file init: ${e.message}")
        }
    }

    private fun handleFileChunk(context: Context, data: JSONObject?) {
        try {
            if (data == null) return
            val id = data.optString("id", "default")
            val index = data.optInt("index", 0)
            val chunk = data.optString("chunk", "")
                if (chunk.isNotEmpty()) {
                FileReceiver.handleChunk(context, id, index, chunk)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in file chunk: ${e.message}")
        }
    }

    private fun handleFileTransferComplete(context: Context, data: JSONObject?) {
        try {
            if (data == null) return
            val id = data.optString("id", "default")
            FileReceiver.handleComplete(context, id)
        } catch (e: Exception) {
            Log.e(TAG, "Error in file complete: ${e.message}")
        }
    }

    private fun handleClipboardUpdate(context: Context, data: JSONObject?) {
        try {
            if (data == null) {
                Log.e(TAG, "Clipboard update data is null")
                return
            }

            val text = data.optString("text")
            if (!text.isNullOrEmpty()) {
                ClipboardSyncManager.handleClipboardUpdate(context, text)
                Log.d(TAG, "Clipboard updated from desktop: ${text.take(50)}...")
            } else {
                Log.w(TAG, "Clipboard update received but text is empty")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling clipboard update: ${e.message}")
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
            var message: String

            when (action) {
                "playPause" -> {
                    success = MediaControlUtil.playPause(context)
                    message = if (success) "Play/pause toggled" else "Failed to toggle play/pause"
                }
                "play" -> {
                    success = MediaControlUtil.playPause(context)
                    message = if (success) "Playback started" else "Failed to start playback"
                }
                "pause" -> {
                    success = MediaControlUtil.playPause(context)
                    message = if (success) "Playback paused" else "Failed to pause playback"
                }
                "next" -> {
                    // Suppress automatic media updates before executing skip command
                    SyncManager.suppressMediaUpdatesForSkip()
                    success = MediaControlUtil.skipNext(context)
                    message = if (success) "Skipped to next track" else "Failed to skip to next track"
                }
                "previous" -> {
                    // Suppress automatic media updates before executing skip command
                    SyncManager.suppressMediaUpdatesForSkip()
                    success = MediaControlUtil.skipPrevious(context)
                    message = if (success) "Skipped to previous track" else "Failed to skip to previous track"
                }
                "stop" -> {
                    success = MediaControlUtil.stop(context)
                    message = if (success) "Playback stopped" else "Failed to stop playback"
                }
                // New: toggle like controls
                "toggleLike" -> {
                    success = MediaControlUtil.toggleLike(context)
                    message = if (success) "Like toggled" else "Failed to toggle like"
                }
                "like" -> {
                    success = MediaControlUtil.like(context)
                    message = if (success) "Liked" else "Failed to like"
                }
                "unlike" -> {
                    success = MediaControlUtil.unlike(context)
                    message = if (success) "Unliked" else "Failed to unlike"
                }
                else -> {
                    Log.w(TAG, "Unknown media control action: $action")
                    message = "Unknown action: $action"
                }
            }

            sendMediaControlResponse(action, success, message)

            // Send updated media state after successful control
            if (success) {
                // For track skip actions (next/previous), add a delay to allow media player to update
                CoroutineScope(Dispatchers.IO).launch {
                    val delayMs = when (action) {
                        "next", "previous" -> 1200L
                        else -> 400L // smaller delay for like/others
                    }
                    delay(delayMs)
                    SyncManager.onMediaStateChanged(context)
                }
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

    private fun handleNotificationAction(data: JSONObject?) {
        try {
            if (data == null) {
                Log.e(TAG, "Notification action data is null")
                sendNotificationActionResponse("unknown", "", false, "No data provided")
                return
            }

            val notificationId = data.optString("id")
            if (notificationId.isEmpty()) {
                sendNotificationActionResponse(notificationId, "", false, "No notification ID provided")
                return
            }

            // We accept either "name" or legacy "action" for action name
            val actionName = data.optString("name", data.optString("action", "")).ifEmpty { "" }
            val replyText = data.optString("text", null)

            if (actionName.isEmpty()) {
                sendNotificationActionResponse(notificationId, actionName, false, "No action name provided")
                return
            }

            val success = NotificationDismissalUtil.performNotificationAction(notificationId, actionName, replyText)
            val message = if (success) {
                if (replyText != null) "Reply sent" else "Action invoked"
            } else {
                "Failed to perform action or notification not found"
            }

            sendNotificationActionResponse(notificationId, actionName, success, message)
        } catch (e: Exception) {
            Log.e(TAG, "Error handling notification action: ${e.message}")
            sendNotificationActionResponse("unknown", "", false, "Error: ${e.message}")
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

    private fun handleDisconnectRequest(context: Context) {
        try {
            // Mark as intentional disconnect to prevent auto-reconnect
            kotlinx.coroutines.runBlocking {
                try {
                    val dataStoreManager = DataStoreManager(context)
                    dataStoreManager.setUserManuallyDisconnected(true)
                } catch (_: Exception) { }
            }
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

    private fun sendNotificationActionResponse(id: String, actionName: String, success: Boolean, message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val response = JsonUtil.createNotificationActionResponse(id, actionName, success, message)
            WebSocketUtil.sendMessage(response)
        }
    }

    private fun handleToggleAppNotification(context: Context, data: JSONObject?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (data == null) {
                    Log.e(TAG, "Toggle app notification data is null")
                    return@launch
                }

                val packageName = data.optString("package")
                val stateString = data.optString("state")

                if (packageName.isEmpty()) {
                    Log.e(TAG, "Package name is empty in toggle app notification")
                    return@launch
                }

                val newState = stateString.toBoolean()

                Log.d(TAG, "Toggling notification for package: $packageName to state: $newState")

                // Get the repository
                val dataStoreManager = DataStoreManager(context)
                val repository = AirSyncRepositoryImpl(dataStoreManager)

                // Get current apps
                val currentApps = repository.getNotificationApps().first().toMutableList()

                // Find and update the app
                val appIndex = currentApps.indexOfFirst { it.packageName == packageName }

                if (appIndex != -1) {
                    // Update existing app
                    val updatedApp = currentApps[appIndex].copy(isEnabled = newState)
                    currentApps[appIndex] = updatedApp

                    // Save updated apps
                    repository.saveNotificationApps(currentApps)

                    Log.d(TAG, "Successfully updated notification state for $packageName to $newState")

                    // Send confirmation response back
                    val responseMessage = JsonUtil.createToggleAppNotificationResponse(
                        packageName = packageName,
                        success = true,
                        newState = newState,
                        message = "App notification state updated successfully"
                    )
                    WebSocketUtil.sendMessage(responseMessage)
                } else {
                    Log.w(TAG, "App with package name $packageName not found in notification apps")

                    // Send error response
                    val responseMessage = JsonUtil.createToggleAppNotificationResponse(
                        packageName = packageName,
                        success = false,
                        newState = newState,
                        message = "App not found"
                    )
                    WebSocketUtil.sendMessage(responseMessage)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling toggle app notification: ${e.message}")
                val packageName = data?.optString("package") ?: ""
                val newState = data?.optString("state")?.toBoolean() ?: false
                val responseMessage = JsonUtil.createToggleAppNotificationResponse(
                    packageName = packageName,
                    success = false,
                    newState = newState,
                    message = "Error: ${e.message}"
                )
                WebSocketUtil.sendMessage(responseMessage)
            }
        }
    }
}
