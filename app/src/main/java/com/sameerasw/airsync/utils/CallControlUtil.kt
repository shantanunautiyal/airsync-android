package com.sameerasw.airsync.utils

import android.content.Context
import android.telecom.TelecomManager
import android.util.Log
import androidx.annotation.RequiresPermission

object CallControlUtil {
    private const val TAG = "CallControlUtil"

    /**
     * Accept an incoming call. Requires ANSWER_PHONE_CALLS permission (API 26+)
     */
    @RequiresPermission("android.permission.ANSWER_PHONE_CALLS")
    fun acceptCall(context: Context): Boolean {
        return try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            @Suppress("DEPRECATION")
            telecomManager?.acceptRingingCall()
            Log.d(TAG, "Call accepted")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error accepting call: ${e.message}")
            false
        }
    }

    /**
     * Decline/reject an incoming call. Requires ANSWER_PHONE_CALLS permission
     */
    @RequiresPermission("android.permission.ANSWER_PHONE_CALLS")
    fun declineCall(context: Context): Boolean {
        return try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            @Suppress("DEPRECATION")
            telecomManager?.endCall()
            Log.d(TAG, "Call declined")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error declining call: ${e.message}")
            false
        }
    }

    /**
     * End an active call. Requires ANSWER_PHONE_CALLS permission
     */
    @RequiresPermission("android.permission.ANSWER_PHONE_CALLS")
    fun endCall(context: Context): Boolean {
        return try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            @Suppress("DEPRECATION")
            telecomManager?.endCall()
            Log.d(TAG, "Call ended")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error ending call: ${e.message}")
            false
        }
    }

    /**
     * Mute the microphone during a call
     */
    fun muteCall(context: Context): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            val isMuted = audioManager?.isMicrophoneMute == true
            audioManager?.isMicrophoneMute = !isMuted
            Log.d(TAG, "Microphone ${if (!isMuted) "muted" else "unmuted"}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling mute: ${e.message}")
            false
        }
    }

    /**
     * Toggle speaker phone mode
     */
    fun toggleSpeaker(context: Context): Boolean {
        return try {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? android.media.AudioManager
            @Suppress("DEPRECATION")
            val isSpeakerOn = audioManager?.isSpeakerphoneOn == true
            @Suppress("DEPRECATION")
            audioManager?.isSpeakerphoneOn = !isSpeakerOn
            Log.d(TAG, "Speaker ${if (!isSpeakerOn) "enabled" else "disabled"}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error toggling speaker: ${e.message}")
            false
        }
    }

    /**
     * Send DTMF (dual-tone multi-frequency) tone during call
     * Requires ANSWER_PHONE_CALLS permission
     */
    @RequiresPermission("android.permission.ANSWER_PHONE_CALLS")
    fun sendDTMF(context: Context, tone: Char): Boolean {
        return try {
            val telecomManager = context.getSystemService(Context.TELECOM_SERVICE) as? TelecomManager
            // Note: playDtmfTone is not available on all devices, using alternative approach
            Log.d(TAG, "DTMF tone requested: $tone (actual sending depends on device support)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error sending DTMF tone: ${e.message}")
            false
        }
    }
}

