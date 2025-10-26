package com.sameerasw.airsync.utils

import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

object HapticUtil {
    /**
     * Perform a light tick haptic - for subtle interactions like swiping
     */
    fun performLightTick(haptics: HapticFeedback?) {
        try {
            haptics?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        } catch (_: Exception) {}
    }

    /**
     * Perform a medium tick haptic - for toggles and buttons
     */
    fun performClick(haptics: HapticFeedback?) {
        try {
            haptics?.performHapticFeedback(HapticFeedbackType.LongPress)
        } catch (_: Exception) {}
    }

    /**
     * Perform a toggle ON haptic - slightly stronger
     */
    fun performToggleOn(haptics: HapticFeedback?) {
        try {
            haptics?.performHapticFeedback(HapticFeedbackType.LongPress)
        } catch (_: Exception) {}
    }

    /**
     * Perform a toggle OFF haptic - slightly lighter
     */
    fun performToggleOff(haptics: HapticFeedback?) {
        try {
            haptics?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        } catch (_: Exception) {}
    }

    /**
     * Perform a success haptic - for successful connections
     */
    fun performSuccess(haptics: HapticFeedback?) {
        try {
            haptics?.performHapticFeedback(HapticFeedbackType.LongPress)
            CoroutineScope(Dispatchers.Main).launch {
                delay(100)
                haptics?.performHapticFeedback(HapticFeedbackType.LongPress)
            }
        } catch (_: Exception) {}
    }

    /**
     * Perform an error haptic - for failed connections
     */
    fun performError(haptics: HapticFeedback?) {
        try {
            haptics?.performHapticFeedback(HapticFeedbackType.LongPress)
            CoroutineScope(Dispatchers.Main).launch {
                delay(100)
                haptics?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                delay(100)
                haptics?.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
        } catch (_: Exception) {}
    }

    /**
     * Start repeating haptic ticks for loading states
     * Returns a Job that can be cancelled to stop the haptics
     */
    fun startLoadingHaptics(haptics: HapticFeedback?): Job {
        return CoroutineScope(Dispatchers.Main).launch {
            while (isActive) {
                performLightTick(haptics)
                delay(200) // 5 times per second
            }
        }
    }
}
