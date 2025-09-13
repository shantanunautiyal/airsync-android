package com.sameerasw.airsync.presentation.ui.components.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sameerasw.airsync.ui.theme.minCornerRadius

@Composable
fun ClipboardSyncCard(
    isClipboardSyncEnabled: Boolean,
    onToggleClipboardSync: (Boolean) -> Unit,
    // New props for Continue Browsing
    isContinueBrowsingEnabled: Boolean,
    onToggleContinueBrowsing: (Boolean) -> Unit,
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
            // Continue Browsing toggle displayed under clipboard sync
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Continue browsing", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Show a quick Open/Dismiss notification for links received from desktop",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isContinueBrowsingEnabled,
                    onCheckedChange = onToggleContinueBrowsing
                )
            }
        }
    }
}
