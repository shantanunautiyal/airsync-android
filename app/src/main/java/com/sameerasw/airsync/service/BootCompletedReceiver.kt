package com.sameerasw.airsync.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.sameerasw.airsync.data.local.DataStoreManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking

/**
 * BroadcastReceiver for android.intent.action.BOOT_COMPLETED
 * Restarts call monitoring service if it was enabled before device reboot
 */
class BootCompletedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent?.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        Log.d(TAG, "Device boot completed, checking call monitoring status")

        // Check if call monitoring was enabled
        val isEnabled = runBlocking {
            try {
                DataStoreManager.getInstance(context).getCallSyncEnabled().first()
            } catch (e: Exception) {
                Log.e(TAG, "Error reading call sync enabled setting", e)
                false
            }
        }

        if (isEnabled) {
            Log.d(TAG, "Call monitoring was enabled, restarting service")
            CallMonitorService.start(context)
        }
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
}
