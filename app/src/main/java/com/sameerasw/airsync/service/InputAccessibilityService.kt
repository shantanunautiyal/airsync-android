package com.sameerasw.airsync.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class InputAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "InputAccessibilityService"
        var instance: InputAccessibilityService? = null
        
        private const val TAP_DURATION = 1L
        private const val LONG_PRESS_DURATION = 500L
        private const val SWIPE_DURATION = 300L
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Not needed for this implementation
    }

    override fun onInterrupt() {
        // Not needed for this implementation
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.d(TAG, "InputAccessibilityService connected")
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "InputAccessibilityService destroyed")
        instance = null
    }

    fun injectTouchEvent(x: Float, y: Float) {
        injectTap(x, y)
    }

    fun injectTap(x: Float, y: Float) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, TAP_DURATION))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d(TAG, "Tap gesture completed at ($x, $y)")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.e(TAG, "Tap gesture cancelled")
            }
        }, null)
    }

    fun injectLongPress(x: Float, y: Float) {
        val path = Path().apply {
            moveTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, LONG_PRESS_DURATION))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d(TAG, "Long press gesture completed at ($x, $y)")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.e(TAG, "Long press gesture cancelled")
            }
        }, null)
    }

    fun injectSwipe(startX: Float, startY: Float, endX: Float, endY: Float, duration: Long = SWIPE_DURATION) {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d(TAG, "Swipe gesture completed from ($startX, $startY) to ($endX, $endY)")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.e(TAG, "Swipe gesture cancelled")
            }
        }, null)
    }

    fun injectScroll(x: Float, y: Float, deltaX: Float, deltaY: Float) {
        // Convert scroll delta to swipe gesture
        val endX = x + deltaX
        val endY = y + deltaY
        injectSwipe(x, y, endX, endY, 100L)
    }

    fun performBack(): Boolean {
        return try {
            val result = performGlobalAction(GLOBAL_ACTION_BACK)
            Log.d(TAG, "Back action: ${if (result) "success" else "failed"}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error performing back action", e)
            false
        }
    }

    fun performHome(): Boolean {
        return try {
            val result = performGlobalAction(GLOBAL_ACTION_HOME)
            Log.d(TAG, "Home action: ${if (result) "success" else "failed"}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error performing home action", e)
            false
        }
    }

    fun performRecents(): Boolean {
        return try {
            val result = performGlobalAction(GLOBAL_ACTION_RECENTS)
            Log.d(TAG, "Recents action: ${if (result) "success" else "failed"}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error performing recents action", e)
            false
        }
    }

    fun performNotifications(): Boolean {
        return try {
            val result = performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
            Log.d(TAG, "Notifications action: ${if (result) "success" else "failed"}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error performing notifications action", e)
            false
        }
    }

    fun performQuickSettings(): Boolean {
        return try {
            val result = performGlobalAction(GLOBAL_ACTION_QUICK_SETTINGS)
            Log.d(TAG, "Quick settings action: ${if (result) "success" else "failed"}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error performing quick settings action", e)
            false
        }
    }

    fun performPowerDialog(): Boolean {
        return try {
            val result = performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
            Log.d(TAG, "Power dialog action: ${if (result) "success" else "failed"}")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error performing power dialog action", e)
            false
        }
    }

    
    /**
     * Inject text input using accessibility service
     * Falls back to shell command if no focused input field
     */
    fun injectText(text: String): Boolean {
        return try {
            val rootNode = this.rootInActiveWindow
            if (rootNode == null) {
                Log.w(TAG, "Root node is null, trying shell input")
                return injectTextViaShell(text)
            }
            
            val focusedNode = rootNode.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)
            if (focusedNode == null) {
                Log.w(TAG, "No focused input field found, trying shell input")
                rootNode.recycle()
                return injectTextViaShell(text)
            }
            
            // Get current text and append new text
            val currentText = focusedNode.text?.toString() ?: ""
            val newText = currentText + text
            
            val arguments = android.os.Bundle()
            arguments.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
            val result = focusedNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            
            focusedNode.recycle()
            rootNode.recycle()
            
            if (!result) {
                Log.w(TAG, "Accessibility text injection failed, trying shell input")
                return injectTextViaShell(text)
            }
            
            Log.d(TAG, "Text injection succeeded: $text")
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error injecting text via accessibility, trying shell", e)
            return injectTextViaShell(text)
        }
    }
    
    /**
     * Inject text via shell command (fallback)
     */
    private fun injectTextViaShell(text: String): Boolean {
        return try {
            // Escape special characters for shell
            val escapedText = text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("'", "\\'")
                .replace(" ", "\\ ")
                .replace("&", "\\&")
                .replace("|", "\\|")
                .replace(";", "\\;")
                .replace("(", "\\(")
                .replace(")", "\\)")
                .replace("<", "\\<")
                .replace(">", "\\>")
            
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "input text '$escapedText'"))
            val exitCode = process.waitFor()
            val result = exitCode == 0
            Log.d(TAG, "Shell text injection ${if (result) "succeeded" else "failed"}: $text")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error injecting text via shell", e)
            false
        }
    }
    
    /**
     * Inject key event using accessibility service
     * Falls back to shell command for unsupported keys
     */
    fun injectKeyEvent(keyCode: Int): Boolean {
        return try {
            when (keyCode) {
                67 -> { // KEYCODE_DEL - Backspace
                    val rootNode = this.rootInActiveWindow
                    val focusedNode = rootNode?.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)
                    val result = if (focusedNode != null) {
                        val currentText = focusedNode.text?.toString() ?: ""
                        if (currentText.isNotEmpty()) {
                            val newText = currentText.dropLast(1)
                            val arguments = android.os.Bundle()
                            arguments.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
                            focusedNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                        } else {
                            injectKeyViaShell(keyCode)
                        }
                    } else {
                        injectKeyViaShell(keyCode)
                    }
                    focusedNode?.recycle()
                    rootNode?.recycle()
                    Log.d(TAG, "Backspace key ${if (result) "succeeded" else "failed"}")
                    result
                }
                66 -> { // KEYCODE_ENTER
                    if (!injectText("\n")) {
                        injectKeyViaShell(keyCode)
                    } else true
                }
                62 -> { // KEYCODE_SPACE
                    if (!injectText(" ")) {
                        injectKeyViaShell(keyCode)
                    } else true
                }
                61 -> { // KEYCODE_TAB
                    if (!injectText("\t")) {
                        injectKeyViaShell(keyCode)
                    } else true
                }
                // Arrow keys - use shell input keyevent
                19, 20, 21, 22 -> { // DPAD UP, DOWN, LEFT, RIGHT
                    injectKeyViaShell(keyCode)
                }
                else -> {
                    Log.d(TAG, "Using shell for key code: $keyCode")
                    injectKeyViaShell(keyCode)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error injecting key event", e)
            injectKeyViaShell(keyCode)
        }
    }
    
    /**
     * Inject key event via shell command (fallback)
     */
    private fun injectKeyViaShell(keyCode: Int): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("sh", "-c", "input keyevent $keyCode"))
            val exitCode = process.waitFor()
            val result = exitCode == 0
            Log.d(TAG, "Shell key injection ${if (result) "succeeded" else "failed"}: keyCode=$keyCode")
            result
        } catch (e: Exception) {
            Log.e(TAG, "Error injecting key via shell", e)
            false
        }
    }
}
