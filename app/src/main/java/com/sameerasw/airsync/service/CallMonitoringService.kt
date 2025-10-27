package com.sameerasw.airsync.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.IBinder
import android.telephony.TelephonyManager
import android.util.Log
import com.sameerasw.airsync.utils.CallStateManager

/**
 * Background service to monitor phone call states and send updates to the Mac app.
 * This service runs whenever the app is connected to detect incoming/outgoing calls.
 */
class CallMonitoringService : Service() {
    companion object {
        private const val TAG = "CallMonitoringService"
        private var callStateReceiver: CallStateReceiver? = null

        fun startMonitoring(context: Context) {
            try {
                val intent = Intent(context, CallMonitoringService::class.java)
                context.startService(intent)
                Log.d(TAG, "Call monitoring service start requested")
            } catch (e: Exception) {
                Log.e(TAG, "Error starting call monitoring service: ${e.message}")
            }
        }

        fun stopMonitoring(context: Context) {
            try {
                val intent = Intent(context, CallMonitoringService::class.java)
                context.stopService(intent)
                Log.d(TAG, "Call monitoring service stop requested")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping call monitoring service: ${e.message}")
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "CallMonitoringService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "CallMonitoringService started")

        try {
            // Register broadcast receiver for call state changes
            if (callStateReceiver == null) {
                callStateReceiver = CallStateReceiver()
                val intentFilter = IntentFilter().apply {
                    addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED)
                    addAction("android.intent.action.NEW_OUTGOING_CALL")
                }
                registerReceiver(callStateReceiver, intentFilter, Context.RECEIVER_EXPORTED)
                Log.d(TAG, "CallStateReceiver registered successfully")
            }

            // Initialize call state monitoring
            CallStateManager.startMonitoring(this)
            Log.d(TAG, "Call state monitoring initialized")
        } catch (e: SecurityException) {
            Log.w(TAG, "SecurityException during service startup: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "Error in onStartCommand: ${e.message}")
        }

        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            if (callStateReceiver != null) {
                unregisterReceiver(callStateReceiver)
                callStateReceiver = null
                Log.d(TAG, "CallStateReceiver unregistered")
            }
            CallStateManager.stopMonitoring()
            Log.d(TAG, "CallMonitoringService destroyed and monitoring stopped")
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping call monitoring: ${e.message}")
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

