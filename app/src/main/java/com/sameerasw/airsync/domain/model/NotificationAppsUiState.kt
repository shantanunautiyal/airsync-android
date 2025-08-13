package com.sameerasw.airsync.domain.model

data class NotificationAppsUiState(
    val apps: List<NotificationApp> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showSystemApps: Boolean = false
)
