package com.sameerasw.airsync.presentation.ui.components.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sameerasw.airsync.domain.model.ConnectedDevice
import com.sameerasw.airsync.ui.theme.ExtraCornerRadius
import com.sameerasw.airsync.ui.theme.minCornerRadius

@Composable
fun LastConnectedDeviceCard(
    device: ConnectedDevice,
    isAutoReconnectEnabled: Boolean,
    onToggleAutoReconnect: (Boolean) -> Unit,
    onQuickConnect: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(
            topStart = minCornerRadius,
            topEnd = minCornerRadius,
            bottomStart = ExtraCornerRadius,
            bottomEnd = ExtraCornerRadius
        ),) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Last Connected Device", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("💻 ${device.name}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 10.dp))

                // Display status badge - PLUS or FREE
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (device.isPlus)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = if (device.isPlus) "PLUS" else "FREE",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (device.isPlus)
                            MaterialTheme.colorScheme.onPrimaryContainer
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text("${device.ipAddress}:${device.port}", style = MaterialTheme.typography.bodyMedium)

            // Auto-reconnect toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Auto reconnect", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = isAutoReconnectEnabled, onCheckedChange = onToggleAutoReconnect)
            }

            // Display device model and type if available
            device.model?.let { model ->
                Text("Model: $model", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            device.deviceType?.let { type ->
                Text("Type: $type", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

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
            Text("Last seen $lastConnectedTime", style = MaterialTheme.typography.bodyMedium)


            Button(
                onClick = onQuickConnect,
                modifier = Modifier.fillMaxWidth().requiredHeight(65.dp).padding(top = 16.dp),
                shape = RoundedCornerShape(
                    topStart = minCornerRadius,
                    topEnd = minCornerRadius,
                    bottomStart = ExtraCornerRadius - minCornerRadius,
                    bottomEnd = ExtraCornerRadius - minCornerRadius
                )
            ) {
                Text("Quick Connect")
            }
        }
    }
}