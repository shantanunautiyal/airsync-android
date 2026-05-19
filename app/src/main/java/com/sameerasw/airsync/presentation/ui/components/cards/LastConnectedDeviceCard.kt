package com.sameerasw.airsync.presentation.ui.components.cards

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.sameerasw.airsync.R
import com.sameerasw.airsync.domain.model.ConnectedDevice
import com.sameerasw.airsync.presentation.ui.components.sheets.ConnectionSettingsBottomSheet
import com.sameerasw.airsync.utils.DevicePreviewResolver
import com.sameerasw.airsync.utils.HapticUtil

@Composable
fun LastConnectedDeviceCard(
    device: ConnectedDevice,
    isAutoReconnectEnabled: Boolean,
    onToggleAutoReconnect: (Boolean) -> Unit,
    onQuickConnect: () -> Unit,
) {
    val haptics = LocalHapticFeedback.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraSmall,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
        ) {
            Text(
                "Last Connected Device",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val previewRes = DevicePreviewResolver.getPreviewRes(device)
                Image(
                    painter = painterResource(id = previewRes),
                    contentDescription = "Connected Mac preview",
                    modifier = Modifier
                        .fillMaxWidth(0.45f),
                    contentScale = ContentScale.Fit,
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(MaterialTheme.colorScheme.primary)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column{

                    Text(
                        "${device.name}",
                        style = MaterialTheme.typography.headlineSmall
                    )

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
                    Text(
                        "Last seen $lastConnectedTime",
                        style = MaterialTheme.typography.bodyMedium
                    )

                }

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

//            device.deviceType?.let { type ->
//                Text("Type: $type", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
//            }

            Button(
                onClick = {
                    HapticUtil.performClick(haptics)
                    onQuickConnect()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .requiredHeight(65.dp)
                    .padding(top = 16.dp),
            ) {
                Icon(
                    painter = painterResource(id = com.sameerasw.airsync.R.drawable.rounded_sync_desktop_24),
                    contentDescription = "Quick connect",
                    modifier = Modifier.padding(end = 12.dp),
//                    tint = MaterialTheme.colorScheme.primary
                )
                Text("Quick Connect")
            }


        }
        var showBottomSheet by remember { mutableStateOf(false) }

        // Auto-reconnect & Bluetooth settings card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    HapticUtil.performClick(haptics)
                    showBottomSheet = true
                },
            shape = MaterialTheme.shapes.extraSmall,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.rounded_compare_arrows_24),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column {
                        Text(
                            text = stringResource(R.string.bluetooth_settings_card_title),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = stringResource(R.string.bluetooth_settings_card_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    painter = painterResource(id = R.drawable.rounded_keyboard_arrow_right_24),
                    contentDescription = "Configure settings",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (showBottomSheet) {
            ConnectionSettingsBottomSheet(
                isAutoReconnectEnabled = isAutoReconnectEnabled,
                onToggleAutoReconnect = onToggleAutoReconnect,
                onDismissRequest = { showBottomSheet = false }
            )
        }
    }
}