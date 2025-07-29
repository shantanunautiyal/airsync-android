package com.sameerasw.airsync

import android.content.ComponentName
import android.content.Context
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

class MediaNotificationListener : NotificationListenerService() {

    companion object {
        @Volatile
        private var currentMediaInfo: MediaInfo? = null
        private const val TAG = "MediaNotificationListener"

        fun getCurrentMediaInfo(): MediaInfo? = currentMediaInfo

        fun getMediaInfo(context: Context): MediaInfo {
            return try {
                val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager

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

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d(TAG, "Notification listener connected")
        updateMediaInfo()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        Log.d(TAG, "Notification posted: ${sbn?.packageName}")
        updateMediaInfo()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        Log.d(TAG, "Notification removed: ${sbn?.packageName}")
        updateMediaInfo()
    }

    private fun updateMediaInfo() {
        currentMediaInfo = getMediaInfo(this)
        Log.d(TAG, "Updated media info: $currentMediaInfo")
    }
}

data class MediaInfo(
    val isPlaying: Boolean,
    val title: String,
    val artist: String
)
