package com.sameerasw.airsync

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "airsync_settings")

object DataStoreUtil {
    private val IP_ADDRESS = stringPreferencesKey("ip_address")
    private val PORT = stringPreferencesKey("port")
    private val DEVICE_NAME = stringPreferencesKey("device_name")
    private val CUSTOM_MESSAGE = stringPreferencesKey("custom_message")
    private val FIRST_RUN = booleanPreferencesKey("first_run")
    private val PERMISSIONS_CHECKED = booleanPreferencesKey("permissions_checked")

    suspend fun saveIpAddress(context: Context, ipAddress: String) {
        context.dataStore.edit { preferences ->
            preferences[IP_ADDRESS] = ipAddress
        }
    }

    fun getIpAddress(context: Context): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[IP_ADDRESS] ?: "192.168.1.100"
        }
    }

    suspend fun savePort(context: Context, port: String) {
        context.dataStore.edit { preferences ->
            preferences[PORT] = port
        }
    }

    fun getPort(context: Context): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[PORT] ?: "6996"
        }
    }

    suspend fun saveDeviceName(context: Context, deviceName: String) {
        context.dataStore.edit { preferences ->
            preferences[DEVICE_NAME] = deviceName
        }
    }

    fun getDeviceName(context: Context): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[DEVICE_NAME] ?: ""
        }
    }

    suspend fun saveCustomMessage(context: Context, message: String) {
        context.dataStore.edit { preferences ->
            preferences[CUSTOM_MESSAGE] = message
        }
    }

    fun getCustomMessage(context: Context): Flow<String> {
        return context.dataStore.data.map { preferences ->
            preferences[CUSTOM_MESSAGE] ?: """{"type":"notification","data":{"title":"Test","body":"Hello!","app":"WhatsApp"}}"""
        }
    }

    suspend fun setFirstRun(context: Context, isFirstRun: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[FIRST_RUN] = isFirstRun
        }
    }

    fun getFirstRun(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[FIRST_RUN] ?: true
        }
    }

    suspend fun setPermissionsChecked(context: Context, checked: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[PERMISSIONS_CHECKED] = checked
        }
    }

    fun getPermissionsChecked(context: Context): Flow<Boolean> {
        return context.dataStore.data.map { preferences ->
            preferences[PERMISSIONS_CHECKED] ?: false
        }
    }
}
