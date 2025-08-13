package com.sameerasw.airsync.domain.model

data class NetworkInfo(
    val isConnected: Boolean,
    val isWifi: Boolean,
    val ipAddress: String?
)