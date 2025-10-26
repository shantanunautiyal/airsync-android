package com.sameerasw.airsync.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sameerasw.airsync.domain.model.ConnectedDevice
import com.sameerasw.airsync.domain.model.NetworkDeviceConnection
import com.sameerasw.airsync.domain.model.NotificationApp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "airsync_settings")

class DataStoreManager(private val context: Context) {

    companion object {
        private val IP_ADDRESS = stringPreferencesKey("ip_address")
        private val PORT = stringPreferencesKey("port")
        private val DEVICE_NAME = stringPreferencesKey("device_name")
        private val FIRST_RUN = booleanPreferencesKey("first_run")
        private val PERMISSIONS_CHECKED = booleanPreferencesKey("permissions_checked")
        private val LAST_CONNECTED_PC_NAME = stringPreferencesKey("last_connected_pc_name")
        private val LAST_CONNECTED_PC_IP = stringPreferencesKey("last_connected_pc_ip")
        private val LAST_CONNECTED_PC_PORT = stringPreferencesKey("last_connected_pc_port")
        private val LAST_CONNECTED_TIMESTAMP = stringPreferencesKey("last_connected_timestamp")
        private val LAST_CONNECTED_PC_PLUS = booleanPreferencesKey("last_connected_pc_plus")
        private val LAST_CONNECTED_SYMMETRIC_KEY = stringPreferencesKey("last_connected_symmetric_key")
        private val LAST_CONNECTED_MODEL = stringPreferencesKey("last_connected_model")
        private val LAST_CONNECTED_DEVICE_TYPE = stringPreferencesKey("last_connected_device_type")
    private val LAST_SYNC_TIME = stringPreferencesKey("last_sync_time")
    // Widget-visible Mac status keys
    private val MAC_BATTERY_LEVEL = intPreferencesKey("mac_battery_level")
    private val MAC_BATTERY_CHARGING = booleanPreferencesKey("mac_battery_charging")
    private val MAC_MUSIC_TITLE = stringPreferencesKey("mac_music_title")
    private val MAC_MUSIC_ARTIST = stringPreferencesKey("mac_music_artist")
    private val MAC_WIDGET_REFRESH_AT = longPreferencesKey("mac_widget_refresh_at")
        private val NOTIFICATION_SYNC_ENABLED = booleanPreferencesKey("notification_sync_enabled")
        private val DEVELOPER_MODE = booleanPreferencesKey("developer_mode")
        private val CLIPBOARD_SYNC_ENABLED = booleanPreferencesKey("clipboard_sync_enabled")
        private val ICON_SYNC_COUNT = stringPreferencesKey("icon_sync_count")
        private val LAST_ICON_SYNC_DATE = stringPreferencesKey("last_icon_sync_date")
    private val USER_MANUALLY_DISCONNECTED = booleanPreferencesKey("user_manually_disconnected")
        // Auto reconnect toggle
        private val AUTO_RECONNECT_ENABLED = booleanPreferencesKey("auto_reconnect_enabled")
        // New: Continue Browsing feature toggle
        private val CONTINUE_BROWSING_ENABLED = booleanPreferencesKey("continue_browsing_enabled")
        // New: Send now playing toggle
        private val SEND_NOW_PLAYING_ENABLED = booleanPreferencesKey("send_now_playing_enabled")
        // New: Keep previous link toggle
        private val KEEP_PREVIOUS_LINK_ENABLED = booleanPreferencesKey("keep_previous_link_enabled")
        private val EXPAND_NETWORKING_ENABLED = booleanPreferencesKey("expand_networking_enabled")

        // Network-aware device connections
        private val NETWORK_DEVICES_PREFIX = "network_device_"
        private val NETWORK_CONNECTIONS_PREFIX = "network_connections_"
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

    suspend fun setFirstRun(isFirstRun: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[FIRST_RUN] = isFirstRun
        }
    }

