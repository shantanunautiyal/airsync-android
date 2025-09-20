package com.sameerasw.airsync.utils

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.sameerasw.airsync.MainActivity
import com.sameerasw.airsync.R
import com.sameerasw.airsync.service.NotificationActionReceiver
import androidx.core.net.toUri

object NotificationUtil {
    private const val CHANNEL_ID = "airsync_status"
    private const val NOTIFICATION_ID = 1001
    private const val FILE_CHANNEL_ID = "airsync_file_transfer"
    // New: Continue Browsing channel
    private const val CONTINUE_CHANNEL_ID = "airsync_continue_browsing"

    fun createNotificationChannel(context: Context) {
        val name = "AirSync Status"
        val descriptionText = "Shows connection status"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
            description = descriptionText
            setShowBadge(false)
        }

        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun createContinueBrowsingChannel(context: Context) {
        val name = "Continue browsing"
        val descriptionText = "Quick open links received from desktop"
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel(CONTINUE_CHANNEL_ID, name, importance).apply {
            description = descriptionText
            setShowBadge(true)
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Show the real-time connection status notification with a single dynamic action.
     * Content contract:
     * - Title: device name if available, otherwise "AirSync"
    * - Text: "Connected", "Connecting...", or "Disconnected"
     * - One action button based on state:
     *   - Connected -> "Disconnect"
     *   - Disconnected with a reconnect target -> "Reconnect"
     *   - Otherwise -> "Open app"
     */
    fun showConnectionStatusNotification(
        context: Context,
        deviceName: String?,
        isConnected: Boolean,
        isConnecting: Boolean,
        isAutoReconnecting: Boolean,
        hasReconnectTarget: Boolean
    ) {
        createNotificationChannel(context)

        val title = deviceName ?: "AirSync"
        val content = when {
            isAutoReconnecting -> "Trying to reconnect to $title..."
            isConnecting -> "Connecting..."
            isConnected -> "Connected"
            else -> "Disconnected"
        }

        // Decide action
        val (actionTitle, pendingIntent) = when {
            isConnected -> {
                "Disconnect" to PendingIntent.getBroadcast(
                    context,
                    101,
                    Intent(context, NotificationActionReceiver::class.java).apply {
                        action = NotificationActionReceiver.ACTION_DISCONNECT
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
            isAutoReconnecting -> {
                "Stop" to PendingIntent.getBroadcast(
                    context,
                    106,
                    Intent(context, NotificationActionReceiver::class.java).apply {
                        action = NotificationActionReceiver.ACTION_STOP_RECONNECT
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
            hasReconnectTarget -> {
                "Reconnect" to PendingIntent.getBroadcast(
                    context,
                    103,
                    Intent(context, NotificationActionReceiver::class.java).apply {
                        action = NotificationActionReceiver.ACTION_RECONNECT
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
            else -> {
                "Open app" to PendingIntent.getActivity(
                    context,
                    104,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            }
        }

        val smallIcon = DeviceIconResolver.getLastDeviceIconRes(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setSmallIcon(smallIcon)
            .setOngoing(isConnecting || isAutoReconnecting)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setShowWhen(false)
            .addAction(0, actionTitle, pendingIntent)
            .setContentIntent(
                PendingIntent.getActivity(
                    context,
                    105,
                    Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
            )

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, builder.build())
        } catch (e: SecurityException) {
            android.util.Log.w("NotificationUtil", "Failed to show status notification: ${e.message}")
        }
    }

    fun hideConnectionStatusNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }

    // File transfer notifications remain unchanged
    fun createFileChannel(context: Context) {
        val name = "File transfers"
        val descriptionText = "Notifications for file transfers"
        val importance = NotificationManager.IMPORTANCE_LOW
        val channel = NotificationChannel(FILE_CHANNEL_ID, name, importance).apply {
            description = descriptionText
            setShowBadge(false)
        }

        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showFileProgress(context: Context, notifId: Int, fileName: String, percent: Int) {
        createFileChannel(context)
        val manager = NotificationManagerCompat.from(context)
        val notif = NotificationCompat.Builder(context, FILE_CHANNEL_ID)
            .setContentTitle("Receiving: $fileName")
            .setContentText("$percent%")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setProgress(100, percent, false)
            .setOnlyAlertOnce(true)
            .build()
        manager.notify(notifId, notif)
    }

    @RequiresPermission(Manifest.permission.POST_NOTIFICATIONS)
    fun showFileComplete(context: Context, notifId: Int, fileName: String, verified: Boolean, contentUri: Uri? = null) {
        createFileChannel(context)
        val manager = NotificationManagerCompat.from(context)
        manager.cancel(notifId)

        val builder = NotificationCompat.Builder(context, FILE_CHANNEL_ID)
            .setContentTitle("Received: $fileName")
            .setContentText(if (verified) "Saved to Downloads" else "Saved to Downloads (checksum mismatch)")
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .setProgress(0, 0, false)

        if (contentUri != null) {
            val openIntent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(contentUri, context.contentResolver.getType(contentUri))
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
            }
            val pending = PendingIntent.getActivity(
                context,
                notifId,
                openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentIntent(pending)
            builder.addAction(android.R.drawable.ic_menu_view, "Open", pending)
        }

        val notif = builder.build()
        manager.notify(notifId, notif)
    }

    // New: Continue Browsing notifications
    fun showContinueBrowsingLink(context: Context, url: String) {
        createContinueBrowsingChannel(context)
        val manager = NotificationManagerCompat.from(context)
        val notifId = (url.hashCode() and 0x7fffffff) // stable positive ID per URL

        // Normalize only for the open intent (keep text as-is)
        val trimmed = url.trim()
        val normalized = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://$trimmed"

        val openIntent = Intent(Intent.ACTION_VIEW, normalized.toUri())
        val openPending = PendingIntent.getActivity(
            context,
            notifId,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, NotificationActionReceiver::class.java).apply {
            action = NotificationActionReceiver.ACTION_CONTINUE_BROWSING_DISMISS
            putExtra("notif_id", notifId)
        }
        val dismissPending = PendingIntent.getBroadcast(
            context,
            notifId + 1,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(context, CONTINUE_CHANNEL_ID)
            .setSmallIcon(R.drawable.outline_open_in_browser_24)
            .setContentTitle("Continue browsing")
            .setContentText(trimmed)
            .setStyle(NotificationCompat.BigTextStyle().bigText(trimmed))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(openPending)
            .addAction(android.R.drawable.ic_menu_view, "Open", openPending)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPending)

        try {
            manager.notify(notifId, builder.build())
        } catch (e: SecurityException) {
            android.util.Log.w("NotificationUtil", "Failed to show continue-browsing notification: ${e.message}")
        }
    }

    fun clearContinueBrowsingNotifications(context: Context) {
        try {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.activeNotifications?.forEach { sbn ->
                if (sbn.notification.channelId == CONTINUE_CHANNEL_ID) {
                    nm.cancel(sbn.id)
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("NotificationUtil", "Failed to clear continue-browsing notifications: ${e.message}")
        }
    }
}
