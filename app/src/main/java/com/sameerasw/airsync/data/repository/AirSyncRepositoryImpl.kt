package com.sameerasw.airsync.data.repository

import com.sameerasw.airsync.data.local.DataStoreManager
import com.sameerasw.airsync.domain.model.ConnectedDevice
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

    override suspend fun saveCustomMessage(message: String) {
        dataStoreManager.saveCustomMessage(message)
    }

    override fun getCustomMessage(): Flow<String> {
        return dataStoreManager.getCustomMessage()
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

    override suspend fun saveLastConnectedDevice(device: ConnectedDevice) {
        dataStoreManager.saveLastConnectedDevice(device)
    }

    override fun getLastConnectedDevice(): Flow<ConnectedDevice?> {
        return dataStoreManager.getLastConnectedDevice()
    }
}
