package com.sameerasw.airsync.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sameerasw.airsync.domain.model.ClipboardEntry
import com.sameerasw.airsync.ui.theme.ExtraCornerRadius
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ClipboardScreen(
    clipboardHistory: List<ClipboardEntry>,
    isConnected: Boolean,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top Bar
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 12.dp),
            shape = RoundedCornerShape(ExtraCornerRadius),
            color = MaterialTheme.colorScheme.surfaceVariant
        ){}

        // Spacer pushes content to bottom
        Spacer(modifier = Modifier.weight(1f))

        // History List or Empty State
        if (clipboardHistory.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = if (isConnected) "No clipboard history yet" else "Connect to start syncing clipboard",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        text = if (isConnected) "Clipboard updates will appear here" else "Clipboard history is cleared on disconnect",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outlineVariant
                    )
                }
            }


            // Spacer pushes content to bottom
            Spacer(modifier = Modifier.weight(1f))

        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
                reverseLayout = true
            ) {
                items(clipboardHistory) { entry ->
                    ClipboardEntryBubble(
                        entry = entry,
                        onBubbleTap = {
                            clipboardManager.setText(AnnotatedString(entry.text))
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ClipboardEntryBubble(
    entry: ClipboardEntry,
    onBubbleTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isSent = !entry.isFromPc
    val bubbleColor = if (isSent) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val textColor = if (isSent) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeString = remember(entry.timestamp) {
        timeFormat.format(Date(entry.timestamp))
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = if (isSent) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .wrapContentHeight()
                .clickable { onBubbleTap() },
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isSent) 12.dp else 4.dp,
                bottomEnd = if (isSent) 4.dp else 12.dp
            ),
            color = bubbleColor
        ) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = entry.text,
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeString,
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = 0.7f)
                    )
                }
            }
        }
    }
}
