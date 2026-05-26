package com.sameerasw.airsync.presentation.ui.components.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sameerasw.airsync.R

@Composable
fun MediaSyncCard(
    isSendNowPlayingEnabled: Boolean,
    onToggleSendNowPlaying: (Boolean) -> Unit,
    isMacMediaControlsEnabled: Boolean,
    onToggleMacMediaControls: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        IconToggleItem(
            iconRes = R.drawable.rounded_music_cast_24,
            title = "Send now playing",
            description = "Share media playback details with desktop",
            isChecked = isSendNowPlayingEnabled,
            onCheckedChange = onToggleSendNowPlaying
        )
        IconToggleItem(
            iconRes = R.drawable.rounded_smart_display_24,
            title = "Show Mac Media Controls",
            description = "Show media controls when Mac is playing music",
            isChecked = isMacMediaControlsEnabled,
            onCheckedChange = onToggleMacMediaControls
        )
    }
}

