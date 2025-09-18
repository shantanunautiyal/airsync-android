package com.sameerasw.airsync.domain.model


data class ConnectedDevice(
    val name: String,
    val ipAddress: String,
    val port: String,
    val lastConnected: Long,
    val lastSyncTime: Long? = null,
    val isPlus: Boolean = false,
    val iconSyncCount: Int = 0,
    val lastIconSyncDate: String? = null,
    val symmetricKey: String? = null,
    val model: String? = null,
    val deviceType: String? = null
)