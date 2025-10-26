package com.sameerasw.airsync.presentation.ui.components.cards

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.sameerasw.airsync.ui.theme.ExtraCornerRadius
import com.sameerasw.airsync.utils.HapticUtil
import com.sameerasw.airsync.utils.PermissionUtil
import com.sameerasw.airsync.utils.QuickSettingsUtil
import com.sameerasw.airsync.utils.HapticUtil

enum class PermissionType {
    NOTIFICATION_ACCESS,
    POST_NOTIFICATIONS,
    BACKGROUND_USAGE,
    WALLPAPER_ACCESS
}

@Composable
fun PermissionStatusCard(
    missingPermissions: List<String>,
    onGrantPermission: (String) -> Unit,
    onRefreshPermissions: () -> Unit
) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val criticalPermissions = PermissionUtil.getCriticalMissingPermissions(context)
    val optionalPermissions = PermissionUtil.getOptionalMissingPermissions(context)

    // Dialog state management
    var showDialog by remember { mutableStateOf<PermissionType?>(null) }

    if (missingPermissions.isNotEmpty()) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 20.dp),
            shape = RoundedCornerShape(
                ExtraCornerRadius
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (criticalPermissions.isNotEmpty())
                    MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (criticalPermissions.isNotEmpty()) "❌ Missing Critical Permissions" else "⚠️ Optional Permissions",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        color = if (criticalPermissions.isNotEmpty())
                            MaterialTheme.colorScheme.onErrorContainer
                        else MaterialTheme.colorScheme.onSecondaryContainer
                    )

                    OutlinedButton(onClick = {
                        HapticUtil.performClick(haptics)
                        onRefreshPermissions()
                    }) {
                        Text("Refresh")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Show critical permissions first
                if (criticalPermissions.isNotEmpty()) {
                    Text(
                        "Recommended permissions:",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )

                    criticalPermissions.forEach { permission ->
                        when (permission) {
                            "Notification Access" -> {
                                PermissionButton(
                                    permissionName = permission,
                                    description = "Required for syncing notifications",
                                    onExplainClick = { showDialog = PermissionType.NOTIFICATION_ACCESS },
                                    isCritical = true
                                )
                            }
                        }
                    }
                }

                // Show optional permissions
                if (optionalPermissions.isNotEmpty()) {
                    if (criticalPermissions.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider()
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    Text(
                        "Optional permissions:",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )

                    optionalPermissions.forEach { permission ->
                        when (permission) {
                            "Post Notifications" -> {
                                PermissionButton(
                                    permissionName = permission,
                                    description = "Show connection status and alerts",
                                    onExplainClick = { showDialog = PermissionType.POST_NOTIFICATIONS },
                                    isCritical = false
                                )
                            }
                            "Background App Usage" -> {
                                PermissionButton(
                                    permissionName = permission,
                                    description = "Keep the app alive when inactive",
                                    onExplainClick = { showDialog = PermissionType.BACKGROUND_USAGE },
                                    isCritical = false
                                )
                            }
                            "Wallpaper Access" -> {
                                PermissionButton(
                                    permissionName = permission,
                                    description = "Enables wallpaper sync",
                                    onExplainClick = { showDialog = PermissionType.WALLPAPER_ACCESS },
                                    isCritical = false
                                )
                            }
                        }
                    }
                }

                // Non-permission tip: Quick Settings tile
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Tip:",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (criticalPermissions.isNotEmpty())
                        MaterialTheme.colorScheme.onErrorContainer
                    else MaterialTheme.colorScheme.onSecondaryContainer
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Add Quick Settings Tile",
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (criticalPermissions.isNotEmpty())
                                MaterialTheme.colorScheme.onErrorContainer
                            else
                                MaterialTheme.colorScheme.onSecondaryContainer
                        )
                        Text(
                            text = "Add AirSync to Quick Settings for one-tap connect/reconnect.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (criticalPermissions.isNotEmpty())
                                MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                            else
                                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            HapticUtil.performClick(haptics)
                            QuickSettingsUtil.requestAddQuickSettingsTile(context)
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Text("Add Tile")
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionButton(
    permissionName: String,
    description: String,
    onExplainClick: () -> Unit,
    isCritical: Boolean
) {
    val haptics = LocalHapticFeedback.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = permissionName,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCritical)
                    MaterialTheme.colorScheme.onErrorContainer
                else
                    MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = if (isCritical)
                    MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.7f)
                else
                    MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
            )
        }

        if (isCritical) {
            Button(
                onClick = {
                    HapticUtil.performClick(haptics)
                    onExplainClick()
                },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("Learn More")
            }
        } else {
            OutlinedButton(
                onClick = {
                    HapticUtil.performClick(haptics)
                    onExplainClick()
                },
                modifier = Modifier.padding(start = 8.dp)
            ) {
                Text("Learn More")
            }
        }
    }
}