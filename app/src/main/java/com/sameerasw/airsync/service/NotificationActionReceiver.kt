package com.sameerasw.airsync.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sameerasw.airsync.data.local.DataStoreManager
import com.sameerasw.airsync.utils.NotificationUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Receives notification actions and updates sync state and notifications.
 */
class NotificationActionReceiver : BroadcastReceiver() {
    
    companion object {
        /** Action string for stopping notification sync. */
        const val ACTION_STOP_SYNC = "com.sameerasw.airsync.STOP_SYNC"
        private const val TAG = "NotificationActionReceiver"
    }
    
    /** Coroutine scope for background operations. */
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_STOP_SYNC -> {
                Log.d(TAG, "Stop sync action received")
                scope.launch {
                    try {
                        val dataStoreManager = DataStoreManager(context)
                        // Disable notification sync
                        dataStoreManager.setNotificationSyncEnabled(false)
                        
                        // Hide the persistent notification
                        NotificationUtil.hideConnectionStatusNotification(context)
                        
                        Log.d(TAG, "Notification sync disabled and notification hidden")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping sync: ${e.message}")
                    }
                }
            }
        }
    }
}
