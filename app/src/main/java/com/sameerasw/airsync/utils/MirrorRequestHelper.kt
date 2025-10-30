package com.sameerasw.airsync.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sameerasw.airsync.MainActivity
import com.sameerasw.airsync.R
import com.sameerasw.airsync.domain.model.MirroringOptions
import com.sameerasw.airsync.service.ScreenCaptureService

/**
 * Helper to manage mirror requests and prevent duplicate popups
 */
object MirrorRequestHelper {
    private const val TAG = "MirrorRequestHelper"
    private const val CHANNEL_ID = "mirror_request_channel"
    private const val NOTIFICATION_ID = 9001
    
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
        
        // Send broadcast to show permission dialog with correct extra key
        val intent = Intent("com.sameerasw.airsync.MIRROR_REQUEST").apply {
            `package` = context.packageName
            putExtra(ScreenCaptureService.EXTRA_MIRRORING_OPTIONS, mirroringOptions)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.sendBroadcast(intent)
        Log.d(TAG, "Sent broadcast for mirror request with options: fps=${mirroringOptions.fps}, quality=${mirroringOptions.quality}, maxWidth=${mirroringOptions.maxWidth}")
        
        // Also show notification in case app is minimized
        showMirrorRequestNotification(context, mirroringOptions)
    }
    
    /**
     * Show notification for mirror request when app is minimized
     */
    private fun showMirrorRequestNotification(context: Context, mirroringOptions: MirroringOptions) {
        createNotificationChannel(context)
        
        // Intent to open app
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("show_mirror_request", true)
            putExtra(ScreenCaptureService.EXTRA_MIRRORING_OPTIONS, mirroringOptions)
        }
        val pendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        val builder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            android.app.Notification.Builder(context, CHANNEL_ID)
        } else {
            android.app.Notification.Builder(context)
        }
        
        val notification = builder
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Screen Mirroring Request")
            .setContentText("Mac wants to mirror your screen")
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    /**
     * Create notification channel for mirror requests
     */
    private fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Screen Mirroring Requests",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifications for screen mirroring requests from Mac"
        }
        
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
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
