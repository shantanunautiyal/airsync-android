package com.sameerasw.airsync.domain.model

data class BatteryInfo(
    val level: Int,
    val isCharging: Boolean
)

data class AudioInfo(
    val isPlaying: Boolean,
    val title: String,
    val artist: String,
    val volume: Int,
    val isMuted: Boolean,
    val albumArt: String? = null,
    // New: like status for current media ("liked", "not_liked", or "none")
    val likeStatus: String = "none"
)

data class MediaInfo(
    val isPlaying: Boolean,
    val title: String,
    val artist: String,
    val albumArt: String? = null,
    // New: like status for current media ("liked", "not_liked", or "none")
    val likeStatus: String = "none"
)