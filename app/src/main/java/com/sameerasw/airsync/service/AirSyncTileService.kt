package com.sameerasw.airsync.service

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.util.Log
import com.sameerasw.airsync.MainActivity
import com.sameerasw.airsync.R
import com.sameerasw.airsync.data.local.DataStoreManager
import com.sameerasw.airsync.utils.WebSocketUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AirSyncTileService : TileService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var dataStoreManager: DataStoreManager

    companion object {
        private const val TAG = "AirSyncTileService"
    }

    override fun onCreate() {
        super.onCreate()
        dataStoreManager = DataStoreManager(this)

        // Register for connection status updates
        WebSocketUtil.registerConnectionStatusListener { isConnected ->
            serviceScope.launch {
                updateTileState()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        WebSocketUtil.unregisterConnectionStatusListener { }
    }

    override fun onStartListening() {
        super.onStartListening()
        serviceScope.launch {
            updateTileState()
        }
    }

    override fun onClick() {
        super.onClick()

        serviceScope.launch {
            val isConnected = WebSocketUtil.isConnected()

            if (isConnected) {
                WebSocketUtil.disconnect()
                dataStoreManager.setUserManuallyDisconnected(true)
                updateTileState()
            } else {
                dataStoreManager.setUserManuallyDisconnected(false)
                connectToLastDevice()
            }
        }
    }

    private suspend fun connectToLastDevice() {
        try {
            val lastDevice = dataStoreManager.getLastConnectedDevice().first()

            if (lastDevice != null) {
                // Update tile to show connecting state
                qsTile?.apply {
                    state = Tile.STATE_UNAVAILABLE
                    label = "Connecting..."
                    subtitle = lastDevice.name
                    updateTile()
                }

                WebSocketUtil.connect(
                    context = this@AirSyncTileService,
                    ipAddress = lastDevice.ipAddress,
                    port = lastDevice.port.toIntOrNull() ?: 6996,
                    symmetricKey = lastDevice.symmetricKey,
                    onConnectionStatus = { connected ->
                        serviceScope.launch {
                            updateTileState()
                        }
                    }
                )
            } else {
                // No last device, open the app
                val intent = Intent(this, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val pendingIntent = PendingIntent.getActivity(
                    this@AirSyncTileService,
                    0,
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                startActivityAndCollapse(pendingIntent)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error connecting to last device", e)
            updateTileState()
        }
    }

    private suspend fun updateTileState() {
        try {
            val isConnected = WebSocketUtil.isConnected()
            val lastDevice = dataStoreManager.getLastConnectedDevice().first()

            qsTile?.apply {
                icon = Icon.createWithResource(this@AirSyncTileService, R.drawable.ic_laptop_24)

                if (isConnected && lastDevice != null) {
                    // Connected state
                    state = Tile.STATE_ACTIVE
                    label = lastDevice.name
                    subtitle = "Connected"
                } else if (lastDevice != null) {
                    // Disconnected but has last device
                    state = Tile.STATE_INACTIVE
                    label = "Reconnect"
                    subtitle = lastDevice.name
                } else {
                    // No last device
                    state = Tile.STATE_INACTIVE
                    label = "AirSync"
                    subtitle = "Tap to setup"
                }

                updateTile()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error updating tile state", e)

            // Fallback state
            qsTile?.apply {
                state = Tile.STATE_INACTIVE
                label = "AirSync"
                subtitle = "Error"
                icon = Icon.createWithResource(this@AirSyncTileService, R.drawable.ic_laptop_24)
                updateTile()
            }
        }
    }
}
