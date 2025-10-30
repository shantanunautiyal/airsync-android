package com.sameerasw.airsync.utils

import android.content.Context
import android.content.Intent
import android.util.Log
import com.sameerasw.airsync.domain.model.MirroringOptions
import com.sameerasw.airsync.service.ScreenCaptureService

/**
 * Helper to manage mirror requests and prevent duplicate popups
 */
object MirrorRequestHelper {
    private const val TAG = "MirrorRequestHelper"
    
    /**
     * Handle mirror request from Mac
     * Prevents duplicate popups if mirroring is already active
     */
    fun handleMirrorRequest(context: Context, mirroringOptions: MirroringOptions) {
        // Check if mirroring is already active
        if (ScreenCaptureService.isStreaming.value) {
            Log.w(TAG, "Screen mirroring already active, ignoring duplicate request")
            // Send acknowledgment to Mac that mirroring is already running
            sendMirrorStatus(context, true, "Already mirroring")
            return
        }
        
        // Send broadcast to show permission dialog
        val intent = Intent("com.sameerasw.airsync.MIRROR_REQUEST").apply {
            `package` = context.packageName
            putExtra("mirroringOptions", mirroringOptions)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.sendBroadcast(intent)
        Log.d(TAG, "Sent broadcast for mirror request with options: $mirroringOptions")
    }
    
    /**
     * Send mirror status back to Mac
     */
    private fun sendMirrorStatus(context: Context, isActive: Boolean, message: String) {
        try {
            val json = JsonUtil.createMirrorStatusJson(isActive, message)
            WebSocketUtil.sendMessage(json)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending mirror status", e)
        }
    }
}
