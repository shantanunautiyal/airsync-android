
package com.sameerasw.airsync.presentation.ui.screens

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.util.Log
import androidx.core.content.ContextCompat
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Phonelink
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Phonelink
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sameerasw.airsync.presentation.ui.components.cards.ClipboardSyncCard
import com.sameerasw.airsync.presentation.ui.components.cards.ConnectionStatusCard
import com.sameerasw.airsync.presentation.ui.components.cards.DeviceInfoCard
import com.sameerasw.airsync.presentation.ui.components.cards.DeveloperModeCard
import com.sameerasw.airsync.presentation.ui.components.cards.LastConnectedDeviceCard
import com.sameerasw.airsync.presentation.ui.components.cards.ManualConnectionCard
import com.sameerasw.airsync.presentation.ui.components.cards.NotificationSyncCard
import com.sameerasw.airsync.presentation.ui.components.cards.PermissionStatusCard
import com.sameerasw.airsync.presentation.ui.components.cards.TailscaleSupportCard
import com.sameerasw.airsync.presentation.ui.components.dialogs.AboutDialog
import com.sameerasw.airsync.presentation.ui.components.dialogs.ConnectionDialog
import com.sameerasw.airsync.presentation.viewmodel.AirSyncViewModel
import com.sameerasw.airsync.service.ScreenCaptureService
import com.sameerasw.airsync.ui.theme.ExtraCornerRadius
import com.sameerasw.airsync.ui.theme.minCornerRadius
import com.sameerasw.airsync.utils.ClipboardSyncManager
import com.sameerasw.airsync.utils.DeviceInfoUtil
import com.sameerasw.airsync.utils.JsonUtil
import com.sameerasw.airsync.utils.PermissionUtil
import com.sameerasw.airsync.utils.TestNotificationUtil
import com.sameerasw.airsync.utils.WallpaperHandler
import com.sameerasw.airsync.utils.WebSocketUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    onDismissAbout: () -> Unit = {},
    showMirroringDialog: Boolean = false,
    mirroringOptions: android.os.Bundle? = null,
    onDismissMirroringDialog: () -> Unit = {}
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
    val connectScrollState = rememberScrollState()
    val settingsScrollState = rememberScrollState()
    var fabVisible by remember { mutableStateOf(true) }
    var fabExpanded by remember { mutableStateOf(true) }

    val isMirroring by ScreenCaptureService.isStreaming.collectAsState()

    val screenCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val serviceIntent = Intent(context, ScreenCaptureService::class.java).apply {
                action = ScreenCaptureService.ACTION_START
                putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(ScreenCaptureService.EXTRA_DATA, result.data)
                putExtra("mirroringOptions", uiState.mirroringOptions)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            viewModel.clearMirroringUrl()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshPermissions(context)
    }

    var hasProcessedQrDialog by remember { mutableStateOf(false) }

    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 2 })

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.initializeState(context, initialIp, initialPort, showConnectionDialog && !hasProcessedQrDialog, pcName, isPlus, symmetricKey)
        viewModel.startNetworkMonitoring(context)
    }

    LaunchedEffect(showConnectionDialog, uiState.isConnected) {
        if (showConnectionDialog) {
            if (uiState.isConnected) {
                hasProcessedQrDialog = true
            } else if (uiState.isDialogVisible) {
                hasProcessedQrDialog = true
            }
        }
    }

    LaunchedEffect(pagerState.currentPage) {
        val state = if (pagerState.currentPage == 0) connectScrollState else settingsScrollState
        var last = state.value
        snapshotFlow { state.value }.collect { value ->
            val delta = value - last
            if (delta > 2) fabVisible = false
            else if (delta < -2) fabVisible = true
            last = value
        }
    }

    LaunchedEffect(uiState.isConnected) {
        fabExpanded = true
        delay(5000)
        fabExpanded = false
    }

    LaunchedEffect(uiState.isConnected, uiState.isClipboardSyncEnabled) {
        if (uiState.isConnected && uiState.isClipboardSyncEnabled) {
            ClipboardSyncManager.startSync(context)
        } else {
            ClipboardSyncManager.stopSync(context)
        }
    }

    LaunchedEffect(uiState.isConnected) {
        if (uiState.isConnected) {
            scope.launch {
                WallpaperHandler.sendWallpaper(context)
            }
        } else {
            // Stop screen capture service when disconnected
            if (isMirroring) {
                val serviceIntent = Intent(context, ScreenCaptureService::class.java).apply {
                    action = ScreenCaptureService.ACTION_STOP
                }
                context.startService(serviceIntent)
            }
        }
    }

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
                    try { haptics.performHapticFeedback(HapticFeedbackType.LongPress) } catch (_: Exception) {}
                    viewModel.setConnectionStatus(isConnected = false, isConnecting = false)
                    WebSocketUtil.disconnect(context)
                    viewModel.showAuthFailure("Connection failed due to authentication failure. Please check the encryption key by re-scanning the QR code.")
                }
            },
            onConnectionStatus = { connected ->
                scope.launch(Dispatchers.Main) {
                    viewModel.setConnectionStatus(isConnected = connected, isConnecting = false)
                    if (connected) {
                        viewModel.setResponse("Connected successfully!")
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
                        // Not a valid JSON or relevant message, ignore
                    }
                }
            }
        )
    }

    if (uiState.showAuthFailureDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissAuthFailure() },
            title = { Text("Connection failed") },
            text = {
                Text(uiState.authFailureMessage.ifEmpty { "Authentication failed. Please re-scan the QR code on your Mac to ensure the encryption key matches." })
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissAuthFailure() }) {
                    Text("OK")
                }
            }
        )
    }

    LaunchedEffect(showMirroringDialog) {
        if (showMirroringDialog) {
            val fps = mirroringOptions?.getInt("fps", 30) ?: 30
            val quality = mirroringOptions?.getFloat("quality", 0.8f) ?: 0.8f
            val maxWidth = mirroringOptions?.getInt("maxWidth", 1280) ?: 1280
            val bitrateKbps = mirroringOptions?.getInt("bitrateKbps", 12000) ?: 12000
            viewModel.onMirroringRequest(fps, quality, maxWidth, bitrateKbps)
        }
    }

    if (uiState.showMirroringDialog) {
        AlertDialog(
            onDismissRequest = {
                viewModel.dismissMirroringDialog()
                onDismissMirroringDialog()
            },
            title = { Text("Screen Mirroring Request") },
            text = { Text("Your Mac is requesting to mirror this device's screen. Do you accept?") },
            confirmButton = {
                TextButton(onClick = { 
                    viewModel.dismissMirroringDialog()
                    onDismissMirroringDialog()
                    val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    screenCaptureLauncher.launch(manager.createScreenCaptureIntent())
                }) {
                    Text("Accept")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    viewModel.dismissMirroringDialog()
                    onDismissMirroringDialog()
                }) {
                    Text("Decline")
                }
            }
        )
    }

    fun disconnect() {
        scope.launch {
            viewModel.setUserManuallyDisconnectedAwait(true)
            WebSocketUtil.disconnect(context)
            viewModel.setConnectionStatus(isConnected = false, isConnecting = false)
            viewModel.setResponse("Disconnected")
        }
    }

    fun launchScanner(context: Context) {
        val lensIntent = Intent("com.google.vr.apps.ornament.app.lens.LensLauncherActivity.MAIN")
        lensIntent.setPackage("com.google.ar.lens")
        try {
            context.startActivity(lensIntent)
        } catch (_: ActivityNotFoundException) {
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
        floatingActionButton = {
            AnimatedVisibility(visible = fabVisible) {
                ExtendedFloatingActionButton(
                    onClick = {
                        if (uiState.isConnected) {
                            disconnect()
                        } else {
                            launchScanner(context)
                        }
                    },
                    icon = {
                        if (uiState.isConnected) {
                            Icon(imageVector = Icons.Filled.LinkOff, contentDescription = "Disconnect")
                        } else {
                            Icon(imageVector = Icons.Filled.QrCodeScanner, contentDescription = "Scan QR")
                        }
                    },
                    text = { Text(text = if (uiState.isConnected) "Disconnect" else "Scan to connect") },
                    expanded = fabExpanded
                )
            }
        },
        bottomBar = {
            val items = listOf("Connect", "Settings")
            val selectedIcons = listOf(Icons.Filled.Phonelink, Icons.Filled.Settings)
            val unselectedIcons = listOf(Icons.Outlined.Phonelink, Icons.Outlined.Settings)
            NavigationBar(
                windowInsets = NavigationBarDefaults.windowInsets
            ) {
                items.forEachIndexed { index, item ->
                    NavigationBarItem(
                        icon = {
                            val selected = pagerState.currentPage == index
                            val iconOffset by animateDpAsState(targetValue = if (selected) 0.dp else 2.dp, label = "NavIconOffset")
                            Icon(
                                imageVector = if (selected) selectedIcons[index] else unselectedIcons[index],
                                contentDescription = item,
                                modifier = Modifier.offset(y = iconOffset)
                            )
                        },
                        label = {
                            val selected = pagerState.currentPage == index
                            val alpha by animateFloatAsState(targetValue = if (selected) 1f else 0f, label = "NavLabelAlpha")
                            Text(item, modifier = Modifier.alpha(alpha))
                        },
                        alwaysShowLabel = true,
                        selected = pagerState.currentPage == index,
                        onClick = { scope.launch { pagerState.animateScrollToPage(index) } }
                    )
                }
            }
        }
    ) { innerPadding ->
        HorizontalPager(
            modifier = Modifier.fillMaxSize(),
            state = pagerState
        ) { page ->
            when (page) {
                0 -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding())
                            .verticalScroll(connectScrollState)
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        AnimatedVisibility(
                            visible = uiState.missingPermissions.isNotEmpty(),
                            enter = expandVertically() + fadeIn(),
                            exit = shrinkVertically() + fadeOut()
                        ) {
                            PermissionStatusCard(
                                missingPermissions = uiState.missingPermissions,
                                onGrantPermission = { permission ->
                                    when (permission) {
                                        PermissionUtil.NOTIFICATION_ACCESS -> PermissionUtil.openNotificationListenerSettings(context)
                                        PermissionUtil.ACCESSIBILITY_SERVICE -> PermissionUtil.openAccessibilitySettings(context)
                                        PermissionUtil.BACKGROUND_APP_USAGE -> PermissionUtil.openBatteryOptimizationSettings(context)
                                        Manifest.permission.POST_NOTIFICATIONS -> onRequestNotificationPermission()
                                        Manifest.permission.READ_MEDIA_IMAGES -> permissionLauncher.launch(Manifest.permission.READ_MEDIA_IMAGES)
                                    }
                                },
                                onRefreshPermissions = { viewModel.refreshPermissions(context) }
                            )
                        }

                        ConnectionStatusCard(
                            isConnected = uiState.isConnected,
                            isConnecting = uiState.isConnecting,
                            onDisconnect = { disconnect() },
                            connectedDevice = uiState.lastConnectedDevice,
                            lastConnected = uiState.lastConnectedDevice != null,
                            uiState = uiState,
                        )

                        AnimatedVisibility(visible = uiState.isConnected && isMirroring) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = {
                                        val intent = Intent(context, ScreenCaptureService::class.java).apply {
                                            action = ScreenCaptureService.ACTION_STOP
                                        }
                                        context.startService(intent)
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("Stop Mirror")
                                }
                            }
                        }

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
                                        val networkAwareDevice = viewModel.getNetworkAwareLastConnectedDevice()
                                        if (networkAwareDevice != null) {
                                            viewModel.updateIpAddress(networkAwareDevice.ipAddress)
                                            viewModel.updatePort(networkAwareDevice.port)
                                            connect()
                                        } else {
                                            viewModel.updateIpAddress(device.ipAddress)
                                            viewModel.updatePort(device.port)
                                            viewModel.updateSymmetricKey(device.symmetricKey)
                                            connect()
                                        }
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding())
                            .verticalScroll(settingsScrollState)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        NotificationSyncCard(
                            isNotificationEnabled = uiState.isNotificationEnabled,
                            isNotificationSyncEnabled = uiState.isNotificationSyncEnabled,
                            onToggleSync = { enabled -> viewModel.setNotificationSyncEnabled(enabled) },
                            onGrantPermissions = { viewModel.setPermissionDialogVisible(true) }
                        )

                        ClipboardSyncCard(
                            isClipboardSyncEnabled = uiState.isClipboardSyncEnabled,
                            onToggleClipboardSync = { enabled -> viewModel.setClipboardSyncEnabled(enabled) },
                            isContinueBrowsingEnabled = uiState.isContinueBrowsingEnabled,
                            onToggleContinueBrowsing = { enabled -> viewModel.setContinueBrowsingEnabled(enabled) },
                            isContinueBrowsingToggleEnabled = if (uiState.isConnected) uiState.lastConnectedDevice?.isPlus == true else true,
                            continueBrowsingSubtitle = "Prompt to open shared links with AirSync+",
                            isSendNowPlayingEnabled = uiState.isSendNowPlayingEnabled,
                            onToggleSendNowPlaying = { enabled -> viewModel.setSendNowPlayingEnabled(enabled) },
                            isKeepPreviousLinkEnabled = uiState.isKeepPreviousLinkEnabled,
                            onToggleKeepPreviousLink = { enabled -> viewModel.setKeepPreviousLinkEnabled(enabled) }
                        )

                        TailscaleSupportCard(context)

                        DeviceInfoCard(
                            deviceName = uiState.deviceNameInput,
                            localIp = deviceInfo.localIp,
                            onDeviceNameChange = { viewModel.updateDeviceName(it) }
                        )

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

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }

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

    if (showAboutDialog) {
        AboutDialog(
            onDismissRequest = onDismissAbout,
            onToggleDeveloperMode = { viewModel.toggleDeveloperModeVisibility() }
        )
    }
}
