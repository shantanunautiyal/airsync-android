package com.sameerasw.airsync.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.support.v4.media.MediaMetadataCompat
import android.util.Base64
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.media.app.NotificationCompat as MediaNotificationCompat
import com.sameerasw.airsync.R
import com.sameerasw.airsync.domain.model.MacDeviceStatus
import com.sameerasw.airsync.domain.model.MacBattery
import com.sameerasw.airsync.domain.model.MacMusicInfo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MacDeviceStatusManager {
    private const val TAG = "MacDeviceStatusManager"
    private const val NOTIFICATION_ID = 1001
    private const val CHANNEL_ID = "mac_media_player"

    private val _macDeviceStatus = MutableStateFlow<MacDeviceStatus?>(null)
    val macDeviceStatus: StateFlow<MacDeviceStatus?> = _macDeviceStatus.asStateFlow()

    private var mediaSession: MediaSessionCompat? = null
    private var currentAlbumArt: Bitmap? = null

    fun updateStatus(
        context: Context,
        batteryLevel: Int,
        isCharging: Boolean,
        isPaired: Boolean,
        isPlaying: Boolean,
        title: String,
        artist: String,
        volume: Int,
        isMuted: Boolean,
        albumArt: String,
        likeStatus: String
    ) {
        try {
            val macBattery = MacBattery(level = batteryLevel, isCharging = isCharging)
            val macMusicInfo = MacMusicInfo(
                isPlaying = isPlaying,
                title = title,
                artist = artist,
                volume = volume,
                isMuted = isMuted,
                albumArt = albumArt,
                likeStatus = likeStatus
            )

            val status = MacDeviceStatus(
                battery = macBattery,
                isPaired = isPaired,
                music = macMusicInfo
            )

            _macDeviceStatus.value = status

            // Decode album art if available
            currentAlbumArt = decodeAlbumArt(albumArt)

            // Create or update media session and notification
            if (title.isNotEmpty() || artist.isNotEmpty()) {
                createOrUpdateMediaSession(context, macMusicInfo)
                showMediaNotification(context, macMusicInfo)
            } else {
                hideMediaNotification(context)
            }

            Log.d(TAG, "Mac device status updated - Playing: $isPlaying, Title: $title, Artist: $artist")
        } catch (e: Exception) {
            Log.e(TAG, "Error updating Mac device status: ${e.message}")
        }
    }

    private fun decodeAlbumArt(base64String: String): Bitmap? {
        return try {
            if (base64String.isNotEmpty()) {
                val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error decoding album art: ${e.message}")
            null
        }
    }

    private fun createOrUpdateMediaSession(context: Context, musicInfo: MacMusicInfo) {
        try {
            if (mediaSession == null) {
                mediaSession = MediaSessionCompat(context, "MacMediaSession").apply {
                    setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
                    setCallback(object : MediaSessionCompat.Callback() {
                        override fun onPlay() {
                            sendMacMediaControl("play")
                        }

                        override fun onPause() {
                            sendMacMediaControl("pause")
                        }

                        override fun onSkipToNext() {
                            sendMacMediaControl("next")
                        }

                        override fun onSkipToPrevious() {
                            sendMacMediaControl("previous")
                        }

                        override fun onStop() {
                            sendMacMediaControl("stop")
                        }
                    })
                }
            }

            // Update metadata
            val metadataBuilder = MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, musicInfo.title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, musicInfo.artist)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, musicInfo.title)
                .putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, musicInfo.artist)

            currentAlbumArt?.let { bitmap ->
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, bitmap)
                metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, bitmap)
            }

            mediaSession?.setMetadata(metadataBuilder.build())

            // Update playback state
            val playbackState = if (musicInfo.isPlaying) {
                PlaybackStateCompat.STATE_PLAYING
            } else {
                PlaybackStateCompat.STATE_PAUSED
            }

            val actions = PlaybackStateCompat.ACTION_PLAY_PAUSE or
                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
                    PlaybackStateCompat.ACTION_STOP

            mediaSession?.setPlaybackState(
                PlaybackStateCompat.Builder()
                    .setState(playbackState, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                    .setActions(actions)
                    .build()
            )

            mediaSession?.isActive = true
        } catch (e: Exception) {
            Log.e(TAG, "Error creating/updating media session: ${e.message}")
        }
    }

    private fun sendMacMediaControl(action: String) {
        try {
            // Send media control command to Mac via WebSocket
            val controlJson = """{"type":"macMediaControl","data":{"action":"$action"}}"""
            WebSocketUtil.sendMessage(controlJson)
            Log.d(TAG, "Sent Mac media control: $action")
        } catch (e: Exception) {
            Log.e(TAG, "Error sending Mac media control: ${e.message}")
        }
    }

    private fun showMediaNotification(context: Context, musicInfo: MacMusicInfo) {
        try {
            createNotificationChannel(context)

            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // Create media style notification
            val builder = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_laptop_24)
                .setContentTitle(musicInfo.title.ifEmpty { "Mac Media Player" })
                .setContentText(musicInfo.artist.ifEmpty { "No artist" })
                .setSubText("Mac Media")
                .setOngoing(true)
                .setShowWhen(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)

            // Add album art if available
            currentAlbumArt?.let { bitmap ->
                builder.setLargeIcon(bitmap)
            }

            // Add media controls
            val prevIntent = createMediaActionIntent(context, "previous")
            val playPauseIntent = createMediaActionIntent(context, if (musicInfo.isPlaying) "pause" else "play")
            val nextIntent = createMediaActionIntent(context, "next")

            builder.addAction(
                R.drawable.outline_skip_previous_24,
                "Previous",
                prevIntent
            )

            builder.addAction(
                if (musicInfo.isPlaying) R.drawable.outline_pause_24 else R.drawable.outline_play_arrow_24,
                if (musicInfo.isPlaying) "Pause" else "Play",
                playPauseIntent
            )

            builder.addAction(
                R.drawable.outline_skip_next_24,
                "Next",
                nextIntent
            )

            // Set media style
            val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)

            mediaSession?.let { session ->
                mediaStyle.setMediaSession(session.sessionToken)
            }

            builder.setStyle(mediaStyle)

            notificationManager.notify(NOTIFICATION_ID, builder.build())

            Log.d(TAG, "Mac media notification shown")
        } catch (e: Exception) {
            Log.e(TAG, "Error showing media notification: ${e.message}")
        }
    }

    private fun createMediaActionIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, MacMediaActionReceiver::class.java).apply {
            this.action = "com.sameerasw.airsync.MAC_MEDIA_$action"
        }
        return PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel(context: Context) {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channel = NotificationChannel(
            CHANNEL_ID,
            "Mac Media Player",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows Mac media playback controls"
            setShowBadge(false)
            setSound(null, null)
        }

        notificationManager.createNotificationChannel(channel)
    }

    fun hideMediaNotification(context: Context) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.cancel(NOTIFICATION_ID)

            mediaSession?.isActive = false

            Log.d(TAG, "Mac media notification hidden")
        } catch (e: Exception) {
            Log.e(TAG, "Error hiding media notification: ${e.message}")
        }
    }

    fun cleanup() {
        try {
            mediaSession?.release()
            mediaSession = null
            currentAlbumArt = null
            _macDeviceStatus.value = null
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up: ${e.message}")
        }
    }
}
