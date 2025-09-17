package com.sameerasw.airsync.presentation.ui.components.cards

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sameerasw.airsync.R
import com.sameerasw.airsync.domain.model.MacDeviceStatus
import com.sameerasw.airsync.ui.theme.ExtraCornerRadius
import com.sameerasw.airsync.ui.theme.minCornerRadius

@Composable
fun MacDeviceStatusCard(
    macStatus: MacDeviceStatus?,
    modifier: Modifier = Modifier
) {
    val cardShape = RoundedCornerShape(ExtraCornerRadius)
    AnimatedVisibility(
        visible = macStatus != null,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        macStatus?.let { status ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = cardShape,
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header with Mac icon and title
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_laptop_24),
                            contentDescription = "Mac",
                            modifier = Modifier.size(24.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Mac Status",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )

                        Spacer(modifier = Modifier.weight(1f))

                        // Battery indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                "${status.battery.level}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // Battery icon with charging indicator
                            Box(
                                modifier = Modifier
                                    .size(width = 20.dp, height = 12.dp)
                                    .background(
                                        color = when {
                                            status.battery.level > 20 -> MaterialTheme.colorScheme.primary
                                            else -> MaterialTheme.colorScheme.error
                                        },
                                        shape = RoundedCornerShape(2.dp)
                                    )
                            ) {
                                if (status.battery.isCharging) {
                                    Text(
                                        "⚡",
                                        modifier = Modifier.align(Alignment.Center),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            }
                        }
                    }

                    // Media information
                    if (status.music.title.isNotEmpty() || status.music.artist.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Album art or placeholder
                            AlbumArtImage(
                                albumArt = status.music.albumArt,
                                modifier = Modifier.size(56.dp)
                            )

                            // Track info
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = status.music.title.ifEmpty { "Unknown Track" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = status.music.artist.ifEmpty { "Unknown Artist" },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )

                                // Playback status
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(
                                            if (status.music.isPlaying) R.drawable.outline_play_arrow_24
                                            else R.drawable.outline_pause_24
                                        ),
                                        contentDescription = if (status.music.isPlaying) "Playing" else "Paused",
                                        modifier = Modifier.size(16.dp),
                                        tint = if (status.music.isPlaying) MaterialTheme.colorScheme.primary
                                               else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        // No media playing
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.outline_pause_24),
                                contentDescription = "No media",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "No media playing on Mac",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumArtImage(
    albumArt: String,
    modifier: Modifier = Modifier
) {
    val bitmap by remember(albumArt) {
        derivedStateOf {
            if (albumArt.isNotEmpty()) {
                try {
                    val decodedBytes = Base64.decode(albumArt, Base64.DEFAULT)
                    BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                } catch (e: Exception) {
                    null
                }
            } else {
                null
            }
        }
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        bitmap?.let { bmp ->
            Image(
                bitmap = bmp.asImageBitmap(),
                contentDescription = "Album Art",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } ?: run {
            Icon(
                painter = painterResource(R.drawable.ic_laptop_24),
                contentDescription = "No album art",
                modifier = Modifier.size(24.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
