package com.sameerasw.airsync.presentation.ui.components.cards

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sameerasw.airsync.data.local.DataStoreManager
import com.sameerasw.airsync.data.ble.BleGattServer
import kotlinx.coroutines.launch

import com.sameerasw.airsync.presentation.ui.components.RoundedCardContainer
import com.sameerasw.airsync.presentation.ui.components.cards.IconToggleItem
import com.sameerasw.airsync.R

@Composable
fun BleSyncCard(viewModel: com.sameerasw.airsync.presentation.viewmodel.AirSyncViewModel) {
    val context = LocalContext.current
    val dataStoreManager = remember { DataStoreManager.getInstance(context) }
    val scope = rememberCoroutineScope()
    
    val bleEnabled by dataStoreManager.getBleSyncEnabled().collectAsState(initial = false)
    val autoConnect by dataStoreManager.getBleAutoConnectEnabled().collectAsState(initial = true)
    
    val uiState by viewModel.uiState.collectAsState()
    val bleState = uiState.bleConnectionState
    
    val statusText = when (bleState) {
        BleGattServer.BleConnectionState.DISCONNECTED -> "For nearby connection"
        BleGattServer.BleConnectionState.ADVERTISING -> "Scanning"
        BleGattServer.BleConnectionState.CONNECTED -> "Authenticating"
        BleGattServer.BleConnectionState.AUTHENTICATED -> "Connected"
    }

        IconToggleItem(
            iconRes = R.drawable.rounded_bluetooth_24,
            title = "Bluetooth LE Sync",
            description = statusText,
            isChecked = bleEnabled,
            onCheckedChange = {
                scope.launch { dataStoreManager.setBleSyncEnabled(it) }
            }
        )
        
        IconToggleItem(
            iconRes = R.drawable.rounded_bluetooth_searching_24,
            title = "BLE Auto-connect",
            description = "Automatically switch to Bluetooth when Wi-Fi drops",
            isChecked = autoConnect,
            onCheckedChange = {
                scope.launch { dataStoreManager.setBleAutoConnectEnabled(it) }
            },
            enabled = bleEnabled
        )
}
