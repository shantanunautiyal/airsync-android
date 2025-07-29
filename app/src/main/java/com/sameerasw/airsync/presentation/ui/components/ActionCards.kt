package com.sameerasw.airsync.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sameerasw.airsync.domain.model.ConnectedDevice

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
fun ConnectionSettingsCard(
    ipAddress: String,
    port: String,
    onIpAddressChange: (String) -> Unit,
    onPortChange: (String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Connection Settings", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = ipAddress,
                onValueChange = onIpAddressChange,
                label = { Text("Desktop IP Address") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = port,
                onValueChange = onPortChange,
                label = { Text("Desktop Port") },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun ActionButtonsCard(
    isLoading: Boolean,
    onSendDeviceInfo: () -> Unit,
    onSendNotification: () -> Unit,
    onSendDeviceStatus: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Quick Actions", style = MaterialTheme.typography.titleMedium)

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
    }
}

@Composable
fun CustomMessageCard(
    customMessage: String,
    isLoading: Boolean,
    isEnabled: Boolean,
    onCustomMessageChange: (String) -> Unit,
    onSendCustomMessage: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Custom Message", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = customMessage,
                onValueChange = onCustomMessageChange,
                label = { Text("Custom Raw JSON") },
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4
            )

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = onSendCustomMessage,
                enabled = isEnabled && !isLoading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isLoading) "Sending..." else "Send Custom Message")
            }
        }
    }
}
