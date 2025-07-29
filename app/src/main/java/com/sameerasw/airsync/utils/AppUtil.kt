package com.sameerasw.airsync.utils

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.sameerasw.airsync.domain.model.NotificationApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object AppUtil {
    private const val TAG = "AppUtil"

    /**
     * Get all installed apps that can potentially send notifications
     */
    suspend fun getInstalledApps(context: Context): List<NotificationApp> = withContext(Dispatchers.IO) {
        try {
            val packageManager = context.packageManager
            val installedApps = packageManager.getInstalledApplications(PackageManager.GET_META_DATA)

            val notificationApps = mutableListOf<NotificationApp>()

            for (appInfo in installedApps) {
                try {
                    // Skip system apps that don't typically send user notifications
                    if (shouldSkipApp(appInfo)) continue

                    val appName = packageManager.getApplicationLabel(appInfo).toString()
                    val packageName = appInfo.packageName
                    val isSystemApp = isSystemApp(appInfo)
                    val icon = try {
                        packageManager.getApplicationIcon(appInfo)
                    } catch (e: Exception) {
                        null
                    }

                    notificationApps.add(
                        NotificationApp(
                            packageName = packageName,
                            appName = appName,
                            isEnabled = true, // Default to enabled
                            icon = icon,
                            isSystemApp = isSystemApp,
                            lastUpdated = System.currentTimeMillis()
                        )
                    )
                } catch (e: Exception) {
                    Log.w(TAG, "Error processing app ${appInfo.packageName}: ${e.message}")
                }
            }

            // Sort by app name
            notificationApps.sortedBy { it.appName.lowercase() }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting installed apps: ${e.message}")
            emptyList()
        }
    }

    /**
     * Merge installed apps with saved preferences, keeping user settings and adding new apps
     */
    fun mergeWithSavedApps(
        installedApps: List<NotificationApp>,
        savedApps: List<NotificationApp>
    ): List<NotificationApp> {
        val savedAppsMap = savedApps.associateBy { it.packageName }

        return installedApps.map { installedApp ->
            val savedApp = savedAppsMap[installedApp.packageName]
            if (savedApp != null) {
                // Use saved preferences but update app info
                savedApp.copy(
                    appName = installedApp.appName,
                    icon = installedApp.icon,
                    isSystemApp = installedApp.isSystemApp
                )
            } else {
                // New app, use defaults (enabled)
                installedApp
            }
        }.sortedBy { it.appName.lowercase() }
    }

    private fun shouldSkipApp(appInfo: ApplicationInfo): Boolean {
        val packageName = appInfo.packageName

        // Skip our own app
        if (packageName.contains("airsync")) return true

        // Skip common system packages that don't send user notifications
        val systemPackagesToSkip = setOf(
            "com.android.systemui",
            "com.android.settings",
            "com.android.providers.downloads",
            "com.android.providers.media",
            "com.android.providers.calendar",
            "com.android.providers.contacts",
            "com.android.inputmethod",
            "com.android.launcher",
            "com.android.phone",
            "com.android.bluetooth",
            "com.android.nfc",
            "com.android.wallpaper",
            "com.android.keychain",
            "com.android.documentsui",
            "com.android.externalstorage",
            "com.android.server.telecom",
            "com.android.cellbroadcastreceiver"
        )

        // Skip if it's a known system package that shouldn't send notifications
        if (systemPackagesToSkip.any { packageName.contains(it) }) return true

        // Skip if it doesn't have a launch intent (likely a service-only app)
        return false
    }

    private fun isSystemApp(appInfo: ApplicationInfo): Boolean {
        return (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
               (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
    }

    /**
     * Get friendly app name with fallback
     */
    fun getAppNameFromPackage(context: Context, packageName: String): String {
        return try {
            val packageManager = context.packageManager
            val applicationInfo = packageManager.getApplicationInfo(packageName, 0)
            packageManager.getApplicationLabel(applicationInfo).toString()
        } catch (_: Exception) {
            // Fallback to friendly names for common apps
            when (packageName) {
                "com.whatsapp" -> "WhatsApp"
                "com.telegram.messenger" -> "Telegram"
                "com.discord" -> "Discord"
                "com.slack" -> "Slack"
                "com.microsoft.teams" -> "Microsoft Teams"
                "com.google.android.gm" -> "Gmail"
                "com.android.email" -> "Email"
                "com.samsung.android.messaging" -> "Messages"
                "com.google.android.apps.messaging" -> "Messages"
                "com.spotify.music" -> "Spotify"
                "com.netflix.mediaclient" -> "Netflix"
                "com.instagram.android" -> "Instagram"
                "com.facebook.katana" -> "Facebook"
                "com.twitter.android" -> "Twitter"
                else -> packageName.split(".").lastOrNull()?.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase() else it.toString()
                } ?: packageName
            }
        }
    }
}
