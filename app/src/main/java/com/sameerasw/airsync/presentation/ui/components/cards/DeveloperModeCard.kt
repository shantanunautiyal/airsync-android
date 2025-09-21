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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sameerasw.airsync.domain.model.UiState
import com.sameerasw.airsync.ui.theme.ExtraCornerRadius
import com.sameerasw.airsync.ui.theme.minCornerRadius

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

                // Removed preview/response display to avoid crashes from large/encoded payloads
            }
        }
    }
}