package com.sameerasw.airsync.service

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.Rating
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.NotificationListenerService.RankingMap
import android.service.notification.StatusBarNotification
import android.util.Base64
import android.util.Log
import com.sameerasw.airsync.data.local.DataStoreManager
import com.sameerasw.airsync.domain.model.MediaInfo
import com.sameerasw.airsync.utils.DeviceInfoUtil
import com.sameerasw.airsync.utils.JsonUtil
import com.sameerasw.airsync.utils.NotificationDismissalUtil
import com.sameerasw.airsync.utils.NotificationUtil
import com.sameerasw.airsync.utils.SyncManager
import com.sameerasw.airsync.utils.WebSocketUtil
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.LinkedList

class MediaNotificationListener : NotificationListenerService() {

    companion object {
        @Volatile
        private var currentMediaInfo: MediaInfo? = null
        @Volatile
        private var serviceInstance: MediaNotificationListener? = null
        private const val TAG = "MediaNotificationListener"

        // Flag to pause media listener when receiving playing media from Mac
        @Volatile
        private var isMediaListenerPaused = false

        // New: global toggle for now playing reporting
        @Volatile
        private var isNowPlayingEnabled: Boolean = true

        fun setNowPlayingEnabled(context: Context, enabled: Boolean) {
            isNowPlayingEnabled = enabled
            if (!enabled) {
                // Clear cached media and pause listener
                currentMediaInfo = null
                isMediaListenerPaused = true
                Log.d(TAG, "Now playing disabled: pausing media listener and clearing cache")
            } else {
                isMediaListenerPaused = false
                Log.d(TAG, "Now playing enabled: resuming media listener")
            }
            // Trigger a status sync to reflect change
            SyncManager.checkAndSyncDeviceStatus(context, forceSync = true)
        }

        // In-memory cache of like status per track key
        private val likeStatusCache = LinkedHashMap<String, String>(32, 0.75f, true)

        // System packages to ignore
        private val SYSTEM_PACKAGES = setOf(
            "android",
            "com.android.systemui",
            "com.android.providers.downloads",
            "com.google.android.gms",
            "com.android.vending"
        )

        // Only attach like status for these media apps
        private val ALLOWED_PACKAGES = setOf(
            "com.google.android.apps.youtube.music", // YouTube Music
            "com.spotify.music" // Spotify
        )

        fun getInstance(): MediaNotificationListener? {
            return serviceInstance
        }

        fun getMediaInfo(context: Context): MediaInfo {
            // Respect global toggle; if disabled, return empty media
            if (!isNowPlayingEnabled) {
                return MediaInfo(false, "", "", null, "none")
            }
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
                        try {
                            if (controller.packageName == context.packageName) {
                                Log.d(TAG, "Skipping own media session from package: ${controller.packageName}")
                                continue
                            }
                        } catch (_: Exception) { }

                        val metadata = controller.metadata
                        val playbackState = controller.playbackState

                        val title = metadata?.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
                        val artist = metadata?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                        val isPlaying = playbackState?.state == PlaybackState.STATE_PLAYING

                        val albumArtBitmap = metadata?.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART)
                            ?: metadata?.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON)

                        val albumArtBase64 = albumArtBitmap?.let {
                            val outputStream = ByteArrayOutputStream()
                            // Compress to a smaller size to avoid large payloads
                            it.compress(Bitmap.CompressFormat.JPEG, 50, outputStream)
                            Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
                        }

                        Log.d(TAG, "Media session - Title: $title, Artist: $artist, Playing: $isPlaying, State: ${playbackState?.state}")

                        // Determine like status; apply app filter and strict positive-only like detection
                        val (detectedStatus, source) = determineLikeStatusWithSource(context, controller)
                        var likeStatus = detectedStatus

                        // If filtered by app, force none and skip cache
                        if (source == "appfilter") {
                            likeStatus = "none"
                        }

                        // Return the first session that has media info or is playing
                        if (title.isNotEmpty() || artist.isNotEmpty() || isPlaying) {
                            return MediaInfo(
                                isPlaying = isPlaying,
                                title = title,
                                artist = artist,
                                albumArt = albumArtBase64,
                                likeStatus = likeStatus
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
                MediaInfo(false, "", "", null, "none")
            } catch (e: Exception) {
                Log.e(TAG, "Error getting media info: ${e.message}")
                MediaInfo(false, "", "", null, "none")
            }
        }

