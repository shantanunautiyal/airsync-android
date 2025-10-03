package com.sameerasw.airsync

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.Flow

class MainActivity : ComponentActivity() {

    // Broadcast receiver for mirror requests (kept as a property so we can unregister)
    private var mirrorReceiver: BroadcastReceiver? = null

    // ActivityResult launcher for MediaProjection permission
    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            if (result.resultCode == RESULT_OK && result.data != null) {
                // Use defaults or previous requested size; for now use 1280x720 and 4 Mbps
                com.sameerasw.airsync.mirror.MirroringManager.startFromResult(
                    this,
                    result.resultCode,
                    result.data,
                    1280,
                    720,
                    4
                )
                // Notify Mac that mirroring started
                val resp = org.json.JSONObject()
                resp.put("type", "startMirrorResponse")
                val d = org.json.JSONObject()
                d.put("success", true)
                resp.put("data", d)
                com.sameerasw.airsync.utils.WebSocketUtil.sendMessage(com.sameerasw.airsync.utils.JsonUtil.toSingleLine(resp.toString()))
            } else {
                val resp = org.json.JSONObject()
                resp.put("type", "startMirrorResponse")
                val d = org.json.JSONObject()
                d.put("success", false)
                d.put("message", "Permission denied")
                resp.put("data", d)
                com.sameerasw.airsync.utils.WebSocketUtil.sendMessage(com.sameerasw.airsync.utils.JsonUtil.toSingleLine(resp.toString()))
            }
        } catch (e: Exception) {
            // Log unexpected exceptions during projection handling to help debugging crashes
            android.util.Log.e("MainActivity", "Error in projectionLauncher callback: ${e.message}", e)
        }
    }

    // Permission launcher for Android 13+ notification permission
    private val callLogPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mirrorReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                try {
                    if (intent == null) return
                    // Request MediaProjection permission via Activity Result API
                    val mgr = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
                    val captureIntent = mgr.createScreenCaptureIntent()
                    projectionLauncher.launch(captureIntent)
                } catch (e: Exception) {
                    android.util.Log.e("MainActivity", "Error launching projection intent: ${e.message}", e)
                }
            }
        }
        ContextCompat.registerReceiver(
            this,
            mirrorReceiver,
            IntentFilter("com.sameerasw.airsync.ACTION_REQUEST_MIRROR"),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )

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
                                showAboutDialog = showAboutDialog,
                                onDismissAbout = { showAboutDialog = false },
                                onRequestCallLogPermission = {
                                    requestCallLogPermission()
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister mirror receiver if registered to avoid leaks/crashes
        try {
            mirrorReceiver?.let { unregisterReceiver(it) }
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "Error unregistering mirrorReceiver: ${e.message}")
        }
        mirrorReceiver = null
    }

    private fun requestCallLogPermission() {
        if (!PermissionUtil.hasReadCallLogPermission(this)) {
            callLogPermissionLauncher.launch(Manifest.permission.READ_CALL_LOG)
        }
    }
}