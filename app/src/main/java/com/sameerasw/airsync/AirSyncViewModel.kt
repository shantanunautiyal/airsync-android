package com.sameerasw.airsync

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AirSyncViewModel : ViewModel() {

    // UI State
    private val _uiState = MutableStateFlow(AirSyncUiState())
    val uiState: StateFlow<AirSyncUiState> = _uiState.asStateFlow()

    // Device info
    private val _deviceInfo = MutableStateFlow(DeviceInfo())
    val deviceInfo: StateFlow<DeviceInfo> = _deviceInfo.asStateFlow()

    fun initializeState(context: Context, initialIp: String?, initialPort: String?, showConnectionDialog: Boolean) {
        viewModelScope.launch {
            try {
                // Load saved data from DataStore
                val savedIp = DataStoreUtil.getIpAddress(context).first()
                val savedPort = DataStoreUtil.getPort(context).first()
                val savedDeviceName = DataStoreUtil.getDeviceName(context).first()
                val savedCustomMessage = DataStoreUtil.getCustomMessage(context).first()
                val isFirstRun = DataStoreUtil.getFirstRun(context).first()
                val permissionsChecked = DataStoreUtil.getPermissionsChecked(context).first()

                // Get device information
                val deviceName = if (savedDeviceName.isEmpty()) {
                    DeviceInfoUtil.getDeviceName(context)
                } else {
                    savedDeviceName
                }
                val localIp = DeviceInfoUtil.getWifiIpAddress(context) ?: "Unknown"

                // Update device info
                _deviceInfo.value = DeviceInfo(
                    name = deviceName,
                    localIp = localIp
                )

                // Use initial values from QR scan if provided, otherwise use saved values
                val ipToUse = initialIp ?: savedIp
                val portToUse = initialPort ?: savedPort

                // Update UI state
                _uiState.value = _uiState.value.copy(
                    ipAddress = ipToUse,
                    port = portToUse,
                    deviceNameInput = deviceName,
                    customMessage = savedCustomMessage,
                    isDialogVisible = showConnectionDialog,
                    showPermissionDialog = !permissionsChecked && isFirstRun,
                    isNotificationEnabled = PermissionUtil.isNotificationListenerEnabled(context),
                    missingPermissions = PermissionUtil.getAllMissingPermissions(context)
                )

                // Save the current values if they came from QR scan
                if (initialIp != null) {
                    DataStoreUtil.saveIpAddress(context, ipToUse)
                }
                if (initialPort != null) {
                    DataStoreUtil.savePort(context, portToUse)
                }
                if (savedDeviceName.isEmpty()) {
                    DataStoreUtil.saveDeviceName(context, deviceName)
                }

                // Mark as not first run anymore
                if (isFirstRun) {
                    DataStoreUtil.setFirstRun(context, false)
                }

            } catch (e: Exception) {
                // Handle initialization error
                _uiState.value = _uiState.value.copy(
                    response = "Error initializing app: ${e.message}"
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

    fun setLoading(isLoading: Boolean) {
        _uiState.value = _uiState.value.copy(isLoading = isLoading)
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
        val isNotificationEnabled = PermissionUtil.isNotificationListenerEnabled(context)
        val missingPermissions = PermissionUtil.getAllMissingPermissions(context)

        _uiState.value = _uiState.value.copy(
            isNotificationEnabled = isNotificationEnabled,
            missingPermissions = missingPermissions
        )

        // Save that permissions have been checked
        viewModelScope.launch {
            DataStoreUtil.setPermissionsChecked(context, true)
        }
    }

    fun refreshDeviceInfo(context: Context) {
        val localIp = DeviceInfoUtil.getWifiIpAddress(context) ?: "Unknown"
        _deviceInfo.value = _deviceInfo.value.copy(localIp = localIp)
    }
}

data class AirSyncUiState(
    val ipAddress: String = "192.168.1.100",
    val port: String = "6996",
    val deviceNameInput: String = "",
    val customMessage: String = """{"type":"notification","data":{"title":"Test","body":"Hello!","app":"WhatsApp"}}""",
    val response: String = "",
    val isLoading: Boolean = false,
    val isDialogVisible: Boolean = false,
    val showPermissionDialog: Boolean = false,
    val isNotificationEnabled: Boolean = false,
    val missingPermissions: List<String> = emptyList()
)

data class DeviceInfo(
    val name: String = "",
    val localIp: String = "Unknown"
)
