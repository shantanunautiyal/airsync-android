package com.sameerasw.airsync.domain.model

import java.util.UUID

/**
 * Represents a call event with comprehensive call metadata.
 * Used for both real-time events (via WebSocket) and call-log synchronization (via HTTP).
 */
data class CallEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val deviceId: String,
    val timestamp: Long,
    val direction: String, // "incoming" | "outgoing"
    val state: String, // "ringing" | "offhook" | "idle" | "missed"
    val number: String,
    val normalizedNumber: String? = null,
    val contactName: String? = null,
    val contactPhoto: String? = null, // base64-encoded photo (max 256px), only for initial ringing/offhook
    val simSlot: Int? = null,
    val callLogId: Long? = null,
    val durationSec: Long? = null
)

/**
 * Batch payload for sending multiple call events via HTTP POST
 */
data class CallLogBatch(
    val deviceId: String,
    val events: List<CallEvent>,
    val lastSyncTimestamp: Long
)

/**
 * Call state enum - represents the lifecycle state of a call
 */
enum class CallState(val displayName: String) {
    RINGING("Ringing"),
    OFFHOOK("Connected"),
    IDLE("Ended"),
    MISSED("Missed")
}

/**
 * Call direction enum
 */
enum class CallDirection(val displayName: String, val apiValue: String) {
    INCOMING("Incoming", "incoming"),
    OUTGOING("Outgoing", "outgoing")
}
