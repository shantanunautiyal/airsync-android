package com.sameerasw.airsync.utils

import android.content.ClipboardManager
import android.content.Context
import android.util.Log
import android.util.Patterns
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

                // Register clipboard listener on Main thread
                launch(Dispatchers.Main) {
                    clipboardListener = ClipboardUtil.registerClipboardListener(context) { clipText ->
                        Log.d(TAG, "Clipboard changed detected: ${clipText.take(50)}...")
                        // Avoid infinite loop by checking both sent and received text
                        if (clipText != lastReceivedText && clipText != lastSentText && clipText.isNotBlank()) {
                            Log.d(TAG, "Syncing clipboard to desktop...")
                            syncClipboardToDesktop(clipText)
                        } else {
                            Log.d(TAG, "Skipping clipboard sync - matches last sent/received text")
                        }
                    }

                    isEnabled = true
                    Log.d(TAG, "Clipboard sync started successfully")
                }

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

        syncScope.launch(Dispatchers.IO) {
            try {
                lastSentText = text
                val clipboardJson = JsonUtil.createClipboardUpdateJson(text)

                Log.d(TAG, "Sending clipboard JSON: $clipboardJson")

                val success = WebSocketUtil.sendMessage(clipboardJson)
                if (success) {
                    Log.d(TAG, "Clipboard synced to desktop: ${text.take(50)}...")
                } else {
                    Log.w(TAG, "Failed to sync clipboard to desktop")
                    // Reset lastSentText if sending failed
                    lastSentText = null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error syncing clipboard to desktop: ${e.message}")
                // Reset lastSentText if sending failed
                lastSentText = null
            }
        }
    }

    private fun isLinkOnly(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        val matcher = Patterns.WEB_URL.matcher(trimmed)
        return matcher.matches()
    }

    /**
     * Handle clipboard update received from desktop
     */
    fun handleClipboardUpdate(context: Context, text: String) {
        try {
            lastReceivedText = text

            // Always update clipboard with the full received text
            val updated = ClipboardUtil.setClipboardText(context, text)
            if (updated) {
                Log.d(TAG, "Clipboard updated from desktop: ${text.take(200)}")
            } else {
                Log.w(TAG, "Failed to update clipboard from desktop")
            }

            // Check Continue Browsing setting and show notification only if the text is a pure link
            val dataStoreManager = DataStoreManager(context)
            syncScope.launch {
                val continueEnabled = try { dataStoreManager.getContinueBrowsingEnabled().first() } catch (_: Exception) { true }
                // Only for Plus and while connected
                val isConnected = WebSocketUtil.isConnected()
                val last = try { dataStoreManager.getLastConnectedDevice().first() } catch (_: Exception) { null }
                val isPlus = last?.isPlus == true
                if (continueEnabled && isConnected && isPlus && isLinkOnly(text)) {
                    NotificationUtil.showContinueBrowsingLink(context, text.trim())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling clipboard update: ${e.message}")
        }
    }

    /**
     * Manually sync specific text (share target)
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
}
