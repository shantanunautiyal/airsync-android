package com.sameerasw.airsync.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sameerasw.airsync.domain.model.ConnectedDevice
import com.sameerasw.airsync.domain.model.NotificationApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "airsync_settings")

class DataStoreManager(private val context: Context) {

    companion object {
        private val IP_ADDRESS = stringPreferencesKey("ip_address")
        private val PORT = stringPreferencesKey("port")
        private val DEVICE_NAME = stringPreferencesKey("device_name")
        private val CUSTOM_MESSAGE = stringPreferencesKey("custom_message")
        private val FIRST_RUN = booleanPreferencesKey("first_run")
        private val PERMISSIONS_CHECKED = booleanPreferencesKey("permissions_checked")
        private val LAST_CONNECTED_PC_NAME = stringPreferencesKey("last_connected_pc_name")
        private val LAST_CONNECTED_PC_IP = stringPreferencesKey("last_connected_pc_ip")
        private val LAST_CONNECTED_PC_PORT = stringPreferencesKey("last_connected_pc_port")
        private val LAST_CONNECTED_TIMESTAMP = stringPreferencesKey("last_connected_timestamp")
        private val LAST_SYNC_TIME = stringPreferencesKey("last_sync_time")
        private val NOTIFICATION_SYNC_ENABLED = booleanPreferencesKey("notification_sync_enabled")
    }

    suspend fun saveIpAddress(ipAddress: String) {
        context.dataStore.edit { preferences ->
            preferences[IP_ADDRESS] = ipAddress
        }
    }

    fun getIpAddress(): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[IP_ADDRESS] ?: "192.168.1.100"
        }
    }

    suspend fun savePort(port: String) {
        context.dataStore.edit { preferences ->
            preferences[PORT] = port
        }
    }

    fun getPort(): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[PORT] ?: "6996"
        }
    }

    suspend fun saveDeviceName(deviceName: String) {
        context.dataStore.edit { preferences ->
            preferences[DEVICE_NAME] = deviceName
        }
    }

    fun getDeviceName(): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[DEVICE_NAME] ?: ""
        }
    }

    suspend fun saveCustomMessage(message: String) {
        context.dataStore.edit { preferences ->
            preferences[CUSTOM_MESSAGE] = message
        }
    }

    fun getCustomMessage(): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[CUSTOM_MESSAGE] ?: """{"type":"notification","data":{"title":"Test","body":"Hello!","app":"WhatsApp"}}"""
        }
    }

    suspend fun setFirstRun(isFirstRun: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[FIRST_RUN] = isFirstRun
        }
    }

    fun getFirstRun(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[FIRST_RUN] ?: true
        }
    }

    suspend fun setPermissionsChecked(checked: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PERMISSIONS_CHECKED] = checked
        }
    }

    fun getPermissionsChecked(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[PERMISSIONS_CHECKED] ?: false
        }
    }

    suspend fun setNotificationSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[NOTIFICATION_SYNC_ENABLED] = enabled
        }
    }

    fun getNotificationSyncEnabled(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[NOTIFICATION_SYNC_ENABLED] ?: true // Default to enabled
        }
    }

    suspend fun saveLastConnectedDevice(device: ConnectedDevice) {
        context.dataStore.edit { preferences ->
            preferences[LAST_CONNECTED_PC_NAME] = device.name
            preferences[LAST_CONNECTED_PC_IP] = device.ipAddress
            preferences[LAST_CONNECTED_PC_PORT] = device.port
            preferences[LAST_CONNECTED_TIMESTAMP] = device.lastConnected.toString()
            device.lastSyncTime?.let {
                preferences[LAST_SYNC_TIME] = it.toString()
            }
        }
    }

    fun getLastConnectedDevice(): Flow<ConnectedDevice?> {
        return context.dataStore.data.map { preferences ->
            val name = preferences[LAST_CONNECTED_PC_NAME]
            val ip = preferences[LAST_CONNECTED_PC_IP]
            val port = preferences[LAST_CONNECTED_PC_PORT]
            val timestamp = preferences[LAST_CONNECTED_TIMESTAMP]
            val lastSyncTime = preferences[LAST_SYNC_TIME]?.toLongOrNull()

            if (name != null && ip != null && port != null && timestamp != null) {
                ConnectedDevice(
                    name = name,
                    ipAddress = ip,
                    port = port,
                    lastConnected = timestamp.toLongOrNull() ?: 0L,
                    lastSyncTime = lastSyncTime
                )
            } else {
                null
            }
        }
    }

    suspend fun updateLastSyncTime(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[LAST_SYNC_TIME] = timestamp.toString()
        }
    }

    fun getLastSyncTime(): Flow<Long?> {
        return context.dataStore.data.map { preferences ->
            preferences[LAST_SYNC_TIME]?.toLongOrNull()
        }
    }

    suspend fun saveNotificationApps(apps: List<NotificationApp>) {
        context.dataStore.edit { preferences ->
            // Clear existing app preferences
            val keysToRemove = preferences.asMap().keys.filter { it.name.startsWith("app_") }
            keysToRemove.forEach { preferences.remove(it) }

            // Save new app preferences
            apps.forEach { app ->
                val enabledKey = booleanPreferencesKey("app_${app.packageName}_enabled")
                val nameKey = stringPreferencesKey("app_${app.packageName}_name")
                val systemKey = booleanPreferencesKey("app_${app.packageName}_system")
                val updatedKey = stringPreferencesKey("app_${app.packageName}_updated")

                preferences[enabledKey] = app.isEnabled
                preferences[nameKey] = app.appName
                preferences[systemKey] = app.isSystemApp
                preferences[updatedKey] = app.lastUpdated.toString()
            }
        }
    }

    fun getNotificationApps(): Flow<List<NotificationApp>> {
        return context.dataStore.data.map { preferences ->
            val apps = mutableListOf<NotificationApp>()
            val packageNames = mutableSetOf<String>()

            // Extract package names from preference keys
            preferences.asMap().keys.forEach { key ->
                if (key.name.startsWith("app_") && key.name.endsWith("_enabled")) {
                    val packageName = key.name.removePrefix("app_").removeSuffix("_enabled")
                    packageNames.add(packageName)
                }
            }

            // Build app objects from preferences
            packageNames.forEach { packageName ->
                val enabledKey = booleanPreferencesKey("app_${packageName}_enabled")
                val nameKey = stringPreferencesKey("app_${packageName}_name")
                val systemKey = booleanPreferencesKey("app_${packageName}_system")
                val updatedKey = stringPreferencesKey("app_${packageName}_updated")

                val isEnabled = preferences[enabledKey] ?: true
                val appName = preferences[nameKey] ?: packageName
                val isSystemApp = preferences[systemKey] ?: false
                val lastUpdated = preferences[updatedKey]?.toLongOrNull() ?: 0L

                apps.add(
                    NotificationApp(
                        packageName = packageName,
                        appName = appName,
                        isEnabled = isEnabled,
                        isSystemApp = isSystemApp,
                        lastUpdated = lastUpdated
                    )
                )
            }

            apps.sortedBy { it.appName }
        }
    }
}
