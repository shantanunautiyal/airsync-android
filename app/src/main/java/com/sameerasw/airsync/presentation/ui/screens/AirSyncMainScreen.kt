package com.sameerasw.airsync.presentation.ui.screens

import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sameerasw.airsync.presentation.ui.components.*
import com.sameerasw.airsync.presentation.viewmodel.AirSyncViewModel
import com.sameerasw.airsync.utils.DeviceInfoUtil
import com.sameerasw.airsync.utils.JsonUtil
import com.sameerasw.airsync.utils.PermissionUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirSyncMainScreen(
    modifier: Modifier = Modifier,
    initialIp: String? = null,
    initialPort: String? = null,
    showConnectionDialog: Boolean = false,
    pcName: String? = null,
    onNavigateToApps: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: AirSyncViewModel = viewModel { AirSyncViewModel.create(context) }
    val uiState by viewModel.uiState.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.initializeState(context, initialIp, initialPort, showConnectionDialog, pcName)
    }

    // Refresh permissions when returning from settings
    LaunchedEffect(uiState.showPermissionDialog) {
        if (!uiState.showPermissionDialog) {
            viewModel.refreshPermissions(context)
        }
    }

    fun send(message: String) {
        scope.launch {
            viewModel.setLoading(true)
            viewModel.setResponse("")
            testSocket(context, uiState.ipAddress, uiState.port.toIntOrNull() ?: 6996, message) { result ->
                viewModel.setResponse(result)
                viewModel.setLoading(false)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("AirSync", style = MaterialTheme.typography.headlineMedium)

        // Permission Status Card
        PermissionStatusCard(
            missingPermissions = uiState.missingPermissions,
            onGrantPermissions = { viewModel.setPermissionDialogVisible(true) },
            onRefreshPermissions = { viewModel.refreshPermissions(context) }
        )

        // Notification Sync Settings Card
        NotificationSyncCard(
            isNotificationEnabled = uiState.isNotificationEnabled,
            isNotificationSyncEnabled = uiState.isNotificationSyncEnabled,
            ipAddress = uiState.ipAddress,
            port = uiState.port,
            onToggleSync = { enabled -> viewModel.setNotificationSyncEnabled(enabled) },
            onGrantPermissions = { viewModel.setPermissionDialogVisible(true) },
            onManageApps = onNavigateToApps
        )

        // Device Info Section
        val batteryInfo by rememberUpdatedState(DeviceInfoUtil.getBatteryInfo(context))
        val audioInfo by rememberUpdatedState(DeviceInfoUtil.getAudioInfo(context))

        DeviceInfoCard(
            deviceName = uiState.deviceNameInput,
            localIp = deviceInfo.localIp,
            batteryLevel = batteryInfo.level,
            isCharging = batteryInfo.isCharging,
            volume = audioInfo.volume,
            isMuted = audioInfo.isMuted,
            mediaTitle = audioInfo.title,
            mediaArtist = audioInfo.artist,
            isNotificationEnabled = uiState.isNotificationEnabled,
            onDeviceNameChange = { viewModel.updateDeviceName(it) },
            onRefreshMedia = {
                viewModel.refreshPermissions(context)
                viewModel.refreshDeviceInfo(context)
            }
        )

        // Last Connected Device Section
        uiState.lastConnectedDevice?.let { device ->
            LastConnectedDeviceCard(
                device = device,
                onQuickConnect = {
                    viewModel.updateIpAddress(device.ipAddress)
                    viewModel.updatePort(device.port)
                    val message = JsonUtil.createDeviceInfoJson(
                        deviceInfo.name,
                        deviceInfo.localIp,
                        device.port.toIntOrNull() ?: 6996
                    )
                    send(message)
                    viewModel.saveLastConnectedDevice(device.name)
                }
            )
        }

        // Connection Settings
        ConnectionSettingsCard(
            ipAddress = uiState.ipAddress,
            port = uiState.port,
            onIpAddressChange = { viewModel.updateIpAddress(it) },
            onPortChange = { viewModel.updatePort(it) }
        )

        HorizontalDivider()

        // Action Buttons
        ActionButtonsCard(
            isLoading = uiState.isLoading,
            onSendDeviceInfo = {
                val message = JsonUtil.createDeviceInfoJson(
                    deviceInfo.name,
                    deviceInfo.localIp,
                    uiState.port.toIntOrNull() ?: 6996
                )
                send(message)
            },
            onSendNotification = {
                val message = JsonUtil.createNotificationJson(
                    "Test Message",
                    "This is a simulated notification.",
                    "Telegram"
                )
                send(message)
            },
            onSendDeviceStatus = {
                val message = DeviceInfoUtil.generateDeviceStatusJson(
                    context,
                    uiState.port.toIntOrNull() ?: 6996
                )
                send(message)
            }
        )

        HorizontalDivider()

        // Custom Message
        CustomMessageCard(
            customMessage = uiState.customMessage,
            isLoading = uiState.isLoading,
            isEnabled = uiState.ipAddress.isNotBlank() && uiState.port.isNotBlank() && uiState.customMessage.isNotBlank(),
            onCustomMessageChange = { viewModel.updateCustomMessage(it) },
            onSendCustomMessage = { send(uiState.customMessage) }
        )

        // Response Display
        if (uiState.response.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(uiState.response, modifier = Modifier.padding(16.dp))
            }
        }

        // Dialogs
        if (uiState.isDialogVisible) {
            ConnectionDialog(
                deviceName = deviceInfo.name,
                localIp = deviceInfo.localIp,
                desktopIp = uiState.ipAddress,
                port = uiState.port,
                pcName = pcName ?: uiState.lastConnectedDevice?.name,
                onDismiss = { viewModel.setDialogVisible(false) },
                onConnect = {
                    viewModel.setDialogVisible(false)
                    // Save the connected device info
                    viewModel.saveLastConnectedDevice(pcName ?: uiState.lastConnectedDevice?.name)
                    // Send device info automatically
                    val message = JsonUtil.createDeviceInfoJson(
                        deviceInfo.name,
                        deviceInfo.localIp,
                        uiState.port.toIntOrNull() ?: 6996
                    )
                    send(message)
                }
            )
        }

        if (uiState.showPermissionDialog) {
            PermissionDialog(
                missingPermissions = uiState.missingPermissions,
                onDismiss = { viewModel.setPermissionDialogVisible(false) },
                onGrantPermissions = {
                    PermissionUtil.openNotificationListenerSettings(context)
                    viewModel.setPermissionDialogVisible(false)
                }
            )
        }
    }
}

