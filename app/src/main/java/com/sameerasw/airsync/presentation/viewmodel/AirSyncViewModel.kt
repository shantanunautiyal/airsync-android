package com.sameerasw.airsync.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sameerasw.airsync.data.local.DataStoreManager
import com.sameerasw.airsync.data.repository.AirSyncRepositoryImpl
import com.sameerasw.airsync.domain.model.ConnectedDevice
import com.sameerasw.airsync.domain.model.DeviceInfo
import com.sameerasw.airsync.domain.model.UiState
import com.sameerasw.airsync.domain.repository.AirSyncRepository
import com.sameerasw.airsync.utils.DeviceInfoUtil
import com.sameerasw.airsync.utils.PermissionUtil
import com.sameerasw.airsync.utils.WebSocketUtil
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

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

    fun initializeState(
        context: Context,
        initialIp: String? = null,
        initialPort: String? = null,
        showConnectionDialog: Boolean = false,
        pcName: String? = null
    ) {
        viewModelScope.launch {
            // Load saved values
            val savedIp = if (initialIp != null) initialIp else repository.getIpAddress().first()
            val savedPort = if (initialPort != null) initialPort else repository.getPort().first()
            val savedDeviceName = repository.getDeviceName().first()
            val lastConnected = repository.getLastConnectedDevice().first()
            val isNotificationSyncEnabled = repository.getNotificationSyncEnabled().first()
            val isDeveloperMode = repository.getDeveloperMode().first()

            // Get device info
            val deviceName = if (savedDeviceName.isEmpty()) {
                val autoName = DeviceInfoUtil.getDeviceName(context)
                repository.saveDeviceName(autoName)
                autoName
            } else savedDeviceName

            val localIp = DeviceInfoUtil.getWifiIpAddress(context) ?: "Unknown"

            _deviceInfo.value = DeviceInfo(name = deviceName, localIp = localIp)

            // Check permissions
            val missingPermissions = PermissionUtil.getAllMissingPermissions(context)
            val isNotificationEnabled = PermissionUtil.isNotificationListenerEnabled(context)

            _uiState.value = _uiState.value.copy(
                ipAddress = savedIp,
                port = savedPort,
                deviceNameInput = deviceName,
                isDialogVisible = showConnectionDialog,
                missingPermissions = missingPermissions,
                isNotificationEnabled = isNotificationEnabled,
                lastConnectedDevice = lastConnected,
                isNotificationSyncEnabled = isNotificationSyncEnabled,
                isDeveloperMode = isDeveloperMode,
                isConnected = WebSocketUtil.isConnected()
            )

            // If we have PC name from QR code, store it temporarily for the dialog
            if (pcName != null && showConnectionDialog) {
                _uiState.value = _uiState.value.copy(
                    lastConnectedDevice = ConnectedDevice(
                        name = pcName,
                        ipAddress = savedIp,
                        port = savedPort,
                        lastConnected = System.currentTimeMillis()
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

    fun saveLastConnectedDevice(pcName: String? = null) {
        viewModelScope.launch {
            val connectedDevice = ConnectedDevice(
                name = pcName ?: "Desktop PC",
                ipAddress = _uiState.value.ipAddress,
                port = _uiState.value.port,
                lastConnected = System.currentTimeMillis()
            )
            repository.saveLastConnectedDevice(connectedDevice)
            _uiState.value = _uiState.value.copy(lastConnectedDevice = connectedDevice)
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
}
