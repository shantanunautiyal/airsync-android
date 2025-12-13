package com.sameerasw.airsync.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.sameerasw.airsync.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel

/**
 * Optional foreground service for call monitoring.
 * 
 * NOTE: The actual call detection is now handled by CallReceiver (BroadcastReceiver)
 * which listens for PHONE_STATE and NEW_OUTGOING_CALL broadcasts system-wide.
 * This service is kept for potential future use (e.g., maintaining notification, 
 * managing background task) but is not essential for call detection.
 */
class CallMonitorService : Service() {

    private val scope = CoroutineScope(Dispatchers.Main + Job())

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "CallMonitorService created")
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "CallMonitorService started with action: ${intent?.action}")

        val action = intent?.action
        when (action) {
            ACTION_START_MONITORING -> startMonitoring()
            ACTION_STOP_MONITORING -> stopMonitoring()
            else -> startMonitoring()
        }

        return START_STICKY
    }

    /**
     * Start monitoring - shows foreground notification
     * Actual call detection is handled by CallReceiver (BroadcastReceiver)
     */
    private fun startMonitoring() {
        Log.d(TAG, "Starting call monitoring foreground service")
        startForeground(NOTIFICATION_ID, buildNotification())
    }

    /**
     * Stop monitoring
     */
    private fun stopMonitoring() {
        Log.d(TAG, "Stopping call monitoring foreground service")
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    /**
     * Create notification channel for Android 8+
     */
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Call Monitoring",
                NotificationManager.IMPORTANCE_LOW
            )
            channel.description = "AirSync is monitoring your calls"
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    /**
     * Build foreground notification
     */
    private fun buildNotification(): android.app.Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AirSync")
            .setContentText("Call monitoring active")
            .setSmallIcon(R.drawable.ic_laptop_24)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        Log.d(TAG, "CallMonitorService destroyed")
        scope.coroutineContext.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "CallMonitorService"
        private const val CHANNEL_ID = "airsync_call_monitoring"
        private const val NOTIFICATION_ID = 4001

        const val ACTION_START_MONITORING = "com.sameerasw.airsync.CALL_START_MONITORING"
        const val ACTION_STOP_MONITORING = "com.sameerasw.airsync.CALL_STOP_MONITORING"

        /**
         * Start call monitoring service
         */
        fun start(context: Context) {
            val intent = Intent(context, CallMonitorService::class.java).apply {
                action = ACTION_START_MONITORING
            }
            try {
                context.startForegroundService(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting foreground service", e)
                context.startService(intent)
            }
        }

        /**
         * Stop call monitoring service
         */
        fun stop(context: Context) {
            val intent = Intent(context, CallMonitorService::class.java).apply {
                action = ACTION_STOP_MONITORING
            }
            context.startService(intent)
        }
    }
}

