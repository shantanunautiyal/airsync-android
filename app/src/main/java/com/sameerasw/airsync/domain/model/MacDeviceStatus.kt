package com.sameerasw.airsync.domain.model

data class MacDeviceStatus(
    val battery: MacBattery,
    val isPaired: Boolean,
    val music: MacMusicInfo
)

data class MacBattery(
    val level: Int,
    val isCharging: Boolean
)

data class MacMusicInfo(
    val isPlaying: Boolean,
    val title: String,
    val artist: String,
    val volume: Int,
    val isMuted: Boolean,
    val albumArt: String,
    val likeStatus: String
)
