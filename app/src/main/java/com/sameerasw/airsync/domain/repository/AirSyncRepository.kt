package com.sameerasw.airsync.domain.repository

import com.sameerasw.airsync.domain.model.ConnectedDevice
import com.sameerasw.airsync.domain.model.NotificationApp
import kotlinx.coroutines.flow.Flow

interface AirSyncRepository {
    suspend fun saveIpAddress(ipAddress: String)
    fun getIpAddress(): Flow<String>

    suspend fun savePort(port: String)
    fun getPort(): Flow<String>

    suspend fun saveDeviceName(deviceName: String)
    fun getDeviceName(): Flow<String>

    suspend fun setFirstRun(isFirstRun: Boolean)
    fun getFirstRun(): Flow<Boolean>

    suspend fun setPermissionsChecked(checked: Boolean)
    fun getPermissionsChecked(): Flow<Boolean>

    suspend fun setNotificationSyncEnabled(enabled: Boolean)
    fun getNotificationSyncEnabled(): Flow<Boolean>

    suspend fun setDeveloperMode(enabled: Boolean)
    fun getDeveloperMode(): Flow<Boolean>

    suspend fun saveLastConnectedDevice(device: ConnectedDevice)
    fun getLastConnectedDevice(): Flow<ConnectedDevice?>

    // App notification preferences
    suspend fun saveNotificationApps(apps: List<NotificationApp>)
    fun getNotificationApps(): Flow<List<NotificationApp>>

    // Last sync time tracking
    suspend fun updateLastSyncTime(timestamp: Long)
    fun getLastSyncTime(): Flow<Long?>
}
