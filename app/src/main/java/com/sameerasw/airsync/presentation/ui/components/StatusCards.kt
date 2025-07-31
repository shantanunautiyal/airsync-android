package com.sameerasw.airsync.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun NotificationSyncCard(
    isNotificationEnabled: Boolean,
    isNotificationSyncEnabled: Boolean,
    ipAddress: String,
    port: String,
    onToggleSync: (Boolean) -> Unit,
    onGrantPermissions: () -> Unit,
    onManageApps: () -> Unit = {}
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
                if (isNotificationEnabled) {
                    Spacer(modifier = Modifier.width(12.dp))
                    IconButton(
                        onClick = onManageApps,
                        modifier = Modifier
                            .size(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Manage Apps"
                        )
                    }
                }
            }

            if (!isNotificationEnabled) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "❌ Notification access required",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

        }
    }
}

@Composable
fun DeviceInfoCard(
    deviceName: String,
    localIp: String,
    onDeviceNameChange: (String) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("My Android", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Local IP: $localIp", style = MaterialTheme.typography.bodyMedium)

            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(
                value = deviceName,
                onValueChange = onDeviceNameChange,
                label = { Text("Device Name") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
