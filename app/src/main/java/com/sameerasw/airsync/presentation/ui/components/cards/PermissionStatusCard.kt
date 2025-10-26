package com.sameerasw.airsync.presentation.ui.components.cards

import android.Manifest
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sameerasw.airsync.ui.theme.ExtraCornerRadius
import com.sameerasw.airsync.utils.PermissionUtil

@Composable
fun PermissionStatusCard(
    missingPermissions: List<String>,
    onGrantPermission: (String) -> Unit,
    onRefreshPermissions: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(ExtraCornerRadius),
        colors = CardDefaults.cardColors(
            containerColor = if (missingPermissions.isEmpty()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (missingPermissions.isEmpty()) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = if (missingPermissions.isEmpty()) "All permissions granted" else "Missing permissions",
                    tint = if (missingPermissions.isEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                )
                Text(
                    text = if (missingPermissions.isEmpty()) "All permissions granted" else "Missing permissions",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp)
                )
            }
            if (missingPermissions.isNotEmpty()) {
                Text(
                    text = "AirSync requires the following permissions to function correctly:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.Start)
                )
                missingPermissions.forEach { permission ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "- ${getPermissionDisplayName(permission)}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Button(onClick = { onGrantPermission(permission) }) {
                            Text("Grant")
                        }
                    }
                }
                TextButton(onClick = onRefreshPermissions) {
                    Text("Refresh")
                }
            } else {
                Button(onClick = onRefreshPermissions) {
                    Text("Refresh Permissions")
                }
            }
        }
    }
}

fun getPermissionDisplayName(permission: String): String {
    return when (permission) {
        PermissionUtil.NOTIFICATION_ACCESS -> "Notification Access"
        PermissionUtil.BACKGROUND_APP_USAGE -> "Background App Usage"
        Manifest.permission.POST_NOTIFICATIONS -> "Post Notifications"
        Manifest.permission.READ_MEDIA_IMAGES -> "Read Media Images"
        PermissionUtil.ACCESSIBILITY_SERVICE -> "Accessibility Service"
        else -> permission.substringAfterLast('.')
    }
}
