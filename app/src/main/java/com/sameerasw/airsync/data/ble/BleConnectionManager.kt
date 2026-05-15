package com.sameerasw.airsync.data.ble

import android.content.Context
import android.util.Log
import com.sameerasw.airsync.data.local.DataStoreManager
import com.sameerasw.airsync.utils.WebSocketUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest

class BleConnectionManager(private val context: Context) {
    companion object {
        private const val TAG = "BleConnectionManager"
    }
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val dataStoreManager = DataStoreManager(context)
    private var bleServer: BleGattServer? = null
    
    private var isBleEnabled = false

    @OptIn(ExperimentalCoroutinesApi::class)
    private val _serverFlow = kotlinx.coroutines.flow.MutableStateFlow<BleGattServer?>(null)
    
    @OptIn(ExperimentalCoroutinesApi::class)
    val connectionState = _serverFlow.flatMapLatest { server ->
        server?.connectionState ?: kotlinx.coroutines.flow.MutableStateFlow(BleGattServer.BleConnectionState.DISCONNECTED)
    }

    fun start() {
        if (bleServer == null) {
            bleServer = BleGattServer(context)
            _serverFlow.value = bleServer
            BleTransportBridge.initialize(bleServer!!)
        }

        scope.launch {
            combine(
                dataStoreManager.getBleSyncEnabled(),
                dataStoreManager.getBleAutoConnectEnabled(),
                WebSocketUtil.connectionState
            ) { enabled, auto, wsState ->
                Triple(enabled, auto, wsState)
            }.collectLatest { (enabled, _, _) ->
                isBleEnabled = enabled
                updateBleState()
            }
        }
    }

    private fun updateBleState() {
        if (isBleEnabled) {
            Log.d(TAG, "BLE enabled, starting GATT server")
            bleServer?.start()
        } else {
            Log.d(TAG, "BLE disabled, stopping server")
            bleServer?.stop()
        }
    }

    fun stop() {
        scope.cancel()
        bleServer?.stop()
    }

    val isAuthenticated: Boolean
        get() = bleServer?.isAuthenticated ?: false

    fun sendChunkedNotification(characteristicUuid: java.util.UUID, payload: String) {
        bleServer?.sendChunkedNotification(characteristicUuid, payload)
    }

    fun sendNotification(characteristicUuid: java.util.UUID, data: ByteArray) {
        bleServer?.sendNotification(characteristicUuid, data)
    }
}
