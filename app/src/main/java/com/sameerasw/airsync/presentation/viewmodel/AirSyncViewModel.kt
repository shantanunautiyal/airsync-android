package com.sameerasw.airsync.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sameerasw.airsync.data.local.DataStoreManager
import com.sameerasw.airsync.data.repository.AirSyncRepositoryImpl
import com.sameerasw.airsync.domain.model.ConnectedDevice
import com.sameerasw.airsync.domain.model.DeviceInfo
import com.sameerasw.airsync.domain.model.NetworkDeviceConnection
import com.sameerasw.airsync.domain.model.UiState
import com.sameerasw.airsync.domain.model.UpdateInfo
import com.sameerasw.airsync.domain.model.NetworkInfo
import com.sameerasw.airsync.domain.model.UpdateStatus
import com.sameerasw.airsync.domain.repository.AirSyncRepository
import com.sameerasw.airsync.utils.DeviceInfoUtil
import com.sameerasw.airsync.utils.NetworkMonitor
import com.sameerasw.airsync.utils.PermissionUtil
import com.sameerasw.airsync.utils.SyncManager
import com.sameerasw.airsync.utils.UpdateManager
import com.sameerasw.airsync.utils.WebSocketUtil
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

class AirSyncViewModel(
    private val repository: AirSyncRepository
) : ViewModel() {

    companion object {
        fun create(context: Context): AirSyncViewModel {
            val dataStoreManager = DataStoreManager(context)
            val repository = AirSyncRepositoryImpl(dataStoreManager)
            return AirSyncViewModel(repository)
        }
    }

    private val _uiState = MutableStateFlow(UiState())
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _deviceInfo = MutableStateFlow(DeviceInfo())
    val deviceInfo: StateFlow<DeviceInfo> = _deviceInfo.asStateFlow()

    // Update-related state
    private val _updateInfo = MutableStateFlow<UpdateInfo?>(null)
    val updateInfo: StateFlow<UpdateInfo?> = _updateInfo.asStateFlow()

    private val _updateStatus = MutableStateFlow(UpdateStatus.NO_UPDATE)
    val updateStatus: StateFlow<UpdateStatus> = _updateStatus.asStateFlow()

    private val _downloadProgress = MutableStateFlow(0)
    val downloadProgress: StateFlow<Int> = _downloadProgress.asStateFlow()

    private val _showUpdateDialog = MutableStateFlow(false)
    val showUpdateDialog: StateFlow<Boolean> = _showUpdateDialog.asStateFlow()

    private var currentDownloadId: Long? = null

    // Network-aware device connections state
    private val _networkDevices = MutableStateFlow<List<NetworkDeviceConnection>>(emptyList())

    // Network monitoring
    private var isNetworkMonitoringActive = false
    private var previousNetworkIp: String? = null

    // Auto-reconnect
    private var autoReconnectJob: kotlinx.coroutines.Job? = null
    private var autoReconnectStart: Long = 0L
    private var appContext: Context? = null

    // Connection status listener for WebSocket updates
    private val connectionStatusListener: (Boolean) -> Unit = { isConnected ->
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isConnected = isConnected,
                isConnecting = false,
                response = if (isConnected) "Connected successfully!" else "Disconnected"
            )

            // Cancel auto-reconnect when connected; schedule when disconnected (if allowed)
            if (isConnected) {
                cancelAutoReconnect()
            } else {
                appContext?.let { ctx ->
                    maybeScheduleAutoReconnect(ctx)
                }
            }
        }
    }

    init {
        // Register for WebSocket connection status updates
        WebSocketUtil.registerConnectionStatusListener(connectionStatusListener)
    }

    override fun onCleared() {
        super.onCleared()
        // Unregister the connection status listener when ViewModel is cleared
        WebSocketUtil.unregisterConnectionStatusListener(connectionStatusListener)
    }

    fun initializeState(
        context: Context,
        initialIp: String? = null,
        initialPort: String? = null,
        showConnectionDialog: Boolean = false,
        pcName: String? = null,
        isPlus: Boolean = false,
        symmetricKey: String? = null
    ) {
        appContext = context.applicationContext
        viewModelScope.launch {
            // Load saved values
            val savedIp = initialIp ?: repository.getIpAddress().first()
            val savedPort = initialPort ?: repository.getPort().first()
            val savedDeviceName = repository.getDeviceName().first()
            val lastConnected = repository.getLastConnectedDevice().first()
            val isNotificationSyncEnabled = repository.getNotificationSyncEnabled().first()
            val isDeveloperMode = repository.getDeveloperMode().first()
            val isClipboardSyncEnabled = repository.getClipboardSyncEnabled().first()
            val lastConnectedSymmetricKey = lastConnected?.symmetricKey
            val isAutoReconnectEnabled = repository.getAutoReconnectEnabled().first()

            // Get device info
            val deviceName = savedDeviceName.ifEmpty {
                val autoName = DeviceInfoUtil.getDeviceName(context)
                repository.saveDeviceName(autoName)
                autoName
            }

            val localIp = DeviceInfoUtil.getWifiIpAddress(context) ?: "Unknown"

            _deviceInfo.value = DeviceInfo(name = deviceName, localIp = localIp)

            // Load network-aware device connections
            loadNetworkDevices()

            // Check for network-aware device for current network
            val networkAwareDevice = getNetworkAwareLastConnectedDevice()
            val deviceToShow = networkAwareDevice ?: lastConnected

            // Check permissions
            val missingPermissions = PermissionUtil.getAllMissingPermissions(context)
            val isNotificationEnabled = PermissionUtil.isNotificationListenerEnabled(context)

            // Check current WebSocket connection status
            val currentlyConnected = WebSocketUtil.isConnected()

            _uiState.value = _uiState.value.copy(
                ipAddress = savedIp,
                port = savedPort,
                deviceNameInput = deviceName,
                // Only show dialog if not already connected and showConnectionDialog is true
                isDialogVisible = showConnectionDialog && !currentlyConnected,
                missingPermissions = missingPermissions,
                isNotificationEnabled = isNotificationEnabled,
                lastConnectedDevice = deviceToShow,
                isNotificationSyncEnabled = isNotificationSyncEnabled,
                isDeveloperMode = isDeveloperMode,
                isClipboardSyncEnabled = isClipboardSyncEnabled,
                isConnected = currentlyConnected,
                symmetricKey = symmetricKey ?: lastConnectedSymmetricKey,
                isAutoReconnectEnabled = isAutoReconnectEnabled
            )

            // If we have PC name from QR code and not already connected, store it temporarily for the dialog
            if (pcName != null && showConnectionDialog && !currentlyConnected) {
                _uiState.value = _uiState.value.copy(
                    lastConnectedDevice = ConnectedDevice(
                        name = pcName,
                        ipAddress = savedIp,
                        port = savedPort,
                        lastConnected = System.currentTimeMillis(),
                        isPlus = isPlus,
                        symmetricKey = symmetricKey
                    )
                )
            }

            // If not connected and conditions allow, schedule auto-reconnect
            if (!currentlyConnected) {
                maybeScheduleAutoReconnect(context)
            }
        }
    }

    fun updateIpAddress(ipAddress: String) {
        _uiState.value = _uiState.value.copy(ipAddress = ipAddress)
        viewModelScope.launch {
            repository.saveIpAddress(ipAddress)
        }
    }

    fun updatePort(port: String) {
        _uiState.value = _uiState.value.copy(port = port)
        viewModelScope.launch {
            repository.savePort(port)
        }
    }

    fun updateSymmetricKey(symmetricKey: String?) {
        _uiState.value = _uiState.value.copy(symmetricKey = symmetricKey)
    }

    fun updateManualPcName(name: String) {
        _uiState.value = _uiState.value.copy(manualPcName = name)
    }

    fun updateManualIsPlus(isPlus: Boolean) {
        _uiState.value = _uiState.value.copy(manualIsPlus = isPlus)
    }

    fun prepareForManualConnection() {
        val manualDevice = ConnectedDevice(
            name = _uiState.value.manualPcName.ifEmpty { "My Mac/PC" },
            ipAddress = _uiState.value.ipAddress,
            port = _uiState.value.port,
            lastConnected = System.currentTimeMillis(),
            isPlus = _uiState.value.manualIsPlus,
            symmetricKey = _uiState.value.symmetricKey
        )
        _uiState.value = _uiState.value.copy(
            lastConnectedDevice = manualDevice,
            isDialogVisible = true
        )
    }

    fun updateDeviceName(name: String) {
        _uiState.value = _uiState.value.copy(deviceNameInput = name)
        _deviceInfo.value = _deviceInfo.value.copy(name = name)
        viewModelScope.launch {
            repository.saveDeviceName(name)
        }
    }

    fun setLoading(loading: Boolean) {
        _uiState.value = _uiState.value.copy(isLoading = loading)
    }

    fun setResponse(response: String) {
        _uiState.value = _uiState.value.copy(response = response)
    }

    fun setDialogVisible(visible: Boolean) {
        _uiState.value = _uiState.value.copy(isDialogVisible = visible)
    }

    fun setPermissionDialogVisible(visible: Boolean) {
        _uiState.value = _uiState.value.copy(showPermissionDialog = visible)
    }

    fun setConnectionStatus(isConnected: Boolean, isConnecting: Boolean = false) {
        _uiState.value = _uiState.value.copy(
            isConnected = isConnected,
            isConnecting = isConnecting
        )
    }

    fun refreshPermissions(context: Context) {
        val missingPermissions = PermissionUtil.getAllMissingPermissions(context)
        val isNotificationEnabled = PermissionUtil.isNotificationListenerEnabled(context)
        _uiState.value = _uiState.value.copy(
            missingPermissions = missingPermissions,
            isNotificationEnabled = isNotificationEnabled
        )
    }

    fun saveLastConnectedDevice(pcName: String? = null, isPlus: Boolean = false, symmetricKey: String? = null) {
        viewModelScope.launch {
            val deviceName = pcName ?: "My Mac"
            val ourIp = _deviceInfo.value.localIp
            val clientIp = _uiState.value.ipAddress
            val port = _uiState.value.port

            // Save using network-aware storage
            repository.saveNetworkDeviceConnection(deviceName, ourIp, clientIp, port, isPlus, symmetricKey)

            // Also save to legacy storage for backwards compatibility
            val connectedDevice = ConnectedDevice(
                name = deviceName,
                ipAddress = clientIp,
                port = port,
                lastConnected = System.currentTimeMillis(),
                isPlus = isPlus,
                symmetricKey = symmetricKey
            )
            repository.saveLastConnectedDevice(connectedDevice)
            _uiState.value = _uiState.value.copy(lastConnectedDevice = connectedDevice)

            // Refresh network devices list
            loadNetworkDevices()
        }
    }

    private suspend fun loadNetworkDevices() {
        repository.getAllNetworkDeviceConnections().first().let { devices ->
            _networkDevices.value = devices
        }
    }

    fun getNetworkAwareLastConnectedDevice(): ConnectedDevice? {
        val ourIp = _deviceInfo.value.localIp
        if (ourIp.isEmpty() || ourIp == "Unknown") return null

        // Find the most recent device that has a connection for our current network
        val networkDevice = _networkDevices.value
            .filter { it.getClientIpForNetwork(ourIp) != null }
            .maxByOrNull { it.lastConnected }

        return networkDevice?.toConnectedDevice(ourIp)
    }

    fun setNotificationSyncEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isNotificationSyncEnabled = enabled)
        viewModelScope.launch {
            repository.setNotificationSyncEnabled(enabled)
        }
    }

    fun setDeveloperMode(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isDeveloperMode = enabled)
        viewModelScope.launch {
            repository.setDeveloperMode(enabled)
        }
    }

    fun toggleDeveloperModeVisibility() {
        _uiState.value = _uiState.value.copy(
            isDeveloperModeVisible = !_uiState.value.isDeveloperModeVisible
        )
    }

    fun setClipboardSyncEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isClipboardSyncEnabled = enabled)
        viewModelScope.launch {
            repository.setClipboardSyncEnabled(enabled)
        }
    }

    fun manualSyncAppIcons(context: Context) {
        _uiState.value = _uiState.value.copy(isIconSyncLoading = true, iconSyncMessage = "")

        SyncManager.manualSyncAppIcons(context) { success, message ->
            viewModelScope.launch {
                _uiState.value = _uiState.value.copy(
                    isIconSyncLoading = false,
                    iconSyncMessage = message
                )
            }
        }
    }

    fun clearIconSyncMessage() {
        _uiState.value = _uiState.value.copy(iconSyncMessage = "")
    }

    fun setUserManuallyDisconnected(disconnected: Boolean) {
        viewModelScope.launch {
            repository.setUserManuallyDisconnected(disconnected)
            if (disconnected) {
                cancelAutoReconnect()
            }
        }
    }

    fun setAutoReconnectEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(isAutoReconnectEnabled = enabled)
        viewModelScope.launch {
            repository.setAutoReconnectEnabled(enabled)
            if (!enabled) {
                cancelAutoReconnect()
            } else {
                appContext?.let { ctx ->
                    if (!_uiState.value.isConnected) {
                        maybeScheduleAutoReconnect(ctx)
                    }
                }
            }
        }
    }

    private fun cancelAutoReconnect() {
        autoReconnectJob?.cancel()
        autoReconnectJob = null
        autoReconnectStart = 0L
    }

    private fun hasNetworkAwareMappingForLastDevice(): ConnectedDevice? {
        val ourIp = _deviceInfo.value.localIp
        val last = _uiState.value.lastConnectedDevice ?: return null
        if (ourIp.isEmpty() || ourIp == "Unknown" || ourIp == "No Wi-Fi") return null
        // Find matching device by name with mapping for our IP
        val networkDevice = _networkDevices.value.firstOrNull { it.deviceName == last.name && it.getClientIpForNetwork(ourIp) != null }
        return networkDevice?.toConnectedDevice(ourIp)
    }

    fun maybeScheduleAutoReconnect(context: Context) {
        viewModelScope.launch {
            try {
                val autoEnabled = repository.getAutoReconnectEnabled().first()
                val manuallyDisconnected = repository.getUserManuallyDisconnected().first()
                if (!autoEnabled || manuallyDisconnected || WebSocketUtil.isConnected()) {
                    return@launch
                }

                // Ensure we have latest network devices
                loadNetworkDevices()

                val targetDevice = hasNetworkAwareMappingForLastDevice() ?: return@launch

                if (autoReconnectJob?.isActive == true) return@launch

                autoReconnectStart = System.currentTimeMillis()
                _uiState.value = _uiState.value.copy(response = "Will auto reconnect to ${targetDevice.name} if possible")

                autoReconnectJob = viewModelScope.launch {
                    while (coroutineContext.isActive) {
                        // Stop conditions
                        val autoStillEnabled = repository.getAutoReconnectEnabled().first()
                        val stillManual = repository.getUserManuallyDisconnected().first()
                        if (WebSocketUtil.isConnected() || !autoStillEnabled || stillManual) {
                            break
                        }

                        // Re-resolve mapping in case IP changed
                        val currentTarget = hasNetworkAwareMappingForLastDevice()
                        if (currentTarget == null) {
                            // No longer on a known network for last device; stop trying
                            break
                        }

                        // Delay according to elapsed time window
                        val elapsed = System.currentTimeMillis() - autoReconnectStart
                        val delayMs = if (elapsed <= 60_000L) 10_000L else 60_000L
                        delay(delayMs)

                        if (WebSocketUtil.isConnected()) continue

                        // Attempt connection
                        _uiState.value = _uiState.value.copy(isConnecting = true, response = "Auto reconnecting to ${currentTarget.name}...")
                        WebSocketUtil.connect(
                            context = context,
                            ipAddress = currentTarget.ipAddress,
                            port = currentTarget.port.toIntOrNull() ?: 6996,
                            symmetricKey = currentTarget.symmetricKey,
                            onConnectionStatus = { connected ->
                                viewModelScope.launch {
                                    _uiState.value = _uiState.value.copy(isConnecting = false)
                                    if (connected) {
                                        _uiState.value = _uiState.value.copy(response = "Auto-reconnected to ${currentTarget.name}")
                                        repository.updateNetworkDeviceLastConnected(currentTarget.name, System.currentTimeMillis())
                                        cancelAutoReconnect()
                                    } else {
                                        _uiState.value = _uiState.value.copy(response = "Auto-reconnect attempt failed")
                                    }
                                }
                            }
                        )
                    }
                }
            } catch (_: Exception) {
                // no-op
            }
        }
    }

    fun checkForUpdates(context: Context, showDialogOnUpdate: Boolean = true) {
        viewModelScope.launch {
            _updateStatus.value = UpdateStatus.CHECKING

            try {
                val updateInfo = UpdateManager.checkForUpdate(context)

                if (updateInfo != null) {
                    _updateInfo.value = updateInfo
                    _updateStatus.value = UpdateStatus.UPDATE_AVAILABLE

                    if (showDialogOnUpdate) {
                        _showUpdateDialog.value = true
                    }
                } else {
                    _updateStatus.value = UpdateStatus.NO_UPDATE

                    // Show "up to date" dialog when manually checking
                    if (showDialogOnUpdate) {
                        val currentVersion = UpdateManager.getCurrentVersionName(context)
                        _updateInfo.value = UpdateInfo(
                            release = com.sameerasw.airsync.domain.model.GitHubRelease(
                                tagName = "v$currentVersion",
                                name = "AirSync $currentVersion",
                                changelog = "You are running the latest version of AirSync!",
                                isPrerelease = currentVersion.contains("BETA", ignoreCase = true),
                                isDraft = false,
                                publishedAt = "",
                                assets = emptyList<com.sameerasw.airsync.domain.model.GitHubAsset>()
                            ),
                            asset = com.sameerasw.airsync.domain.model.GitHubAsset(
                                name = "current-version",
                                downloadUrl = "",
                                size = 0
                            ),
                            currentVersion = currentVersion,
                            newVersion = currentVersion,
                            isBetaUpdate = currentVersion.contains("BETA", ignoreCase = true),
                            downloadSize = ""
                        )
                        _showUpdateDialog.value = true
                    }
                }
            } catch (_: Exception) {
                _updateStatus.value = UpdateStatus.ERROR

                // Show error dialog when manually checking
                if (showDialogOnUpdate) {
                    val currentVersion = UpdateManager.getCurrentVersionName(context)
                    _updateInfo.value = UpdateInfo(
                        release = com.sameerasw.airsync.domain.model.GitHubRelease(
                            tagName = "v$currentVersion",
                            name = "Update Check Failed",
                            changelog = "Failed to check for updates. Please check your internet connection and try again.",
                            isPrerelease = false,
                            isDraft = false,
                            publishedAt = "",
                            assets = emptyList<com.sameerasw.airsync.domain.model.GitHubAsset>()
                        ),
                        asset = com.sameerasw.airsync.domain.model.GitHubAsset(
                            name = "error",
                            downloadUrl = "",
                            size = 0
                        ),
                        currentVersion = currentVersion,
                        newVersion = currentVersion,
                        isBetaUpdate = false,
                        downloadSize = ""
                    )
                    _showUpdateDialog.value = true
                }
            }
        }
    }

    fun downloadUpdate(context: Context) {
        val updateInfo = _updateInfo.value ?: return

        viewModelScope.launch {
            try {
                _updateStatus.value = UpdateStatus.DOWNLOADING
                _downloadProgress.value = 0

                val downloadId = UpdateManager.downloadUpdate(context, updateInfo)
                currentDownloadId = downloadId

                // Monitor download progress
                monitorDownloadProgress(context, downloadId)

            } catch (_: Exception) {
                _updateStatus.value = UpdateStatus.ERROR
            }
        }
    }

    private suspend fun monitorDownloadProgress(context: Context, downloadId: Long) {
        while (_updateStatus.value == UpdateStatus.DOWNLOADING) {
            delay(500)

            val progress = UpdateManager.getDownloadProgress(context, downloadId)
            _downloadProgress.value = progress

            if (UpdateManager.isDownloadComplete(context, downloadId)) {
                _updateStatus.value = UpdateStatus.DOWNLOADED
                break
            }
        }
    }

    fun dismissUpdateDialog() {
        _showUpdateDialog.value = false
        _updateStatus.value = UpdateStatus.NO_UPDATE
        _updateInfo.value = null
        currentDownloadId = null
    }

    fun startNetworkMonitoring(context: Context) {
        if (isNetworkMonitoringActive) return

        isNetworkMonitoringActive = true
        previousNetworkIp = _deviceInfo.value.localIp

        viewModelScope.launch {
            NetworkMonitor.observeNetworkChanges(context)
                .collect { networkInfo ->
                    handleNetworkChange(context, networkInfo)
                }
        }
    }

    private suspend fun handleNetworkChange(context: Context, networkInfo: NetworkInfo) {
        val currentIp = networkInfo.ipAddress

        // Update device info with new IP
        if (currentIp != null && currentIp != _deviceInfo.value.localIp) {
            _deviceInfo.value = _deviceInfo.value.copy(localIp = currentIp)

            // Load network devices to check for network-aware connections
            loadNetworkDevices()

            // Check if we have a network-aware device for this new network
            val networkAwareDevice = getNetworkAwareLastConnectedDevice()

            if (networkAwareDevice != null) {
                // Update the UI state with network-aware device info
                _uiState.value = _uiState.value.copy(
                    lastConnectedDevice = networkAwareDevice,
                    ipAddress = networkAwareDevice.ipAddress,
                    port = networkAwareDevice.port,
                    symmetricKey = networkAwareDevice.symmetricKey
                )

                // Save the new connection info
                repository.saveIpAddress(networkAwareDevice.ipAddress)
                repository.savePort(networkAwareDevice.port)

                // If we were connected and user didn't manually disconnect, try to reconnect
                val wasConnected = WebSocketUtil.isConnected()
                val userManuallyDisconnected = repository.getUserManuallyDisconnected().first()

                if (wasConnected && !userManuallyDisconnected) {
                    // Attempt automatic reconnection with network-aware device
                    attemptAutoReconnection(context, networkAwareDevice)
                }
            } else {
                // No network-aware device found, update last connected device info
                // with current network mapping if we have a previous connection
                _uiState.value.lastConnectedDevice?.let { lastDevice ->
                    // Save network mapping for future connections
                    repository.saveNetworkDeviceConnection(
                        lastDevice.name,
                        currentIp,
                        lastDevice.ipAddress,
                        lastDevice.port,
                        lastDevice.isPlus,
                        lastDevice.symmetricKey
                    )

                    // Reload network devices
                    loadNetworkDevices()
                }
            }

            // Update previous IP for next comparison
            previousNetworkIp = currentIp
        } else if (currentIp == null && networkInfo.isWifi) {
            // Wi-Fi connected but no IP yet, update device info
            _deviceInfo.value = _deviceInfo.value.copy(localIp = "Unknown")
        } else if (!networkInfo.isWifi) {
            // Not on Wi-Fi anymore
            _deviceInfo.value = _deviceInfo.value.copy(localIp = "No Wi-Fi")

            // Disconnect if connected
            if (WebSocketUtil.isConnected()) {
                WebSocketUtil.disconnect()
                setConnectionStatus(isConnected = false, isConnecting = false)
                setResponse("Disconnected - Wi-Fi lost")
            }
        }
    }

    private suspend fun attemptAutoReconnection(context: Context, device: ConnectedDevice) {
        // Add a small delay to ensure network is stable
        delay(2000)

        // Check if we're still on the same network and not manually disconnected
        val currentIp = DeviceInfoUtil.getWifiIpAddress(context)
        val userManuallyDisconnected = repository.getUserManuallyDisconnected().first()

        if (currentIp != null && !userManuallyDisconnected && !WebSocketUtil.isConnected()) {
            setConnectionStatus(isConnected = false, isConnecting = true)
            setResponse("Auto-reconnecting to ${device.name}...")

            WebSocketUtil.connect(
                context = context,
                ipAddress = device.ipAddress,
                port = device.port.toIntOrNull() ?: 6996,
                symmetricKey = device.symmetricKey,
                onConnectionStatus = { connected ->
                    viewModelScope.launch {
                        setConnectionStatus(isConnected = connected, isConnecting = false)
                        if (connected) {
                            setResponse("Auto-reconnected to ${device.name}")
                            // Update last connected timestamp
                            repository.updateNetworkDeviceLastConnected(device.name, System.currentTimeMillis())
                        } else {
                            setResponse("Auto-reconnection failed")
                        }
                    }
                },
                onMessage = { response ->
                    viewModelScope.launch {
                        setResponse("Received: $response")
                        // Handle clipboard updates and other messages as usual
                        try {
                            val json = org.json.JSONObject(response)
                            if (json.optString("type") == "clipboardUpdate") {
                                val data = json.optJSONObject("data")
                                val text = data?.optString("text")
                                if (!text.isNullOrEmpty()) {
                                    com.sameerasw.airsync.utils.ClipboardSyncManager.handleClipboardUpdate(context, text)
                                }
                            }
                        } catch (_: Exception) {
                            // Not a clipboard update, ignore
                        }
                    }
                }
            )
        }
    }
}
