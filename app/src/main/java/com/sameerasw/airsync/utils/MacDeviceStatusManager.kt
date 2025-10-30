package com.sameerasw.airsync.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.Log
import com.sameerasw.airsync.domain.model.MacBattery
import com.sameerasw.airsync.domain.model.MacDeviceStatus
import com.sameerasw.airsync.domain.model.MacMusicInfo
import com.sameerasw.airsync.service.MacMediaPlayerService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object MacDeviceStatusManager {
    private const val TAG = "MacDeviceStatusManager"

    private val _macDeviceStatus = MutableStateFlow<MacDeviceStatus?>(null)
    val macDeviceStatus: StateFlow<MacDeviceStatus?> = _macDeviceStatus.asStateFlow()

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

            // Always show Mac media player when there's any media info from Mac
            // This ensures the media controls are always visible in the sidebar/notification area
            // even when music is paused, so users can control Mac playback at any time
            if (title.isNotEmpty() || artist.isNotEmpty()) {
                MacMediaPlayerService.startMacMedia(context, title, artist, isPlaying, currentAlbumArt)
                Log.d(TAG, "Started/Updated Mac media player service")
            } else {
                MacMediaPlayerService.stopMacMedia(context)
                Log.d(TAG, "Stopped Mac media player service - no media info")
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

    fun cleanup() {
        try {
            currentAlbumArt = null
            _macDeviceStatus.value = null
            Log.d(TAG, "Mac device status cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error cleaning up: ${e.message}")
        }
    }
}