    fun getFirstRun(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[FIRST_RUN] != false
        }
    }

    suspend fun setPermissionsChecked(checked: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PERMISSIONS_CHECKED] = checked
        }
    }

    fun getPermissionsChecked(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[PERMISSIONS_CHECKED] == true
        }
    }

    suspend fun setNotificationSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[NOTIFICATION_SYNC_ENABLED] = enabled
        }
    }

    fun getNotificationSyncEnabled(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[NOTIFICATION_SYNC_ENABLED] != false // Default to enabled
        }
    }

    suspend fun setClipboardSyncEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[CLIPBOARD_SYNC_ENABLED] = enabled
        }
    }

    fun getClipboardSyncEnabled(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[CLIPBOARD_SYNC_ENABLED] != false // Default to enabled
        }
    }

    // Auto Reconnect toggle
    suspend fun setAutoReconnectEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[AUTO_RECONNECT_ENABLED] = enabled
        }
    }

    fun getAutoReconnectEnabled(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[AUTO_RECONNECT_ENABLED] != false // Default to enabled
        }
    }

    // New: Continue Browsing toggle
    suspend fun setContinueBrowsingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[CONTINUE_BROWSING_ENABLED] = enabled
        }
    }

    fun getContinueBrowsingEnabled(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[CONTINUE_BROWSING_ENABLED] != false // Default to enabled
        }
    }

    // New: Send now playing toggle
    suspend fun setSendNowPlayingEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[SEND_NOW_PLAYING_ENABLED] = enabled
        }
    }

    fun getSendNowPlayingEnabled(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[SEND_NOW_PLAYING_ENABLED] != false // Default to enabled
        }
    }

    // New: Keep previous link toggle
    suspend fun setKeepPreviousLinkEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEEP_PREVIOUS_LINK_ENABLED] = enabled
        }
    }

    fun getKeepPreviousLinkEnabled(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[KEEP_PREVIOUS_LINK_ENABLED] != false // Default to enabled
        }
    }

    suspend fun setExpandNetworkingEnabled(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[EXPAND_NETWORKING_ENABLED] = enabled
        }
    }

    fun getExpandNetworkingEnabled(): Flow<Boolean> {
        return context.dataStore.data.map { prefs ->
            prefs[EXPAND_NETWORKING_ENABLED] ?: false
        }
    }

    suspend fun saveLastConnectedDevice(device: ConnectedDevice) {
        context.dataStore.edit { preferences ->
            preferences[LAST_CONNECTED_PC_NAME] = device.name
            preferences[LAST_CONNECTED_PC_IP] = device.ipAddress
            preferences[LAST_CONNECTED_PC_PORT] = device.port
            preferences[LAST_CONNECTED_TIMESTAMP] = device.lastConnected.toString()
            preferences[LAST_CONNECTED_PC_PLUS] = device.isPlus
            device.symmetricKey?.let {
                preferences[LAST_CONNECTED_SYMMETRIC_KEY] = it
            }
            device.model?.let {
                preferences[LAST_CONNECTED_MODEL] = it
            }
            device.deviceType?.let {
                preferences[LAST_CONNECTED_DEVICE_TYPE] = it
            }
            preferences[ICON_SYNC_COUNT] = device.iconSyncCount.toString()
            device.lastIconSyncDate?.let {
                preferences[LAST_ICON_SYNC_DATE] = it
            }
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
            val isPlus = preferences[LAST_CONNECTED_PC_PLUS] ?: false
            val symmetricKey = preferences[LAST_CONNECTED_SYMMETRIC_KEY]
            val model = preferences[LAST_CONNECTED_MODEL]
            val deviceType = preferences[LAST_CONNECTED_DEVICE_TYPE]
            val lastSyncTime = preferences[LAST_SYNC_TIME]?.toLongOrNull()
            val iconSyncCount = preferences[ICON_SYNC_COUNT]?.toIntOrNull() ?: 0
            val lastIconSyncDate = preferences[LAST_ICON_SYNC_DATE]

            if (name != null && ip != null && port != null && timestamp != null) {
                ConnectedDevice(
                    name = name,
                    ipAddress = ip,
                    port = port,
                    lastConnected = timestamp.toLongOrNull() ?: 0L,
                    lastSyncTime = lastSyncTime,
                    isPlus = isPlus,
                    iconSyncCount = iconSyncCount,
                    lastIconSyncDate = lastIconSyncDate,
                    symmetricKey = symmetricKey,
                    model = model,
                    deviceType = deviceType
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

    // --- Mac status persisted for widget ---
    suspend fun saveMacStatusForWidget(batteryLevel: Int, isCharging: Boolean, title: String, artist: String) {
        context.dataStore.edit { preferences ->
            preferences[MAC_BATTERY_LEVEL] = batteryLevel
            preferences[MAC_BATTERY_CHARGING] = isCharging
            preferences[MAC_MUSIC_TITLE] = title
            preferences[MAC_MUSIC_ARTIST] = artist
        }
    }

    suspend fun setMacWidgetRefreshedAt(timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[MAC_WIDGET_REFRESH_AT] = timestamp
        }
    }

    fun getMacWidgetRefreshedAt(): Flow<Long?> {
        return context.dataStore.data.map { preferences ->
            preferences[MAC_WIDGET_REFRESH_AT]
        }
    }

    data class MacStatusSnapshot(
        val batteryLevel: Int?,
        val isCharging: Boolean?,
        val title: String?,
        val artist: String?
    )

    fun getMacStatusForWidget(): Flow<MacStatusSnapshot> = context.dataStore.data.map { preferences ->
        MacStatusSnapshot(
            batteryLevel = preferences[MAC_BATTERY_LEVEL],
            isCharging = preferences[MAC_BATTERY_CHARGING],
            title = preferences[MAC_MUSIC_TITLE],
            artist = preferences[MAC_MUSIC_ARTIST]
        )
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

                val isEnabled = preferences[enabledKey] != false
                val appName = preferences[nameKey] ?: packageName
                val isSystemApp = preferences[systemKey] == true
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

    suspend fun setDeveloperMode(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[DEVELOPER_MODE] = enabled
        }
    }

    fun getDeveloperMode(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[DEVELOPER_MODE] == true // Default to disabled
        }
    }

    suspend fun setUserManuallyDisconnected(disconnected: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[USER_MANUALLY_DISCONNECTED] = disconnected
        }
    }

    fun getUserManuallyDisconnected(): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[USER_MANUALLY_DISCONNECTED] == true // Default to false
        }
    }

    // Network-aware device connections
    suspend fun saveNetworkDeviceConnection(deviceName: String, ourIp: String, clientIp: String, port: String, isPlus: Boolean, symmetricKey: String?, model: String? = null, deviceType: String? = null) {
        context.dataStore.edit { preferences ->
            // Load existing connections for this device
            val existingConnectionsJson = preferences[stringPreferencesKey("${NETWORK_CONNECTIONS_PREFIX}${deviceName}")] ?: "{}"
            val existingConnections = try {
                val json = org.json.JSONObject(existingConnectionsJson)
                val map = mutableMapOf<String, String>()
                json.keys().forEach { key ->
                    map[key] = json.getString(key)
                }
                map
            } catch (_: Exception) {
                mutableMapOf()
            }

            // Add/update the new connection
            existingConnections[ourIp] = clientIp

            // Convert back to JSON
            val updatedJson = org.json.JSONObject(existingConnections).toString()

            // Save device info
            preferences[stringPreferencesKey("${NETWORK_DEVICES_PREFIX}${deviceName}_name")] = deviceName
            preferences[stringPreferencesKey("${NETWORK_DEVICES_PREFIX}${deviceName}_port")] = port
            preferences[booleanPreferencesKey("${NETWORK_DEVICES_PREFIX}${deviceName}_plus")] = isPlus
            symmetricKey?.let {
                preferences[stringPreferencesKey("${NETWORK_DEVICES_PREFIX}${deviceName}_symmetric_key")] = it
            }
            model?.let {
                preferences[stringPreferencesKey("${NETWORK_DEVICES_PREFIX}${deviceName}_model")] = it
            }
            deviceType?.let {
                preferences[stringPreferencesKey("${NETWORK_DEVICES_PREFIX}${deviceName}_type")] = it
            }
            preferences[stringPreferencesKey("${NETWORK_DEVICES_PREFIX}${deviceName}_last_connected")] = System.currentTimeMillis().toString()
            preferences[stringPreferencesKey("${NETWORK_CONNECTIONS_PREFIX}${deviceName}")] = updatedJson
        }
    }

    fun getNetworkDeviceConnection(deviceName: String): Flow<NetworkDeviceConnection?> {
        return context.dataStore.data.map { preferences ->
            val name = preferences[stringPreferencesKey("${NETWORK_DEVICES_PREFIX}${deviceName}_name")]
            val port = preferences[stringPreferencesKey("${NETWORK_DEVICES_PREFIX}${deviceName}_port")]
            val isPlus = preferences[booleanPreferencesKey("${NETWORK_DEVICES_PREFIX}${deviceName}_plus")] ?: false
            val symmetricKey = preferences[stringPreferencesKey("${NETWORK_DEVICES_PREFIX}${deviceName}_symmetric_key")]
            val model = preferences[stringPreferencesKey("${NETWORK_DEVICES_PREFIX}${deviceName}_model")]
            val deviceType = preferences[stringPreferencesKey("${NETWORK_DEVICES_PREFIX}${deviceName}_type")]
            val lastConnected = preferences[stringPreferencesKey("${NETWORK_DEVICES_PREFIX}${deviceName}_last_connected")]?.toLongOrNull() ?: 0L
            val connectionsJson = preferences[stringPreferencesKey("${NETWORK_CONNECTIONS_PREFIX}${deviceName}")] ?: "{}"

            if (name != null && port != null) {
                val connections = try {
                    val json = org.json.JSONObject(connectionsJson)
                    val map = mutableMapOf<String, String>()
                    json.keys().forEach { key ->
                        map[key] = json.getString(key)
                    }
                    map
                } catch (_: Exception) {
                    emptyMap()
                }

                NetworkDeviceConnection(
                    deviceName = name,
                    networkConnections = connections,
                    port = port,
                    lastConnected = lastConnected,
                    isPlus = isPlus,
                    symmetricKey = symmetricKey,
                    model = model,
                    deviceType = deviceType
                )
            } else {
                null
            }
        }
    }

    fun getAllNetworkDeviceConnections(): Flow<List<NetworkDeviceConnection>> {
        return context.dataStore.data.map { preferences ->
            val devices = mutableListOf<NetworkDeviceConnection>()
            val deviceNames = mutableSetOf<String>()

            // Extract device names from preference keys
            preferences.asMap().keys.forEach { key ->
                if (key.name.startsWith(NETWORK_DEVICES_PREFIX) && key.name.endsWith("_name")) {
                    val deviceName = key.name.removePrefix(NETWORK_DEVICES_PREFIX).removeSuffix("_name")
                    deviceNames.add(deviceName)
                }
            }

            // Build device objects
            deviceNames.forEach { deviceName ->
                val name = preferences[stringPreferencesKey("${NETWORK_DEVICES_PREFIX}${deviceName}_name")]
                val port = preferences[stringPreferencesKey("${NETWORK_DEVICES_PREFIX}${deviceName}_port")]
                val isPlus = preferences[booleanPreferencesKey("${NETWORK_DEVICES_PREFIX}${deviceName}_plus")] ?: false
                val symmetricKey = preferences[stringPreferencesKey("${NETWORK_DEVICES_PREFIX}${deviceName}_symmetric_key")]
                val model = preferences[stringPreferencesKey("${NETWORK_DEVICES_PREFIX}${deviceName}_model")]
                val deviceType = preferences[stringPreferencesKey("${NETWORK_DEVICES_PREFIX}${deviceName}_type")]
                val lastConnected = preferences[stringPreferencesKey("${NETWORK_DEVICES_PREFIX}${deviceName}_last_connected")]?.toLongOrNull() ?: 0L
                val connectionsJson = preferences[stringPreferencesKey("${NETWORK_CONNECTIONS_PREFIX}${deviceName}")] ?: "{}"

                if (name != null && port != null) {
                    val connections = try {
                        val json = org.json.JSONObject(connectionsJson)
                        val map = mutableMapOf<String, String>()
                        json.keys().forEach { key ->
                            map[key] = json.getString(key)
                        }
                        map
                    } catch (_: Exception) {
                        emptyMap()
                    }

                    devices.add(
                        NetworkDeviceConnection(
                            deviceName = name,
                            networkConnections = connections,
                            port = port,
                            lastConnected = lastConnected,
                            isPlus = isPlus,
                            symmetricKey = symmetricKey,
                            model = model,
                            deviceType = deviceType
                        )
                    )
                }
            }

            devices.sortedByDescending { it.lastConnected }
        }
    }

    suspend fun updateNetworkDeviceLastConnected(deviceName: String, timestamp: Long) {
        context.dataStore.edit { preferences ->
            preferences[stringPreferencesKey("${NETWORK_DEVICES_PREFIX}${deviceName}_last_connected")] = timestamp.toString()
        }
    }
}