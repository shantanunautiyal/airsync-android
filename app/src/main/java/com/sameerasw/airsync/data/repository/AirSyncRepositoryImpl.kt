package com.sameerasw.airsync.data.repository

import com.sameerasw.airsync.data.local.DataStoreManager
import com.sameerasw.airsync.domain.model.ConnectedDevice
import com.sameerasw.airsync.domain.model.NotificationApp
import com.sameerasw.airsync.domain.repository.AirSyncRepository
import kotlinx.coroutines.flow.Flow

class AirSyncRepositoryImpl(
    private val dataStoreManager: DataStoreManager
) : AirSyncRepository {

    override suspend fun saveIpAddress(ipAddress: String) {
        dataStoreManager.saveIpAddress(ipAddress)
    }

    override fun getIpAddress(): Flow<String> {
        return dataStoreManager.getIpAddress()
    }

    override suspend fun savePort(port: String) {
        dataStoreManager.savePort(port)
    }

    override fun getPort(): Flow<String> {
        return dataStoreManager.getPort()
    }

    override suspend fun saveDeviceName(deviceName: String) {
        dataStoreManager.saveDeviceName(deviceName)
    }

    override fun getDeviceName(): Flow<String> {
        return dataStoreManager.getDeviceName()
    }

    override suspend fun saveAdbPort(adbPort: String) {
        dataStoreManager.saveAdbPort(adbPort)
    }

    override fun getAdbPort(): Flow<String> {
        return dataStoreManager.getAdbPort()
    }

    override suspend fun setFirstRun(isFirstRun: Boolean) {
        dataStoreManager.setFirstRun(isFirstRun)
    }

    override fun getFirstRun(): Flow<Boolean> {
        return dataStoreManager.getFirstRun()
    }

    override suspend fun setPermissionsChecked(checked: Boolean) {
        dataStoreManager.setPermissionsChecked(checked)
    }

    override fun getPermissionsChecked(): Flow<Boolean> {
        return dataStoreManager.getPermissionsChecked()
    }

    override suspend fun setNotificationSyncEnabled(enabled: Boolean) {
        dataStoreManager.setNotificationSyncEnabled(enabled)
    }

    override fun getNotificationSyncEnabled(): Flow<Boolean> {
        return dataStoreManager.getNotificationSyncEnabled()
    }

    override suspend fun setDeveloperMode(enabled: Boolean) {
        dataStoreManager.setDeveloperMode(enabled)
    }

    override fun getDeveloperMode(): Flow<Boolean> {
        return dataStoreManager.getDeveloperMode()
    }

    override suspend fun saveLastConnectedDevice(device: ConnectedDevice) {
        dataStoreManager.saveLastConnectedDevice(device)
    }

    override fun getLastConnectedDevice(): Flow<ConnectedDevice?> {
        return dataStoreManager.getLastConnectedDevice()
    }

    override suspend fun saveNotificationApps(apps: List<NotificationApp>) {
        dataStoreManager.saveNotificationApps(apps)
    }

    override fun getNotificationApps(): Flow<List<NotificationApp>> {
        return dataStoreManager.getNotificationApps()
    }

    override suspend fun updateLastSyncTime(timestamp: Long) {
        dataStoreManager.updateLastSyncTime(timestamp)
    }

    override fun getLastSyncTime(): Flow<Long?> {
        return dataStoreManager.getLastSyncTime()
    }

    override suspend fun setClipboardSyncEnabled(enabled: Boolean) {
        dataStoreManager.setClipboardSyncEnabled(enabled)
    }

    override fun getClipboardSyncEnabled(): Flow<Boolean> {
        return dataStoreManager.getClipboardSyncEnabled()
    }
}
