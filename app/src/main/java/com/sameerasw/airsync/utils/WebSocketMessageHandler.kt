package com.sameerasw.airsync.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import com.sameerasw.airsync.data.local.DataStoreManager
import com.sameerasw.airsync.data.repository.AirSyncRepositoryImpl
import com.sameerasw.airsync.domain.model.MirroringOptions
import com.sameerasw.airsync.service.InputAccessibilityService
import com.sameerasw.airsync.service.MediaNotificationListener
import com.sameerasw.airsync.service.ScreenCaptureService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONObject

object WebSocketMessageHandler {
    private const val TAG = "WebSocketMessageHandler"

    // Track if we're currently receiving playing media from Mac to prevent feedback loop
    private var isReceivingPlayingMedia = false

    // Callback for clipboard entry history tracking
    private var onClipboardEntryReceived: ((text: String) -> Unit)? = null

    fun setOnClipboardEntryCallback(callback: ((text: String) -> Unit)?) {
        onClipboardEntryReceived = callback
        Log.d(TAG, "Clipboard entry callback ${if (callback != null) "registered" else "unregistered"}")
    }

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
                "macMediaControl" -> handleMacMediaControl(context, data)
                "dismissNotification" -> handleNotificationDismissal(data)
                "notificationAction" -> handleNotificationAction(data)
                "disconnectRequest" -> handleDisconnectRequest(context)
                "toggleAppNotif" -> handleToggleAppNotification(context, data)
                "toggleNowPlaying" -> handleToggleNowPlaying(context, data)
                "ping" -> handlePing(context)
                "status" -> handleMacDeviceStatus(context, data)
                "macInfo" -> handleMacInfo(context, data)
                "requestWallpaper" -> handleRequestWallpaper(context)
                "inputEvent" -> handleInputEvent(context, data)
                "navAction" -> handleNavAction(context, data)
                "stopMirroring" -> handleStopMirroring(context)
                "setScreenState" -> handleSetScreenState(context, data)
                // SMS and Messaging
                "requestSmsThreads" -> handleRequestSmsThreads(context, data)
                "requestSmsMessages" -> handleRequestSmsMessages(context, data)
                "sendSms" -> handleSendSms(context, data)
                "markSmsRead" -> handleMarkSmsRead(context, data)
                // Call Logs
                "requestCallLogs" -> handleRequestCallLogs(context, data)
                "markCallLogRead" -> handleMarkCallLogRead(context, data)
                // Call Actions
                "callAction" -> handleCallAction(context, data)
                // Health Data
                "requestHealthSummary" -> handleRequestHealthSummary(context, data)
                "requestHealthData" -> handleRequestHealthData(context, data)
                "mirrorRequest" -> {
                    // Extract mirror mode and package name
                    val mode = data?.optString("mode", "device") ?: "device"
                    val packageName = data?.optString("package", "") ?: ""
                    
                    val options = data?.optJSONObject("options")
                    val rawFps = options?.optInt("fps", 30) ?: 30
                    // Clamp FPS to reasonable range (10-60)
                    val fps = rawFps.coerceIn(10, 60)
                    if (rawFps != fps) {
                        Log.w(TAG, "FPS value $rawFps out of range, clamped to $fps")
                    }
                    
                    val quality = (options?.optDouble("quality", 0.6) ?: 0.6).toFloat().coerceIn(0.3f, 1.0f)
                    val maxWidth = options?.optInt("maxWidth", 1280) ?: 1280
                    val rawBitrate = options?.optInt("bitrateKbps", 4000) ?: 4000
                    // Clamp bitrate to reasonable range (1-8 Mbps)
                    val bitrateKbps = rawBitrate.coerceIn(1000, 8000)
                    if (rawBitrate != bitrateKbps) {
                        Log.w(TAG, "Bitrate $rawBitrate out of range, clamped to $bitrateKbps")
                    }
                    
                    // Check for auto-approve flag
                    val autoApprove = options?.optBoolean("autoApprove", false) ?: false
                    
                    // Check for audio mirroring flag
                    val enableAudio = options?.optBoolean("enableAudio", false) ?: false

                    val mirroringOptions = MirroringOptions(
                        fps = fps,
                        quality = quality,
                        maxWidth = maxWidth,
                        bitrateKbps = bitrateKbps,
                        enableAudio = enableAudio
                    )

                    // Log mirror request details
                    when (mode) {
                        "app" -> {
                            if (packageName.isNotEmpty()) {
                                Log.d(TAG, "📱 App-specific mirror request: package=$packageName, autoApprove=$autoApprove")
                                Log.d(TAG, "ℹ️ Note: Mirroring current screen (not launching app)")
                                // Just start mirroring - don't launch the app
                                // User should already have the app open or will open it manually
                                MirrorRequestHelper.handleMirrorRequest(context, mirroringOptions, autoApprove)
                            } else {
                                Log.w(TAG, "⚠️ App mirror requested but no package name provided")
                                MirrorRequestHelper.handleMirrorRequest(context, mirroringOptions, autoApprove)
                            }
                        }
                        "desktop" -> {
                            Log.d(TAG, "🖥️ Desktop mirror request: fps=$fps, quality=$quality, autoApprove=$autoApprove")
                            MirrorRequestHelper.handleMirrorRequest(context, mirroringOptions, autoApprove)
                        }
                        else -> {
                            Log.d(TAG, "📱 Device mirror request: fps=$fps, quality=$quality, autoApprove=$autoApprove")
                            MirrorRequestHelper.handleMirrorRequest(context, mirroringOptions, autoApprove)
                        }
                    }
                }
                "stopMirroring" -> {
                    Log.d(TAG, "🛑 Stop mirroring request from Mac")
                    MirrorRequestHelper.stopMirroring(context)
                }
                else -> {
                    Log.w(TAG, "Unknown message type: $type")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling incoming message: ${e.message}")
        }
    }

    private fun handleInputEvent(context: Context, data: JSONObject?) {
        if (data == null) {
            Log.e(TAG, "Input event data is null")
            sendInputEventResponse("unknown", false, "No data provided")
            return
        }

        val service = InputAccessibilityService.instance
        if (service == null) {
            Log.e(TAG, "InputAccessibilityService not available")
            sendInputEventResponse(data.optString("type", "unknown"), false, "Accessibility service not enabled")
            return
        }

        // Mac sends "action" field for input events
        val inputType = data.optString("action", data.optString("type", data.optString("inputType", "")))
        var success = false
        var message = ""

        try {
            when (inputType) {
                "tap" -> {
                    val x = data.optDouble("x").toFloat()
                    val y = data.optDouble("y").toFloat()
                    service.injectTap(x, y)
                    success = true
                    message = "Tap injected at ($x, $y)"
                    Log.d(TAG, message)
                }
                "longPress", "long_press" -> {
                    val x = data.optDouble("x").toFloat()
                    val y = data.optDouble("y").toFloat()
                    service.injectLongPress(x, y)
                    success = true
                    message = "Long press injected at ($x, $y)"
                    Log.d(TAG, message)
                }
                "swipe" -> {
                    // Mac sends x1, y1, x2, y2 - map to startX, startY, endX, endY
                    val startX = if (data.has("startX")) data.optDouble("startX").toFloat() else data.optDouble("x1").toFloat()
                    val startY = if (data.has("startY")) data.optDouble("startY").toFloat() else data.optDouble("y1").toFloat()
                    val endX = if (data.has("endX")) data.optDouble("endX").toFloat() else data.optDouble("x2").toFloat()
                    val endY = if (data.has("endY")) data.optDouble("endY").toFloat() else data.optDouble("y2").toFloat()
                    val duration = data.optLong("durationMs", data.optLong("duration", 300L))
                    service.injectSwipe(startX, startY, endX, endY, duration)
                    success = true
                    message = "Swipe injected from ($startX, $startY) to ($endX, $endY)"
                    Log.d(TAG, message)
                }
                "scroll" -> {
                    val x = data.optDouble("x").toFloat()
                    val y = data.optDouble("y").toFloat()
                    val deltaX = data.optDouble("deltaX").toFloat()
                    val deltaY = data.optDouble("deltaY").toFloat()
                    service.injectScroll(x, y, deltaX, deltaY)
                    success = true
                    message = "Scroll injected at ($x, $y) with delta ($deltaX, $deltaY)"
                    Log.d(TAG, message)
                }
                "back" -> {
                    success = service.performBack()
                    message = if (success) "Back action performed" else "Back action failed"
                    Log.d(TAG, message)
                }
                "home" -> {
                    success = service.performHome()
                    message = if (success) "Home action performed" else "Home action failed"
                    Log.d(TAG, message)
                }
                "recents" -> {
                    success = service.performRecents()
                    message = if (success) "Recents action performed" else "Recents action failed"
                    Log.d(TAG, message)
                }
                "notifications" -> {
                    success = service.performNotifications()
                    message = if (success) "Notifications action performed" else "Notifications action failed"
                    Log.d(TAG, message)
                }
                "quickSettings" -> {
                    success = service.performQuickSettings()
                    message = if (success) "Quick settings action performed" else "Quick settings action failed"
                    Log.d(TAG, message)
                }
                "powerDialog" -> {
                    success = service.performPowerDialog()
                    message = if (success) "Power dialog action performed" else "Power dialog action failed"
                    Log.d(TAG, message)
                }
                "text" -> {
                    val text = data.optString("text", "")
                    if (text.isNotEmpty()) {
                        success = service.injectText(text)
                        message = if (success) "Text injected: $text" else "Text injection failed"
                        Log.d(TAG, message)
                    } else {
                        message = "Empty text provided"
                        Log.w(TAG, message)
                    }
                }
                "key" -> {
                    val keyCode = data.optInt("keyCode", -1)
                    if (keyCode != -1) {
                        success = service.injectKeyEvent(keyCode)
                        message = if (success) "Key event injected: $keyCode" else "Key event injection failed"
                        Log.d(TAG, message)
                    } else {
                        message = "Invalid key code"
                        Log.w(TAG, message)
                    }
                }
                else -> {
                    Log.w(TAG, "Unknown input event type: $inputType")
                    message = "Unknown input type: $inputType"
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling input event: ${e.message}", e)
            message = "Error: ${e.message}"
        }

        sendInputEventResponse(inputType, success, message)
    }

    private fun sendInputEventResponse(inputType: String, success: Boolean, message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val response = JsonUtil.createInputEventResponse(inputType, success, message)
            WebSocketUtil.sendMessage(response)
        }
    }

    private fun handleNavAction(context: Context, data: JSONObject?) {
        if (data == null) {
            Log.e(TAG, "Nav action data is null")
            return
        }

        val service = InputAccessibilityService.instance
        if (service == null) {
            Log.e(TAG, "InputAccessibilityService not available for nav action")
            return
        }

        val action = data.optString("action", "")
        var success = false

        try {
            success = when (action) {
                "back" -> service.performBack()
                "home" -> service.performHome()
                "recents" -> service.performRecents()
                "notifications" -> service.performNotifications()
                "quickSettings" -> service.performQuickSettings()
                "powerDialog" -> service.performPowerDialog()
                else -> {
                    Log.w(TAG, "Unknown nav action: $action")
                    false
                }
            }
            Log.d(TAG, "Nav action '$action': ${if (success) "success" else "failed"}")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling nav action: ${e.message}", e)
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
                Log.d(TAG, "Clipboard update received from desktop: ${text.take(50)}...")

                // Notify ViewModel/UI to add entry to clipboard history
                onClipboardEntryReceived?.invoke(text)

                // Update system clipboard
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

    private fun handleMacMediaControl(context: Context, data: JSONObject?) {
        try {
            if (data == null) {
                Log.e(TAG, "Mac media control data is null")
                sendMacMediaControlResponse("unknown", false, "No data provided")
                return
            }

            val action = data.optString("action")
            var success = false
            var message: String

            Log.d(TAG, "Handling Mac media control action: $action")

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
                    SyncManager.suppressMediaUpdatesForSkip()
                    success = MediaControlUtil.skipNext(context)
                    message = if (success) "Skipped to next track" else "Failed to skip to next track"
                }
                "previous" -> {
                    SyncManager.suppressMediaUpdatesForSkip()
                    success = MediaControlUtil.skipPrevious(context)
                    message = if (success) "Skipped to previous track" else "Failed to skip to previous track"
                }
                "stop" -> {
                    success = MediaControlUtil.stop(context)
                    message = if (success) "Playback stopped" else "Failed to stop playback"
                }
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
                    Log.w(TAG, "Unknown Mac media control action: $action")
                    message = "Unknown action: $action"
                }
            }

            Log.d(TAG, "Mac media control result: action=$action, success=$success, message=$message")
            sendMacMediaControlResponse(action, success, message)

            // Send updated media state after successful control
            if (success) {
                CoroutineScope(Dispatchers.IO).launch {
                    val delayMs = when (action) {
                        "next", "previous" -> 1200L
                        else -> 400L
                    }
                    delay(delayMs)
                    SyncManager.onMediaStateChanged(context)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling Mac media control: ${e.message}", e)
            sendMacMediaControlResponse("unknown", false, "Error: ${e.message}")
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
            val replyText = data.optString("text", "")

            if (actionName.isEmpty()) {
                sendNotificationActionResponse(notificationId, actionName, false, "No action name provided")
                return
            }

            val success = NotificationDismissalUtil.performNotificationAction(notificationId, actionName, replyText)
            val message = if (success) {
                if (replyText.isNotEmpty()) "Reply sent" else "Action invoked"
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
        if (!WebSocketUtil.isConnected()) return
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

    private fun handleMacDeviceStatus(context: Context, data: JSONObject?) {
        try {
            if (data == null) {
                Log.e(TAG, "Mac device status data is null")
                return
            }

            Log.d(TAG, "Received Mac device status: ${data.toString()}")

            // Parse battery information
            val battery = data.optJSONObject("battery")
            val batteryLevel = battery?.optInt("level", 0) ?: 0
            val isCharging = battery?.optBoolean("isCharging", false) ?: false

            // Parse music information
            val music = data.optJSONObject("music")
            val isPlaying = music?.optBoolean("isPlaying", false) ?: false
            val title = music?.optString("title", "") ?: ""
            val artist = music?.optString("artist", "") ?: ""
            val volume = music?.optInt("volume", 50) ?: 50
            val isMuted = music?.optBoolean("isMuted", false) ?: false
            val albumArt = music?.optString("albumArt", "") ?: ""
            val likeStatus = music?.optString("likeStatus", "none") ?: "none"

            val isPaired = data.optBoolean("isPaired", true)

            // Pause/resume media listener based on Mac media playback status
            val hasActiveMedia = isPlaying && (title.isNotEmpty() || artist.isNotEmpty())
            isReceivingPlayingMedia = hasActiveMedia
            if (hasActiveMedia) {
                MediaNotificationListener.pauseMediaListener()
            } else {
                MediaNotificationListener.resumeMediaListener()
            }

            // Update the Mac device status manager with all media info
            MacDeviceStatusManager.updateStatus(
                context = context,
                batteryLevel = batteryLevel,
                isCharging = isCharging,
                isPaired = isPaired,
                isPlaying = isPlaying,
                title = title,
                artist = artist,
                volume = volume,
                isMuted = isMuted,
                albumArt = albumArt,
                likeStatus = likeStatus
            )

            // Persist a lightweight snapshot for widget consumption and throttle widget refresh
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val ds = DataStoreManager(context)
                    ds.saveMacStatusForWidget(batteryLevel, isCharging, title, artist)

                    // Throttle widget updates to once per minute to reduce battery usage
                    val lastRefresh = ds.getMacWidgetRefreshedAt().first() ?: 0L
                    val now = System.currentTimeMillis()
                    if (now - lastRefresh >= 30_000L) {
                        com.sameerasw.airsync.widget.AirSyncWidgetProvider.updateAllWidgets(context)
                        ds.setMacWidgetRefreshedAt(now)
                    }
                } catch (_: Exception) { }
            }

            Log.d(TAG, "Mac device status updated successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Error handling Mac device status: ${e.message}")
        }
    }

    private fun handleMacInfo(context: Context, data: JSONObject?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (data == null) {
                    Log.e(TAG, "macInfo data is null")
                    return@launch
                }

                val macName = data.optString("name", "")
                val isPlus = data.optBoolean("isPlusSubscription", false)
                
                Log.d(TAG, "Processing macInfo - name: '$macName', isPlus: $isPlus")
                
                val savedAppPackagesJson = data.optJSONArray("savedAppPackages")
                val savedPackages = mutableSetOf<String>()
                if (savedAppPackagesJson != null) {
                    for (i in 0 until savedAppPackagesJson.length()) {
                        val pkg = savedAppPackagesJson.optString(i)
                        if (!pkg.isNullOrBlank()) savedPackages.add(pkg)
                    }
                }

                // Update last connected device info with Mac name and Plus flag
                try {
                    val ds = DataStoreManager(context)
                    val last = ds.getLastConnectedDevice().first()
                    if (last != null) {
                        // Extract model and device type from macInfo
                        val model = data.optString("model", "").ifBlank { null }
                        val deviceType = when {
                            data.has("type") -> data.optString("type", "").ifBlank { null }
                            data.has("deviceType") -> data.optString("deviceType", "").ifBlank { null }
                            else -> null
                        }

                        Log.d(TAG, "Updating device: name='${if (macName.isNotBlank()) macName else last.name}', isPlus=$isPlus, model='$model', type='$deviceType'")

                        ds.saveLastConnectedDevice(
                            last.copy(
                                name = if (macName.isNotBlank()) macName else last.name,
                                isPlus = isPlus,
                                lastConnected = System.currentTimeMillis(),
                                model = model,
                                deviceType = deviceType
                            )
                        )
                        
                        Log.d(TAG, "Device info updated successfully in storage")
                        
                        // Also update the network-aware device storage if possible
                        try {
                            val ourIp = DeviceInfoUtil.getWifiIpAddress(context) ?: ""
                            val clientIp = last.ipAddress
                            val port = last.port
                            val symmetricKey = last.symmetricKey

                            if (clientIp.isNotBlank() && ourIp.isNotBlank()) {
                                ds.saveNetworkDeviceConnection(
                                    deviceName = if (macName.isNotBlank()) macName else last.name,
                                    ourIp = ourIp,
                                    clientIp = clientIp,
                                    port = port,
                                    isPlus = isPlus,
                                    symmetricKey = symmetricKey,
                                    model = model,
                                    deviceType = deviceType
                                )
                                Log.d(TAG, "Network device info also updated successfully")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Unable to update network device info from macInfo: ${e.message}")
                        }
                        
                        // Force update the last connected timestamp for network device as well
                        try {
                            if (macName.isNotBlank()) {
                                ds.updateNetworkDeviceLastConnected(macName, System.currentTimeMillis())
                                Log.d(TAG, "Network device timestamp updated")
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Unable to update network device timestamp: ${e.message}")
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Unable to update connected device info from macInfo: ${e.message}")
                }

                // Build Android launcher package list (lightweight)
                val androidPackages = try {
                    AppUtil.getLauncherPackageNames(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to get launcher package names: ${e.message}")
                    emptyList()
                }

                // Decide how to sync icons based on differences between Android and Mac package lists
                val androidSet = androidPackages.toSet()
                val savedSet = savedPackages.toSet()

                if (savedSet.isEmpty()) {
                    // Mac has none; send full current Android list
                    Log.d(TAG, "macInfo: Mac has no saved packages; syncing full list of ${androidPackages.size} apps")
                    SyncManager.sendOptimizedAppIcons(context, androidPackages)
                    return@launch
                }

                val newOnAndroid = androidSet - savedSet // apps present on Android but not on Mac
                val missingOnAndroid = savedSet - androidSet // apps present on Mac but uninstalled on Android

                if (newOnAndroid.isNotEmpty() || missingOnAndroid.isNotEmpty()) {
                    Log.d(
                        TAG,
                        "macInfo: App list changed (new=${newOnAndroid.size}, missing=${missingOnAndroid.size}); syncing full list of ${androidPackages.size} apps"
                    )
                    // Send the full current Android list so desktop can add new and remove missing
                    SyncManager.sendOptimizedAppIcons(context, androidPackages)
                } else {
                    Log.d(TAG, "macInfo: No app list changes; skipping icon extraction")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling macInfo: ${e.message}")
            }
        }
    }

    // Helper method to check if we should send media controls to prevent feedback loop
    fun shouldSendMediaControl(): Boolean {
        return !isReceivingPlayingMedia
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

    private fun handleToggleNowPlaying(context: Context, data: JSONObject?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (data == null) {
                    Log.e(TAG, "toggleNowPlaying data is null")
                    val resp = JsonUtil.createToggleNowPlayingResponse(false, null, "No data provided")
                    WebSocketUtil.sendMessage(resp)
                    return@launch
                }
                // Accept either boolean or string "true"/"false"
                val hasBoolean = data.has("state") && (data.opt("state") is Boolean)
                val newState = if (hasBoolean) data.optBoolean("state") else data.optString("state").toBoolean()

                val ds = DataStoreManager(context)
                ds.setSendNowPlayingEnabled(newState)
                MediaNotificationListener.setNowPlayingEnabled(context, newState)

                val resp = JsonUtil.createToggleNowPlayingResponse(true, newState, "Now playing set to $newState")
                WebSocketUtil.sendMessage(resp)
            } catch (e: Exception) {
                Log.e(TAG, "Error handling toggleNowPlaying: ${e.message}")
                val resp = JsonUtil.createToggleNowPlayingResponse(false, null, "Error: ${e.message}")
                WebSocketUtil.sendMessage(resp)
            }
        }
    }

    private fun handleRequestWallpaper(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            WallpaperHandler.sendWallpaper(context)
        }
    }

    // ========== SMS and Messaging Handlers ==========

    private fun handleRequestSmsThreads(context: Context, data: JSONObject?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val limit = data?.optInt("limit", 50) ?: 50
                val threads = SmsUtil.getAllThreads(context, limit)
                val json = JsonUtil.createSmsThreadsJson(threads)
                WebSocketUtil.sendMessage(json)
                Log.d(TAG, "Sent ${threads.size} SMS threads")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling request SMS threads", e)
            }
        }
    }

    private fun handleRequestSmsMessages(context: Context, data: JSONObject?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (data == null) {
                    Log.e(TAG, "Request SMS messages data is null")
                    return@launch
                }

                val threadId = data.optString("threadId")
                val limit = data.optInt("limit", 100)

                if (threadId.isEmpty()) {
                    Log.e(TAG, "Thread ID is empty")
                    return@launch
                }

                val messages = SmsUtil.getMessagesInThread(context, threadId, limit)
                val json = JsonUtil.createSmsMessagesJson(messages)
                WebSocketUtil.sendMessage(json)
                Log.d(TAG, "Sent ${messages.size} messages for thread $threadId")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling request SMS messages", e)
            }
        }
    }

    private fun handleSendSms(context: Context, data: JSONObject?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (data == null) {
                    Log.e(TAG, "Send SMS data is null")
                    val response = JsonUtil.createSmsSendResponse(false, "No data provided")
                    WebSocketUtil.sendMessage(response)
                    return@launch
                }

                val address = data.optString("address")
                val message = data.optString("message")

                if (address.isEmpty() || message.isEmpty()) {
                    Log.e(TAG, "Address or message is empty")
                    val response = JsonUtil.createSmsSendResponse(false, "Address or message is empty")
                    WebSocketUtil.sendMessage(response)
                    return@launch
                }

                val success = SmsUtil.sendSms(context, address, message)
                val responseMessage = if (success) "SMS sent successfully" else "Failed to send SMS"
                val response = JsonUtil.createSmsSendResponse(success, responseMessage)
                WebSocketUtil.sendMessage(response)
                Log.d(TAG, "SMS send result: $success")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling send SMS", e)
                val response = JsonUtil.createSmsSendResponse(false, "Error: ${e.message}")
                WebSocketUtil.sendMessage(response)
            }
        }
    }

    private fun handleMarkSmsRead(context: Context, data: JSONObject?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (data == null) {
                    Log.e(TAG, "Mark SMS read data is null")
                    return@launch
                }

                val messageId = data.optString("messageId")
                if (messageId.isEmpty()) {
                    Log.e(TAG, "Message ID is empty")
                    return@launch
                }

                val success = SmsUtil.markAsRead(context, messageId)
                Log.d(TAG, "Mark SMS as read result: $success")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling mark SMS read", e)
            }
        }
    }

    // ========== Call Log Handlers ==========

    private fun handleRequestCallLogs(context: Context, data: JSONObject?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val limit = data?.optInt("limit", 100) ?: 100
                val sinceTimestamp = data?.optLong("since", 0) ?: 0

                val callLogs = if (sinceTimestamp > 0) {
                    CallLogUtil.getCallLogsSince(context, sinceTimestamp)
                } else {
                    CallLogUtil.getCallLogs(context, limit)
                }

                val json = JsonUtil.createCallLogsJson(callLogs)
                WebSocketUtil.sendMessage(json)
                Log.d(TAG, "Sent ${callLogs.size} call logs")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling request call logs", e)
            }
        }
    }

    private fun handleMarkCallLogRead(context: Context, data: JSONObject?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (data == null) {
                    Log.e(TAG, "Mark call log read data is null")
                    return@launch
                }

                val callId = data.optString("callId")
                if (callId.isEmpty()) {
                    Log.e(TAG, "Call ID is empty")
                    return@launch
                }

                val success = CallLogUtil.markAsRead(context, callId)
                Log.d(TAG, "Mark call log as read result: $success")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling mark call log read", e)
            }
        }
    }

    // ========== Call Action Handlers ==========

    private fun handleCallAction(context: Context, data: JSONObject?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (data == null) {
                    Log.e(TAG, "Call action data is null")
                    val response = JsonUtil.createCallActionResponse("unknown", false, "No data provided")
                    WebSocketUtil.sendMessage(response)
                    return@launch
                }

                val action = data.optString("action")
                
                when (action) {
                    "answer" -> {
                        val success = answerCall(context)
                        val response = JsonUtil.createCallActionResponse(action, success, 
                            if (success) "Call answered" else "Failed to answer call - check permissions")
                        WebSocketUtil.sendMessage(response)
                    }
                    "reject", "hangup" -> {
                        val success = endCall(context)
                        val response = JsonUtil.createCallActionResponse(action, success,
                            if (success) "Call ended" else "Failed to end call - check permissions")
                        WebSocketUtil.sendMessage(response)
                    }
                    "mute" -> {
                        val success = muteCall(context)
                        val response = JsonUtil.createCallActionResponse(action, success,
                            if (success) "Call muted" else "Failed to mute call")
                        WebSocketUtil.sendMessage(response)
                    }
                    "unmute" -> {
                        val success = unmuteCall(context)
                        val response = JsonUtil.createCallActionResponse(action, success,
                            if (success) "Call unmuted" else "Failed to unmute call")
                        WebSocketUtil.sendMessage(response)
                    }
                    "speaker" -> {
                        val success = toggleSpeaker(context, true)
                        val response = JsonUtil.createCallActionResponse(action, success,
                            if (success) "Speaker enabled" else "Failed to enable speaker")
                        WebSocketUtil.sendMessage(response)
                    }
                    "speakerOff" -> {
                        val success = toggleSpeaker(context, false)
                        val response = JsonUtil.createCallActionResponse(action, success,
                            if (success) "Speaker disabled" else "Failed to disable speaker")
                        WebSocketUtil.sendMessage(response)
                    }
                    else -> {
                        val response = JsonUtil.createCallActionResponse(action, false, "Unknown action")
                        WebSocketUtil.sendMessage(response)
                        Log.w(TAG, "Unknown call action: $action")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling call action", e)
                val response = JsonUtil.createCallActionResponse("unknown", false, "Error: ${e.message}")
                WebSocketUtil.sendMessage(response)
            }
        }
    }
    
    @Suppress("DEPRECATION")
    private fun answerCall(context: Context): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
                if (telecomManager != null && 
                    androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ANSWER_PHONE_CALLS) 
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    telecomManager.acceptRingingCall()
                    Log.d(TAG, "✅ Call answered via TelecomManager")
                    true
                } else {
                    Log.w(TAG, "ANSWER_PHONE_CALLS permission not granted")
                    false
                }
            } else {
                // For older Android versions, use ITelephony via reflection
                answerCallLegacy(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error answering call", e)
            false
        }
    }
    
    @Suppress("DEPRECATION")
    private fun answerCallLegacy(context: Context): Boolean {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
            val clazz = Class.forName(telephonyManager?.javaClass?.name)
            val method = clazz.getDeclaredMethod("getITelephony")
            method.isAccessible = true
            val telephonyService = method.invoke(telephonyManager)
            val telephonyServiceClass = Class.forName(telephonyService.javaClass.name)
            val answerMethod = telephonyServiceClass.getDeclaredMethod("answerRingingCall")
            answerMethod.invoke(telephonyService)
            Log.d(TAG, "✅ Call answered via ITelephony (legacy)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error answering call (legacy)", e)
            false
        }
    }
    
    @Suppress("DEPRECATION")
    private fun endCall(context: Context): Boolean {
        return try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? android.telecom.TelecomManager
                if (telecomManager != null &&
                    androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.ANSWER_PHONE_CALLS)
                    == android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    val result = telecomManager.endCall()
                    Log.d(TAG, "✅ Call ended via TelecomManager: $result")
                    result
                } else {
                    Log.w(TAG, "ANSWER_PHONE_CALLS permission not granted for ending call")
                    false
                }
            } else {
                // For older Android versions, use ITelephony via reflection
                endCallLegacy(context)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error ending call", e)
            false
        }
    }
    
    @Suppress("DEPRECATION")
    private fun endCallLegacy(context: Context): Boolean {
        return try {
            val telephonyManager = context.getSystemService(Context.TELEPHONY_SERVICE) as? android.telephony.TelephonyManager
            val clazz = Class.forName(telephonyManager?.javaClass?.name)
            val method = clazz.getDeclaredMethod("getITelephony")
            method.isAccessible = true
            val telephonyService = method.invoke(telephonyManager)
            val telephonyServiceClass = Class.forName(telephonyService.javaClass.name)
            val endMethod = telephonyServiceClass.getDeclaredMethod("endCall")
            val result = endMethod.invoke(telephonyService) as Boolean
            Log.d(TAG, "✅ Call ended via ITelephony (legacy): $result")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error ending call (legacy)", e)
            false
        }
    }
    
    private fun muteCall(context: Context): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            audioManager?.isMicrophoneMute = true
            Log.d(TAG, "✅ Call muted")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error muting call", e)
            false
        }
    }
    
    private fun unmuteCall(context: Context): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            audioManager?.isMicrophoneMute = false
            Log.d(TAG, "✅ Call unmuted")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error unmuting call", e)
            false
        }
    }
    
    private fun toggleSpeaker(context: Context, enabled: Boolean): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            audioManager?.isSpeakerphoneOn = enabled
            Log.d(TAG, "✅ Speaker ${if (enabled) "enabled" else "disabled"}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling speaker", e)
            false
        }
    }

    // ========== Health Data Handlers ==========

    private fun handleRequestHealthSummary(context: Context, data: JSONObject?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!HealthConnectUtil.isAvailable(context)) {
                    Log.w(TAG, "Health Connect not available")
                    return@launch
                }

                if (!HealthConnectUtil.hasPermissions(context)) {
                    Log.w(TAG, "Health Connect permissions not granted")
                    return@launch
                }

                // Get requested date from Mac, default to today
                val requestedDate = data?.optLong("date", System.currentTimeMillis()) 
                    ?: System.currentTimeMillis()
                
                Log.d(TAG, "Requesting health summary for date: $requestedDate")

                // Use cache to reduce Health Connect queries
                val summary = com.sameerasw.airsync.health.HealthDataCache.getSummaryWithCache(
                    context,
                    requestedDate
                ) { date ->
                    HealthConnectUtil.getSummaryForDate(context, date)
                }
                
                if (summary != null) {
                    val json = JsonUtil.createHealthSummaryJson(summary)
                    WebSocketUtil.sendMessage(json)
                    Log.d(TAG, "Sent health summary for date: $requestedDate")
                } else {
                    Log.w(TAG, "Failed to get health summary for date: $requestedDate")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling request health summary", e)
            }
        }
    }

    private fun handleRequestHealthData(context: Context, data: JSONObject?) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!HealthConnectUtil.isAvailable(context)) {
                    Log.w(TAG, "Health Connect not available")
                    return@launch
                }

                if (!HealthConnectUtil.hasPermissions(context)) {
                    Log.w(TAG, "Health Connect permissions not granted")
                    return@launch
                }

                val hours = data?.optInt("hours", 24) ?: 24
                val healthData = HealthConnectUtil.getRecentHealthData(context, hours)
                
                if (healthData.isNotEmpty()) {
                    val json = JsonUtil.createHealthDataJson(healthData)
                    WebSocketUtil.sendMessage(json)
                    Log.d(TAG, "Sent ${healthData.size} health data records")
                } else {
                    Log.w(TAG, "No health data available")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error handling request health data", e)
            }
        }
    }

    private fun handleStopMirroring(context: Context) {
        Log.d(TAG, "Received stop mirroring request from Mac")
        try {
            val service = ScreenCaptureService.instance
            if (service != null) {
                service.stopMirroring()
                Log.d(TAG, "Screen mirroring stopped successfully")
            } else {
                Log.w(TAG, "ScreenCaptureService not running")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping mirroring", e)
        }
    }

    private fun handleSetScreenState(context: Context, data: JSONObject?) {
        try {
            val screenOff = data?.optBoolean("screenOff", false) ?: false
            Log.d(TAG, "Received screen state request: screenOff=$screenOff")
            
            // Use ScreenCaptureService to show/hide black overlay
            val service = ScreenCaptureService.instance
            if (service != null) {
                if (screenOff) {
                    // Show black overlay to hide screen content
                    service.showBlackOverlay()
                    Log.d(TAG, "✅ Black overlay shown - screen content hidden")
                } else {
                    // Hide black overlay to show screen content
                    service.hideBlackOverlay()
                    Log.d(TAG, "✅ Black overlay hidden - screen content visible")
                }
            } else {
                Log.w(TAG, "⚠️ ScreenCaptureService not available - cannot control screen overlay")
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling screen state: ${e.message}", e)
        }
    }

    private fun sendMacMediaControlResponse(action: String, success: Boolean, message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            val response = JsonUtil.createMacMediaControlResponse(action, success, message)
            WebSocketUtil.sendMessage(response)
            Log.d(TAG, "Sent Mac media control response: action=$action, success=$success")
        }
    }
}
