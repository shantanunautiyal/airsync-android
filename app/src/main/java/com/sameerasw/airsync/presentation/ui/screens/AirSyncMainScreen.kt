package com.sameerasw.airsync.presentation.ui.screens

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sameerasw.airsync.R
import com.sameerasw.airsync.presentation.viewmodel.AirSyncViewModel
import com.sameerasw.airsync.utils.ClipboardSyncManager
import com.sameerasw.airsync.utils.DeviceInfoUtil
import com.sameerasw.airsync.utils.JsonUtil
import com.sameerasw.airsync.utils.TestNotificationUtil
import com.sameerasw.airsync.utils.WebSocketUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.sameerasw.airsync.ui.theme.ExtraCornerRadius
import com.sameerasw.airsync.ui.theme.minCornerRadius
import com.sameerasw.airsync.presentation.ui.components.cards.ClipboardSyncCard
import com.sameerasw.airsync.presentation.ui.components.cards.DeveloperModeCard
import com.sameerasw.airsync.presentation.ui.components.cards.ConnectionStatusCard
import com.sameerasw.airsync.presentation.ui.components.cards.PermissionStatusCard
import com.sameerasw.airsync.presentation.ui.components.cards.LastConnectedDeviceCard
import com.sameerasw.airsync.presentation.ui.components.cards.ManualConnectionCard
import com.sameerasw.airsync.presentation.ui.components.cards.QrScannerRow
import com.sameerasw.airsync.presentation.ui.components.cards.NotificationSyncCard
import com.sameerasw.airsync.presentation.ui.components.cards.DeviceInfoCard
import com.sameerasw.airsync.presentation.ui.components.dialogs.AboutDialog
import com.sameerasw.airsync.presentation.ui.components.dialogs.ConnectionDialog
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AirSyncMainScreen(
    modifier: Modifier = Modifier,
    initialIp: String? = null,
    initialPort: String? = null,
    showConnectionDialog: Boolean = false,
    pcName: String? = null,
    isPlus: Boolean = false,
    symmetricKey: String? = null,
    onNavigateToApps: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    showAboutDialog: Boolean = false,
    onDismissAbout: () -> Unit = {}
) {
    val context = LocalContext.current

    val versionName = try {
        context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName
    } catch (_: Exception) {
        "2.0.0"
    }
    val viewModel: AirSyncViewModel = viewModel { AirSyncViewModel.create(context) }
    val uiState by viewModel.uiState.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    // Track if we've already processed the QR code dialog to prevent re-showing
    var hasProcessedQrDialog by remember { mutableStateOf(false) }

    // Bottom navigation state (0 = Connect, 1 = Settings)
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        viewModel.initializeState(context, initialIp, initialPort, showConnectionDialog && !hasProcessedQrDialog, pcName, isPlus, symmetricKey)

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
            symmetricKey = uiState.symmetricKey,
            manualAttempt = true,
            onHandshakeTimeout = {
                scope.launch(Dispatchers.Main) {
                    // Strong haptic feedback
                    try { haptics.performHapticFeedback(HapticFeedbackType.LongPress) } catch (_: Exception) {}
                    viewModel.setConnectionStatus(isConnected = false, isConnecting = false)
                    WebSocketUtil.disconnect(context)
                    viewModel.showAuthFailure(
                        "Connection failed due to authentication failure. Please check the encryption key by re-scanning the QR code."
                    )
                }
            },
            onConnectionStatus = { connected ->
                scope.launch(Dispatchers.Main) {
                    viewModel.setConnectionStatus(isConnected = connected, isConnecting = false)
                    if (connected) {
                        viewModel.setResponse("Connected successfully!")
                        // Get plus status from current temporary device or use QR code value
                        val plusStatus = uiState.lastConnectedDevice?.isPlus ?: isPlus
                        viewModel.saveLastConnectedDevice(pcName, plusStatus, uiState.symmetricKey)
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
                    } catch (_: Exception) {
                        // Not a clipboard update, ignore
                    }
                }
            }
        )
    }

    // Auth failure dialog
    if (uiState.showAuthFailureDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAuthFailure() },
            title = { Text("Connection failed") },
            text = {
                Text(uiState.authFailureMessage.ifEmpty {
                    "Authentication failed. Please re-scan the QR code on your Mac to ensure the encryption key matches."
                })
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissAuthFailure() }) {
                    Text("OK")
                }
            }
        )
    }

    fun disconnect() {
        // Set manual disconnect flag BEFORE disconnecting to prevent auto-reconnect trigger
        viewModel.setUserManuallyDisconnected(true)
        WebSocketUtil.disconnect(context)
        viewModel.setConnectionStatus(isConnected = false, isConnecting = false)
        viewModel.setResponse("Disconnected")
    }

    fun launchScanner(context: Context) {
        val lensIntent = Intent("com.google.vr.apps.ornament.app.lens.LensLauncherActivity.MAIN")
        lensIntent.setPackage("com.google.ar.lens")

        try {
            context.startActivity(lensIntent)
        } catch (_: ActivityNotFoundException) {
            // Fallback to default camera app
            val cameraIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
            try {
                context.startActivity(cameraIntent)
            } catch (_: ActivityNotFoundException) {
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
                delay(500)
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

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            val items = listOf("Connect", "Settings")
            val selectedIcons = listOf(Icons.Filled.Home, Icons.Filled.Settings)
            val unselectedIcons = listOf(Icons.Outlined.Home, Icons.Outlined.Settings)
            NavigationBar {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = {
                            Icon(
                                imageVector = if (selectedTab == index) selectedIcons[index] else unselectedIcons[index],
                                contentDescription = item
                            )
                        },
                        label = { Text(item) },
                        selected = selectedTab == index,
                        onClick = { selectedTab = index }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            0 -> {
                // Connect tab content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = innerPadding.calculateBottomPadding())
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AnimatedVisibility(
                        visible = !uiState.isConnected,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        QrScannerRow(
                            onLaunchScanner = {
                                launchScanner(context)
                            }
                        )
                    }

                    // Permission Status Card
                    AnimatedVisibility(
                        visible = uiState.missingPermissions.isNotEmpty(),
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        PermissionStatusCard(
                            missingPermissions = uiState.missingPermissions,
                            onGrantPermissions = { viewModel.setPermissionDialogVisible(true) },
                            onRefreshPermissions = { viewModel.refreshPermissions(context) },
                            onRequestNotificationPermission = onRequestNotificationPermission
                        )
                    }

                    // Connection Status Card
                    ConnectionStatusCard(
                        isConnected = uiState.isConnected,
                        isConnecting = uiState.isConnecting,
                        onDisconnect = { disconnect() },
                        connectedDevice = uiState.lastConnectedDevice,
                        lastConnected = uiState.lastConnectedDevice != null,
                        uiState = uiState,
                    )

                    AnimatedVisibility(
                        visible = !uiState.isConnected,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        Column {
                            ManualConnectionCard(
                                isConnected = uiState.isConnected,
                                lastConnected = uiState.lastConnectedDevice != null,
                                uiState = uiState,
                                onIpChange = { viewModel.updateIpAddress(it) },
                                onPortChange = { viewModel.updatePort(it) },
                                onPcNameChange = { viewModel.updateManualPcName(it) },
                                onIsPlusChange = { viewModel.updateManualIsPlus(it) },
                                onSymmetricKeyChange = { viewModel.updateSymmetricKey(it) },
                                onConnect = { viewModel.prepareForManualConnection() }
                            )
                        }
                    }

                    // Last Connected Device Section
                    AnimatedVisibility(
                        visible = !uiState.isConnected && uiState.lastConnectedDevice != null,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
                        uiState.lastConnectedDevice?.let { device ->
                            LastConnectedDeviceCard(
                                device = device,
                                isAutoReconnectEnabled = uiState.isAutoReconnectEnabled,
                                onToggleAutoReconnect = { enabled -> viewModel.setAutoReconnectEnabled(enabled) },
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
                                        viewModel.updateSymmetricKey(device.symmetricKey)
                                        connect()
                                    }
                                }
                            )
                        }
                    }
                }
            }
            else -> {
                // Settings tab content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = innerPadding.calculateBottomPadding())
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Notification Sync Settings Card
                    NotificationSyncCard(
                        isNotificationEnabled = uiState.isNotificationEnabled,
                        isNotificationSyncEnabled = uiState.isNotificationSyncEnabled,
                        onToggleSync = { enabled -> viewModel.setNotificationSyncEnabled(enabled) },
                        onGrantPermissions = { viewModel.setPermissionDialogVisible(true) }
                    )

                    // Clipboard Sync Card
                    ClipboardSyncCard(
                        isClipboardSyncEnabled = uiState.isClipboardSyncEnabled,
                        onToggleClipboardSync = { enabled ->
                            viewModel.setClipboardSyncEnabled(enabled)
                        },
                        isContinueBrowsingEnabled = uiState.isContinueBrowsingEnabled,
                        onToggleContinueBrowsing = { enabled -> viewModel.setContinueBrowsingEnabled(enabled) },
                        isContinueBrowsingToggleEnabled = if (uiState.isConnected) uiState.lastConnectedDevice?.isPlus == true else true,
                        continueBrowsingSubtitle = "Prompt to open shared links with AirSync+",
                        isSendNowPlayingEnabled = uiState.isSendNowPlayingEnabled,
                        onToggleSendNowPlaying = { enabled -> viewModel.setSendNowPlayingEnabled(enabled) }
                    )

                    DeviceInfoCard(
                        deviceName = uiState.deviceNameInput,
                        localIp = deviceInfo.localIp,
                        onDeviceNameChange = { viewModel.updateDeviceName(it) }
                    )

                    // Developer Mode Card
                    AnimatedVisibility(
                        visible = uiState.isDeveloperModeVisible,
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
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
                                val testNotification = TestNotificationUtil.generateRandomNotification()
                                val message = JsonUtil.createNotificationJson(
                                    testNotification.id,
                                    testNotification.title,
                                    testNotification.body,
                                    testNotification.appName,
                                    testNotification.packageName
                                )
                                sendMessage(message)
                            },
                            onSendDeviceStatus = {
                                val message = DeviceInfoUtil.generateDeviceStatusJson(context)
                                sendMessage(message)
                            },
                            uiState = uiState
                        )
                    }

                    // Manual Icon Sync Button
                    OutlinedButton(
                        onClick = { viewModel.manualSyncAppIcons(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = if (uiState.isDeveloperModeVisible) 0.dp else 20.dp),
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
                    AnimatedVisibility(
                        visible = uiState.iconSyncMessage.isNotEmpty(),
                        enter = expandVertically() + fadeIn(),
                        exit = shrinkVertically() + fadeOut()
                    ) {
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
                                TextButton(onClick = { viewModel.clearIconSyncMessage() }) {
                                    Text("Dismiss")
                                }
                            }
                        }
                    }
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

    // About Dialog - controlled by parent via showAboutDialog
    if (showAboutDialog) {
        AboutDialog(
            onDismissRequest = onDismissAbout,
            onToggleDeveloperMode = { viewModel.toggleDeveloperModeVisibility() }
        )
    }
}