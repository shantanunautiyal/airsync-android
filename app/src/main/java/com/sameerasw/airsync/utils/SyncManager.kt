package com.sameerasw.airsync.utils

import android.content.Context
import android.util.Log
import com.sameerasw.airsync.data.local.DataStoreManager
import com.sameerasw.airsync.domain.model.AudioInfo
import com.sameerasw.airsync.domain.model.BatteryInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.util.concurrent.atomic.AtomicBoolean

object SyncManager {
    private const val TAG = "SyncManager"
    private const val BATTERY_SYNC_INTERVAL = 60_000L // 1 minute

    private var syncJob: Job? = null
    private var lastAudioInfo: AudioInfo? = null
    private var lastBatteryInfo: BatteryInfo? = null
    private var lastVolume: Int = -1
    private val isSyncing = AtomicBoolean(false)

    fun startPeriodicSync(context: Context) {
        if (isSyncing.get()) {
            Log.d(TAG, "Sync already running")
            return
        }

        isSyncing.set(true)
        syncJob = CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "Starting periodic sync")

            while (isActive && isSyncing.get()) {
                try {
                    // Check if WebSocket is connected and sync is enabled
                    if (WebSocketUtil.isConnected()) {
                        val dataStoreManager = DataStoreManager(context)
                        val isSyncEnabled = dataStoreManager.getNotificationSyncEnabled().first()

                        if (isSyncEnabled) {
                            checkAndSyncDeviceStatus(context)
                        }
                    }

                    delay(BATTERY_SYNC_INTERVAL)
                } catch (e: Exception) {
                    Log.e(TAG, "Error in periodic sync: ${e.message}")
                    delay(BATTERY_SYNC_INTERVAL) // Continue even if there's an error
                }
            }
        }
    }

    fun stopPeriodicSync() {
        Log.d(TAG, "Stopping periodic sync")
        isSyncing.set(false)
        syncJob?.cancel()
        syncJob = null
        lastAudioInfo = null
        lastBatteryInfo = null
        lastVolume = -1
    }

    fun checkAndSyncDeviceStatus(context: Context, forceSync: Boolean = false) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val currentAudio = DeviceInfoUtil.getAudioInfo(context)
                val currentBattery = DeviceInfoUtil.getBatteryInfo(context)

                var shouldSync = forceSync

                // Check if audio info changed (playing state, track, or volume)
                lastAudioInfo?.let { last ->
                    if (last.isPlaying != currentAudio.isPlaying ||
                        last.title != currentAudio.title ||
                        last.artist != currentAudio.artist ||
                        last.volume != currentAudio.volume ||
                        last.isMuted != currentAudio.isMuted) {
                        shouldSync = true
                        Log.d(TAG, "Audio info changed, syncing device status")
                    }
                } ?: run {
                    shouldSync = true // First time
                }

                // Check if battery info changed significantly (>5% or charging state)
                lastBatteryInfo?.let { last ->
                    if (last.isCharging != currentBattery.isCharging ||
                        kotlin.math.abs(last.level - currentBattery.level) >= 5) {
                        shouldSync = true
                        Log.d(TAG, "Battery info changed significantly, syncing device status")
                    }
                } ?: run {
                    shouldSync = true // First time
                }

                if (shouldSync && WebSocketUtil.isConnected()) {
                    val statusJson = DeviceInfoUtil.generateDeviceStatusJson(context)
                    val success = WebSocketUtil.sendMessage(statusJson)

                    if (success) {
                        Log.d(TAG, "Device status synced successfully")
                        lastAudioInfo = currentAudio
                        lastBatteryInfo = currentBattery
                        lastVolume = currentAudio.volume
                    } else {
                        Log.w(TAG, "Failed to sync device status")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error checking device status: ${e.message}")
            }
        }
    }

    fun performInitialSync(context: Context) {
        CoroutineScope(Dispatchers.IO).launch {
            Log.d(TAG, "Performing initial sync sequence")

            try {
                val dataStoreManager = DataStoreManager(context)

                // 1. Send device info
                delay(500) // Small delay to ensure connection is stable
                val deviceName = dataStoreManager.getDeviceName().first()
                val localIp = DeviceInfoUtil.getWifiIpAddress(context) ?: "Unknown"
                val port = dataStoreManager.getPort().first().toIntOrNull() ?: 6996
                val adbPort = dataStoreManager.getAdbPort().first()
                val deviceInfoJson = JsonUtil.createDeviceInfoJson(deviceName, localIp, port, adbPort)

                if (WebSocketUtil.sendMessage(deviceInfoJson)) {
                    Log.d(TAG, "✅ Device info sent")
                } else {
                    Log.e(TAG, "❌ Failed to send device info")
                }

                delay(1000)

                // 2. Send device status
                val statusJson = DeviceInfoUtil.generateDeviceStatusJson(context)
                if (WebSocketUtil.sendMessage(statusJson)) {
                    Log.d(TAG, "✅ Device status sent")
                    // Update our cached values
                    lastAudioInfo = DeviceInfoUtil.getAudioInfo(context)
                    lastBatteryInfo = DeviceInfoUtil.getBatteryInfo(context)
                } else {
                    Log.e(TAG, "❌ Failed to send device status")
                }

                delay(1000)

                // 3. Send app icons
                sendAppIcons(context)

                delay(1000)

                // 4. Send existing notifications (recent ones)
                sendRecentNotifications()

                Log.d(TAG, "Initial sync sequence completed")

            } catch (e: Exception) {
                Log.e(TAG, "Error in initial sync: ${e.message}")
            }
        }
    }

    private fun sendRecentNotifications() {
        try {
            // Send a sample notification to indicate sync is active
            val sampleNotificationJson = JsonUtil.createNotificationJson(
                id = "airsync_welcome_${System.currentTimeMillis()}",
                title = "AirSync Connected",
                body = "Notification sync is now active. You'll see your Android notifications here.",
                app = "AirSync",
                packageName = "com.sameerasw.airsync"
            )

            if (WebSocketUtil.sendMessage(sampleNotificationJson)) {
                Log.d(TAG, "✅ Sample notification sent")
            } else {
                Log.e(TAG, "❌ Failed to send sample notification")
            }

            // Note: Android doesn't provide access to existing notifications
            // We can only sync new notifications that arrive after the listener is active

        } catch (e: Exception) {
            Log.e(TAG, "Error sending recent notifications: ${e.message}")
        }
    }

    private suspend fun sendAppIcons(context: Context) {
        try {
            Log.d(TAG, "Starting app icons sync")

            // Get enabled notification apps from DataStore
            val dataStoreManager = DataStoreManager(context)
            val notificationApps = dataStoreManager.getNotificationApps().first()

            if (notificationApps.isEmpty()) {
                Log.d(TAG, "No notification apps found, skipping app icons sync")
                return
            }

            // Get package names of enabled apps
            val enabledPackages = notificationApps.filter { it.isEnabled }.map { it.packageName }

            if (enabledPackages.isEmpty()) {
                Log.d(TAG, "No enabled notification apps found, skipping app icons sync")
                return
            }

            Log.d(TAG, "Collecting icons for ${enabledPackages.size} enabled apps")

            // Get app icons as base64
            val iconMap = AppIconUtil.getAppIconsAsBase64(context, enabledPackages)

            if (iconMap.isNotEmpty()) {
                // Create and send app icons JSON
                val appIconsJson = JsonUtil.createAppIconsJson(iconMap)

                if (WebSocketUtil.sendMessage(appIconsJson)) {
                    Log.d(TAG, "✅ App icons sent successfully (${iconMap.size} icons)")
                } else {
                    Log.e(TAG, "❌ Failed to send app icons")
                }
            } else {
                Log.w(TAG, "No app icons could be collected")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Error sending app icons: ${e.message}")
        }
    }

    fun onVolumeChanged(context: Context) {
        Log.d(TAG, "Volume change detected, checking sync")
        checkAndSyncDeviceStatus(context)
    }

    fun onMediaStateChanged(context: Context) {
        Log.d(TAG, "Media state change detected, checking sync")
        checkAndSyncDeviceStatus(context)
    }

    fun reset() {
        lastAudioInfo = null
        lastBatteryInfo = null
        lastVolume = -1
    }
}
