package com.sameerasw.airsync

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sameerasw.airsync.presentation.ui.screens.AirSyncMainScreen
import com.sameerasw.airsync.presentation.ui.screens.NotificationAppsScreen
import com.sameerasw.airsync.ui.theme.AirSyncTheme
import com.sameerasw.airsync.utils.PermissionUtil
import java.net.URLDecoder

class MainActivity : ComponentActivity() {
    
    // Permission launcher for Android 13+ notification permission
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Handle permission result if needed
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val data: android.net.Uri? = intent?.data
        val ip = data?.host
        val port = data?.port?.takeIf { it != -1 }?.toString()

        // Parse QR code parameters
        var pcName: String? = null
        var isPlus = false

        data?.let { uri ->
            val query = uri.query
            if (query != null) {
                // Split on first occurrence of "?plus="
                if (query.contains("?plus=")) {
                    val parts = query.split("?plus=", limit = 2)
                    if (parts.size == 2) {
                        // Extract name part
                        val namePart = parts[0]
                        if (namePart.startsWith("name=")) {
                            pcName = URLDecoder.decode(namePart.substring(5), "UTF-8")
                        }

                        // Extract plus value
                        isPlus = parts[1].toBooleanStrictOrNull() ?: false
                    }
                } else {
                    // Fallback to standard parameter parsing
                    pcName = uri.getQueryParameter("name")?.let {
                        URLDecoder.decode(it, "UTF-8")
                    }
                    isPlus = uri.getQueryParameter("plus")?.toBooleanStrictOrNull() ?: false
                }
            }
        }

        val isFromQrScan = data != null

        setContent {
            AirSyncTheme {
                val navController = rememberNavController()

                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "main",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("main") {
                            AirSyncMainScreen(
                                initialIp = ip,
                                initialPort = port,
                                showConnectionDialog = isFromQrScan,
                                pcName = pcName,
                                isPlus = isPlus,
                                onNavigateToApps = {
                                    navController.navigate("notification_apps")
                                },
                                onRequestNotificationPermission = {
                                    requestNotificationPermission()
                                }
                            )
                        }

                        composable("notification_apps") {
                            NotificationAppsScreen(
                                onBackClick = {
                                    navController.popBackStack()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!PermissionUtil.isPostNotificationPermissionGranted(this)) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
