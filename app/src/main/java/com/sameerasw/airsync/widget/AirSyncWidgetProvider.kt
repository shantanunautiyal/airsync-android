package com.sameerasw.airsync.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.util.Log
import com.sameerasw.airsync.MainActivity
import com.sameerasw.airsync.R
import com.sameerasw.airsync.data.local.DataStoreManager
import com.sameerasw.airsync.utils.DevicePreviewResolver
import com.sameerasw.airsync.utils.WebSocketUtil
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first

class AirSyncWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val TAG = "AirSyncWidget"
        const val ACTION_DISCONNECT = "com.sameerasw.airsync.widget.ACTION_DISCONNECT"
        const val ACTION_RECONNECT = "com.sameerasw.airsync.widget.ACTION_RECONNECT"

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

        appWidgetIds.forEach { widgetId ->
            updateWidget(context, appWidgetManager, widgetId)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            AppWidgetManager.ACTION_APPWIDGET_UPDATE -> {
                updateAllWidgets(context)
            }
            ACTION_DISCONNECT -> {
                try {
                    // Mark manual disconnect and disconnect
                    runBlocking {
                        DataStoreManager(context).setUserManuallyDisconnected(true)
                    }
                    WebSocketUtil.disconnect(context)
                } catch (e: Exception) {
                    Log.e(TAG, "Widget disconnect failed: ${e.message}")
                }
                updateAllWidgets(context)
            }
            ACTION_RECONNECT -> {
                try {
                    val ds = DataStoreManager(context)
                    runBlocking { ds.setUserManuallyDisconnected(false) }
                    // Try to connect using last connected device
                    val last = runBlocking { ds.getLastConnectedDevice().first() }
                    if (last != null) {
                        WebSocketUtil.connect(
                            context = context,
                            ipAddress = last.ipAddress,
                            port = last.port.toIntOrNull() ?: 6996,
                            symmetricKey = last.symmetricKey,
                            manualAttempt = true,
                            onConnectionStatus = { updateAllWidgets(context) },
                            onHandshakeTimeout = { updateAllWidgets(context) }
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Widget reconnect failed: ${e.message}")
                }
                updateAllWidgets(context)
            }
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        widgetId: Int
    ) {
        val views = RemoteViews(context.packageName, R.layout.widget_airsync)

        try {
            val ds = DataStoreManager(context)
            val isConnected = WebSocketUtil.isConnected()
            val isConnecting = WebSocketUtil.isConnecting()
            val lastDevice = runBlocking { ds.getLastConnectedDevice().first() }

            // Device image (large preview) and name
            val previewRes = DevicePreviewResolver.getPreviewRes(lastDevice)
            views.setImageViewResource(R.id.widget_device_image, previewRes)
            views.setTextViewText(R.id.widget_device_name, lastDevice?.name ?: "AirSync")

            // Status
            val statusText = when {
                isConnecting -> "Connecting…"
                isConnected -> "Connected"
                lastDevice != null -> "Disconnected"
                else -> "Not setup"
            }
            views.setTextViewText(R.id.widget_status, statusText)


            // Open app when tapping header area
            val openAppIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val openAppPendingIntent = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_container, openAppPendingIntent)

            // Buttons visibility and actions
            if (isConnected) {
                // Show Disconnect
                views.setViewVisibility(R.id.widget_btn_disconnect, 0)
                views.setViewVisibility(R.id.widget_btn_reconnect, 8)

                val disconnectIntent = Intent(context, AirSyncWidgetProvider::class.java).apply {
                    action = ACTION_DISCONNECT
                }
                val disconnectPI = PendingIntent.getBroadcast(
                    context, 1, disconnectIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_btn_disconnect, disconnectPI)
            } else {
                // Show Reconnect if we have a device
                views.setViewVisibility(R.id.widget_btn_disconnect, 8)
                val showReconnect = (lastDevice != null)
                views.setViewVisibility(R.id.widget_btn_reconnect, if (showReconnect) 0 else 8)
                if (showReconnect) {
                    val reconnectIntent = Intent(context, AirSyncWidgetProvider::class.java).apply {
                        action = ACTION_RECONNECT
                    }
                    val reconnectPI = PendingIntent.getBroadcast(
                        context, 2, reconnectIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    views.setOnClickPendingIntent(R.id.widget_btn_reconnect, reconnectPI)
                }
            }

            appWidgetManager.updateAppWidget(widgetId, views)
            Log.d(TAG, "Widget $widgetId updated")

        } catch (e: Exception) {
            Log.e(TAG, "Error updating widget $widgetId: ${e.message}")
        }
    }

    // (no additional companion objects)
}
