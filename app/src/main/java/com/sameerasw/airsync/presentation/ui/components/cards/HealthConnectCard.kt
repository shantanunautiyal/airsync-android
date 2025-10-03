package com.sameerasw.airsync.presentation.ui.components.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sameerasw.airsync.ui.theme.ExtraCornerRadius

@Composable
fun HealthConnectCard(
    isHealthConnectEnabled: Boolean,
    onToggleHealthConnect: (Boolean) -> Unit,
    availability: String, // e.g., "Installed", "NotInstalled", "NotSupported"
    hasPermissions: Boolean,
    onConnectClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 20.dp),
        shape = RoundedCornerShape(ExtraCornerRadius)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Health Connect Sync", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Sync steps and heart rate",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Switch(
                    checked = isHealthConnectEnabled,
                    onCheckedChange = onToggleHealthConnect,
                    enabled = availability == "Installed" && hasPermissions
                )
            }

            if (availability != "Installed" || !hasPermissions) {
                Spacer(modifier = Modifier.height(12.dp))
                when {
                    availability == "NotSupported" -> {
                        Text(
                            "❌ Health Connect is not supported on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    availability == "NotInstalled" -> {
                        Text(
                            "⚠️ Health Connect app not found.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onConnectClick) {
                            Text("Install Health Connect")
                        }
                    }
                    !hasPermissions -> {
                        Text(
                            "⚠️ Permissions required to read health data.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(onClick = onConnectClick) {
                            Text("Grant Permissions")
                        }
                    }
                }
            }
        }
    }
}