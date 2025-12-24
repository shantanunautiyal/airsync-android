package com.sameerasw.airsync.utils

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import android.text.TextUtils
import androidx.core.content.ContextCompat
import com.sameerasw.airsync.service.MediaNotificationListener
import androidx.core.net.toUri

object PermissionUtil {

    // Special permission constants
    const val NOTIFICATION_ACCESS = "notification_access"
    const val ACCESSIBILITY_SERVICE = "accessibility_service"
    const val BACKGROUND_APP_USAGE = "background_app_usage"
    const val HEALTH_CONNECT = "health_connect"

    fun isNotificationListenerEnabled(context: Context): Boolean {
        val componentName = ComponentName(context, MediaNotificationListener::class.java)
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return !TextUtils.isEmpty(flat) && flat.contains(componentName.flattenToString())
    }

    /**
     * Check if the app is whitelisted from battery optimization
     */
    fun isBatteryOptimizationDisabled(context: Context): Boolean {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            return powerManager.isIgnoringBatteryOptimizations(context.packageName)
    }

    /**
     * Check if battery optimization permission is required for this Android version
     */
    fun isBatteryOptimizationPermissionRequired(): Boolean {
        return true // for min API level
    }

    /**
     * Open battery optimization settings for the app
     */
    fun openBatteryOptimizationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = "package:${context.packageName}".toUri()
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Fallback to app-specific battery settings
            openAppSpecificBatterySettings(context)
        }
    }

    /**
     * Open app-specific battery settings as alternative
     */
    fun openAppSpecificBatterySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Final fallback to general battery optimization settings
            openGeneralBatteryOptimizationSettings(context)
        }
    }

    /**
     * Open general battery optimization settings as fallback
     */
    fun openGeneralBatteryOptimizationSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Final fallback to general settings
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
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

    /**
     * Check if MANAGE_EXTERNAL_STORAGE permission is granted (Android 11+)
     */
    fun hasManageExternalStoragePermission(): Boolean {
            return try {
                android.os.Environment.isExternalStorageManager()
            } catch (_: Exception) {
                false
            }
        }

    /**
     * Open MANAGE_EXTERNAL_STORAGE permission settings
     */
    fun openManageExternalStorageSettings(context: Context) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = "package:${context.packageName}".toUri()
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                // Fallback to app settings
                openAppSpecificBatterySettings(context)
            }
        }

    /**
     * Check if wallpaper access is available
     */
    fun hasWallpaperAccess(): Boolean {
        return hasManageExternalStoragePermission()
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

        // Check battery optimization permission
        if (isBatteryOptimizationPermissionRequired() && !isBatteryOptimizationDisabled(context)) {
            missing.add("Background App Usage")
        }

        // Check wallpaper access permission (optional)
        if (!hasWallpaperAccess()) {
            missing.add("Wallpaper Access")
        }

        // Check call log permission (optional)
        if (!isCallLogPermissionGranted(context)) {
            missing.add("Call Log Access")
        }

        // Check contacts permission (optional)
        if (!isContactsPermissionGranted(context)) {
            missing.add("Contacts Access")
        }

        // Check phone state permission (optional)
        if (!isPhoneStatePermissionGranted(context)) {
            missing.add("Phone Access")
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

        // Battery optimization is recommended for better reliability
        if (isBatteryOptimizationPermissionRequired() && !isBatteryOptimizationDisabled(context)) {
            optional.add("Background App Usage")
        }

        // Wallpaper access is optional for wallpaper sync feature
        if (!hasWallpaperAccess()) {
            optional.add("Wallpaper Access")
        }

        // Call log access is optional for call log sync
        if (!isCallLogPermissionGranted(context)) {
            optional.add("Call Log Access")
        }

        // Contacts access is optional for contacts sync
        if (!isContactsPermissionGranted(context)) {
            optional.add("Contacts Access")
        }

        // Phone state access is optional
        if (!isPhoneStatePermissionGranted(context)) {
            optional.add("Phone Access")
        }

        return optional
    }

    /**
     * Check if READ_CALL_LOG permission is granted
     */
    fun isCallLogPermissionGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CALL_LOG
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if READ_CONTACTS permission is granted
     */
    fun isContactsPermissionGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if READ_PHONE_STATE permission is granted
     */
    fun isPhoneStatePermissionGranted(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if READ_MEDIA_IMAGES permission is granted (Android 13+)
     */
    fun hasReadMediaImagesPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_MEDIA_IMAGES
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            // For older versions, check READ_EXTERNAL_STORAGE
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Open accessibility settings
     */
    fun openAccessibilitySettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            openAppSettings(context)
        }
    }

    /**
     * Open Health Connect permissions
     */
    fun openHealthConnectPermissions(context: Context) {
        try {
            val intent = Intent("android.health.connect.action.MANAGE_HEALTH_PERMISSIONS").apply {
                putExtra("android.intent.extra.PACKAGE_NAME", context.packageName)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            openAppSettings(context)
        }
    }

    /**
     * Open app settings
     */
    fun openAppSettings(context: Context) {
        try {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            val intent = Intent(Settings.ACTION_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }

    /**
     * Get runtime permissions that can be requested
     */
    fun getRuntimePermissionsToRequest(context: Context): List<String> {
        val permissions = mutableListOf<String>()

        // POST_NOTIFICATIONS (Android 13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!isPostNotificationPermissionGranted(context)) {
                permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        // Call log permission
        if (!isCallLogPermissionGranted(context)) {
            permissions.add(Manifest.permission.READ_CALL_LOG)
        }

        // Contacts permission
        if (!isContactsPermissionGranted(context)) {
            permissions.add(Manifest.permission.READ_CONTACTS)
        }

        // Phone state permission
        if (!isPhoneStatePermissionGranted(context)) {
            permissions.add(Manifest.permission.READ_PHONE_STATE)
        }

        return permissions
    }

    /**
     * Get all permission groups for the permissions screen
     */
    fun getAllPermissionGroups(context: Context): List<com.sameerasw.airsync.models.PermissionGroup> {
        return listOf(
            com.sameerasw.airsync.models.PermissionGroup(
                title = "Core",
                description = "Essential permissions for app functionality",
                category = com.sameerasw.airsync.models.PermissionCategory.CORE,
                permissions = listOf(
                    com.sameerasw.airsync.models.PermissionInfo(
                        permission = NOTIFICATION_ACCESS,
                        displayName = "Notification Access",
                        description = "Required to sync notifications from your phone",
                        category = com.sameerasw.airsync.models.PermissionCategory.CORE,
                        isGranted = isNotificationListenerEnabled(context),
                        isRequired = true,
                        requiresSpecialHandling = true
                    ),
                    com.sameerasw.airsync.models.PermissionInfo(
                        permission = BACKGROUND_APP_USAGE,
                        displayName = "Background App Usage",
                        description = "Keeps the app running in the background",
                        category = com.sameerasw.airsync.models.PermissionCategory.CORE,
                        isGranted = isBatteryOptimizationDisabled(context),
                        isRequired = false,
                        requiresSpecialHandling = true
                    )
                )
            ),
            com.sameerasw.airsync.models.PermissionGroup(
                title = "Calls",
                description = "Permissions for call-related features",
                category = com.sameerasw.airsync.models.PermissionCategory.CALLS,
                permissions = listOf(
                    com.sameerasw.airsync.models.PermissionInfo(
                        permission = Manifest.permission.READ_CALL_LOG,
                        displayName = "Call Log",
                        description = "View recent calls on your Mac",
                        category = com.sameerasw.airsync.models.PermissionCategory.CALLS,
                        isGranted = isCallLogPermissionGranted(context),
                        isRequired = false,
                        requiresSpecialHandling = false
                    ),
                    com.sameerasw.airsync.models.PermissionInfo(
                        permission = Manifest.permission.READ_CONTACTS,
                        displayName = "Contacts",
                        description = "Show caller names in notifications",
                        category = com.sameerasw.airsync.models.PermissionCategory.CALLS,
                        isGranted = isContactsPermissionGranted(context),
                        isRequired = false,
                        requiresSpecialHandling = false
                    ),
                    com.sameerasw.airsync.models.PermissionInfo(
                        permission = Manifest.permission.READ_PHONE_STATE,
                        displayName = "Phone State",
                        description = "Detect incoming calls",
                        category = com.sameerasw.airsync.models.PermissionCategory.CALLS,
                        isGranted = isPhoneStatePermissionGranted(context),
                        isRequired = false,
                        requiresSpecialHandling = false
                    )
                )
            )
        )
    }
}
