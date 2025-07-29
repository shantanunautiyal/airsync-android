package com.sameerasw.airsync

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sameerasw.airsync.ui.theme.AirSyncTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.Socket

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val data: android.net.Uri? = intent?.data
        val ip = data?.host
        val port = data?.port?.takeIf { it != -1 }?.toString()
        val isFromQrScan = data != null

        setContent {
            AirSyncTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SocketTestScreen(
                        modifier = Modifier.padding(innerPadding),
                        initialIp = ip,
                        initialPort = port,
                        showConnectionDialog = isFromQrScan
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SocketTestScreen(
    modifier: Modifier = Modifier,
    initialIp: String? = null,
    initialPort: String? = null,
    showConnectionDialog: Boolean = false,
    viewModel: AirSyncViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val deviceInfo by viewModel.deviceInfo.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.initializeState(context, initialIp, initialPort, showConnectionDialog)
    }

    // Refresh permissions when returning from settings
    LaunchedEffect(uiState.showPermissionDialog) {
        if (!uiState.showPermissionDialog) {
            viewModel.refreshPermissions(context)
        }
    }

    fun send(message: String) {
        scope.launch {
            viewModel.setLoading(true)
            viewModel.setResponse("")
            testSocket(uiState.ipAddress, uiState.port.toIntOrNull() ?: 6996, message) { result ->
                viewModel.setResponse(result)
                viewModel.setLoading(false)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("AirSync", style = MaterialTheme.typography.headlineMedium)

        // Permission Status Card
        if (uiState.missingPermissions.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("⚠️ Permissions Required",
                         style = MaterialTheme.typography.titleMedium,
                         color = MaterialTheme.colorScheme.onErrorContainer)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Missing: ${uiState.missingPermissions.joinToString(", ")}",
                         style = MaterialTheme.typography.bodyMedium,
                         color = MaterialTheme.colorScheme.onErrorContainer)

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.setPermissionDialogVisible(true) },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Grant Permissions", color = MaterialTheme.colorScheme.onError)
                        }

                        OutlinedButton(
                            onClick = { viewModel.refreshPermissions(context) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Refresh")
                        }
                    }
                }
            }
        }

        // Device Info Section
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Device Information", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Local IP: ${deviceInfo.localIp}", style = MaterialTheme.typography.bodyMedium)

                val batteryInfo by rememberUpdatedState(DeviceInfoUtil.getBatteryInfo(context))
                val audioInfo by rememberUpdatedState(DeviceInfoUtil.getAudioInfo(context))

                Text("Battery: ${batteryInfo.level}% ${if (batteryInfo.isCharging) "⚡" else "🔋"}",
                    style = MaterialTheme.typography.bodyMedium)
                Text("Volume: ${audioInfo.volume}% ${if (audioInfo.isMuted) "🔇" else "🔊"}",
                    style = MaterialTheme.typography.bodyMedium)

                // Show media info status with refresh
                if (uiState.isNotificationEnabled) {
                    if (audioInfo.title.isNotEmpty()) {
                        Text("🎵 ${audioInfo.title} - ${audioInfo.artist}",
                             style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text("🎵 No media playing", style = MaterialTheme.typography.bodyMedium)
                    }
                } else {
                    Text("🎵 Media info unavailable (permission needed)",
                         style = MaterialTheme.typography.bodyMedium,
                         color = MaterialTheme.colorScheme.error)
                }

                OutlinedTextField(
                    value = uiState.deviceNameInput,
                    onValueChange = { viewModel.updateDeviceName(context, it) },
                    label = { Text("Device Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            viewModel.refreshPermissions(context)
                            viewModel.refreshDeviceInfo(context)
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Refresh Media")
                    }
                }
            }
        }

        // Connection Settings
        OutlinedTextField(
            value = uiState.ipAddress,
            onValueChange = { viewModel.updateIpAddress(context, it) },
            label = { Text("Desktop IP Address") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = uiState.port,
            onValueChange = { viewModel.updatePort(context, it) },
            label = { Text("Desktop Port") },
            modifier = Modifier.fillMaxWidth()
        )

        HorizontalDivider()

        Button(
            onClick = {
                val message = JsonUtil.createDeviceInfoJson(deviceInfo.name, deviceInfo.localIp, uiState.port.toIntOrNull() ?: 6996)
                send(message)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Send Device Info")
        }

        Button(
            onClick = {
                val message = JsonUtil.createNotificationJson("Test Message", "This is a simulated notification.", "Telegram")
                send(message)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Send Notification")
        }

        Button(
            onClick = {
                val message = DeviceInfoUtil.generateDeviceStatusJson(context, uiState.port.toIntOrNull() ?: 6996)
                send(message)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Send Device Status")
        }

        HorizontalDivider()

        OutlinedTextField(
            value = uiState.customMessage,
            onValueChange = { viewModel.updateCustomMessage(context, it) },
            label = { Text("Custom Raw JSON") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 4
        )

        Button(
            onClick = { send(uiState.customMessage) },
            enabled = !uiState.isLoading && uiState.ipAddress.isNotBlank() && uiState.port.isNotBlank() && uiState.customMessage.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(if (uiState.isLoading) "Sending..." else "Send Custom Message")
        }

        if (uiState.response.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(uiState.response, modifier = Modifier.padding(16.dp))
            }
        }

        if (uiState.isDialogVisible) {
            ConnectionDialog(
                deviceName = deviceInfo.name,
                localIp = deviceInfo.localIp,
                desktopIp = uiState.ipAddress,
                port = uiState.port,
                onDismiss = { viewModel.setDialogVisible(false) },
                onConnect = {
                    viewModel.setDialogVisible(false)
                    // Send device info automatically
                    val message = JsonUtil.createDeviceInfoJson(deviceInfo.name, deviceInfo.localIp, uiState.port.toIntOrNull() ?: 6996)
                    send(message)
                }
            )
        }

        if (uiState.showPermissionDialog) {
            PermissionDialog(
                missingPermissions = uiState.missingPermissions,
                onDismiss = { viewModel.setPermissionDialogVisible(false) },
                onGrantPermissions = {
                    PermissionUtil.openNotificationListenerSettings(context)
                    viewModel.setPermissionDialogVisible(false)
                }
            )
        }
    }
}

@Composable
fun ConnectionDialog(
    deviceName: String,
    localIp: String,
    desktopIp: String,
    port: String,
    onDismiss: () -> Unit,
    onConnect: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 3.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .width(300.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Connect to Desktop?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Text("Device: $deviceName")
                Text("Local IP: $localIp")

                HorizontalDivider()

                Text("Connect to Desktop:")
                Text("IP Address: $desktopIp")
                Text("Port: $port")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Cancel")
                    }

                    Button(
                        onClick = onConnect,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Connect")
                    }
                }
            }
        }
    }
}

