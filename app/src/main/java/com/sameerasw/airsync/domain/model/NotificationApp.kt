package com.sameerasw.airsync.domain.model

import android.graphics.drawable.Drawable

data class NotificationApp(
    val packageName: String,
    val appName: String,
    val isEnabled: Boolean = true,
    val icon: Drawable? = null,
    val isSystemApp: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)
