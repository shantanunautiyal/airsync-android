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
import com.sameerasw.airsync.domain.model.UpdateStatus
import com.sameerasw.airsync.domain.repository.AirSyncRepository
import com.sameerasw.airsync.utils.DeviceInfoUtil
import com.sameerasw.airsync.utils.PermissionUtil
import com.sameerasw.airsync.utils.SyncManager
import com.sameerasw.airsync.utils.UpdateManager
import com.sameerasw.airsync.utils.WebSocketUtil
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

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
    val networkDevices: StateFlow<List<NetworkDeviceConnection>> = _networkDevices.asStateFlow()

    // Connection status listener for WebSocket updates
    private val connectionStatusListener: (Boolean) -> Unit = { isConnected ->
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isConnected = isConnected,
                isConnecting = false,
                response = if (isConnected) "Connected successfully!" else "Disconnected"
            )
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
        isPlus: Boolean = false
    ) {
        viewModelScope.launch {
            // Load saved values
            val savedIp = initialIp ?: repository.getIpAddress().first()
            val savedPort = initialPort ?: repository.getPort().first()
            val savedDeviceName = repository.getDeviceName().first()
            val lastConnected = repository.getLastConnectedDevice().first()
            val isNotificationSyncEnabled = repository.getNotificationSyncEnabled().first()
            val isDeveloperMode = repository.getDeveloperMode().first()
            val isClipboardSyncEnabled = repository.getClipboardSyncEnabled().first()

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
                isConnected = currentlyConnected
            )

            // If we have PC name from QR code and not already connected, store it temporarily for the dialog
            if (pcName != null && showConnectionDialog && !currentlyConnected) {
                _uiState.value = _uiState.value.copy(
                    lastConnectedDevice = ConnectedDevice(
                        name = pcName,
                        ipAddress = savedIp,
                        port = savedPort,
                        lastConnected = System.currentTimeMillis(),
                        isPlus = isPlus
                    )
                )
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

    fun refreshDeviceInfo(context: Context) {
        val localIp = DeviceInfoUtil.getWifiIpAddress(context) ?: "Unknown"
        _deviceInfo.value = _deviceInfo.value.copy(localIp = localIp)
    }

    fun saveLastConnectedDevice(pcName: String? = null, isPlus: Boolean = false) {
        viewModelScope.launch {
            val deviceName = pcName ?: "My Mac"
            val ourIp = _deviceInfo.value.localIp
            val clientIp = _uiState.value.ipAddress
            val port = _uiState.value.port

            // Save using network-aware storage
            repository.saveNetworkDeviceConnection(deviceName, ourIp, clientIp, port, isPlus)

            // Also save to legacy storage for backwards compatibility
            val connectedDevice = ConnectedDevice(
                name = deviceName,
                ipAddress = clientIp,
                port = port,
                lastConnected = System.currentTimeMillis(),
                isPlus = isPlus
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

    fun connectToNetworkDevice(deviceName: String) {
        viewModelScope.launch {
            val ourIp = _deviceInfo.value.localIp
            if (ourIp.isEmpty() || ourIp == "Unknown") return@launch

            val networkDevice = repository.getNetworkDeviceConnection(deviceName).first()
            val clientIp = networkDevice?.getClientIpForNetwork(ourIp)

            if (clientIp != null && networkDevice != null) {
                // Update IP and port in UI state
                updateIpAddress(clientIp)
                updatePort(networkDevice.port)

                // Update last connected timestamp
                repository.updateNetworkDeviceLastConnected(deviceName, System.currentTimeMillis())
                loadNetworkDevices()
            }
        }
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
                                assets = emptyList()
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
            } catch (e: Exception) {
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
                            assets = emptyList()
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

            } catch (e: Exception) {
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

    fun retryDownload(context: Context) {
        _updateStatus.value = UpdateStatus.UPDATE_AVAILABLE
        downloadUpdate(context)
    }
}
