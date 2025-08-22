package com.sameerasw.airsync.utils

import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log
import com.sameerasw.airsync.service.MediaNotificationListener
import java.util.concurrent.ConcurrentHashMap

object NotificationDismissalUtil {
    private const val TAG = "NotificationDismissalUtil"
    
    // Store active notifications with their IDs for dismissal or actions
    private val activeNotifications = ConcurrentHashMap<String, StatusBarNotification>()
    
    /**
     * Generate a unique notification ID
     */
    fun generateNotificationId(packageName: String, title: String, timestamp: Long): String {
        return "${packageName}_${title.hashCode()}_$timestamp"
    }
    
    /**
     * Store a notification for dismissal
     */
    fun storeNotification(id: String, notification: StatusBarNotification) {
        activeNotifications[id] = notification
        Log.d(TAG, "Stored notification with ID: $id")
        
        // Clean up old notifications
        if (activeNotifications.size > 50) {
            val oldestKeys = activeNotifications.keys.take(activeNotifications.size - 50)
            oldestKeys.forEach { activeNotifications.remove(it) }
        }
    }
    
    /**
     * Dismiss a notification by its ID
     */
    fun dismissNotification(notificationId: String): Boolean {
        return try {
            val notification = activeNotifications[notificationId]
            if (notification != null) {
                val service = getNotificationListenerService()
                if (service != null) {
                    service.cancelNotification(notification.key)
                    activeNotifications.remove(notificationId)
                    Log.d(TAG, "Successfully dismissed notification: $notificationId")
                    true
                } else {
                    Log.w(TAG, "Notification listener service not available")
                    false
                }
            } else {
                Log.w(TAG, "Notification with ID $notificationId not found")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing notification $notificationId: ${e.message}")
            false
        }
    }

    /**
     * Perform an action on the notification by title. If replyText is provided and the action supports inline reply, send it.
     */
    fun performNotificationAction(notificationId: String, actionName: String, replyText: String? = null): Boolean {
        return try {
            val service = getNotificationListenerService()
            if (service == null) {
                Log.w(TAG, "Notification listener service not available")
                return false
            }
            val sbn = activeNotifications[notificationId]
            if (sbn == null) {
                Log.w(TAG, "Notification with ID $notificationId not found for action $actionName")
                return false
            }

            val actions = sbn.notification.actions
            if (actions == null || actions.isEmpty()) {
                Log.w(TAG, "No actions available on notification $notificationId")
                return false
            }

            val target = actions.firstOrNull { it.title?.toString()?.equals(actionName, ignoreCase = true) == true }
            if (target == null) {
                Log.w(TAG, "Action '$actionName' not found on notification $notificationId")
                return false
            }

            val pendingIntent = target.actionIntent
            if (replyText != null) {
                // Inline reply path
                val remoteInputs = target.remoteInputs
                if (remoteInputs.isNullOrEmpty()) {
                    Log.w(TAG, "Action '$actionName' does not support reply on $notificationId")
                    return false
                }
                val intent = Intent()
                val results = Bundle()
                remoteInputs.forEach { ri ->
                    results.putCharSequence(ri.resultKey, replyText)
                }
                // Attach results
                RemoteInput.addResultsToIntent(remoteInputs, intent, results)
                try {
                    pendingIntent.send(service, 0, intent)
                    Log.d(TAG, "Sent inline reply for action '$actionName' on $notificationId")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending inline reply: ${e.message}")
                    false
                }
            } else {
                // Simple button action
                try {
                    pendingIntent.send()
                    Log.d(TAG, "Invoked action '$actionName' on $notificationId")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "Error invoking action '$actionName': ${e.message}")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error performing action: ${e.message}")
            false
        }
    }

    /**
     * Get the notification listener service instance
     */
    private fun getNotificationListenerService(): MediaNotificationListener? {
        return MediaNotificationListener.getInstance()
    }
}
