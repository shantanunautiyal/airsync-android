package com.sameerasw.airsync.presentation.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sameerasw.airsync.R
import com.sameerasw.airsync.presentation.ui.components.*
import com.sameerasw.airsync.presentation.viewmodel.AirSyncViewModel
import com.sameerasw.airsync.utils.ClipboardSyncManager
import com.sameerasw.airsync.utils.DeviceInfoUtil
import com.sameerasw.airsync.utils.JsonUtil
import com.sameerasw.airsync.utils.PermissionUtil
import com.sameerasw.airsync.utils.WebSocketUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject
import androidx.core.net.toUri

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirSyncMainScreen(
    modifier: Modifier = Modifier,
    initialIp: String? = null,
    initialPort: String? = null,
    showConnectionDialog: Boolean = false,
    pcName: String? = null,
    isPlus: Boolean = false,
    onNavigateToApps: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: AirSyncViewModel = viewModel { AirSyncViewModel.create(context) }
    val uiState by viewModel.uiState.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val scope = rememberCoroutineScope()

    // Track if we've already processed the QR code dialog to prevent re-showing
    var hasProcessedQrDialog by remember { mutableStateOf(false) }

    // State for About dialog
    var showAboutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.initializeState(context, initialIp, initialPort, showConnectionDialog && !hasProcessedQrDialog, pcName, isPlus)
    }

    // Mark QR dialog as processed when it's shown or when already connected
    LaunchedEffect(showConnectionDialog, uiState.isConnected) {
        if (showConnectionDialog) {
            if (uiState.isConnected) {
                // If already connected, don't show dialog
                hasProcessedQrDialog = true
            } else if (uiState.isDialogVisible) {
                // Dialog is being shown, mark as processed
                hasProcessedQrDialog = true
            }
        }
    }

    // Refresh permissions when returning from settings
    LaunchedEffect(uiState.showPermissionDialog) {
        if (!uiState.showPermissionDialog) {
            viewModel.refreshPermissions(context)
        }
    }

    // Start/stop clipboard sync based on connection status and settings
    LaunchedEffect(uiState.isConnected, uiState.isClipboardSyncEnabled) {
        if (uiState.isConnected && uiState.isClipboardSyncEnabled) {
            ClipboardSyncManager.startSync(context)
        } else {
            ClipboardSyncManager.stopSync(context)
        }
    }

    // Connection management functions
    fun connect() {
        viewModel.setConnectionStatus(isConnected = false, isConnecting = true)

        viewModel.setUserManuallyDisconnected(false)

        WebSocketUtil.connect(
            context = context,
            ipAddress = uiState.ipAddress,
            port = uiState.port.toIntOrNull() ?: 6996,
            onConnectionStatus = { connected ->
                scope.launch(Dispatchers.Main) {
                    viewModel.setConnectionStatus(isConnected = connected, isConnecting = false)
                    if (connected) {
                        viewModel.setResponse("Connected successfully!")
                        // Get plus status from current temporary device or use QR code value
                        val plusStatus = uiState.lastConnectedDevice?.isPlus ?: isPlus
                        viewModel.saveLastConnectedDevice(pcName, plusStatus)
                    } else {
                        viewModel.setResponse("Failed to connect")
                    }
                }
            },
            onMessage = { response ->
                scope.launch(Dispatchers.Main) {
                    viewModel.setResponse("Received: $response")
                    // Handle clipboard updates from desktop
                    try {
                        val json = JSONObject(response)
                        if (json.optString("type") == "clipboardUpdate") {
                            val data = json.optJSONObject("data")
                            val text = data?.optString("text")
                            if (!text.isNullOrEmpty()) {
                                ClipboardSyncManager.handleClipboardUpdate(context, text)
                            }
                        }
                    } catch (e: Exception) {
                        // Not a clipboard update, ignore
                    }
                }
            }
        )
    }

    fun disconnect() {
        WebSocketUtil.disconnect()
        viewModel.setConnectionStatus(isConnected = false, isConnecting = false)
        viewModel.setResponse("Disconnected")

        // Set manual disconnect flag to prevent auto-reconnection
        viewModel.setUserManuallyDisconnected(true)
    }

    fun sendMessage(message: String) {
        scope.launch {
            viewModel.setLoading(true)
            viewModel.setResponse("")

            if (!WebSocketUtil.isConnected()) {
                connect()
                // Wait a moment for connection, then send
                delay(1000)
            }

            val success = WebSocketUtil.sendMessage(message)
            if (success) {
                viewModel.setResponse("Message sent: $message")
            } else {
                viewModel.setResponse("Failed to send message")
            }
            viewModel.setLoading(false)
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            ClipboardSyncManager.stopSync(context)
        }
    }

        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("AirSync", style = MaterialTheme.typography.headlineLarge)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(
                    onClick = {
                        val airSyncPlusUrl = "https://github.com/sameerasw/airsync-android/issues/new"
                        val intent = Intent(Intent.ACTION_VIEW, airSyncPlusUrl.toUri())
                        context.startActivity(intent)
                    }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.outline_feedback_24),
                        contentDescription = "Feedback"
                    )
                }
                IconButton(onClick = { showAboutDialog = true }) {
                    Icon(
                        painter = painterResource(id = R.drawable.outline_info_24),
                        contentDescription = "Feedback"
                    )
                }
            }


            // Permission Status Card
            PermissionStatusCard(
                missingPermissions = uiState.missingPermissions,
                onGrantPermissions = { viewModel.setPermissionDialogVisible(true) },
                onRefreshPermissions = { viewModel.refreshPermissions(context) },
                onRequestNotificationPermission = onRequestNotificationPermission
            )

            // Connection Status Card - New main connection interface
            ConnectionStatusCard(
                isConnected = uiState.isConnected,
                isConnecting = uiState.isConnecting,
                onDisconnect = { disconnect() },
                connectedDevice = if (uiState.isConnected) uiState.lastConnectedDevice else null
            )

            // Last Connected Device Section - only show when not currently connected
            if (!uiState.isConnected) {
                uiState.lastConnectedDevice?.let { device ->
                    LastConnectedDeviceCard(
                        device = device,
                        onQuickConnect = {
                            viewModel.updateIpAddress(device.ipAddress)
                            viewModel.updatePort(device.port)
                            connect()
                        }
                    )
                }
            }

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

            // Clipboard Sync Card
            ClipboardSyncCard(
                isClipboardSyncEnabled = uiState.isClipboardSyncEnabled,
                onToggleClipboardSync = { enabled -> viewModel.setClipboardSyncEnabled(enabled) },
                isConnected = uiState.isConnected
            )


            DeviceInfoCard(
                deviceName = uiState.deviceNameInput,
                localIp = deviceInfo.localIp,
                onDeviceNameChange = { viewModel.updateDeviceName(it) }
            )

            // Developer Mode Card
            if (uiState.isDeveloperModeVisible) {
                DeveloperModeCard(
                    isDeveloperMode = uiState.isDeveloperMode,
                    onToggleDeveloperMode = { viewModel.setDeveloperMode(it) },
                    isLoading = uiState.isLoading,
                    onSendDeviceInfo = {
                        val message = JsonUtil.createDeviceInfoJson(
                            deviceInfo.name,
                            deviceInfo.localIp,
                            uiState.port.toIntOrNull() ?: 6996
                        )
                        sendMessage(message)
                    },
                    onSendNotification = {
                        val message = JsonUtil.createNotificationJson(
                            "121212",
                            "Test Message",
                            "This is a simulated notification from AirSync.",
                            "AirSync",
                            "com.sameerasw.airsync"
                        )
                        sendMessage(message)
                    },
                    onSendDeviceStatus = {
                        val message = DeviceInfoUtil.generateDeviceStatusJson(
                            context
                        )
                        sendMessage(message)
                    },
                    uiState = uiState
                )
            }



            // Manual Icon Sync Button
            OutlinedButton(
                onClick = {
                    viewModel.manualSyncAppIcons(context)
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState.isConnected && !uiState.isIconSyncLoading
            ) {
                if (uiState.isIconSyncLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (uiState.isIconSyncLoading) "Syncing Icons..." else "Sync App Icons")
            }

            // Icon Sync Message Display
            if (uiState.iconSyncMessage.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (uiState.iconSyncMessage.contains("Successfully"))
                            MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.iconSyncMessage,
                            modifier = Modifier.weight(1f),
                            color = if (uiState.iconSyncMessage.contains("Successfully"))
                                MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onErrorContainer
                        )
                        TextButton(
                            onClick = { viewModel.clearIconSyncMessage() }
                        ) {
                            Text("Dismiss")
                        }
                    }
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
                    isPlus = uiState.lastConnectedDevice?.isPlus ?: isPlus,
                    onDismiss = { viewModel.setDialogVisible(false) },
                    onConnect = {
                        viewModel.setDialogVisible(false)
                        connect()
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

        // About Dialog
        if (showAboutDialog) {
            AboutDialog(
                onDismissRequest = { showAboutDialog = false },
                onToggleDeveloperMode = { viewModel.toggleDeveloperModeVisibility() }
            )
        }
    }
}
