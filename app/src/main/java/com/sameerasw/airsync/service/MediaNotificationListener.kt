package com.sameerasw.airsync.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.sameerasw.airsync.data.local.DataStoreManager
import com.sameerasw.airsync.domain.model.MediaInfo
import com.sameerasw.airsync.utils.JsonUtil
import com.sameerasw.airsync.utils.NotificationUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class MediaNotificationListener : NotificationListenerService() {

    companion object {
        @Volatile
        private var currentMediaInfo: MediaInfo? = null
        private const val TAG = "MediaNotificationListener"

        // System packages to ignore
        private val SYSTEM_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.providers.downloads",
            "com.google.android.gms",
            "com.android.vending"
        )

        fun getMediaInfo(context: Context): MediaInfo {
            return try {
                val mediaSessionManager = context.getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager

                val componentName = ComponentName(context, MediaNotificationListener::class.java)

                val activeSessions = try {
                    mediaSessionManager.getActiveSessions(componentName)
                } catch (e: SecurityException) {
                    Log.w(TAG, "SecurityException getting active sessions: ${e.message}")
                    emptyList()
                }

                Log.d(TAG, "Found ${activeSessions.size} active media sessions")

                if (activeSessions.isNotEmpty()) {
                    for (controller in activeSessions) {
                        val metadata = controller.metadata
                        val playbackState = controller.playbackState

                        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
                        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                        val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING

                        Log.d(TAG, "Media session - Title: $title, Artist: $artist, Playing: $isPlaying, State: ${playbackState?.state}")

                        // Return the first session that has media info or is playing
                        if (title.isNotEmpty() || artist.isNotEmpty() || isPlaying) {
                            return MediaInfo(
                                isPlaying = isPlaying,
                                title = title,
                                artist = artist
                            )
                        }
                    }
                }

                // Return current cached info if no active sessions but we have cached data
                currentMediaInfo?.let { cached ->
                    if (cached.title.isNotEmpty() || cached.artist.isNotEmpty()) {
                        Log.d(TAG, "Using cached media info")
                        return cached.copy(isPlaying = false)
                    }
                }

                Log.d(TAG, "No media info found")
                MediaInfo(false, "", "")
            } catch (e: Exception) {
                Log.e(TAG, "Error getting media info: ${e.message}")
                MediaInfo(false, "", "")
            }
        }
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var dataStoreManager: DataStoreManager

    override fun onCreate() {
        super.onCreate()
        dataStoreManager = DataStoreManager(this)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected - Ready to sync notifications")
        updateMediaInfo()

        // Show initial persistent notification
        serviceScope.launch {
            updatePersistentNotification()
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Notification listener disconnected")

        // Hide persistent notification when service disconnects
        NotificationUtil.hideConnectionStatusNotification(this)
        serviceJob.cancel()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let { notification ->
            Log.d(TAG, "Notification posted: ${notification.packageName} - ${notification.notification?.extras?.getString(Notification.EXTRA_TITLE)}")

            // Update media info first
            updateMediaInfo()

            // Process notification for sync
            processNotificationForSync(notification)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        Log.d(TAG, "Notification removed: ${sbn?.packageName}")
        updateMediaInfo()
    }

    private fun processNotificationForSync(sbn: StatusBarNotification) {
        serviceScope.launch {
            try {
                // Check if notification sync is enabled
                val isSyncEnabled = dataStoreManager.getNotificationSyncEnabled().first()
                if (!isSyncEnabled) {
                    Log.d(TAG, "Notification sync is disabled, skipping notification")
                    return@launch
                }

                // Check if this specific app is enabled for notifications
                val notificationApps = dataStoreManager.getNotificationApps().first()
                val appSettings = notificationApps.find { it.packageName == sbn.packageName }

                // If app settings exist and it's disabled, skip
                if (appSettings != null && !appSettings.isEnabled) {
                    Log.d(TAG, "App ${sbn.packageName} is disabled for notifications, skipping")
                    return@launch
                }

                // Skip system notifications and media-only notifications
                if (shouldSkipNotification(sbn)) {
                    Log.d(TAG, "Skipping notification from ${sbn.packageName}")
                    return@launch
                }

                val notification = sbn.notification
                val extras = notification.extras

                val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

                // Use big text if available, otherwise use regular text
                val body = bigText?.takeIf { it.isNotEmpty() } ?: text

                // Get app name from package name
                val appName = getAppNameFromPackage(sbn.packageName)

                // Only sync if we have meaningful content
                if (title.isNotEmpty() || body.isNotEmpty()) {
                    Log.d(TAG, "Syncing notification - App: $appName, Title: $title, Body: $body")

                    // Save the app to our list if it's new (auto-enable new apps)
                    if (appSettings == null) {
                        Log.d(TAG, "New app detected: ${sbn.packageName}, adding to preferences")
                        saveNewAppToPreferences(sbn.packageName, appName)
                    }

                    sendNotificationToDesktop(title, body, appName)
                } else {
                    Log.d(TAG, "Skipping empty notification from ${sbn.packageName}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error processing notification: ${e.message}")
            }
        }
    }

    private suspend fun saveNewAppToPreferences(packageName: String, appName: String) {
        try {
            val currentApps = dataStoreManager.getNotificationApps().first().toMutableList()

            // Check if app doesn't already exist
            if (currentApps.none { it.packageName == packageName }) {
                val packageManager = packageManager
                val isSystemApp = try {
                    val appInfo = packageManager.getApplicationInfo(packageName, 0)
                    (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (appInfo.flags and android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
                } catch (e: Exception) {
                    false
                }

                val newApp = com.sameerasw.airsync.domain.model.NotificationApp(
                    packageName = packageName,
                    appName = appName,
                    isEnabled = true, // Auto-enable new apps
                    isSystemApp = isSystemApp,
                    lastUpdated = System.currentTimeMillis()
                )

                currentApps.add(newApp)
                dataStoreManager.saveNotificationApps(currentApps)
                Log.d(TAG, "Added new app to preferences: $appName ($packageName)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error saving new app to preferences: ${e.message}")
        }
    }

    private fun shouldSkipNotification(sbn: StatusBarNotification): Boolean {
        // Skip system packages
        if (SYSTEM_PACKAGES.contains(sbn.packageName)) {
            return true
        }

        // Skip if notification is not clearable (usually system notifications)
        if (!sbn.isClearable) {
            return true
        }

        // Skip ongoing notifications (like music players, downloads, etc.)
        if (sbn.notification.flags and Notification.FLAG_ONGOING_EVENT != 0) {
            return true
        }

        // Skip notifications without meaningful content
        val extras = sbn.notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        if (title.isNullOrEmpty() && text.isNullOrEmpty()) {
            return true
        }

        return false
    }

    private fun getAppNameFromPackage(packageName: String): String {
        return try {
            val packageManager = packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (_: Exception) {
            // Fallback to package name or friendly name
            when (packageName) {
                "com.whatsapp" -> "WhatsApp"
                "com.telegram.messenger" -> "Telegram"
                "com.discord" -> "Discord"
                "com.slack" -> "Slack"
                "com.microsoft.teams" -> "Microsoft Teams"
                "com.google.android.gm" -> "Gmail"
                "com.android.email" -> "Email"
                "com.samsung.android.messaging" -> "Messages"
                "com.google.android.apps.messaging" -> "Messages"
                else -> packageName.split(".").lastOrNull()?.replaceFirstChar { it.uppercase() } ?: packageName
            }
        }
    }

    private suspend fun sendNotificationToDesktop(title: String, body: String, appName: String) {
        try {
            // Get connection settings from DataStore
            val ipAddress = dataStoreManager.getIpAddress().first()
            val port = dataStoreManager.getPort().first().toIntOrNull() ?: 6996

            // Create notification JSON and ensure it's a single line
            val notificationJson = JsonUtil.toSingleLine(JsonUtil.createNotificationJson(title, body, appName))

            Log.d(TAG, "Sending notification to $ipAddress:$port - $notificationJson")

            // Send via socket
            withContext(Dispatchers.IO) {
                try {
                    val socket = Socket(ipAddress, port)
                    val output = PrintWriter(socket.getOutputStream(), true)
                    val input = BufferedReader(InputStreamReader(socket.getInputStream()))

                    output.println(notificationJson)
                    val response = input.readLine()

                    socket.close()

                    // Update last sync time on successful send
                    val currentTime = System.currentTimeMillis()
                    dataStoreManager.updateLastSyncTime(currentTime)

                    // Update persistent notification with latest sync time
                    updatePersistentNotification(isConnected = true)

                    Log.d(TAG, "Notification sent successfully, response: $response")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to send notification: ${e.message}")
                    // Update persistent notification to show connection failed
                    updatePersistentNotification(isConnected = false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in sendNotificationToDesktop: ${e.message}")
            // Update persistent notification to show connection failed
            updatePersistentNotification(isConnected = false)
        }
    }

    private suspend fun updatePersistentNotification(isConnected: Boolean = false) {
        try {
            val isSyncEnabled = dataStoreManager.getNotificationSyncEnabled().first()
            if (!isSyncEnabled) {
                // Hide notification if sync is disabled
                NotificationUtil.hideConnectionStatusNotification(this)
                return
            }

            val connectedDevice = dataStoreManager.getLastConnectedDevice().first()
            val lastSyncTime = dataStoreManager.getLastSyncTime().first()

            NotificationUtil.showConnectionStatusNotification(
                context = this,
                connectedDevice = connectedDevice,
                lastSyncTime = lastSyncTime,
                isConnected = isConnected
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error updating persistent notification: ${e.message}")
        }
    }

    private fun updateMediaInfo() {
        currentMediaInfo = getMediaInfo(this)
        Log.d(TAG, "Updated media info: $currentMediaInfo")
    }
}
