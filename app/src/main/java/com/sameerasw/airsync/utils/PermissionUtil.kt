package com.sameerasw.airsync.utils

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.sameerasw.airsync.service.MediaNotificationListener

object PermissionUtil {

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val componentName = ComponentName(context, MediaNotificationListener::class.java)
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return !TextUtils.isEmpty(flat) && flat.contains(componentName.flattenToString())
    }

    /**
     * Check if POST_NOTIFICATIONS permission is granted (Android 13+)
     */
    fun isPostNotificationPermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required on older versions
        }
    }

    /**
     * Check if notification permissions are required for this Android version
     */
    fun isNotificationPermissionRequired(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    }

    fun openNotificationListenerSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }

    fun getAllMissingPermissions(context: Context): List<String> {
        val missing = mutableListOf<String>()

        // Check notification listener permission (always required)
        if (!isNotificationListenerEnabled(context)) {
            missing.add("Notification Access")
        }

        // Check POST_NOTIFICATIONS permission (Android 13+)
        if (isNotificationPermissionRequired() && !isPostNotificationPermissionGranted(context)) {
            missing.add("Post Notifications")
        }

        return missing
    }

    /**
     * Get permissions that are critical for app functionality
     */
    fun getCriticalMissingPermissions(context: Context): List<String> {
        val critical = mutableListOf<String>()

        // Notification listener is critical for the app's main functionality
        if (!isNotificationListenerEnabled(context)) {
            critical.add("Notification Access")
        }

        return critical
    }

    /**
     * Get permissions that are optional but recommended
     */
    fun getOptionalMissingPermissions(context: Context): List<String> {
        val optional = mutableListOf<String>()

        // POST_NOTIFICATIONS is recommended but not critical
        if (isNotificationPermissionRequired() && !isPostNotificationPermissionGranted(context)) {
            optional.add("Post Notifications")
        }

        return optional
    }
}
