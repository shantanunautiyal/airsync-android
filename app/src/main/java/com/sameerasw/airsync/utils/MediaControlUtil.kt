package com.sameerasw.airsync.utils

import android.content.ComponentName
import android.content.Context
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.util.Log
import android.view.KeyEvent
import com.sameerasw.airsync.service.MediaNotificationListener

object MediaControlUtil {
    private const val TAG = "MediaControlUtil"

    /**
     * Play or pause the current media
     */
    fun playPause(context: Context): Boolean {
        return try {
            val controller = getActiveMediaController(context)
            if (controller != null) {
                val playbackState = controller.playbackState
                if (playbackState?.state == PlaybackState.STATE_PLAYING) {
                    controller.transportControls.pause()
                    Log.d(TAG, "Media paused")
                } else {
                    controller.transportControls.play()
                    Log.d(TAG, "Media played")
                }
                true
            } else {
                // Fallback: Send media button event
                sendMediaButtonEvent(context, KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in playPause: ${e.message}")
            false
        }
    }

    /**
     * Skip to next track
     */
    fun skipNext(context: Context): Boolean {
        return try {
            val controller = getActiveMediaController(context)
            if (controller != null) {
                controller.transportControls.skipToNext()
                Log.d(TAG, "Skipped to next track")
                true
            } else {
                // Fallback: Send media button event
                sendMediaButtonEvent(context, KeyEvent.KEYCODE_MEDIA_NEXT)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in skipNext: ${e.message}")
            false
        }
    }

    /**
     * Skip to previous track
     */
    fun skipPrevious(context: Context): Boolean {
        return try {
            val controller = getActiveMediaController(context)
            if (controller != null) {
                controller.transportControls.skipToPrevious()
                Log.d(TAG, "Skipped to previous track")
                true
            } else {
                // Fallback: Send media button event
                sendMediaButtonEvent(context, KeyEvent.KEYCODE_MEDIA_PREVIOUS)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in skipPrevious: ${e.message}")
            false
        }
    }

    /**
     * Stop media playback
     */
    fun stop(context: Context): Boolean {
        return try {
            val controller = getActiveMediaController(context)
            if (controller != null) {
                controller.transportControls.stop()
                Log.d(TAG, "Media stopped")
                true
            } else {
                // Fallback: Send media button event
                sendMediaButtonEvent(context, KeyEvent.KEYCODE_MEDIA_STOP)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in stop: ${e.message}")
            false
        }
    }

    /**
     * Get the active media controller
     */
    private fun getActiveMediaController(context: Context): MediaController? {
        return try {
            val mediaSessionManager = context.getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
            val componentName = ComponentName(context, MediaNotificationListener::class.java)
            
            val activeSessions = try {
                mediaSessionManager.getActiveSessions(componentName)
            } catch (e: SecurityException) {
                Log.w(TAG, "SecurityException getting active sessions: ${e.message}")
                emptyList()
            }

            // Return the first active session that can handle transport controls
            activeSessions.firstOrNull { controller ->
                controller.playbackState?.actions != 0L
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting active media controller: ${e.message}")
            null
        }
    }

    /**
     * Send media button event as fallback
     */
    private fun sendMediaButtonEvent(context: Context, keyCode: Int): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
            val downEvent = KeyEvent(KeyEvent.ACTION_DOWN, keyCode)
            val upEvent = KeyEvent(KeyEvent.ACTION_UP, keyCode)
            
            audioManager.dispatchMediaKeyEvent(downEvent)
            audioManager.dispatchMediaKeyEvent(upEvent)
            
            Log.d(TAG, "Sent media button event: $keyCode")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending media button event: ${e.message}")
            false
        }
    }
}
