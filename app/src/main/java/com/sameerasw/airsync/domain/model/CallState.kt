package com.sameerasw.airsync.domain.model

import java.util.*

/**
 * Represents the state of an active or incoming call
 */
data class CallState(
    val id: String = UUID.randomUUID().toString(),
    val phoneNumber: String = "",
    val callerName: String = "",
    val callType: CallType = CallType.INCOMING, // INCOMING or OUTGOING
    val callState: CallStatus = CallStatus.RINGING, // RINGING, ACTIVE, HELD, DISCONNECTED
    val appName: String = "Phone",
    val packageName: String = "com.google.android.dialer",
    val duration: Long = 0L, // Duration in milliseconds
    val timestamp: Long = System.currentTimeMillis()
)

enum class CallType {
    INCOMING,
    OUTGOING
}

enum class CallStatus {
    RINGING,
    ACTIVE,
    HELD,
    DISCONNECTED
}

