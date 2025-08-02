package com.sameerasw.airsync.domain.model

import android.graphics.drawable.Drawable

data class UiState(
    val ipAddress: String = "",
    val port: String = "",
    val deviceNameInput: String = "",
    val isLoading: Boolean = false,
    val response: String = "",
    val isDialogVisible: Boolean = false,
    val showPermissionDialog: Boolean = false,
    val missingPermissions: List<String> = emptyList(),
    val isNotificationEnabled: Boolean = false,
    val lastConnectedDevice: ConnectedDevice? = null,
    val isNotificationSyncEnabled: Boolean = true,
    val isDeveloperMode: Boolean = false,
    val isConnected: Boolean = false,
    val isConnecting: Boolean = false,
    val isClipboardSyncEnabled: Boolean = true,
    val isIconSyncLoading: Boolean = false,
    val iconSyncMessage: String = ""
)

data class DeviceInfo(
    val name: String = "",
    val localIp: String = ""
)

data class ConnectedDevice(
    val name: String,
    val ipAddress: String,
    val port: String,
    val lastConnected: Long,
    val lastSyncTime: Long? = null,
    val isPlus: Boolean = false,
    val iconSyncCount: Int = 0,
    val lastIconSyncDate: String? = null // Format: YYYY-MM-DD
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

data class NotificationApp(
    val packageName: String,
    val appName: String,
    val isEnabled: Boolean = true,
    val icon: Drawable? = null,
    val isSystemApp: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)
