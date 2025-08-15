package com.sameerasw.airsync.presentation.ui.components.dialogs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.sameerasw.airsync.ui.theme.ExtraCornerRadius

enum class PermissionType {
    NOTIFICATION_ACCESS,
    POST_NOTIFICATIONS,
    BACKGROUND_USAGE,
    WALLPAPER_ACCESS
}

data class PermissionInfo(
    val title: String,
    val icon: String,
    val description: String,
    val whyNeeded: String,
    val buttonText: String
)

@Composable
fun PermissionExplanationDialog(
    permissionType: PermissionType,
    onDismiss: () -> Unit,
    onGrantPermission: () -> Unit
) {
    val permissionInfo = getPermissionInfo(permissionType)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(ExtraCornerRadius),
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = permissionInfo.icon,
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = permissionInfo.title,
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                HorizontalDivider()

                // Description
                Text(
                    text = permissionInfo.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Why we need this permission
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Why AirSync needs this permission:",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            text = permissionInfo.whyNeeded,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Maybe Later")
                    }

                    Button(
                        onClick = {
                            onGrantPermission()
                            onDismiss()
                        },
                        modifier = Modifier.weight(2f)
                    ) {
                        Text(permissionInfo.buttonText)
                    }
                }
            }
        }
    }
}

private fun getPermissionInfo(permissionType: PermissionType): PermissionInfo {
    return when (permissionType) {
        PermissionType.NOTIFICATION_ACCESS -> PermissionInfo(
            title = "Notification Access",
            icon = "🔔",
            description = "AirSync needs access to read your device notifications to sync them with your mac in real-time.",
            whyNeeded = "This is the core functionality of AirSync. Without notification access, the app cannot read incoming notifications from other apps and forward them to your mac. \nThe media now playing status also relies on this permission. \nThe app will use this permission only for the explained use cases and will always sync these data via an encrypted local network",
            buttonText = "Grant Notification Access"
        )

        PermissionType.POST_NOTIFICATIONS -> PermissionInfo(
            title = "Post Notifications",
            icon = "📱",
            description = "On Android 13+, apps need explicit permission to display notifications. AirSync uses this to show connection status and sync confirmations.",
            whyNeeded = "AirSync needs to show you important notifications about connection status, sync progress, and any errors that occur during operation.",
            buttonText = "Allow Notifications"
        )

        PermissionType.BACKGROUND_USAGE -> PermissionInfo(
            title = "Background App Usage",
            icon = "🔋",
            description = "Android's battery optimization can kill background apps. Disabling this for AirSync ensures reliable notification syncing even when you're not actively using the app.",
            whyNeeded = "Modern Android aggressively kills background apps to save battery. AirSync needs to run continuously to sync notifications in real-time. \nThis may result in minor battery drain increases but will ensure the connection stays stable and functions when the app is not in focus like QS tiles work well.",
            buttonText = "Disable Battery Optimization"
        )

        PermissionType.WALLPAPER_ACCESS -> PermissionInfo(
            title = "Wallpaper & File Access",
            icon = "🖼️",
            description = "This optional permission allows AirSync to sync your phone's wallpaper to your mac.",
            whyNeeded = "To read your current wallpaper which is not accessible with regular privileges,  AirSync needs external storage permissions. \nBut the app will only use the permission for the given explained use cases and will not alter or read any other files on the storage.",
            buttonText = "Grant Storage Access"
        )
    }
}
