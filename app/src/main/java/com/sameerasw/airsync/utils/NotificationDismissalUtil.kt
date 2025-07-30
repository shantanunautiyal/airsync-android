package com.sameerasw.airsync.utils

import android.service.notification.StatusBarNotification
import android.util.Log
import com.sameerasw.airsync.service.MediaNotificationListener
import java.util.concurrent.ConcurrentHashMap

object NotificationDismissalUtil {
    private const val TAG = "NotificationDismissalUtil"
    
    // Store active notifications with their IDs for dismissal
    private val activeNotifications = ConcurrentHashMap<String, StatusBarNotification>()
    
    /**
     * Generate a unique notification ID
     */
    fun generateNotificationId(packageName: String, title: String, timestamp: Long): String {
        return "${packageName}_${title.hashCode()}_$timestamp"
    }
    
    /**
     * Store a notification for potential dismissal
     */
    fun storeNotification(id: String, notification: StatusBarNotification) {
        activeNotifications[id] = notification
        Log.d(TAG, "Stored notification with ID: $id")
        
        // Clean up old notifications (keep only last 50)
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
                // Try to cancel the notification through the service
                val service = getNotificationListenerService()
                if (service != null) {
                    service.cancelNotification(notification.key)
                    activeNotifications.remove(notificationId)
                    Log.d(TAG, "Successfully dismissed notification: $notificationId")
                    return true
                } else {
                    Log.w(TAG, "Notification listener service not available")
                    return false
                }
            } else {
                Log.w(TAG, "Notification with ID $notificationId not found")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error dismissing notification $notificationId: ${e.message}")
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
