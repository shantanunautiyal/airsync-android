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
     */
    fun injectText(text: String): Boolean {
        return try {
            val rootNode = this.rootInActiveWindow
            if (rootNode == null) {
                Log.e(TAG, "Root node is null, cannot inject text")
                return false
            }
            
            val focusedNode = rootNode.findFocus(android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT)
            if (focusedNode == null) {
                Log.w(TAG, "No focused input field found")
                rootNode.recycle()
                return false
            }
            
            // Get current text and append new text
            val currentText = focusedNode.text?.toString() ?: ""
            val newText = currentText + text
            
            val arguments = android.os.Bundle()
            arguments.putCharSequence(android.view.accessibility.AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, newText)
            val result = focusedNode.performAction(android.view.accessibility.AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            
            focusedNode.recycle()
            rootNode.recycle()
            
            Log.d(TAG, "Text injection ${if (result) "succeeded" else "failed"}: $text")
            return result
        } catch (e: Exception) {
            Log.e(TAG, "Error injecting text", e)
            false
        }
    }
    
    /**
     * Inject key event using accessibility service
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
                            false
                        }
                    } else {
                        false
                    }
                    focusedNode?.recycle()
                    rootNode?.recycle()
                    Log.d(TAG, "Backspace key ${if (result) "succeeded" else "failed"}")
                    result
                }
                66 -> injectText("\n") // KEYCODE_ENTER
                62 -> injectText(" ")  // KEYCODE_SPACE
                61 -> injectText("\t") // KEYCODE_TAB
                else -> {
                    Log.w(TAG, "Unsupported key code: $keyCode")
                    false
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error injecting key event", e)
            false
        }
    }
}
