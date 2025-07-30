package com.sameerasw.airsync.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sameerasw.airsync.domain.model.ConnectedDevice
import com.sameerasw.airsync.utils.WebSocketUtil

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
