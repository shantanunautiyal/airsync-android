package com.sameerasw.airsync.utils

import android.content.Context
import android.util.Log
import com.sameerasw.airsync.domain.model.CallState
import com.sameerasw.airsync.domain.model.CallStatus
import com.sameerasw.airsync.domain.model.CallType
import com.sameerasw.airsync.service.CallStateReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

object CallStateManager {
    private const val TAG = "CallStateManager"

    private val _activeCall = MutableStateFlow<CallState?>(null)
    val activeCall: StateFlow<CallState?> = _activeCall.asStateFlow()

    private var contextRef: WeakReference<Context>? = null
    private var isMonitoring = false

    // Track the last sent call state to avoid duplicates
    private var lastSentPhoneNumber: String? = null
    private var lastSentCallState: CallState? = null
    private var lastSentTime: Long = 0L
    private const val DEDUP_WINDOW_MS = 500L

    /**
     * Initialize call state monitoring using broadcast receiver.
     */
    fun startMonitoring(appContext: Context) {
        if (isMonitoring) {
            Log.d(TAG, "Call monitoring already started")
            return
        }

        contextRef = WeakReference(appContext)
        isMonitoring = true

        try {
            CallStateReceiver.registerReceiver(appContext)
            Log.d(TAG, "Started call monitoring using broadcast receiver")
        } catch (e: Exception) {
            Log.e(TAG, "Error starting call monitoring: ${e.message}")
            isMonitoring = false
        }
    }

    /**
     * Stop monitoring calls
     */
    fun stopMonitoring() {
        try {
            isMonitoring = false
            contextRef = null
            _activeCall.value = null
            lastSentPhoneNumber = null
            lastSentCallState = null
            Log.d(TAG, "Call monitoring stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping call monitoring: ${e.message}")
        }
    }

    /**
     * Record an incoming call with phone number
     * Only updates the state, doesn't send notification yet
     * Wait for contact name from dialer
     */
    fun recordIncomingCall(phoneNumber: String) {
        Log.d(TAG, "Recording incoming call from: $phoneNumber")

        val callState = _activeCall.value?.let { existing ->
            // Preserve the contact name if it exists, just update the number and type
            if (existing.callerName != existing.phoneNumber && existing.callerName.isNotEmpty()) {
                // We have a real name from dialer, keep it
                Log.d(TAG, "Preserving existing caller name: ${existing.callerName}")
                existing.copy(
                    phoneNumber = phoneNumber,
                    callType = CallType.INCOMING,
                    callState = CallStatus.RINGING
                )
            } else {
                // No real name yet, use "Someone" as placeholder
                existing.copy(
                    phoneNumber = phoneNumber,
                    callerName = "Someone",
                    callType = CallType.INCOMING,
                    callState = CallStatus.RINGING
                )
            }
        } ?: run {
            // No existing call, create new one with placeholder name
            CallState(
                phoneNumber = phoneNumber,
                callerName = "Someone",
                callType = CallType.INCOMING,
                callState = CallStatus.RINGING,
                appName = "Phone",
                packageName = "com.android.phone"
            )
        }

        _activeCall.value = callState
        // Don't notify yet - wait for dialer notification with real name
        Log.d(TAG, "Updated call with phone number, waiting for dialer notification with contact name")
    }

    /**
     * Record an outgoing call with phone number
     * Only updates the state, doesn't send notification yet
     * Wait for contact name from dialer
     */
    fun recordOutgoingCall(phoneNumber: String) {
        Log.d(TAG, "Recording outgoing call to: $phoneNumber")

        val callState = _activeCall.value?.let { existing ->
            // Preserve the contact name if it exists
            if (existing.callerName != existing.phoneNumber && existing.callerName.isNotEmpty()) {
                // We have a real name from dialer, keep it
                Log.d(TAG, "Preserving existing caller name: ${existing.callerName}")
                existing.copy(
                    phoneNumber = phoneNumber,
                    callType = CallType.OUTGOING,
                    callState = CallStatus.RINGING
                )
            } else {
                // No real name yet, use "Someone" as placeholder
                existing.copy(
                    phoneNumber = phoneNumber,
                    callerName = "Someone",
                    callType = CallType.OUTGOING,
                    callState = CallStatus.RINGING
                )
            }
        } ?: run {
            CallState(
                phoneNumber = phoneNumber,
                callerName = "Someone",
                callType = CallType.OUTGOING,
                callState = CallStatus.RINGING,
                appName = "Phone",
                packageName = "com.android.phone"
            )
        }

        _activeCall.value = callState
        // Don't notify yet - wait for dialer notification with real name
        Log.d(TAG, "Updated call with phone number, waiting for dialer notification with contact name")
    }