private suspend fun testSocket(
    context: Context,
    ipAddress: String,
    port: Int,
    message: String,
    onResult: (String) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            val socket = Socket(ipAddress, port)
            val output = PrintWriter(socket.getOutputStream(), true)
            val input = BufferedReader(InputStreamReader(socket.getInputStream()))

            output.println(message)
            val response = input.readLine()
            Log.d("TCP", "Received: $response")

            socket.close()

            // Update last sync time on successful connection
            val currentTime = System.currentTimeMillis()
            val dataStoreManager = com.sameerasw.airsync.data.local.DataStoreManager(context)
            dataStoreManager.updateLastSyncTime(currentTime)

            // Update persistent notification
            val connectedDevice = dataStoreManager.getLastConnectedDevice().first()
            com.sameerasw.airsync.utils.NotificationUtil.updateLastSyncTime(
                context = context,
                connectedDevice = connectedDevice,
                lastSyncTime = currentTime,
                isConnected = true
            )

            withContext(Dispatchers.Main) {
                onResult("Success! Received: $response")
            }
        } catch (e: Exception) {
            Log.e("TCP", "Socket error: ${e.message}")

            // Update persistent notification to show connection failed
            try {
                val dataStoreManager = com.sameerasw.airsync.data.local.DataStoreManager(context)
                val connectedDevice = dataStoreManager.getLastConnectedDevice().first()
                val lastSyncTime = dataStoreManager.getLastSyncTime().first()
                com.sameerasw.airsync.utils.NotificationUtil.showConnectionStatusNotification(
                    context = context,
                    connectedDevice = connectedDevice,
                    lastSyncTime = lastSyncTime,
                    isConnected = false
                )
            } catch (notificationError: Exception) {
                Log.e("TCP", "Failed to update notification: ${notificationError.message}")
            }

            withContext(Dispatchers.Main) {
                onResult("Error: ${e.message}")
            }
        }
    }
}
