package com.sameerasw.airsync.utils

import android.util.Log
import com.sameerasw.airsync.domain.model.CallState
import com.sameerasw.airsync.domain.model.CallStatus
import com.sameerasw.airsync.domain.model.CallType

/**
 * Utility to detect calls from third-party apps via notification analysis.
 * Supports WhatsApp, Skype, Google Meet, Telegram, Discord, etc.
 */
object ThirdPartyCallDetector {
    private const val TAG = "ThirdPartyCallDetector"

    private var lastDetectedCallId: String? = null
    private var lastDetectedCallTime: Long = 0L

    // Map of calling app package names and their display names
    private val CALLING_APPS = mapOf(
        "com.whatsapp" to "WhatsApp",
        "com.whatsapp.w4b" to "WhatsApp Business",
        "com.skype.raider" to "Skype",
        "com.google.android.apps.meetings" to "Google Meet",
        "org.telegram.messenger" to "Telegram",
        "com.discord" to "Discord",
        "com.viber.voip" to "Viber",
        "com.facebook.orca" to "Messenger",
        "com.google.android.gms" to "Google Duo",
        "com.jami" to "Jami",
        "org.jitsi.meet" to "Jitsi Meet",
        "com.google.android.dialer" to "Phone" // System dialer
    )

    /**
     * Check if a notification is from the dialer app (outgoing/active call)
     * The dialer shows contact name when a call is active
     */
    fun detectDialerCallNotification(
        packageName: String,
        title: String,
        body: String
    ): Pair<String, CallType>? {
        if (packageName != "com.google.android.dialer") return null
        if (title.isEmpty()) return null

        // The dialer notification title is usually the contact name during call
        // Skip if it's a system message or empty
        val isSystemMessage = title.lowercase() in listOf(
            "call", "phone", "incoming call", "outgoing call", "dialer", "phone"
        )

        if (isSystemMessage || title.length < 2) {
            Log.d(TAG, "Skipping system message: title=$title")
            return null
        }

        Log.d(TAG, "Dialer notification detected: title='$title', body='$body'")

        // Determine call type based on body or patterns
        val isIncoming = body.contains("incoming", ignoreCase = true) ||
                         body.contains("from", ignoreCase = true)
        val isOutgoing = body.contains("outgoing", ignoreCase = true) ||
                         body.contains("calling", ignoreCase = true) ||
                         body.isEmpty() // When active, usually just shows contact name

        val callType = when {
            isIncoming -> CallType.INCOMING
            isOutgoing || body.isEmpty() -> CallType.OUTGOING
            else -> CallType.OUTGOING // Default to outgoing for dialer
        }

        Log.d(TAG, "Detected dialer call: name='$title', type=$callType")
        return Pair(title, callType)
    }

    /**
     * Check if a notification title suggests an incoming call.
     * Returns the caller info if a call is detected.
     */
    fun detectCallFromNotification(
        packageName: String,
        title: String,
        body: String
    ): Pair<String, String>? {
        val appName = CALLING_APPS[packageName] ?: return null

        // Common patterns for incoming call notifications
        val callPatterns = listOf(
            Regex("(calling|ringing|incoming call)", RegexOption.IGNORE_CASE),
            Regex("(video|voice|call)\\s+(from|with)", RegexOption.IGNORE_CASE),
        )

        val allText = "$title $body"

        for (pattern in callPatterns) {
            if (pattern.containsMatchIn(allText)) {
                Log.d(TAG, "Call detected in $appName: $title")

                // Extract caller name from title or body
                val callerName = extractCallerName(title, body) ?: "Unknown"
                return Pair(callerName, appName)
            }
        }

        return null
    }

    /**
     * Record a dialer call (incoming or outgoing)
     */
    fun recordDialerCall(
        contactName: String,
        callType: CallType
    ) {
        Log.d(TAG, "Recording dialer call: $callType - $contactName")

        // Update call with the contact name from notification
        // Phone number will come from the broadcast later
        CallStateManager.updateCallWithContactName(contactName, callType)
    }

    /**
     * Record a call from a third-party app
     */
    fun recordThirdPartyIncomingCall(
        packageName: String,
        callerName: String,
        appName: String,
        phoneNumber: String = callerName
    ) {
        // Debounce duplicate detections
        val callId = "$packageName:$callerName"
        if (callId == lastDetectedCallId && System.currentTimeMillis() - lastDetectedCallTime < 5000) {
            Log.d(TAG, "Ignoring duplicate call detection")
            return
        }

        lastDetectedCallId = callId
        lastDetectedCallTime = System.currentTimeMillis()

        Log.d(TAG, "Recording third-party incoming call from $appName: $callerName")

        val callState = CallState(
            phoneNumber = phoneNumber,
            callerName = callerName,
            callType = CallType.INCOMING,
            callState = CallStatus.RINGING,
            appName = appName,
            packageName = packageName
        )

        notifyCallStateChange(callState)
    }

    /**
     * Extract caller name from notification text
     */
    private fun extractCallerName(title: String, body: String): String? {
        // Remove common prefixes
        val cleanTitle = title
            .replace(Regex("^(incoming call|call from|calling|video call|voice call)\\s+", RegexOption.IGNORE_CASE), "")
            .trim()

        if (cleanTitle.isNotEmpty() && cleanTitle.length > 2) {
            return cleanTitle
        }

        val cleanBody = body
            .replace(Regex("^(calling|ringing).*", RegexOption.IGNORE_CASE), "")
            .trim()

        return if (cleanBody.isNotEmpty()) cleanBody else null
    }

    private fun notifyCallStateChange(callState: CallState) {
        val json = JsonUtil.createCallStateJson(
            id = callState.id,
            phoneNumber = callState.phoneNumber,
            callerName = callState.callerName,
            callType = callState.callType.name,
            callStatus = callState.callState.name,
            duration = callState.duration
        )

        // Send via WebSocket
        if (WebSocketUtil.isConnected()) {
            WebSocketUtil.sendMessage(json)
            Log.d(TAG, "Sent third-party call notification to Mac: ${callState.appName} - ${callState.callerName}")
        }
    }
}

