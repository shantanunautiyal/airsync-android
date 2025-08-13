package com.sameerasw.airsync.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sameerasw.airsync.MainActivity
import com.sameerasw.airsync.R
import com.sameerasw.airsync.domain.model.ConnectedDevice
import com.sameerasw.airsync.service.NotificationActionReceiver
import java.text.SimpleDateFormat
import java.util.*

object NotificationUtil {
    private const val CHANNEL_ID = "airsync_status"
    private const val NOTIFICATION_ID = 1001
    private const val TAG = "NotificationUtil"

    fun createNotificationChannel(context: Context) {
        val name = "AirSync Status"
        val descriptionText = "Shows connection status and last sync time"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
            setShowBadge(false)
        }

        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    fun showConnectionStatusNotification(
        context: Context,
        connectedDevice: ConnectedDevice?,
        lastSyncTime: Long?,
        isConnected: Boolean = false
    ) {
        createNotificationChannel(context)

        // Opening the app
        val openAppIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openAppPendingIntent = PendingIntent.getActivity(
            context, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stopping notification sync
        val stopSyncIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_STOP_SYNC
        }
        val stopSyncPendingIntent = PendingIntent.getBroadcast(
            context, 1, stopSyncIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (connectedDevice != null) {
            "Connected to ${connectedDevice.name}"
        } else {
            "AirSync Ready"
        }

        val content = buildString {
            if (connectedDevice != null) {
                append("${connectedDevice.ipAddress}:${connectedDevice.port}")
                if (lastSyncTime != null) {
                    append("\nLast seen: ${formatLastSeen(lastSyncTime)}")
                } else {
                    append("\nNever synced")
                }
            } else {
                append("Waiting for connection")
            }
        }

        val icon = if (isConnected) {
            android.R.drawable.ic_dialog_info
        } else {
            android.R.drawable.ic_dialog_alert
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setSmallIcon(R.drawable.ic_stat_name)
            .setContentIntent(openAppPendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop Sync",
                stopSyncPendingIntent
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(true)
            .setWhen(lastSyncTime ?: System.currentTimeMillis())
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // notification permission is not granted
            android.util.Log.w(TAG, "Failed to show notification: ${e.message}")
        }
    }

    fun hideConnectionStatusNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    private fun formatLastSeen(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diffMs = now - timestamp
        val diffMinutes = diffMs / (1000 * 60)
        val diffHours = diffMinutes / 60
        val diffDays = diffHours / 24

        return when {
            diffMinutes < 1 -> "Just now"
            diffMinutes < 60 -> "${diffMinutes}m ago"
            diffHours < 24 -> "${diffHours}h ago"
            diffDays < 7 -> "${diffDays}d ago"
            else -> {
                val dateFormat = SimpleDateFormat("MMM dd", Locale.getDefault())
                dateFormat.format(Date(timestamp))
            }
        }
    }
}
