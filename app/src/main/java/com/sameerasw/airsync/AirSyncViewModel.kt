package com.sameerasw.airsync

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class UiState(
    val ipAddress: String = "",
    val port: String = "",
    val deviceNameInput: String = "",
    val customMessage: String = "",
    val isLoading: Boolean = false,
    val response: String = "",
    val isDialogVisible: Boolean = false,
    val showPermissionDialog: Boolean = false,
    val missingPermissions: List<String> = emptyList(),
    val isNotificationEnabled: Boolean = false,
    val lastConnectedDevice: ConnectedDevice? = null
)

data class DeviceInfo(
    val name: String = "",
    val localIp: String = ""
)

class AirSyncViewModel : ViewModel() {
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
            val savedIp = if (initialIp != null) initialIp else DataStoreUtil.getIpAddress(context).first()
            val savedPort = if (initialPort != null) initialPort else DataStoreUtil.getPort(context).first()
            val savedDeviceName = DataStoreUtil.getDeviceName(context).first()
            val savedCustomMessage = DataStoreUtil.getCustomMessage(context).first()
            val lastConnected = DataStoreUtil.getLastConnectedDevice(context).first()

            // Get device info
            val deviceName = if (savedDeviceName.isEmpty()) {
                val autoName = DeviceInfoUtil.getDeviceName(context)
                DataStoreUtil.saveDeviceName(context, autoName)
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
                customMessage = savedCustomMessage,
                isDialogVisible = showConnectionDialog,
                missingPermissions = missingPermissions,
                isNotificationEnabled = isNotificationEnabled,
                lastConnectedDevice = lastConnected
            )

            // If we have PC name from QR code, store it temporarily for the dialog
            if (pcName != null && showConnectionDialog) {
                //  pass to the dialog through the UI state
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

    fun updateIpAddress(context: Context, ip: String) {
        _uiState.value = _uiState.value.copy(ipAddress = ip)
        viewModelScope.launch {
            DataStoreUtil.saveIpAddress(context, ip)
        }
    }

    fun updatePort(context: Context, port: String) {
        _uiState.value = _uiState.value.copy(port = port)
        viewModelScope.launch {
            DataStoreUtil.savePort(context, port)
        }
    }

    fun updateDeviceName(context: Context, name: String) {
        _uiState.value = _uiState.value.copy(deviceNameInput = name)
        _deviceInfo.value = _deviceInfo.value.copy(name = name)
        viewModelScope.launch {
            DataStoreUtil.saveDeviceName(context, name)
        }
    }

    fun updateCustomMessage(context: Context, message: String) {
        _uiState.value = _uiState.value.copy(customMessage = message)
        viewModelScope.launch {
            DataStoreUtil.saveCustomMessage(context, message)
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

    fun saveLastConnectedDevice(context: Context, pcName: String? = null) {
        viewModelScope.launch {
            val connectedDevice = ConnectedDevice(
                name = pcName ?: "Desktop PC",
                ipAddress = _uiState.value.ipAddress,
                port = _uiState.value.port,
                lastConnected = System.currentTimeMillis()
            )
            DataStoreUtil.saveLastConnectedDevice(context, connectedDevice)
            _uiState.value = _uiState.value.copy(lastConnectedDevice = connectedDevice)
        }
    }
}