@Composable
fun PermissionDialog(
    missingPermissions: List<String>,
    onDismiss: () -> Unit,
    onGrantPermissions: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            tonalElevation = 3.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .width(300.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Permissions Required",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Text("The following permissions are needed for full functionality:")
                missingPermissions.forEach { permission ->
                    Text("• $permission")
                }

                Text("Please enable them in the settings.")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Dismiss")
                    }

                    Button(
                        onClick = onGrantPermissions,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Settings")
                    }
                }
            }
        }
    }
}

private suspend fun testSocket(ipAddress: String, port: Int, message: String, onResult: (String) -> Unit) {
    withContext(Dispatchers.IO) {
        try {
            val socket = Socket(ipAddress, port)
            val output = PrintWriter(socket.getOutputStream(), true)
            val input = BufferedReader(InputStreamReader(socket.getInputStream()))

            output.println(message)
            val response = input.readLine()
            Log.d("TCP", "Received: $response")

            socket.close()

            withContext(Dispatchers.Main) {
                onResult("Success! Received: $response")
            }
        } catch (e: Exception) {
            Log.e("TCP", "Socket error: ${e.message}")
            withContext(Dispatchers.Main) {
                onResult("Error: ${e.message}")
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun SocketTestPreview() {
    AirSyncTheme {
        SocketTestScreen()
    }
}