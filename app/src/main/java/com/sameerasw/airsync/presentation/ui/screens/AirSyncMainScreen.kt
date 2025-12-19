package com.sameerasw.airsync.presentation.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.alpha
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Phonelink
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.outlined.Phonelink
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sameerasw.airsync.presentation.viewmodel.AirSyncViewModel
import com.sameerasw.airsync.utils.ClipboardSyncManager
import com.sameerasw.airsync.utils.DeviceInfoUtil
import com.sameerasw.airsync.utils.JsonUtil
import com.sameerasw.airsync.utils.TestNotificationUtil
import com.sameerasw.airsync.utils.WebSocketUtil
import com.sameerasw.airsync.utils.HapticUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.sameerasw.airsync.ui.theme.ExtraCornerRadius
import com.sameerasw.airsync.ui.theme.minCornerRadius
import com.sameerasw.airsync.presentation.ui.components.cards.SyncFeaturesCard
import com.sameerasw.airsync.presentation.ui.components.cards.DeveloperModeCard
import com.sameerasw.airsync.presentation.ui.components.cards.PermissionsCard
import com.sameerasw.airsync.presentation.ui.components.cards.LastConnectedDeviceCard
import com.sameerasw.airsync.presentation.ui.components.cards.ManualConnectionCard
import com.sameerasw.airsync.presentation.ui.components.cards.NotificationSyncCard
import com.sameerasw.airsync.presentation.ui.components.cards.DeviceInfoCard
import com.sameerasw.airsync.presentation.ui.components.cards.ConnectionStatusCard
import com.sameerasw.airsync.presentation.ui.components.cards.ExpandNetworkingCard
import com.sameerasw.airsync.presentation.ui.components.cards.QuickSettingsTipCard
import com.sameerasw.airsync.presentation.ui.components.dialogs.AboutDialog
import com.sameerasw.airsync.presentation.ui.components.dialogs.ConnectionDialog
import com.sameerasw.airsync.presentation.ui.activities.QRScannerActivity
import org.json.JSONObject
import kotlinx.coroutines.Job
import java.net.URLDecoder

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
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val connectScrollState = rememberScrollState()
    val settingsScrollState = rememberScrollState()
    var hasProcessedQrDialog by remember { mutableStateOf(false) }
    val pagerState = rememberPagerState(initialPage = 0, pageCount = { 3 })
    val navCallbackState = rememberUpdatedState(onNavigateToApps)
    LaunchedEffect(navCallbackState.value) {
    }
    var fabVisible by remember { mutableStateOf(true) }
    var fabExpanded by remember { mutableStateOf(true) }
    var loadingHapticsJob by remember { mutableStateOf<Job?>(null) }

    // For export/import flow
    var pendingExportJson by remember { mutableStateOf<String?>(null) }
    
    fun connect() {
        // Check if critical permissions are missing
        val criticalPermissions = com.sameerasw.airsync.utils.PermissionUtil.getCriticalMissingPermissions(context)
        if (criticalPermissions.isNotEmpty()) {
            Toast.makeText(context, "Missing permissions", Toast.LENGTH_SHORT).show()
            return
        }

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
                                // Add to clipboard history (from PC)
                                viewModel.addClipboardEntry(text, isFromPc = true)
                                // Handle the clipboard update
                                ClipboardSyncManager.handleClipboardUpdate(context, text)
                            }
                        }
                    } catch (_: Exception) {}
                }
            }
        )
    }

    // CreateDocument launcher for export (MIME application/json)
    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri == null) {
            Toast.makeText(context, "Export cancelled", Toast.LENGTH_SHORT).show()
            viewModel.setLoading(false)
            return@rememberLauncherForActivityResult
        }

        // Write pendingExportJson to uri
        scope.launch(Dispatchers.IO) {
            try {
                val json = pendingExportJson
                if (json == null) {
                    // Nothing to write
                    viewModel.setLoading(false)
                    return@launch
                }
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(json.toByteArray(Charsets.UTF_8))
                }
                scope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "Export successful", Toast.LENGTH_SHORT).show()
                    viewModel.setLoading(false)
                    pendingExportJson = null
                }
            } catch (e: Exception) {
                e.printStackTrace()
                scope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
                    viewModel.setLoading(false)
                    pendingExportJson = null
                }
            }
        }
    }

    // OpenDocument launcher for import (allow picking JSON)
    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            Toast.makeText(context, "Import cancelled", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }

        viewModel.setLoading(true)
        scope.launch(Dispatchers.IO) {
            try {
                val input = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (input == null) {
                    scope.launch(Dispatchers.Main) {
                        Toast.makeText(context, "Failed to read file", Toast.LENGTH_LONG).show()
                        viewModel.setLoading(false)
                    }
                    return@launch
                }

                val success = viewModel.importDataFromJson(context, input)
                scope.launch(Dispatchers.Main) {
                    if (success) {
                        Toast.makeText(context, "Import successful", Toast.LENGTH_SHORT).show()
                        viewModel.initializeState(context)
                    } else {
                        Toast.makeText(context, "Import failed or invalid file", Toast.LENGTH_LONG).show()
                    }
                    viewModel.setLoading(false)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                scope.launch(Dispatchers.Main) {
                    Toast.makeText(context, "Import error: ${e.message}", Toast.LENGTH_LONG).show()
                    viewModel.setLoading(false)
                }
            }
        }
    }

    // QR Scanner launcher
    val qrScannerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val qrCode = result.data?.getStringExtra("QR_CODE")
            if (!qrCode.isNullOrEmpty()) {
                // Parse the QR code (expected format: airsync://ip:port?name=...&plus=...&key=...)
                try {
                    val uri = Uri.parse(qrCode)
                    val ip = uri.host ?: ""
                    val port = uri.port.takeIf { it != -1 }?.toString() ?: ""

                    // Parse query parameters
                    var pcName: String? = null
                    var isPlus = false
                    var symmetricKey: String? = null

                    val queryPart = uri.toString().substringAfter('?', "")
                    if (queryPart.isNotEmpty()) {
                        val params = queryPart.split('?')
                        val paramMap = params.associate { param ->
                            val parts = param.split('=', limit = 2)
                            val key = parts.getOrNull(0) ?: ""
                            val value = parts.getOrNull(1) ?: ""
                            key to value
                        }
                        pcName = paramMap["name"]?.let { URLDecoder.decode(it, "UTF-8") }
                        isPlus = paramMap["plus"]?.toBooleanStrictOrNull() ?: false
                        symmetricKey = paramMap["key"]
                    }

                    if (ip.isNotEmpty() && port.isNotEmpty()) {
                        // Update UI state with scanned values
                        viewModel.updateIpAddress(ip)
                        viewModel.updatePort(port)
                        viewModel.updateManualPcName(pcName ?: "")
                        viewModel.updateManualIsPlus(isPlus)
                        if (!symmetricKey.isNullOrEmpty()) {
                            viewModel.updateSymmetricKey(symmetricKey)
                        }

                        // Trigger connection
                        scope.launch {
                            delay(300)  // Brief delay to ensure UI updates
                            connect()
                        }
                    } else {
                        Toast.makeText(context, "Invalid QR code format", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to parse QR code: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    fun disconnect() {
        scope.launch {
            viewModel.setUserManuallyDisconnectedAwait(true)
            WebSocketUtil.disconnect(context)
            viewModel.setConnectionStatus(isConnected = false, isConnecting = false)
            viewModel.clearClipboardHistory()
            viewModel.setResponse("Disconnected")
        }
    }

    LaunchedEffect(Unit) {
        viewModel.initializeState(context, initialIp, initialPort, showConnectionDialog && !hasProcessedQrDialog, pcName, isPlus, symmetricKey)

        // Start network monitoring for dynamic Wi-Fi changes
        viewModel.startNetworkMonitoring(context)

        // Refresh permissions on app launch
        viewModel.refreshPermissions(context)
    }

    // Refresh permissions when app resumes from pause
    DisposableEffect(lifecycle) {
        val lifecycleObserver = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions(context)
            }
        }
        lifecycle.addObserver(lifecycleObserver)

        onDispose {
            lifecycle.removeObserver(lifecycleObserver)
        }
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

    // Hide FAB on scroll down, show on scroll up for the active tab
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

    // Expand FAB on first launch and whenever variant changes (connect <-> disconnect), then collapse after 5s
    LaunchedEffect(uiState.isConnected) {
        fabExpanded = true
        // Give users a hint for a short period, then collapse to icon-only
        delay(5000)
        fabExpanded = false
    }

    // Start/stop clipboard sync based on connection status and settings
    LaunchedEffect(uiState.isConnected, uiState.isClipboardSyncEnabled) {
        if (uiState.isConnected && uiState.isClipboardSyncEnabled) {
            // Register callback to track clipboard history
            ClipboardSyncManager.setOnClipboardSentCallback { text ->
                viewModel.addClipboardEntry(text, isFromPc = false)
            }
            ClipboardSyncManager.startSync(context)
        } else {
            ClipboardSyncManager.stopSync(context)
        }
    }

    // Start/stop loading haptics when connecting
    LaunchedEffect(uiState.isConnecting) {
        if (uiState.isConnecting) {
            loadingHapticsJob = HapticUtil.startLoadingHaptics(haptics, lifecycle)
        } else {
            loadingHapticsJob?.cancel()
            loadingHapticsJob = null
        }
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


    fun launchScanner(context: Context) {
        // Launch our custom QR Scanner Activity
        val scannerIntent = Intent(context, QRScannerActivity::class.java)
        qrScannerLauncher.launch(scannerIntent)
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
            ClipboardSyncManager.setOnClipboardSentCallback(null)
            ClipboardSyncManager.stopSync(context)
        }
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Secondary FAB for clearing clipboard history
                AnimatedVisibility(
                    visible = fabVisible && uiState.isConnected && pagerState.currentPage == 1,
                    enter = scaleIn(),
                    exit = scaleOut()
                ) {
                    FloatingActionButton(
                        onClick = {
                            HapticUtil.performClick(haptics)
                            viewModel.clearClipboardHistory()
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Clear clipboard history"
                        )
                    }
                }
                // Primary FAB for connect/disconnect
                AnimatedVisibility(visible = fabVisible, enter = scaleIn(), exit = scaleOut()) {
                    ExtendedFloatingActionButton(
                        onClick = {
                            HapticUtil.performClick(haptics)
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
            }
        },
        bottomBar = {
            val items = listOf("Connect", "Clipboard", "Settings")
            val selectedIcons = listOf(Icons.Filled.Phonelink, Icons.Filled.ContentPaste, Icons.Filled.Settings)
            val unselectedIcons = listOf(Icons.Outlined.Phonelink, Icons.Rounded.ContentPaste, Icons.Outlined.Settings)
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
                            // Keep label space reserved (alwaysShowLabel=true) and fade it in/out to avoid icon jumps
                            Text(item, modifier = Modifier.alpha(alpha))
                        },
                        alwaysShowLabel = true,
                        selected = pagerState.currentPage == index,
                        onClick = {
                            HapticUtil.performLightTick(haptics)
                            scope.launch { pagerState.animateScrollToPage(index) }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        // Track page changes for haptic feedback on swipe
        LaunchedEffect(pagerState.currentPage) {
            snapshotFlow { pagerState.currentPage }.collect { page ->
                HapticUtil.performLightTick(haptics)
            }
        }

        HorizontalPager(
            modifier = modifier.fillMaxSize(),
            state = pagerState
        ) { page ->
            when (page) {
                0 -> {
                    // Connect tab content
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding())
                            .verticalScroll(connectScrollState)
                            .padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {

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
                                onConnect = { viewModel.prepareForManualConnection() },
                                onQrScanClick = { launchScanner(context) }
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

                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
                1 -> {
                    // Clipboard tab content
                    ClipboardScreen(
                        clipboardHistory = uiState.clipboardHistory,
                        isConnected = uiState.isConnected,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding())
                    )
                }
                else -> {
                    // Settings tab content
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(bottom = innerPadding.calculateBottomPadding())
                            .verticalScroll(settingsScrollState)
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                    // Permissions Card
                    PermissionsCard(
                        missingPermissionsCount = uiState.missingPermissions.size
                    )

                    // Quick Settings Tip Card
                    QuickSettingsTipCard(
                        isQSTileAdded = com.sameerasw.airsync.utils.QuickSettingsUtil.isQSTileAdded(context)
                    )

                    // Notification Sync Settings Card
                    NotificationSyncCard(
                        isNotificationEnabled = uiState.isNotificationEnabled,
                        isNotificationSyncEnabled = uiState.isNotificationSyncEnabled,
                        onToggleSync = { enabled -> viewModel.setNotificationSyncEnabled(enabled) },
                        onGrantPermissions = { viewModel.setPermissionDialogVisible(true) }
                    )

                    // Clipboard Sync Card
                    SyncFeaturesCard(
                        isClipboardSyncEnabled = uiState.isClipboardSyncEnabled,
                        onToggleClipboardSync = { enabled ->
                            viewModel.setClipboardSyncEnabled(enabled)
                        },
                        isContinueBrowsingEnabled = uiState.isContinueBrowsingEnabled,
                        onToggleContinueBrowsing = { enabled -> viewModel.setContinueBrowsingEnabled(enabled) },
                        isContinueBrowsingToggleEnabled = if (uiState.isConnected) uiState.lastConnectedDevice?.isPlus == true else true,
                        continueBrowsingSubtitle = "Prompt to open shared links with AirSync+",
                        isSendNowPlayingEnabled = uiState.isSendNowPlayingEnabled,
                        onToggleSendNowPlaying = { enabled -> viewModel.setSendNowPlayingEnabled(enabled) },
                        isKeepPreviousLinkEnabled = uiState.isKeepPreviousLinkEnabled,
                        onToggleKeepPreviousLink = { enabled -> viewModel.setKeepPreviousLinkEnabled(enabled) },
                        isSmartspacerShowWhenDisconnected = uiState.isSmartspacerShowWhenDisconnected,
                        onToggleSmartspacerShowWhenDisconnected = { enabled -> viewModel.setSmartspacerShowWhenDisconnected(enabled) }
                    )

                        ExpandNetworkingCard(context)


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
                                val adbPorts = try {
                                    val discoveredServices = com.sameerasw.airsync.AdbDiscoveryHolder.getDiscoveredServices()
                                    discoveredServices.map { it.port.toString() }
                                } catch (e: Exception) {
                                    emptyList()
                                }
                                val message = JsonUtil.createDeviceInfoJson(
                                    deviceInfo.name,
                                    deviceInfo.localIp,
                                    uiState.port.toIntOrNull() ?: 6996,
                                    versionName ?: "2.0.0",
                                    adbPorts
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
                            // Export/Import actions
                            onExportData = {
                                viewModel.setLoading(true)
                                scope.launch(Dispatchers.IO) {
                                    val json = viewModel.exportAllDataToJson(context)
                                    if (json == null) {
                                        scope.launch(Dispatchers.Main) {
                                            Toast.makeText(context, "Export failed", Toast.LENGTH_LONG).show()
                                            viewModel.setLoading(false)
                                        }
                                    } else {
                                        pendingExportJson = json
                                        scope.launch(Dispatchers.Main) {
                                            createDocLauncher.launch("airsync_settings_${System.currentTimeMillis()}.json")
                                        }
                                    }
                                }
                            },
                            onImportData = {
                                // Launch picker to select a file to import
                                openDocLauncher.launch(arrayOf("application/json"))
                            }
                        )
                    }

                    // Manual Icon Sync Button
                    OutlinedButton(
                        onClick = {
                            HapticUtil.performClick(haptics)
                            viewModel.manualSyncAppIcons(context)
                        },
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
                                TextButton(onClick = {
                                    HapticUtil.performClick(haptics)
                                    viewModel.clearIconSyncMessage()
                                }) {
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
