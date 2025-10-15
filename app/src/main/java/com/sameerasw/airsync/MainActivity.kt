package com.sameerasw.airsync

import android.Manifest
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
import android.content.Intent

class MainActivity : ComponentActivity() {

    private var showMirroringDialog by mutableStateOf(false)
    private var mirroringOptions by mutableStateOf<Bundle?>(null)

    // Permission launcher for Android 13+ notification permission
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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

        if (intent != null) {
            handleIntent(intent)
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
                                onDismissAbout = { showAboutDialog = false },
                                showMirroringDialog = showMirroringDialog,
                                mirroringOptions = mirroringOptions,
                                onDismissMirroringDialog = { showMirroringDialog = false }
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action == "com.sameerasw.airsync.MIRROR_REQUEST") {
            mirroringOptions = intent.extras
            showMirroringDialog = true
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
