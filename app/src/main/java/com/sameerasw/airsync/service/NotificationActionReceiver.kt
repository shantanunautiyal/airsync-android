package com.sameerasw.airsync.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sameerasw.airsync.data.local.DataStoreManager
import com.sameerasw.airsync.utils.DeviceInfoUtil
import com.sameerasw.airsync.utils.NotificationUtil
import com.sameerasw.airsync.utils.WebSocketUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Receives notification actions and updates sync/connection state and notifications.
 */
class NotificationActionReceiver : BroadcastReceiver() {

    companion object {
        /** Action string for stopping notification sync. */
        const val ACTION_STOP_SYNC = "com.sameerasw.airsync.STOP_SYNC"
        // New dynamic actions for status notification
        const val ACTION_DISCONNECT = "com.sameerasw.airsync.DISCONNECT"
    const val ACTION_RECONNECT = "com.sameerasw.airsync.RECONNECT"
        const val ACTION_STOP_RECONNECT = "com.sameerasw.airsync.STOP_RECONNECT"
        // New: Continue Browsing dismiss action
        const val ACTION_CONTINUE_BROWSING_DISMISS = "com.sameerasw.airsync.CONTINUE_BROWSING_DISMISS"
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
                        val ds = DataStoreManager(context)
                        // Disable notification sync
                        ds.setNotificationSyncEnabled(false)
                        // Hide the persistent notification
                        NotificationUtil.hideConnectionStatusNotification(context)
                        Log.d(TAG, "Notification sync disabled and notification hidden")
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping sync: ${e.message}")
                    }
                }
            }
            ACTION_DISCONNECT -> {
                scope.launch {
                    try {
                        val ds = DataStoreManager(context)
                        // Mark manual disconnect BEFORE disconnecting so auto-reconnect won't trigger
                        ds.setUserManuallyDisconnected(true)
                        WebSocketUtil.disconnect()
                        // Clear Continue Browsing notifications on disconnect
                        NotificationUtil.clearContinueBrowsingNotifications(context)
                        // Update notification to Disconnected with appropriate action
                        updateStatusNotification(context, isConnecting = false)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error handling disconnect: ${e.message}")
                    }
                }
            }
            ACTION_RECONNECT -> {
                scope.launch {
                    try {
                        val ds = DataStoreManager(context)
                        ds.setUserManuallyDisconnected(false)

                        val ourIp = DeviceInfoUtil.getWifiIpAddress(context)
                        val lastDevice = ds.getLastConnectedDevice().first()
                        val all = ds.getAllNetworkDeviceConnections().first()
                        val target = if (ourIp != null && lastDevice != null) {
                            all.firstOrNull { it.deviceName == lastDevice.name && it.getClientIpForNetwork(ourIp) != null }
                        } else null

                        if (target != null && ourIp != null) {
                            val ip = target.getClientIpForNetwork(ourIp) ?: return@launch
                            val port = target.port.toIntOrNull() ?: 6996
                            // Show connecting state immediately
                            showStatus(
                                context,
                                deviceName = target.deviceName,
                                isConnected = false,
                                isConnecting = true,
                                isAutoReconnecting = false,
                                hasReconnectTarget = true
                            )
                            WebSocketUtil.connect(
                                context = context,
                                ipAddress = ip,
                                port = port,
                                symmetricKey = target.symmetricKey,
                                manualAttempt = true,
                                onHandshakeTimeout = {
                                    // Haptic feedback via vibrator if available
                                    try {
                                        val v = context.getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
                                        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                                            v.vibrate(android.os.VibrationEffect.createOneShot(150, android.os.VibrationEffect.DEFAULT_AMPLITUDE))
                                        } else {
                                            @Suppress("DEPRECATION") v.vibrate(150)
                                        }
                                    } catch (_: Exception) {}
                                    WebSocketUtil.disconnect(context)
                                    // Update status notification to show disconnected
                                    scope.launch { updateStatusNotification(context, isConnecting = false) }
                                },
                                onConnectionStatus = { connected ->
                                    scope.launch {
                                        if (connected) {
                                            // Update last connected timestamp
                                            ds.updateNetworkDeviceLastConnected(target.deviceName, System.currentTimeMillis())
                                        }
                                        showStatus(
                                            context,
                                            deviceName = target.deviceName,
                                            isConnected = connected,
                                            isConnecting = false,
                                            isAutoReconnecting = false,
                                            hasReconnectTarget = true
                                        )
                                    }
                                }
                            )
                        } else {
                            // No target; just update notification to disconnected with Open app
                            updateStatusNotification(context, isConnecting = false)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error handling reconnect: ${e.message}")
                        updateStatusNotification(context, isConnecting = false)
                    }
                }
            }
            ACTION_STOP_RECONNECT -> {
                scope.launch {
                    try {
                        // Cancel any auto-reconnect loops
                        WebSocketUtil.cancelAutoReconnect()
                        // Mark manual so it won't auto-retry until a non-manual disconnect happens
                        DataStoreManager(context).setUserManuallyDisconnected(true)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error stopping auto-reconnect: ${e.message}")
                    }
                }
            }
            ACTION_CONTINUE_BROWSING_DISMISS -> {
                val notifId = intent.getIntExtra("notif_id", -1)
                if (notifId != -1) {
                    try {
                        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                        nm.cancel(notifId)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to cancel continue-browsing notif: ${e.message}")
                    }
                }
            }
        }
    }

    private suspend fun updateStatusNotification(context: Context, isConnecting: Boolean) {
        val ds = DataStoreManager(context)
        val lastDevice = ds.getLastConnectedDevice().first()
        val deviceName = lastDevice?.name
        val isConnected = WebSocketUtil.isConnected()
        val ourIp = DeviceInfoUtil.getWifiIpAddress(context)
        val all = ds.getAllNetworkDeviceConnections().first()
        val hasReconnectTarget = if (ourIp != null && lastDevice != null) {
            all.firstOrNull { it.deviceName == lastDevice.name && it.getClientIpForNetwork(ourIp) != null } != null
        } else false
        val manual = ds.getUserManuallyDisconnected().first()
        // Show only while connecting; otherwise hide
        if (!isConnected && isConnecting) {
            showStatus(context, deviceName, isConnected, isConnecting, false, hasReconnectTarget)
        } else {
            NotificationUtil.hideConnectionStatusNotification(context)
        }
    }

    private fun showStatus(
        context: Context,
        deviceName: String?,
        isConnected: Boolean,
        isConnecting: Boolean,
        isAutoReconnecting: Boolean,
        hasReconnectTarget: Boolean
    ) {
        // Only show while actively connecting. Hide in all other states.
        if (!isConnected && isConnecting) {
            NotificationUtil.showConnectionStatusNotification(
                context = context,
                deviceName = deviceName,
                isConnected = isConnected,
                isConnecting = isConnecting,
                isAutoReconnecting = isAutoReconnecting,
                hasReconnectTarget = hasReconnectTarget
            )
        } else {
            NotificationUtil.hideConnectionStatusNotification(context)
        }
    }
}
