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

class MainActivity : ComponentActivity() {
    
    // Permission launcher for Android 13+ notification permission
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        // Handle permission result if needed
        // This is mainly for logging/analytics, the UI will update automatically
        // when the user returns to the app since we check permissions in onResume
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val data: android.net.Uri? = intent?.data
        val ip = data?.host
        val port = data?.port?.takeIf { it != -1 }?.toString()
        val pcName = data?.getQueryParameter("name") // Extract PC name from QR code
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
