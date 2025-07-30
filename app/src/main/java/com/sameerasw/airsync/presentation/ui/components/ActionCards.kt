package com.sameerasw.airsync.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sameerasw.airsync.domain.model.ConnectedDevice
import com.sameerasw.airsync.utils.PermissionUtil

@Composable
fun LastConnectedDeviceCard(
    device: ConnectedDevice,
    onQuickConnect: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Last Connected Device", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("💻 ${device.name}", style = MaterialTheme.typography.bodyMedium)
            Text("🌐 ${device.ipAddress}:${device.port}", style = MaterialTheme.typography.bodyMedium)

            val lastConnectedTime = remember(device.lastConnected) {
                val currentTime = System.currentTimeMillis()
                val diffMinutes = (currentTime - device.lastConnected) / (1000 * 60)
                when {
                    diffMinutes < 1 -> "Just now"
                    diffMinutes < 60 -> "${diffMinutes}m ago"
                    diffMinutes < 1440 -> "${diffMinutes / 60}h ago"
                    else -> "${diffMinutes / 1440}d ago"
                }
            }
            Text("⏰ $lastConnectedTime", style = MaterialTheme.typography.bodyMedium)

            Button(
                onClick = onQuickConnect,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            ) {
                Text("Quick Connect")
            }
        }
    }
}

@Composable
fun ConnectionStatusCard(
    ipAddress: String,
    port: String,
    isConnected: Boolean,
    isConnecting: Boolean,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit,
    onIpAddressChange: (String) -> Unit,
    onPortChange: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Connection", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            // Connection status
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val statusText = when {
                    isConnecting -> "🟡 Connecting..."
                    isConnected -> "🟢 Connected"
                    else -> "🔴 Disconnected"
                }

                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )

                if (isConnected) {
                    OutlinedButton(onClick = onDisconnect) {
                        Text("Disconnect")
                    }
                } else {
                    Button(
                        onClick = onConnect,
                        enabled = !isConnecting && ipAddress.isNotBlank() && port.isNotBlank()
                    ) {
                        Text("Connect")
                    }
                }
            }

            if (isConnected) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "📡 Connected to $ipAddress:$port",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Connection settings (only show when not connected)
            if (!isConnected) {
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = ipAddress,
                    onValueChange = onIpAddressChange,
                    label = { Text("Desktop IP Address") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isConnecting
                )

                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = port,
                    onValueChange = onPortChange,
                    label = { Text("Desktop Port") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isConnecting
                )
            }
        }
    }
}

@Composable
fun DeveloperModeCard(
    isDeveloperMode: Boolean,
    onToggleDeveloperMode: (Boolean) -> Unit,
    isLoading: Boolean,
    onSendDeviceInfo: () -> Unit,
    onSendNotification: () -> Unit,
    onSendDeviceStatus: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Developer Mode", style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = isDeveloperMode,
                    onCheckedChange = onToggleDeveloperMode
                )
            }

            if (isDeveloperMode) {
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Test Functions",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(8.dp))

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onSendDeviceInfo,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    ) {
                        Text("Send Device Info")
                    }

                    Button(
                        onClick = onSendNotification,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    ) {
                        Text("Send Test Notification")
                    }

                    Button(
                        onClick = onSendDeviceStatus,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isLoading
                    ) {
                        Text("Send Device Status")
                    }
                }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Enable to access testing functions",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PermissionStatusCard(
    missingPermissions: List<String>,
    onGrantPermissions: () -> Unit,
    onRefreshPermissions: () -> Unit,
    onRequestNotificationPermission: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val criticalPermissions = PermissionUtil.getCriticalMissingPermissions(context)
    val optionalPermissions = PermissionUtil.getOptionalMissingPermissions(context)

    if (missingPermissions.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (criticalPermissions.isNotEmpty())
                    MaterialTheme.colorScheme.errorContainer
                else MaterialTheme.colorScheme.secondaryContainer
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

                    TextButton(onClick = onRefreshPermissions) {
                        Text("Refresh")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Show critical permissions first
                if (criticalPermissions.isNotEmpty()) {
                    Text(
                        "Critical permissions (required for core functionality):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    criticalPermissions.forEach { permission ->
                        Text(
                            "• $permission",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }

                    Button(
                        onClick = onGrantPermissions,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text("Grant Critical Permissions", color = MaterialTheme.colorScheme.onError)
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
                        "Optional permissions (recommended for better experience):",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    optionalPermissions.forEach { permission ->
                        Text(
                            "• $permission",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }

                    // Show Android 14+ notification permission button if applicable
                    if (PermissionUtil.isNotificationPermissionRequired() &&
                        !PermissionUtil.isPostNotificationPermissionGranted(context) &&
                        onRequestNotificationPermission != null) {

                        OutlinedButton(
                            onClick = onRequestNotificationPermission,
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                        ) {
                            Text("Grant Notification Permission")
                        }
                    }
                }
            }
        }
    } else {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "✅ All Permissions Granted",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.weight(1f)
                )

                TextButton(onClick = onRefreshPermissions) {
                    Text("Refresh")
                }
            }
        }
    }
}

@Composable
fun ClipboardSyncCard(
    isClipboardSyncEnabled: Boolean,
    onToggleClipboardSync: (Boolean) -> Unit,
    onTestClipboard: () -> Unit,
    isConnected: Boolean
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Clipboard Sync", style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = isClipboardSyncEnabled,
                    onCheckedChange = onToggleClipboardSync
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (isClipboardSyncEnabled) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (isConnected) "🔄 Clipboard sync active" else "⏸️ Waiting for connection",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )

                    if (isConnected) {
                        OutlinedButton(
                            onClick = onTestClipboard,
                            modifier = Modifier.padding(start = 8.dp)
                        ) {
                            Text("Test")
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Automatically syncs copied text between your device and PC",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Enable to automatically sync clipboard content with your PC",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
