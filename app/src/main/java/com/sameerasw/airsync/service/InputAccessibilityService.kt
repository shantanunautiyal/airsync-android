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
        val path = Path().apply {
            moveTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 1))
            .build()

        dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                super.onCompleted(gestureDescription)
                Log.d(TAG, "Gesture completed")
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                super.onCancelled(gestureDescription)
                Log.e(TAG, "Gesture cancelled")
            }
        }, null)
    }
}