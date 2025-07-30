package com.sameerasw.airsync.utils

import android.content.Context
import android.media.AudioManager
import android.util.Log

object VolumeControlUtil {
    private const val TAG = "VolumeControlUtil"

    /**
     * Set system volume to a specific percentage (0-100)
     */
    fun setVolume(context: Context, volumePercentage: Int): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

            // Clamp volume percentage between 0 and 100
            val clampedPercentage = volumePercentage.coerceIn(0, 100)

            // Calculate the actual volume level
            val targetVolume = (clampedPercentage * maxVolume / 100f).toInt()

            // Set the volume
            audioManager.setStreamVolume(
                AudioManager.STREAM_MUSIC,
                targetVolume,
                AudioManager.FLAG_SHOW_UI
            )

            Log.d(TAG, "Volume set to $clampedPercentage% (level: $targetVolume/$maxVolume)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error setting volume: ${e.message}")
            false
        }
    }

    /**
     * Get current volume as percentage (0-100)
     */
    fun getCurrentVolumePercentage(context: Context): Int {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
            val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)

            if (maxVolume > 0) {
                (currentVolume * 100 / maxVolume)
            } else {
                0
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting current volume: ${e.message}")
            0
        }
    }

    /**
     * Increase volume by specified percentage
     */
    fun increaseVolume(context: Context, incrementPercentage: Int = 10): Boolean {
        val currentPercentage = getCurrentVolumePercentage(context)
        val newPercentage = (currentPercentage + incrementPercentage).coerceAtMost(100)
        return setVolume(context, newPercentage)
    }

    /**
     * Decrease volume by specified percentage
     */
    fun decreaseVolume(context: Context, decrementPercentage: Int = 10): Boolean {
        val currentPercentage = getCurrentVolumePercentage(context)
        val newPercentage = (currentPercentage - decrementPercentage).coerceAtLeast(0)
        return setVolume(context, newPercentage)
    }

    /**
     * Mute/unmute the device
     */
    fun toggleMute(context: Context): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

            if (currentVolume > 0) {
                // Mute by setting volume to 0
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    0,
                    AudioManager.FLAG_SHOW_UI
                )
                Log.d(TAG, "Device muted")
            } else {
                // Unmute by setting to 50% volume
                val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                val halfVolume = maxVolume / 2
                audioManager.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    halfVolume,
                    AudioManager.FLAG_SHOW_UI
                )
                Log.d(TAG, "Device unmuted to 50% volume")
            }
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling mute: ${e.message}")
            false
        }
    }
}
