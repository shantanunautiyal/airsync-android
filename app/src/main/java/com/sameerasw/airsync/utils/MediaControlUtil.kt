package com.sameerasw.airsync.utils

import android.app.Notification
import android.app.PendingIntent
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
     * Toggle like status by invoking the Like/Unlike action in the active media notification.
     */
    fun toggleLike(context: Context): Boolean {
        return try {
            val service = MediaNotificationListener.getInstance() ?: run {
                Log.w(TAG, "Notification listener not available; cannot toggle like")
                return false
            }
            val controller = getActiveMediaController(context)
            val packageName = try { controller?.packageName } catch (_: Exception) { null }

            val active = try { service.activeNotifications } catch (_: Exception) { emptyArray() }
            if (active.isEmpty()) {
                Log.w(TAG, "No active notifications for media; cannot toggle like")
                return false
            }

            // Determine current like status to pick the opposite action
            val currentStatus = try { MediaNotificationListener.getMediaInfo(context).likeStatus } catch (_: Exception) { "none" }
            val preferUnlike = currentStatus == "liked"

            val candidates = if (packageName != null) {
                active.filter { it.packageName == packageName } + active.filter { it.packageName != packageName }
            } else active.toList()

            for (sbn in candidates) {
                val actions = sbn.notification.actions ?: continue
                val action = findLikeAction(actions, preferUnlike)
                    ?: findLikeAction(actions, !preferUnlike)
                if (action != null) {
                    return sendAction(action.actionIntent)
                }
            }

            Log.w(TAG, "No like/unlike action found in active notifications")
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling like: ${e.message}")
            false
        }
    }

    /**
     * Try to perform a direct 'like' action when available.
     */
    fun like(context: Context): Boolean = performSpecificLikeAction(context, preferUnlike = false)

    /**
     * Try to perform a direct 'unlike' action when available.
     */
    fun unlike(context: Context): Boolean = performSpecificLikeAction(context, preferUnlike = true)

    private fun performSpecificLikeAction(context: Context, preferUnlike: Boolean): Boolean {
        return try {
            val service = MediaNotificationListener.getInstance() ?: return false
            val controller = getActiveMediaController(context)
            val packageName = try { controller?.packageName } catch (_: Exception) { null }
            val active = try { service.activeNotifications } catch (_: Exception) { emptyArray() }
            val candidates = if (packageName != null) {
                active.filter { it.packageName == packageName } + active.filter { it.packageName != packageName }
            } else active.toList()
            for (sbn in candidates) {
                val actions = sbn.notification.actions ?: continue
                val action = findLikeAction(actions, preferUnlike)
                if (action != null) return sendAction(action.actionIntent)
            }
            false
        } catch (e: Exception) {
            Log.e(TAG, "Error performing specific like action: ${e.message}")
            false
        }
    }

    private fun findLikeAction(actions: Array<Notification.Action>, preferUnlike: Boolean): Notification.Action? {
        // Heuristics: match action titles for like/unlike/favorite
        val likePredicates = listOf<(String) -> Boolean>(
            { it.contains("like") },
            { it.contains("favorite") },
            { it.contains("favourite") },
            { it.contains("❤") },
            { it.contains("♥") }
        )
        val unlikePredicates = listOf<(String) -> Boolean>(
            { it.contains("unlike") },
            { it.contains("remove from liked") },
            { it.contains("remove like") },
            { it.contains("liked") && it.startsWith("un") }
        )
        val candidates = actions.toList()
        return if (preferUnlike) {
            candidates.firstOrNull { titleMatches(it, unlikePredicates) }
        } else {
            candidates.firstOrNull { titleMatches(it, likePredicates) }
        }
    }

    private fun titleMatches(action: Notification.Action, preds: List<(String) -> Boolean>): Boolean {
        val title = action.title?.toString()?.lowercase()?.trim() ?: return false
        return preds.any { it(title) }
    }

    private fun sendAction(pi: PendingIntent?): Boolean {
        return try {
            if (pi == null) return false
            pi.send()
            Log.d(TAG, "Sent like/unlike action via PendingIntent")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to send like/unlike action: ${e.message}")
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
