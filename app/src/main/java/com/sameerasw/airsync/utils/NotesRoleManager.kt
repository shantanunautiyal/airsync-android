package com.sameerasw.airsync.utils

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.activity.result.ActivityResultLauncher

/**
 * Manages the Notes Role for the application.
 * The Notes Role enables the app to act as the default note-taking application.
 *
 * Features enabled by Notes Role:
 * - Responds to ACTION_CREATE_NOTE intent
 * - Can be launched from device lock screen
 * - Access to LAUNCH_CAPTURE_CONTENT_ACTIVITY_FOR_NOTE permission
 * - Receives EXTRA_USE_STYLUS_MODE hint for stylus-optimized experience
 */
object NotesRoleManager {
    private const val TAG = "NotesRoleManager"

    /**
     * Checks if the Notes Role is available on this device.
     * The Notes Role is available on Android 14 (API 34) and higher.
     */
    fun isNotesRoleAvailable(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return false
        }
        return try {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
            roleManager.isRoleAvailable(RoleManager.ROLE_NOTES)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if Notes Role is available", e)
            false
        }
    }

    /**
     * Checks if this app currently holds the Notes Role.
     */
    fun isNotesRoleHeld(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return false
        }
        return try {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
            roleManager.isRoleHeld(RoleManager.ROLE_NOTES)
        } catch (e: Exception) {
            Log.e(TAG, "Error checking if Notes Role is held", e)
            false
        }
    }

    /**
     * Requests the Notes Role for this app.
     * Must be called with an ActivityResultLauncher registered in the Activity.
     */
    fun requestNotesRole(
        context: Context,
        launcher: ActivityResultLauncher<Intent>
    ) {
        if (!isNotesRoleAvailable(context)) {
            Log.w(TAG, "Notes Role is not available on this device")
            return
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            return
        }

        try {
            val roleManager = context.getSystemService(Context.ROLE_SERVICE) as RoleManager
            val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_NOTES)
            launcher.launch(intent)
            Log.d(TAG, "Notes Role request intent launched")
        } catch (e: Exception) {
            Log.e(TAG, "Error requesting Notes Role", e)
        }
    }

    /**
     * Extracts the stylus mode hint from the intent that launched the activity.
     * When true, the app should optimize for stylus input (e.g., drawing mode).
     * When false, the app should optimize for keyboard input.
     */
    fun shouldUseStylusMode(intent: Intent?): Boolean {
        if (intent == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        return try {
            intent.getBooleanExtra(Intent.EXTRA_USE_STYLUS_MODE, false)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Checks if the app is running in a floating window/bubble mode.
     * Content capture should only be available in floating window mode.
     */
    fun isRunningInFloatingWindow(activity: android.app.Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return false
        }
        return try {
            activity.isLaunchedFromBubble
        } catch (e: Exception) {
            Log.e(TAG, "Error checking floating window mode", e)
            false
        }
    }
}

