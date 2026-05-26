package com.sameerasw.airsync.presentation.ui.components.cards

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.sameerasw.airsync.R

@Composable
fun NotificationSyncCard(
    isNotificationEnabled: Boolean,
    isNotificationSyncEnabled: Boolean,
    onToggleSync: (Boolean) -> Unit,
    onGrantPermissions: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconToggleItem(
        modifier = modifier,
        iconRes = R.drawable.rounded_notifications_active_24,
        title = "Notification Sync",
        description = if (!isNotificationEnabled) "❌ Notification access required" else null,
        isChecked = isNotificationSyncEnabled && isNotificationEnabled,
        onCheckedChange = { enabled ->
            if (isNotificationEnabled) {
                onToggleSync(enabled)
            } else {
                onGrantPermissions()
            }
        },
        enabled = isNotificationEnabled,
        onDisabledClick = onGrantPermissions
    )
}