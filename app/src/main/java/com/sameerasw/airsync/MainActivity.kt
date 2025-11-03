package com.sameerasw.airsync

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sameerasw.airsync.presentation.ui.screens.AirSyncMainScreen
import com.sameerasw.airsync.ui.theme.AirSyncTheme
import com.sameerasw.airsync.utils.PermissionUtil
import java.net.URLDecoder
import androidx.compose.runtime.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.graphics.ColorFilter
import androidx.core.net.toUri
import androidx.compose.ui.input.nestedscroll.nestedScroll
import com.sameerasw.airsync.data.local.DataStoreManager
import android.content.Intent
import com.sameerasw.airsync.presentation.ui.activities.QRScannerActivity
import com.sameerasw.airsync.utils.WebSocketUtil

class MainActivity : ComponentActivity() {

    // Permission launcher for Android 13+ notification permission
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
    }

    private val qrScannerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val qrCode = result.data?.getStringExtra("QR_CODE")
            if (qrCode != null) {
                // Parse and connect with the QR code data
                handleQRCodeResult(qrCode)
            }
        } else {
            // User cancelled or invalid QR, do nothing - app remains open
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check if this is a QS tile long-press intent and device is not connected
        if (intent?.action == "android.service.quicksettings.action.QS_TILE_PREFERENCES") {
            if (!WebSocketUtil.isConnected()) {
                // Not connected, open QR scanner instead
                val qrScannerIntent = Intent(this, QRScannerActivity::class.java)
                qrScannerLauncher.launch(qrScannerIntent)
                return
            }
        }

        // ...existing code...
        // Enable full edge-to-edge drawing for both status and navigation bars
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            ),
            navigationBarStyle = SystemBarStyle.auto(
                android.graphics.Color.TRANSPARENT,
                android.graphics.Color.TRANSPARENT
            )
        )

        // On Android 10+ disable forced high-contrast nav bar, so app can draw beneath gesture bar
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }

        val data: android.net.Uri? = intent?.data
        val ip = data?.host
        val port = data?.port?.takeIf { it != -1 }?.toString()

        // Parse QR code parameters
        var pcName: String? = null
        var isPlus = false
        var symmetricKey: String? = null

        data?.let { uri ->
            val urlString = uri.toString()
            val queryPart = urlString.substringAfter('?', "")
            if (queryPart.isNotEmpty()) {
                val params = queryPart.split('?')
                val paramMap = params.associate {
                    val parts = it.split('=', limit = 2)
                    val key = parts.getOrNull(0) ?: ""
                    val value = parts.getOrNull(1) ?: ""
                    key to value
                }
                pcName = paramMap["name"]?.let { URLDecoder.decode(it, "UTF-8") }
                isPlus = paramMap["plus"]?.toBooleanStrictOrNull() ?: false
                symmetricKey = paramMap["key"]
            }
        }

        val isFromQrScan = data != null

        setContent {
            AirSyncTheme {
                val navController = rememberNavController()
                var showAboutDialog by remember { mutableStateOf(false) }
                val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

                Scaffold(
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(scrollBehavior.nestedScrollConnection),
                    contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
                    topBar = {
                        LargeTopAppBar(
                            title = {
                                Row {
                                    // Dynamic icon based on last connected device category
                                    val ctx = androidx.compose.ui.platform.LocalContext.current
                                    val ds = remember(ctx) { DataStoreManager(ctx) }
                                    val lastDevice by ds.getLastConnectedDevice().collectAsState(initial = null)
                                    val iconRes = com.sameerasw.airsync.utils.DeviceIconResolver.getIconRes(lastDevice)
                                    Image(
                                        painter = painterResource(id = iconRes),
                                        contentDescription = "AirSync Logo",
                                        modifier = Modifier.size(32.dp),
                                        contentScale = ContentScale.Fit,
                                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.primary)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "AirSync",
                                        maxLines = 1,
                                    )
                                }
                            },
                            actions = {
                                IconButton(
                                    onClick = {
                                        val airSyncPlusUrl =
                                            "https://airsync.notion.site"
                                        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, airSyncPlusUrl.toUri())
                                        startActivity(intent)
                                    }
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.outline_feedback_24),
                                        contentDescription = "Feedback"
                                    )
                                }

                                IconButton(onClick = { showAboutDialog = true }) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.outline_info_24),
                                        contentDescription = "About"
                                    )
                                }
                            },
                            scrollBehavior = scrollBehavior
                        )
                    }
                ) { innerPadding ->
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
                                symmetricKey = symmetricKey,
                                onRequestNotificationPermission = {
                                    requestNotificationPermission()
                                },
                                showAboutDialog = showAboutDialog,
                                onDismissAbout = { showAboutDialog = false }
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

    private fun handleQRCodeResult(qrCode: String) {
        try {
            val uri = android.net.Uri.parse(qrCode)

            // Validate QR code format: must be airsync scheme with host and port
            if (uri.scheme != "airsync") {
                Log.w("MainActivity", "Invalid QR code scheme: ${uri.scheme}")
                android.widget.Toast.makeText(
                    this,
                    "Invalid QR code format",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                finish()
                return
            }

            val host = uri.host
            val port = uri.port

            if (host.isNullOrEmpty() || port == -1) {
                Log.w("MainActivity", "Invalid QR code: missing host or port")
                android.widget.Toast.makeText(
                    this,
                    "Invalid QR code format",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
                finish()
                return
            }

            // Valid QR code, proceed with connection
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                data = uri
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(mainIntent)
            finish()
        } catch (e: Exception) {
            Log.e("MainActivity", "Error handling QR code result: ${e.message}", e)
            android.widget.Toast.makeText(
                this,
                "Invalid QR code format",
                android.widget.Toast.LENGTH_SHORT
            ).show()
            finish()
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)

        // Check if this is a QS tile long-press intent
        if (intent?.action == "android.service.quicksettings.action.QS_TILE_PREFERENCES") {
            // Check if device is connected
            if (!WebSocketUtil.isConnected()) {
                // Not connected, open QR scanner
                val qrScannerIntent = Intent(this, QRScannerActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                startActivity(qrScannerIntent)
                finish()
            }
        }
    }
}
