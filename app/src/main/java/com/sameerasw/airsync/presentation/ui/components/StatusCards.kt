package com.sameerasw.airsync.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun PermissionStatusCard(
    missingPermissions: List<String>,
    onGrantPermissions: () -> Unit,
    onRefreshPermissions: () -> Unit
) {
    if (missingPermissions.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "⚠️ Permissions Required",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Missing: ${missingPermissions.joinToString(", ")}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onGrantPermissions,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Grant Permissions", color = MaterialTheme.colorScheme.onError)
                    }

                    OutlinedButton(
                        onClick = onRefreshPermissions,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Refresh")
                    }
                }
            }
        }
    }
}

@Composable
fun NotificationSyncCard(
    isNotificationEnabled: Boolean,
    isNotificationSyncEnabled: Boolean,
    ipAddress: String,
    port: String,
    onToggleSync: (Boolean) -> Unit,
    onGrantPermissions: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Notification Sync", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (isNotificationEnabled && isNotificationSyncEnabled)
                            "Automatically sync notifications to desktop"
                        else "Enable to sync notifications",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isNotificationSyncEnabled && isNotificationEnabled,
                    onCheckedChange = { enabled ->
                        if (isNotificationEnabled) {
                            onToggleSync(enabled)
                        } else {
                            onGrantPermissions()
                        }
                    },
                    enabled = isNotificationEnabled
                )
            }

            if (!isNotificationEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "❌ Notification access required",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            } else if (isNotificationSyncEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "✅ Notifications will be sent to $ipAddress:$port",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun DeviceInfoCard(
    deviceName: String,
    localIp: String,
    batteryLevel: Int,
    isCharging: Boolean,
    volume: Int,
    isMuted: Boolean,
    mediaTitle: String,
    mediaArtist: String,
    isNotificationEnabled: Boolean,
    onDeviceNameChange: (String) -> Unit,
    onRefreshMedia: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Device Information", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Local IP: $localIp", style = MaterialTheme.typography.bodyMedium)
            Text(
                "Battery: $batteryLevel% ${if (isCharging) "⚡" else "🔋"}",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                "Volume: $volume% ${if (isMuted) "🔇" else "🔊"}",
                style = MaterialTheme.typography.bodyMedium
            )

            // Show media info status
            if (isNotificationEnabled) {
                if (mediaTitle.isNotEmpty()) {
                    Text(
                        "🎵 $mediaTitle - $mediaArtist",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    Text("🎵 No media playing", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text(
                    "🎵 Media info unavailable (permission needed)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = deviceName,
                onValueChange = onDeviceNameChange,
                label = { Text("Device Name") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedButton(
                onClick = onRefreshMedia,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text("Refresh Media")
            }
        }
    }
}
