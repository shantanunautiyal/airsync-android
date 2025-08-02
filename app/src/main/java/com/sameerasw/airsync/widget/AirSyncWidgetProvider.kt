package com.sameerasw.airsync.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.util.Log
import androidx.core.content.ContextCompat
import com.sameerasw.airsync.MainActivity
import com.sameerasw.airsync.R
import com.sameerasw.airsync.data.local.DataStoreManager
import com.sameerasw.airsync.service.MediaNotificationListener
import com.sameerasw.airsync.utils.DeviceInfoUtil
import com.sameerasw.airsync.utils.WebSocketUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AirSyncWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "AirSyncWidget"
        const val ACTION_CONNECT = "com.sameerasw.airsync.widget.CONNECT"
        const val ACTION_DISCONNECT = "com.sameerasw.airsync.widget.DISCONNECT"
        const val ACTION_OPEN_APP = "com.sameerasw.airsync.widget.OPEN_APP"
        const val ACTION_MANUAL_SYNC = "com.sameerasw.airsync.widget.MANUAL_SYNC"

        private val widgetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetComponent = ComponentName(context, AirSyncWidgetProvider::class.java)
            val widgetIds = appWidgetManager.getAppWidgetIds(widgetComponent)

            val provider = AirSyncWidgetProvider()
            provider.onUpdate(context, appWidgetManager, widgetIds)
        }
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        Log.d(TAG, "Updating ${appWidgetIds.size} widgets")

        widgetScope.launch {
            val dataStoreManager = DataStoreManager(context)
            val isConnected = WebSocketUtil.isConnected()
            val deviceName = dataStoreManager.getDeviceName().first()
            val connectedDevice = dataStoreManager.getLastConnectedDevice().first()
            val lastSyncTime = dataStoreManager.getLastSyncTime().first()

            appWidgetIds.forEach { widgetId ->
                updateWidget(
                    context,
                    appWidgetManager,
                    widgetId,
                    isConnected,
                    deviceName,
                    connectedDevice,
                    lastSyncTime
                )
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_CONNECT -> {
                Log.d(TAG, "Connect action received")
                handleConnectAction(context)
            }
            ACTION_DISCONNECT -> {
                Log.d(TAG, "Disconnect action received")
                handleDisconnectAction(context)
            }
            ACTION_OPEN_APP -> {
                Log.d(TAG, "Open app action received")
                handleOpenAppAction(context)
            }
            ACTION_MANUAL_SYNC -> {
                Log.d(TAG, "Manual sync action received")
                handleManualSyncAction(context)
            }
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                // Trigger update when requested
                updateAllWidgets(context)
            }
        }
    }

    private fun handleConnectAction(context: Context) {
        widgetScope.launch {
            try {
                val dataStoreManager = DataStoreManager(context)
                val lastDevice = dataStoreManager.getLastConnectedDevice().first()

                if (lastDevice != null) {
                    WebSocketUtil.connect(
                        context = context,
                        ipAddress = lastDevice.ipAddress,
                        port = lastDevice.port.toIntOrNull() ?: 6996,
                        onConnectionStatus = { connected ->
                            if (connected) {
                                Log.d(TAG, "Widget reconnected successfully")
                            } else {
                                Log.e(TAG, "Widget reconnection failed")
                            }
                            updateAllWidgets(context)
                        }
                    )
                } else {
                    // No last device, open app
                    handleOpenAppAction(context)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in connect action: ${e.message}")
            }
        }
    }

    private fun handleDisconnectAction(context: Context) {
        WebSocketUtil.disconnect()

        CoroutineScope(Dispatchers.IO).launch {
            val dataStoreManager = DataStoreManager(context)
            dataStoreManager.setUserManuallyDisconnected(true)
        }

        updateAllWidgets(context)
    }

    private fun handleOpenAppAction(context: Context) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        context.startActivity(intent)
    }

    private fun handleManualSyncAction(context: Context) {
        widgetScope.launch {
            try {
                if (WebSocketUtil.isConnected()) {
                    // Trigger manual icon sync
                    val dataStoreManager = DataStoreManager(context)
                    com.sameerasw.airsync.utils.SyncManager.manualSyncAppIcons(context) { success, message ->
                        Log.d(TAG, "Manual sync result: $success - $message")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error in manual sync action: ${e.message}")
            }
        }
    }

    private suspend fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int,
        isConnected: Boolean,
        deviceName: String,
        connectedDevice: com.sameerasw.airsync.domain.model.ConnectedDevice?,
        lastSyncTime: Long?
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_airsync)

        try {
            // Device name
            views.setTextViewText(
                R.id.widget_device_name,
                if (deviceName.isNotEmpty()) deviceName else "Android Device"
            )

            // Connection status
            views.setTextViewText(
                R.id.widget_connection_status,
                if (isConnected) "🟢" else "🔴"
            )

            // Battery info (real data)
            val batteryInfo = DeviceInfoUtil.getBatteryInfo(context)
            val batteryText = buildString {
                append("🔋 ${batteryInfo.level}%")
                if (batteryInfo.isCharging) append(" ⚡")
            }
            views.setTextViewText(R.id.widget_battery_info, batteryText)

            // Volume info (real data)
            val audioInfo = DeviceInfoUtil.getAudioInfo(context)
            val volumeText = if (audioInfo.isMuted) {
                "🔇 Muted"
            } else {
                "🔊 ${audioInfo.volume}%"
            }
            views.setTextViewText(R.id.widget_volume_info, volumeText)

            // Media info (real data from MediaNotificationListener)
            val mediaInfo = MediaNotificationListener.getMediaInfo(context)
            if (mediaInfo.isPlaying && (mediaInfo.title.isNotEmpty() || mediaInfo.artist.isNotEmpty())) {
                // Show real media data
                views.setViewVisibility(R.id.widget_media_section, android.view.View.VISIBLE)
                views.setTextViewText(R.id.widget_media_status, "🎵 Playing")
                views.setTextViewText(
                    R.id.widget_media_title,
                    if (mediaInfo.title.isNotEmpty()) mediaInfo.title else "Unknown Track"
                )
                views.setTextViewText(
                    R.id.widget_media_artist,
                    if (mediaInfo.artist.isNotEmpty()) mediaInfo.artist else "Unknown Artist"
                )
            } else if (!mediaInfo.isPlaying && (mediaInfo.title.isNotEmpty() || mediaInfo.artist.isNotEmpty())) {
                // Show paused media
                views.setViewVisibility(R.id.widget_media_section, android.view.View.VISIBLE)
                views.setTextViewText(R.id.widget_media_status, "⏸️ Paused")
                views.setTextViewText(
                    R.id.widget_media_title,
                    if (mediaInfo.title.isNotEmpty()) mediaInfo.title else "Unknown Track"
                )
                views.setTextViewText(
                    R.id.widget_media_artist,
                    if (mediaInfo.artist.isNotEmpty()) mediaInfo.artist else "Unknown Artist"
                )
            } else {
                // Show dummy data when no media is playing (placeholder for future data)
                views.setViewVisibility(R.id.widget_media_section, android.view.View.VISIBLE)
                views.setTextViewText(R.id.widget_media_status, "🎵 Ready")
                views.setTextViewText(R.id.widget_media_title, "No media playing")
                views.setTextViewText(R.id.widget_media_artist, "Ready to sync")
            }

            // Network info
            val networkText = if (connectedDevice != null && isConnected) {
                "📡 ${connectedDevice.ipAddress}:${connectedDevice.port}"
            } else if (connectedDevice != null) {
                "📡 ${connectedDevice.ipAddress}:${connectedDevice.port} (disconnected)"
            } else {
                "📡 Not configured"
            }
            views.setTextViewText(R.id.widget_network_info, networkText)

            // Last sync info
            val syncText = if (lastSyncTime != null) {
                "Last sync: ${formatLastSync(lastSyncTime)}"
            } else {
                "Never synced"
            }
            views.setTextViewText(R.id.widget_last_sync, syncText)

            // Set click listener to open app
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = android.app.PendingIntent.getActivity(
                context, 0, openAppIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, openAppPendingIntent)

            appWidgetManager.updateAppWidget(widgetId, views)
            Log.d(TAG, "Widget $widgetId updated successfully with device data")

        } catch (e: Exception) {
            Log.e(TAG, "Error updating widget $widgetId: ${e.message}")
            setupErrorState(context, views)
            appWidgetManager.updateAppWidget(widgetId, views)
        }
    }

    private fun setupErrorState(context: Context, views: RemoteViews) {
        views.setTextViewText(R.id.widget_device_name, "AirSync")
        views.setTextViewText(R.id.widget_connection_status, "❌")
        views.setTextViewText(R.id.widget_battery_info, "Error")
        views.setTextViewText(R.id.widget_volume_info, "Error")
        views.setViewVisibility(R.id.widget_media_section, android.view.View.VISIBLE)
        views.setTextViewText(R.id.widget_media_status, "❌ Error")
        views.setTextViewText(R.id.widget_media_title, "Widget error")
        views.setTextViewText(R.id.widget_media_artist, "Tap to open app")
        views.setTextViewText(R.id.widget_network_info, "❌ Error loading data")
        views.setTextViewText(R.id.widget_last_sync, "Error")
    }

    private fun formatLastSync(timestamp: Long): String {
        val now = System.currentTimeMillis()
        val diffMinutes = (now - timestamp) / (1000 * 60)

        return when {
            diffMinutes < 1 -> "Just now"
            diffMinutes < 60 -> "${diffMinutes}m ago"
            diffMinutes < 1440 -> "${diffMinutes / 60}h ago"
            else -> "${diffMinutes / 1440}d ago"
        }
    }
}
