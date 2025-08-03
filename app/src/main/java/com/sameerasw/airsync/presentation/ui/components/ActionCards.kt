package com.sameerasw.airsync.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.sameerasw.airsync.domain.model.ConnectedDevice
import com.sameerasw.airsync.domain.model.UiState
import com.sameerasw.airsync.ui.theme.ExtraCornerRadius
import com.sameerasw.airsync.ui.theme.minCornerRadius
import com.sameerasw.airsync.utils.PermissionUtil

@Composable
fun LastConnectedDeviceCard(
    device: ConnectedDevice,
    onQuickConnect: () -> Unit
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

@Composable
fun ConnectionStatusCard(
    isConnected: Boolean,
    isConnecting: Boolean,
    onDisconnect: () -> Unit,
    connectedDevice: ConnectedDevice? = null,
    lastConnected: Boolean
) {
    val cardShape = if (!isConnected && lastConnected) {
        RoundedCornerShape(
            topStart = ExtraCornerRadius,
            topEnd = ExtraCornerRadius,
            bottomStart = minCornerRadius,
            bottomEnd = minCornerRadius
        )
    } else {
        RoundedCornerShape(ExtraCornerRadius)
    }

    // Determine gradient color
    val gradientColor = when {
        isConnected -> Color(0xFF4CAF50) // Green
        isConnecting -> Color(0xFFFFC107) // Yellow
        else -> Color(0xFFF44336) // Red
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp)
            .requiredHeight( if (isConnected) 150.dp else 85.dp),
        shape = cardShape,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            gradientColor.copy(alpha = 0.2f),
                            Color.Transparent
                        ),
                        start = Offset(0f, 0f),
                        end = Offset.Infinite
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Connection status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val statusText = when {
                        isConnecting -> "🟡  Connecting..."
                        isConnected -> "🟢  Syncing"
                        else -> "🔴  Disconnected"
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
                    }
                }

                if (isConnected && connectedDevice != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Connected to ${connectedDevice.name}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )

                        // Status badge
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (connectedDevice.isPlus)
                                    MaterialTheme.colorScheme.primaryContainer
                                else
                                    MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.padding(start = 16.dp)
                        ) {
                            Text(
                                text = if (connectedDevice.isPlus) "PLUS" else "FREE",
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (connectedDevice.isPlus)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "${connectedDevice.ipAddress}:${connectedDevice.port}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
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
    onSendDeviceStatus: () -> Unit,
    uiState: UiState
) {
    Card(modifier = Modifier.fillMaxWidth().padding(top=20.dp),
        shape = RoundedCornerShape(
            topStart = ExtraCornerRadius,
            topEnd = ExtraCornerRadius,
            bottomStart = minCornerRadius,
            bottomEnd = minCornerRadius
        ),
        ) {
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

                Spacer(modifier = Modifier.height(8.dp))


                // Response Display
                if (uiState.response.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (uiState.response.startsWith("Error") || uiState.response.startsWith("Failed"))
                                MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Text(
                            text = uiState.response,
                            modifier = Modifier.padding(16.dp),
                            color = if (uiState.response.startsWith("Error") || uiState.response.startsWith("Failed"))
                                MaterialTheme.colorScheme.onErrorContainer
                            else MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
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
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
            shape = RoundedCornerShape(
                ExtraCornerRadius
            ),
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
                        when (permission) {
                            "Background App Usage" -> {
                                Text(
                                    "• $permission - Prevents Android from killing AirSync in background",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            "Wallpaper Access" -> {
                                Text(
                                    "• $permission - Enables wallpaper sync to desktop (optional feature)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            else -> {
                                Text(
                                    "• $permission",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Show Android 14+ notification permission
                    if (PermissionUtil.isNotificationPermissionRequired() &&
                        !PermissionUtil.isPostNotificationPermissionGranted(context) &&
                        onRequestNotificationPermission != null) {

                        OutlinedButton(
                            onClick = onRequestNotificationPermission,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                        ) {
                            Text("Grant Notification Permission")
                        }
                    }

                    // Show battery optimization
                    if (PermissionUtil.isBatteryOptimizationPermissionRequired() &&
                        !PermissionUtil.isBatteryOptimizationDisabled(context)) {

                        OutlinedButton(
                            onClick = {
                                PermissionUtil.openBatteryOptimizationSettings(context)
                            },
                            modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)
                        ) {
                            Text("Allow Background Usage")
                        }
                    }

                    // Show wallpaper access
                    if (!PermissionUtil.hasWallpaperAccess(context)) {
                        OutlinedButton(
                            onClick = {
                                PermissionUtil.openManageExternalStorageSettings(context)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Enable Wallpaper Sync")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ClipboardSyncCard(
    isClipboardSyncEnabled: Boolean,
    onToggleClipboardSync: (Boolean) -> Unit,
    isConnected: Boolean
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(
            topStart = minCornerRadius,
            topEnd = minCornerRadius,
            bottomStart = minCornerRadius,
            bottomEnd = minCornerRadius
        ),
        ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Automatic Clipboard Sync", style = MaterialTheme.typography.titleMedium)
                Switch(
                    checked = isClipboardSyncEnabled,
                    onCheckedChange = onToggleClipboardSync
                )
            }
        }
    }
}
