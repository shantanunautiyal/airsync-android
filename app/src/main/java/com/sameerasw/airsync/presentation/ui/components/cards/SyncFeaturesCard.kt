package com.sameerasw.airsync.presentation.ui.components.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.sameerasw.airsync.ui.theme.minCornerRadius
import com.sameerasw.airsync.utils.HapticUtil

@Composable
fun SyncFeaturesCard(
    isClipboardSyncEnabled: Boolean,
    onToggleClipboardSync: (Boolean) -> Unit,
    // Continue Browsing props
    isContinueBrowsingEnabled: Boolean,
    onToggleContinueBrowsing: (Boolean) -> Unit,
    // New: control the UI enabled state and subtitle for Continue Browsing
    isContinueBrowsingToggleEnabled: Boolean,
    continueBrowsingSubtitle: String,
    // New: Send now playing props
    isSendNowPlayingEnabled: Boolean,
    onToggleSendNowPlaying: (Boolean) -> Unit,
    // New: Keep previous link props
    isKeepPreviousLinkEnabled: Boolean,
    onToggleKeepPreviousLink: (Boolean) -> Unit,
    // New: Smartspacer props
    isSmartspacerShowWhenDisconnected: Boolean,
    onToggleSmartspacerShowWhenDisconnected: (Boolean) -> Unit,
) {
    val haptics = LocalHapticFeedback.current

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
                Column(modifier = Modifier.weight(1f)) {
                    Text("Clipboard Sync", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Unfortunately Google killed automatic sync, You need to manually share the text to AirSync app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isClipboardSyncEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) HapticUtil.performToggleOn(haptics) else HapticUtil.performToggleOff(haptics)
                        onToggleClipboardSync(enabled)
                    }
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
                        continueBrowsingSubtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Status badge
                Card(
                    colors = CardDefaults.cardColors(
                            MaterialTheme.colorScheme.primaryContainer
                    ),
                    modifier = Modifier.padding(start = 16.dp)
                ) {
                    Text(
                        text = "PLUS",
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color =  MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Spacer(modifier = Modifier.padding(end = 8.dp))
                Switch(
                    checked = isContinueBrowsingEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) HapticUtil.performToggleOn(haptics) else HapticUtil.performToggleOff(haptics)
                        onToggleContinueBrowsing(enabled)
                    },
                    enabled = isContinueBrowsingToggleEnabled
                )
            }

            // Keep previous link toggle under Send now playing
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Keep previous link", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Keep multiple continue browsing notifications",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isKeepPreviousLinkEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) HapticUtil.performToggleOn(haptics) else HapticUtil.performToggleOff(haptics)
                        onToggleKeepPreviousLink(enabled)
                    },
                    enabled = isContinueBrowsingToggleEnabled
                )
            }

            // Send now playing toggle under Continue Browsing
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Send now playing", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Share media playback details with desktop",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isSendNowPlayingEnabled,
                    onCheckedChange = { enabled ->
                        if (enabled) HapticUtil.performToggleOn(haptics) else HapticUtil.performToggleOff(haptics)
                        onToggleSendNowPlaying(enabled)
                    }
                )
            }

            // Smartspacer toggle displayed under Send now playing
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Smartspacer", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Show Smartspacer when disconnected",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isSmartspacerShowWhenDisconnected,
                    onCheckedChange = { enabled ->
                        if (enabled) HapticUtil.performToggleOn(haptics) else HapticUtil.performToggleOff(haptics)
                        onToggleSmartspacerShowWhenDisconnected(enabled)
                    }
                )
            }

        }
    }
}