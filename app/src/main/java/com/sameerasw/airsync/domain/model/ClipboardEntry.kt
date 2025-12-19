package com.sameerasw.airsync.domain.model

/**
 * Represents a clipboard entry in the clipboard history
 * @param id Unique identifier for the entry
 * @param text The clipboard text content
 * @param timestamp When this entry was added
 * @param isFromPc True if received from PC, false if sent from Android
 */
data class ClipboardEntry(
    val id: String,
    val text: String,
    val timestamp: Long,
    val isFromPc: Boolean
)

