package com.sameerasw.airsync.domain.model

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
    val lastConnectedDevice: ConnectedDevice? = null,
    val isNotificationSyncEnabled: Boolean = true
)

data class DeviceInfo(
    val name: String = "",
    val localIp: String = ""
)

data class ConnectedDevice(
    val name: String,
    val ipAddress: String,
    val port: String,
    val lastConnected: Long
)

data class BatteryInfo(
    val level: Int,
    val isCharging: Boolean
)

data class AudioInfo(
    val isPlaying: Boolean,
    val title: String,
    val artist: String,
    val volume: Int,
    val isMuted: Boolean
)

data class MediaInfo(
    val isPlaying: Boolean,
    val title: String,
    val artist: String
)