        private fun determineLikeStatusWithSource(context: Context, controller: MediaController): Pair<String, String> {
            // Enforce package filter first
            val pkg = controller.packageName
            if (pkg.isNullOrEmpty() || !ALLOWED_PACKAGES.contains(pkg)) {
                return "none" to "appfilter"
            }

            try {
                val md = controller.metadata
                // Prefer explicit user rating/heart/thumbs metadata if present
                val userRating: Rating? = try { md?.getRating(MediaMetadata.METADATA_KEY_USER_RATING) } catch (_: Exception) { null }
                val rating: Rating? = userRating ?: try { md?.getRating(MediaMetadata.METADATA_KEY_RATING) } catch (_: Exception) { null }
                if (rating != null) {
                    if (rating.isRated) {
                        when (rating.ratingStyle) {
                            Rating.RATING_HEART -> {
                                val liked = try { rating.hasHeart() } catch (_: Exception) { false }
                                Log.d(TAG, "Like from HEART rating: $liked")
                                return if (liked) "liked" to "rating" else "not_liked" to "rating"
                            }
                            Rating.RATING_THUMB_UP_DOWN -> {
                                val up = try { rating.isThumbUp } catch (_: Exception) { false }
                                Log.d(TAG, "Like from THUMB rating: $up")
                                return if (up) "liked" to "rating" else "not_liked" to "rating"
                            }
                            else -> Log.d(TAG, "Rating present but not mappable (style=${rating.ratingStyle})")
                        }
                    } else {
                        // Not rated counts as not_liked per requirement
                        Log.d(TAG, "Rating present but not rated -> not_liked")
                        return "not_liked" to "rating"
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to read metadata rating: ${e.message}")
            }

            // For allowed apps: if no positive rating, treat as not_liked
            return "not_liked" to "rating"
        }

        private fun detectLikeStatusForPackage(targetPackage: String?): String {
            val service = getInstance() ?: return "none"
            val notifs = try { service.activeNotifications } catch (_: Exception) { emptyArray<StatusBarNotification>() }
            if (notifs.isEmpty()) return "none"

            // Look for notification from the target package first, then others
            val candidates = notifs.filter { it.packageName == targetPackage } + notifs.filter { it.packageName != targetPackage }

            for (sbn in candidates) {
                val n = sbn.notification
                val actions = n.actions ?: continue
                // Debug log action titles and semantic actions for inspection
                try {
                    actions.forEachIndexed { idx, act ->
                        Log.d(TAG, "Notif action[$idx]: title='${act.title}', semantic=${act.semanticAction}")
                    }
                } catch (_: Exception) {}
                val status = inferLikeStatusFromActions(actions)
                if (status != null) return status
            }
            return "none"
        }

        private fun inferLikeStatusFromActions(actions: Array<Notification.Action>): String? {
            // Priority 1: explicit text indicating Unlike/Remove -> liked
            for (action in actions) {
                val title = action.title?.toString()?.lowercase()?.trim() ?: ""
                if (title.isEmpty()) continue
                if (title.contains("unlike") || title.contains("remove from liked") || title.contains("remove like") || title == "liked") {
                    return "liked"
                }
            }
            // Priority 2: semantic thumbs up/down if titles are missing
            for (action in actions) {
                val sem = action.semanticAction
                if (sem == Notification.Action.SEMANTIC_ACTION_THUMBS_DOWN) {
                    return "liked" // apps often show "unlike" when already liked
                }
            }
            for (action in actions) {
                val title = action.title?.toString()?.lowercase()?.trim() ?: ""
                val sem = action.semanticAction
                if (title.contains("like") || title.contains("favorite") || title.contains("favourite") ||
                    title.contains("❤") || title.contains("♥") ||
                    sem == Notification.Action.SEMANTIC_ACTION_THUMBS_UP) {
                    return "not_liked"
                }
            }
            return null
        }

        // Build a track key using most stable metadata available
        private fun buildTrackKey(controller: MediaController): String? {
            return try {
                val pkg = controller.packageName ?: return null
                val md = controller.metadata ?: return null
                val mediaId = md.getString(MediaMetadata.METADATA_KEY_MEDIA_ID)
                val title = md.getString(MediaMetadata.METADATA_KEY_TITLE) ?: ""
                val artist = md.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: ""
                val album = md.getString(MediaMetadata.METADATA_KEY_ALBUM) ?: ""
                val base = if (!mediaId.isNullOrEmpty()) mediaId else listOf(title, artist, album).joinToString("|")
                if (base.isBlank()) null else "$pkg|$base"
            } catch (_: Exception) { null }
        }

        private fun getCachedLikeStatusFor(controller: MediaController): String {
            val key = buildTrackKey(controller) ?: return "none"
            synchronized(likeStatusCache) {
                return likeStatusCache[key] ?: "none"
            }
        }

        private fun updateCachedLikeStatusFor(controller: MediaController, status: String) {
            val key = buildTrackKey(controller) ?: return
            synchronized(likeStatusCache) {
                likeStatusCache[key] = status
                // Trim cache to 100 entries
                if (likeStatusCache.size > 100) {
                    val it = likeStatusCache.entries.iterator()
                    if (it.hasNext()) {
                        it.next()
                        it.remove()
                    }
                }
            }
        }

        // Public helpers for other components
        fun setCachedLikeStatusForCurrent(context: Context, status: String): Boolean {
            return try {
                val msm = context.getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
                val componentName = ComponentName(context, MediaNotificationListener::class.java)
                val sessions = try { msm.getActiveSessions(componentName) } catch (_: Exception) { emptyList() }
                val controller = sessions.firstOrNull { it.playbackState?.actions != 0L } ?: return false
                updateCachedLikeStatusFor(controller, status)
                true
            } catch (_: Exception) { false }
        }

        fun pauseMediaListener() {
            isMediaListenerPaused = true
            Log.d(TAG, "Media listener paused - receiving playing media from Mac")
        }

        fun resumeMediaListener() {
            // Don't resume if globally disabled
            if (!isNowPlayingEnabled) return
            isMediaListenerPaused = false
            Log.d(TAG, "Media listener resumed")
        }
    }

    private val serviceJob = Job()
    private val serviceScope = CoroutineScope(Dispatchers.IO + serviceJob)
    private lateinit var dataStoreManager: DataStoreManager

    // Cache to store last 5 notifications
    private val notificationCache = LinkedList<Pair<String, String>>()
    private val maxCache = 1

    override fun onCreate() {
        super.onCreate()
        dataStoreManager = DataStoreManager(this)
        serviceInstance = this

        // Load the persisted now playing setting
        serviceScope.launch {
            try {
                val enabled = dataStoreManager.getSendNowPlayingEnabled().first()
                isNowPlayingEnabled = enabled
                if (!enabled) {
                    isMediaListenerPaused = true
                }
                Log.d(TAG, "Initialized now playing setting: $enabled")
            } catch (_: Exception) { }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected - Ready to sync notifications")
        updateMediaInfo()
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.d(TAG, "Notification listener disconnected")

        serviceJob.cancel()
        WebSocketUtil.cleanup()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        sbn?.let { notification ->
            Log.d(TAG, "Notification posted: ${notification.packageName} - ${notification.notification?.extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()}")

            // Skip media processing if media listener is paused or globally disabled
            if (!isMediaListenerPaused && isNowPlayingEnabled) {
                // Update media info and check for changes (includes like status)
                val previousMediaInfo = currentMediaInfo
                updateMediaInfo()

                // If media info changed, trigger sync
                if (previousMediaInfo != currentMediaInfo) {
                    Log.d(TAG, "Media info changed, triggering sync")
                    SyncManager.onMediaStateChanged(this)
                }
            } else {
                Log.d(TAG, "Media listener paused/disabled - skipping media state change processing")
            }

            // Always process notification for sync (non-media notifications)
            processNotificationForSync(notification)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        if (sbn != null) handleNotificationRemoval(sbn)
    }

    // API level variants call the same handler to ensure we catch swipe-away removals
    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap) {
        super.onNotificationRemoved(sbn, rankingMap)
        handleNotificationRemoval(sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification, rankingMap: RankingMap, reason: Int) {
        super.onNotificationRemoved(sbn, rankingMap, reason)
        handleNotificationRemoval(sbn)
    }

    private fun handleNotificationRemoval(sbn: StatusBarNotification) {
        Log.d(TAG, "Notification removed: ${sbn.packageName}")

        // Skip media processing if media listener is paused or globally disabled
        if (!isMediaListenerPaused && isNowPlayingEnabled) {
            // Update media info and check for changes
            val previousMediaInfo = currentMediaInfo
            updateMediaInfo()

            // If media info changed, trigger sync
            if (previousMediaInfo != currentMediaInfo) {
                Log.d(TAG, "Media info changed after notification removal, triggering sync")
                SyncManager.onMediaStateChanged(this)
            }
        } else {
            Log.d(TAG, "Media listener paused/disabled - skipping media state change processing after removal")
        }

        // Send dismissal update to Mac for real removals
        serviceScope.launch {
            try {
                val id = NotificationDismissalUtil.getIdForSbn(sbn)
                if (id.isNullOrEmpty()) {
                    // Skip if we cannot map to a known id
                    return@launch
                }
                // If this dismissal was initiated by our own dismiss request, skip echo
                val wasSuppressed = NotificationDismissalUtil.consumeSuppressed(id)
                if (wasSuppressed) {
                    // Clean local caches and ignore
                    NotificationDismissalUtil.removeFromCaches(id)
                    return@launch
                }

                // Build and send update
                if (WebSocketUtil.isConnected()) {
                    val update = JsonUtil.toSingleLine(JsonUtil.createNotificationUpdateJson(id, dismissed = true, action = "dismiss"))
                    val sent = WebSocketUtil.sendMessage(update)
                    Log.d(TAG, "Sent notificationUpdate for $id: $sent")
                } else {
                    Log.d(TAG, "WebSocket not connected; skipping notificationUpdate for $id")
                }

                // Remove from caches since it's gone now
                NotificationDismissalUtil.removeFromCaches(id)
            } catch (e: Exception) {
                Log.e(TAG, "Error sending notificationUpdate: ${e.message}")
            }
        }
    }

    private fun processNotificationForSync(sbn: StatusBarNotification) {
        serviceScope.launch {
            try {
                // Skip notifications from AirSync itself to prevent feedback loops
                if (sbn.packageName == packageName) {
                    return@launch
                }

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

                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()

                // Use big text if available, otherwise use regular text
                val body = bigText?.takeIf { it.isNotEmpty() } ?: text

                // Get app name from package name
                val appName = getAppNameFromPackage(sbn.packageName)

                // Only sync if we have meaningful content
                if (title.isNotEmpty() || body.isNotEmpty()) {
                    Log.d(TAG, "Syncing notification - App: $appName, Package: ${sbn.packageName}, Title: $title, Body: $body")

                    // Save the app to our list if it's new (auto-enable new apps)
                    if (appSettings == null) {
                        Log.d(TAG, "New app detected: ${sbn.packageName}, adding to preferences")
                        saveNewAppToPreferences(sbn.packageName, appName)
                    }

                    // Check for duplicate notifications
                    if (isDuplicateNotification(sbn.packageName, body)) {
                        Log.d(TAG, "Duplicate notification detected, skipping sync: $title")
                        return@launch
                    }

                    // Generate unique notification ID
                    val notificationId = NotificationDismissalUtil.generateNotificationId(
                        sbn.packageName,
                        title,
                        sbn.postTime
                    )

                    // Store notification for potential dismissal or actions
                    NotificationDismissalUtil.storeNotification(notificationId, sbn)

                    // Extract actions: button or reply
                    val actions = mutableListOf<Pair<String, String>>()
                    try {
                        val notifActions = notification.actions
                        if (notifActions != null) {
                            for (action in notifActions) {
                                val hasReply = action.remoteInputs?.any { it.allowFreeFormInput } == true
                                val type = if (hasReply) "reply" else "button"
                                val name = action.title?.toString() ?: "Action"
                                actions.add(name to type)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to extract actions: ${e.message}")
                    }

                    // Create notification JSON with actions
                    val notificationJson = JsonUtil.toSingleLine(
                        JsonUtil.createNotificationJson(
                            id = notificationId,
                            title = title,
                            body = body,
                            app = appName,
                            packageName = sbn.packageName,
                            actions = actions
                        )
                    )

                    Log.d(TAG, "Preparing to send notification: $notificationJson")

                    if (WebSocketUtil.isConnected()) {
                        Log.d(TAG, "Sending notification via WebSocket")
                        val success = WebSocketUtil.sendMessage(notificationJson)
                        if (success) {
                            Log.d(TAG, "Notification sent successfully via existing WebSocket connection")
                        } else {
                            Log.e(TAG, "Failed to send notification via WebSocket")
                        }
                    } else {
                        Log.d(TAG, "WebSocket not connected, skipping notification sync")
                    }
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
                    Log.e(TAG, "Error checking app: ${e.message}")
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

        val extras = sbn.notification.extras
        // Skip notifications without meaningful content
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        return title.isNullOrEmpty() && text.isNullOrEmpty()
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

    private suspend fun sendNotificationToDesktop(title: String, body: String, appName: String, sbn: StatusBarNotification) {
        // replaced by processNotificationForSync path; keep for compatibility if referenced elsewhere
        try {
            val ipAddress = dataStoreManager.getIpAddress().first()
            val port = dataStoreManager.getPort().first().toIntOrNull() ?: 6996
            Log.d(TAG, "Skipping legacy send path to $ipAddress:$port; using unified processNotificationForSync")
        } catch (e: Exception) {
            Log.e(TAG, "Error in legacy sendNotificationToDesktop: ${e.message}")
        }
    }


    private fun updateMediaInfo() {
        currentMediaInfo = getMediaInfo(this)
        Log.d(TAG, "Updated media info: $currentMediaInfo")
    }

    private fun isDuplicateNotification(packageName: String, body: String?): Boolean {
        if (body == null) return false

        // Check if the notification is already in the cache
        val isDuplicate = notificationCache.any { it.first == packageName && it.second == body }

        // If not a duplicate, add it to the cache
        if (!isDuplicate) {
            if (notificationCache.size >= maxCache) {
                notificationCache.removeFirst() // Remove the oldest notification
            }
            notificationCache.add(Pair(packageName, body))
        }

        return isDuplicate
    }
}