    /**
     * Update call with contact name from dialer notification
     * This is when we finally send the notification to Mac
     * Sets both name and type from the dialer
     */
    fun updateCallWithContactName(contactName: String, callType: CallType) {
        Log.d(TAG, "Updating call with contact name from dialer: '$contactName' (type: $callType)")

        // Use "Someone" if contact name is empty or just whitespace
        val safeName = if (contactName.isBlank()) "Someone" else contactName.trim()

        val callState = _activeCall.value?.let { existing ->
            // Update with the real contact name from dialer
            val updated = existing.copy(
                callerName = safeName,
                callType = callType
                // Phone number is already set from broadcast
            )
            Log.d(TAG, "Updated existing call: name='$safeName', number='${existing.phoneNumber}'")
            updated
        } ?: run {
            // No existing call yet, create one
            Log.d(TAG, "Creating new call from dialer notification with name='$safeName'")
            CallState(
                phoneNumber = "", // Will be filled by broadcast
                callerName = safeName,
                callType = callType,
                callState = CallStatus.RINGING,
                appName = "Phone",
                packageName = "com.google.android.dialer"
            )
        }

        _activeCall.value = callState
        // NOW send notification - we have the real name
        notifyCallStateChange(callState)
    }

    /**
     * Mark the current call as active (OFFHOOK state)
     */
    fun markCallActive() {
        Log.d(TAG, "Call marked as active (OFFHOOK)")

        _activeCall.value?.let { call ->
            if (call.callState != CallStatus.ACTIVE) {
                val updatedCall = call.copy(callState = CallStatus.ACTIVE)
                _activeCall.value = updatedCall
                notifyCallStateChange(updatedCall)
            }
        }
    }

    /**
     * Mark the current call as ended (IDLE state)
     */
    fun markCallEnded() {
        Log.d(TAG, "Call marked as ended (IDLE)")

        _activeCall.value?.let { call ->
            if (call.callState != CallStatus.DISCONNECTED) {
                val updatedCall = call.copy(callState = CallStatus.DISCONNECTED)
                _activeCall.value = updatedCall
                notifyCallStateChange(updatedCall)

                // Auto-dismiss after a short delay
                CoroutineScope(Dispatchers.Default).launch {
                    delay(1000)
                    _activeCall.value = null
                    lastSentPhoneNumber = null
                    lastSentCallState = null
                }
            }
        }
    }

    private fun notifyCallStateChange(callState: CallState) {
        // Deduplication: avoid sending the same call state twice within DEDUP_WINDOW_MS
        val now = System.currentTimeMillis()
        if (lastSentPhoneNumber == callState.phoneNumber &&
            lastSentCallState != null &&
            lastSentCallState!!.callState == callState.callState &&
            (now - lastSentTime) < DEDUP_WINDOW_MS) {
            Log.d(TAG, "Skipping duplicate call state for ${callState.phoneNumber}: ${callState.callState}")
            return
        }

        val json = JsonUtil.createCallStateJson(
            id = callState.id,
            phoneNumber = callState.phoneNumber,
            callerName = callState.callerName,
            callType = callState.callType.name,
            callStatus = callState.callState.name,
            packageName = callState.packageName,
            duration = callState.duration
        )

        if (WebSocketUtil.isConnected()) {
            WebSocketUtil.sendMessage(json)
            lastSentPhoneNumber = callState.phoneNumber
            lastSentCallState = callState
            lastSentTime = now
            Log.d(TAG, "Sent call state: ${callState.callType} - ${callState.callerName} (${callState.phoneNumber}) - ${callState.callState}")
        } else {
            Log.w(TAG, "WebSocket not connected, cannot send call state update")
        }
    }
}

