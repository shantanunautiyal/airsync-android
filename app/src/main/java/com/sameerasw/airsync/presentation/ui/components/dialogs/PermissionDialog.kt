package com.sameerasw.airsync.presentation.ui.components.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class PermissionType {
    NOTIFICATION_ACCESS,
    POST_NOTIFICATIONS,
    BACKGROUND_USAGE,
    WALLPAPER_ACCESS,
    ACCESSIBILITY_SERVICE,
    SCREEN_MIRRORING
}

@Composable
fun PermissionExplanationDialog(
    permissionType: PermissionType,
    onDismiss: () -> Unit,
    onGrantPermission: () -> Unit
) {
    val title: String
    val text: String

    when (permissionType) {
        PermissionType.NOTIFICATION_ACCESS -> {
            title = "Notification Access"
            text = "AirSync needs notification access to mirror your notifications to your computer. Please grant this permission in the next screen."
        }
        PermissionType.POST_NOTIFICATIONS -> {
            title = "Post Notifications"
            text = "Allowing notifications is recommended to show connection status and other important alerts."
        }
        PermissionType.BACKGROUND_USAGE -> {
            title = "Background App Usage"
            text = "To ensure AirSync runs reliably in the background, please set its battery usage to \"Unrestricted\"."
        }
        PermissionType.WALLPAPER_ACCESS -> {
            title = "Wallpaper Access"
            text = "To sync your wallpaper, AirSync needs permission to read media images. Please grant this in the app settings."
        }
        PermissionType.ACCESSIBILITY_SERVICE -> {
            title = "Accessibility Service"
            text = "To enable remote input, you must enable the AirSync Accessibility Service. This is a sensitive permission required for controlling your device from your computer."
        }
        PermissionType.SCREEN_MIRRORING -> {
            title = "Screen Mirroring"
            text = "AirSync needs permission to capture your screen to mirror it to your Mac."
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                Text(text)
                Spacer(modifier = Modifier.height(8.dp))
                Text("You will be taken to the appropriate settings screen.")
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onGrantPermission()
                onDismiss()
            }) {
                Text("Open Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
