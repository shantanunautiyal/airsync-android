package com.sameerasw.airsync.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.telephony.TelephonyManager
import android.util.Log
import com.sameerasw.airsync.utils.CallStateManager

/**
 * Broadcast receiver to detect incoming calls and call state changes.
 * This catches both system calls and third-party calling app calls.
 */
class CallStateReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "CallStateReceiver"

        // Deduplication: track last state changes to avoid duplicate processing
        private var lastProcessedState: String? = null
        private var lastProcessedTime: Long = 0L
        private const val DEDUP_WINDOW_MS = 300L // Ignore same state within 300ms

        fun registerReceiver(context: Context) {
            try {
                val receiver = CallStateReceiver()
                val intentFilter = IntentFilter().apply {
                    addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
                    addAction("com.android.internal.telephony.PHONE_STATE_CHANGED")
                    addAction("android.intent.action.NEW_OUTGOING_CALL")
                }
                // Use RECEIVER_EXPORTED flag for Android 12+ (API 31+), with fallback for older versions
                @Suppress("RECEIVER_EXPORTED")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    context.registerReceiver(receiver, intentFilter, Context.RECEIVER_EXPORTED)
                } else {
                    @Suppress("DEPRECATION")
                    context.registerReceiver(receiver, intentFilter)
                }
                Log.d(TAG, "CallStateReceiver registered")
            } catch (e: Exception) {
                Log.e(TAG, "Error registering CallStateReceiver: ${e.message}")
            }
        }
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return

        try {
            when (intent.action) {
                TelephonyManager.ACTION_PHONE_STATE_CHANGED -> {
                    handlePhoneStateChanged(intent)
                }
                "android.intent.action.NEW_OUTGOING_CALL" -> {
                    handleOutgoingCall(intent)
                }
                "com.android.internal.telephony.PHONE_STATE_CHANGED" -> {
                    handlePhoneStateChanged(intent)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling broadcast: ${e.message}")
        }
    }

    private fun handlePhoneStateChanged(intent: Intent) {
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        @Suppress("DEPRECATION")
        val incomingNumber = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        // Deduplication: skip if we just processed this exact state
        val now = System.currentTimeMillis()
        val stateKey = "$state:$incomingNumber"
        if (lastProcessedState == stateKey && (now - lastProcessedTime) < DEDUP_WINDOW_MS) {
            Log.d(TAG, "Skipping duplicate phone state broadcast: $state")
            return
        }
        lastProcessedState = stateKey
        lastProcessedTime = now

        Log.d(TAG, "Phone state changed: $state, incoming number: $incomingNumber")

        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> {
                // Incoming call ringing with phone number
                if (!incomingNumber.isNullOrEmpty()) {
                    Log.d(TAG, "Incoming call from: $incomingNumber")
                    CallStateManager.recordIncomingCall(incomingNumber)
                } else {
                    Log.d(TAG, "Ringing but no incoming number available")
                }
            }
            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // Call is active (could be incoming or outgoing)
                Log.d(TAG, "Call active (OFFHOOK)")
                if (!incomingNumber.isNullOrEmpty()) {
                    // If we have a number, it might be outgoing - record it as outgoing
                    Log.d(TAG, "OFFHOOK with number: $incomingNumber - updating call")
                    CallStateManager.recordOutgoingCall(incomingNumber)
                }
                CallStateManager.markCallActive()
            }
            TelephonyManager.EXTRA_STATE_IDLE -> {
                // Call ended
                Log.d(TAG, "Call ended (IDLE)")
                CallStateManager.markCallEnded()
            }
        }
    }

    private fun handleOutgoingCall(intent: Intent) {
        val outgoingNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER)
        Log.d(TAG, "Outgoing call detected: $outgoingNumber")

        if (!outgoingNumber.isNullOrEmpty()) {
            CallStateManager.recordOutgoingCall(outgoingNumber)
        }
    }
}

