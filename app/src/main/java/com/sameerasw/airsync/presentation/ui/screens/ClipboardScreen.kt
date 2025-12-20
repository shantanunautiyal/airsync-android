package com.sameerasw.airsync.presentation.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.content.ClipDescription
import android.view.DragEvent
import android.util.Log
import com.sameerasw.airsync.domain.model.ClipboardEntry
import com.sameerasw.airsync.ui.theme.ExtraCornerRadius
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ClipboardScreen(
    clipboardHistory: List<ClipboardEntry>,
    isConnected: Boolean,
    onSendText: (String) -> Unit,
    onClearHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboardManager.current
    var inputText by remember { mutableStateOf("") }
    var isDraggingOver by remember { mutableStateOf(false) }
    val lazyListState = rememberLazyListState()
    val view = LocalView.current

    // Auto-scroll to newest message when clipboard history changes
    LaunchedEffect(clipboardHistory.size) {
        if (clipboardHistory.isNotEmpty()) {
            lazyListState.animateScrollToItem(0)
        }
    }

    // Helper function to extract text from drag event
    fun handleDroppedContent(event: DragEvent): Boolean {
        return try {
            if (event.clipData != null && event.clipData.itemCount > 0) {
                val item = event.clipData.getItemAt(0)
                val text = when {
                    item.text != null -> item.text.toString()
                    item.uri != null -> item.uri.toString()
                    else -> null
                }
                if (!text.isNullOrBlank()) {
                    Log.d("ClipboardScreen", "Dropped text: ${text.take(50)}")
                    onSendText(text)
                    true
                } else {
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            Log.e("ClipboardScreen", "Error handling dropped content: ${e.message}", e)
            false
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        // Set up drag listener for the entire view
        LaunchedEffect(Unit) {
            view.setOnDragListener { _, event ->
                when (event.action) {
                    DragEvent.ACTION_DRAG_STARTED -> {
                        // Check if the dragged data is text
                        event.clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) ||
                        event.clipDescription.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML)
                    }
                    DragEvent.ACTION_DRAG_ENTERED -> {
                        isDraggingOver = true
                        true
                    }
                    DragEvent.ACTION_DRAG_LOCATION -> true
                    DragEvent.ACTION_DRAG_EXITED -> {
                        isDraggingOver = false
                        true
                    }
                    DragEvent.ACTION_DROP -> {
                        isDraggingOver = false
                        handleDroppedContent(event)
                    }
                    DragEvent.ACTION_DRAG_ENDED -> {
                        isDraggingOver = false
                        true
                    }
                    else -> false
                }
            }
        }

        // Visual feedback when dragging over
        if (isDraggingOver && isConnected) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                shape = RoundedCornerShape(ExtraCornerRadius),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = "Drop to send",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }

        // Clear button - only show when connected and chat not empty
        if (isConnected && clipboardHistory.isNotEmpty()) {
            Button(
                onClick = onClearHistory,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Text("Clear History")
            }
        }

        // Scrollable Message Area - takes remaining space
        Box(modifier = Modifier.weight(1f)) {
            // History List or Empty State
            if (clipboardHistory.isEmpty()) {
                // Just show nothing when empty - spacer will center it visually
                Box(modifier = Modifier.fillMaxWidth())
            } else {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    reverseLayout = true
                ) {
                    items(
                        items = clipboardHistory,
                        key = { entry -> entry.id }
                    ) { entry ->
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

        // Chat Input Box - always visible at bottom when connected
        if (isConnected) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(),
                shape = RoundedCornerShape(topStart = ExtraCornerRadius, topEnd = ExtraCornerRadius),
                color = MaterialTheme.colorScheme.surfaceContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 18.dp, max = 100.dp),
                        placeholder = {
                            Text(
                                text = "Type a message or drag text here...",
                                style = MaterialTheme.typography.bodySmall
                            )
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                            focusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        singleLine = false,
                        maxLines = 4
                    )

                    IconButton(
                        onClick = {
                            if (inputText.isNotBlank()) {
                                onSendText(inputText)
                                inputText = ""
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .background(
                                color = if (inputText.isNotBlank()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = CircleShape
                            ),
                        enabled = inputText.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send",
                            tint = if (inputText.isNotBlank()) {
                                MaterialTheme.colorScheme.onPrimary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        )
                    }
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
