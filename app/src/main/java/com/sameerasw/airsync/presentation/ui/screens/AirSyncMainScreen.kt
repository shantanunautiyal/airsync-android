package com.sameerasw.airsync.presentation.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
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
import com.sameerasw.airsync.ui.theme.ExtraCornerRadius
import com.sameerasw.airsync.ui.theme.minCornerRadius
import com.sameerasw.airsync.domain.model.UpdateStatus

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

    val versionName = try {
        context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName
    } catch (e: Exception) {
        "2.0.0"
    }
    val viewModel: AirSyncViewModel = viewModel { AirSyncViewModel.create(context) }
    val uiState by viewModel.uiState.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val updateInfo by viewModel.updateInfo.collectAsState()
    val updateStatus by viewModel.updateStatus.collectAsState()
    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val showUpdateDialog by viewModel.showUpdateDialog.collectAsState()
    val scope = rememberCoroutineScope()

    // Track if we've already processed the QR code dialog to prevent re-showing
    var hasProcessedQrDialog by remember { mutableStateOf(false) }

    // State for About dialog
    var showAboutDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.initializeState(context, initialIp, initialPort, showConnectionDialog && !hasProcessedQrDialog, pcName, isPlus)

        // Check for updates on app start (silently)
        viewModel.checkForUpdates(context, showDialogOnUpdate = false)

        // Start network monitoring for dynamic Wi-Fi changes
        viewModel.startNetworkMonitoring(context)
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

    fun launchScanner(context: Context) {
        val lensIntent = Intent("com.google.vr.apps.ornament.app.lens.LensLauncherActivity.MAIN")
        lensIntent.setPackage("com.google.ar.lens")

        try {
            context.startActivity(lensIntent)
        } catch (e: ActivityNotFoundException) {
            // Fallback to default camera app
            val cameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            try {
                context.startActivity(cameraIntent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(context, "No camera app found", Toast.LENGTH_SHORT).show()
            }
        }
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
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(all = 0.dp)
            ) {
                Image(
                    painter = painterResource(id = R.drawable.ic_laptop_24),
                    contentDescription = "AirSync Logo",
                    modifier = Modifier.size(32.dp),
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                )

                Text("AirSync", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.padding(start = 10.dp), color = MaterialTheme.colorScheme.primary)
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
                IconButton(
                    onClick = {
                        // Check for updates and show dialog if available
                        viewModel.checkForUpdates(context, showDialogOnUpdate = true)
                    }
                ) {
                    // Show update indicator if available
                    Box {
                        Icon(
                            painter = painterResource(id = R.drawable.outline_downloading_24),
                            contentDescription = "Check for Updates"
                        )
                        if (updateStatus == UpdateStatus.UPDATE_AVAILABLE) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        MaterialTheme.colorScheme.error,
                                        shape = RoundedCornerShape(50)
                                    )
                                    .align(Alignment.TopEnd)
                            )
                        }
                    }
                }
                IconButton(onClick = { showAboutDialog = true }) {
                    Icon(
                        painter = painterResource(id = R.drawable.outline_info_24),
                        contentDescription = "About"
                    )
                }

            }

            if (uiState.missingPermissions.isEmpty()) {
                QrScannerRow(
                    onLaunchScanner = {
                        launchScanner(context)
                    }
                )
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
                connectedDevice = uiState.lastConnectedDevice,
                lastConnected = uiState.lastConnectedDevice != null
            )

            // Last Connected Device Section - only show when not currently connected
            if (!uiState.isConnected) {
                uiState.lastConnectedDevice?.let { device ->
                    LastConnectedDeviceCard(
                        device = device,
                        onQuickConnect = {
                            // Check if we can use network-aware connection first
                            val networkAwareDevice = viewModel.getNetworkAwareLastConnectedDevice()
                            if (networkAwareDevice != null) {
                                // Use network-aware device IP for current network
                                viewModel.updateIpAddress(networkAwareDevice.ipAddress)
                                viewModel.updatePort(networkAwareDevice.port)
                                connect()
                            } else {
                                // Fallback to legacy stored device
                                viewModel.updateIpAddress(device.ipAddress)
                                viewModel.updatePort(device.port)
                                connect()
                            }
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
                            uiState.port.toIntOrNull() ?: 6996,
                            versionName ?: "2.0.0"
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
                modifier = Modifier.fillMaxWidth().padding(top=  if (uiState.isDeveloperModeVisible)0.dp else 20.dp),
                shape = RoundedCornerShape(
                    topStart = if (uiState.isDeveloperModeVisible) minCornerRadius else ExtraCornerRadius,
                    topEnd = if (uiState.isDeveloperModeVisible) minCornerRadius else ExtraCornerRadius,
                    bottomStart = ExtraCornerRadius,
                    bottomEnd = ExtraCornerRadius
                ),
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
                    shape = RoundedCornerShape(ExtraCornerRadius),
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

        // Update Dialog
        if (showUpdateDialog) {
            UpdateDialog(
                updateInfo = updateInfo,
                updateStatus = updateStatus,
                downloadProgress = downloadProgress,
                onDismiss = { viewModel.dismissUpdateDialog() },
                onDownload = { viewModel.downloadUpdate(context) }
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
