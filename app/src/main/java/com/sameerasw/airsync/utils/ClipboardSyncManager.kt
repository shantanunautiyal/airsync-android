package com.sameerasw.airsync.utils

import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.sameerasw.airsync.data.local.DataStoreManager

object ClipboardSyncManager {
    private const val TAG = "ClipboardSyncManager"

    private var clipboardListener: ClipboardManager.OnPrimaryClipChangedListener? = null
    private var isEnabled = false
    private var lastSentText: String? = null
    private var lastReceivedText: String? = null
    private var syncJob: Job? = null
    private val syncScope = CoroutineScope(Dispatchers.IO)

    /**
     * Start clipboard sync
     */
    fun startSync(context: Context) {
        if (isEnabled) {
            Log.d(TAG, "Clipboard sync already enabled")
            return
        }

        syncJob = syncScope.launch {
            try {
                val dataStoreManager = DataStoreManager(context)
                val isClipboardSyncEnabled = dataStoreManager.getClipboardSyncEnabled().first()

                if (!isClipboardSyncEnabled) {
                    Log.d(TAG, "Clipboard sync is disabled in settings")
                    return@launch
                }

                // Register clipboard listener
                clipboardListener = ClipboardUtil.registerClipboardListener(context) { clipText ->
                    // Avoid infinite loop
                    if (clipText != lastReceivedText && clipText != lastSentText) {
                        syncClipboardToDesktop(clipText)
                    }
                }

                isEnabled = true
                Log.d(TAG, "Clipboard sync started")

            } catch (e: Exception) {
                Log.e(TAG, "Error starting clipboard sync: ${e.message}")
            }
        }
    }

    /**
     * Stop clipboard sync
     */
    fun stopSync(context: Context) {
        if (!isEnabled) return

        clipboardListener?.let { listener ->
            ClipboardUtil.unregisterClipboardListener(context, listener)
            clipboardListener = null
        }

        syncJob?.cancel()
        isEnabled = false
        Log.d(TAG, "Clipboard sync stopped")
    }

    /**
     * Send clipboard text to desktop
     */
    private fun syncClipboardToDesktop(text: String) {
        if (text.isBlank()) return

        try {
            lastSentText = text
            val clipboardJson = JsonUtil.createClipboardUpdateJson(text)

            val success = WebSocketUtil.sendMessage(clipboardJson)
            if (success) {
                Log.d(TAG, "Clipboard synced to desktop: ${text.take(50)}...")
            } else {
                Log.w(TAG, "Failed to sync clipboard to desktop")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error syncing clipboard to desktop: ${e.message}")
        }
    }

    /**
     * Handle clipboard update received from desktop
     */
    fun handleClipboardUpdate(context: Context, text: String) {
        try {
            lastReceivedText = text
            val success = ClipboardUtil.setClipboardText(context, text)
            if (success) {
                Log.d(TAG, "Clipboard updated from desktop: ${text.take(50)}...")
            } else {
                Log.w(TAG, "Failed to update clipboard from desktop")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling clipboard update: ${e.message}")
        }
    }

    /**
     * Manually sync specific text (for share target functionality)
     */
    fun syncTextToDesktop(text: String) {
        if (text.isBlank()) return

        try {
            val clipboardJson = JsonUtil.createClipboardUpdateJson(text)

            val success = WebSocketUtil.sendMessage(clipboardJson)
            if (success) {
                Log.d(TAG, "Text shared to desktop: ${text.take(50)}...")
            } else {
                Log.w(TAG, "Failed to share text to desktop")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error sharing text to desktop: ${e.message}")
        }
    }

    /**
     * Check if clipboard sync is currently enabled
     */
    fun isClipboardSyncEnabled(): Boolean = isEnabled
}
