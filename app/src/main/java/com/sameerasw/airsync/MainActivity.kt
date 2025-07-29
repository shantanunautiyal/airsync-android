package com.sameerasw.airsync

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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
        val ip = data?.host ?: "192.168.1.100"
        val port = data?.port?.takeIf { it != -1 }?.toString() ?: "6996"

        setContent {
            AirSyncTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SocketTestScreen(
                        modifier = Modifier.padding(innerPadding),
                        initialIp = ip,
                        initialPort = port
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
    initialIp: String = "192.168.1.100",
    initialPort: String = "6996"
) {
    var ipAddress by remember { mutableStateOf(initialIp) }
    var port by remember { mutableStateOf(initialPort) }
    var customMessage by remember { mutableStateOf("{\"type\":\"notification\",\"data\":{\"title\":\"Test\",\"body\":\"Hello!\",\"app\":\"WhatsApp\"}}") }
    var response by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun send(message: String) {
        scope.launch {
            testSocket(ipAddress, port.toIntOrNull() ?: 6996, message) { result ->
                response = result
                isLoading = false
            }
        }
        isLoading = true
        response = ""
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("AirSync Tester", style = MaterialTheme.typography.headlineMedium)

        OutlinedTextField(
            value = ipAddress,
            onValueChange = { ipAddress = it },
            label = { Text("IP Address") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = port,
            onValueChange = { port = it },
            label = { Text("Port") },
            modifier = Modifier.fillMaxWidth()
        )

        Divider()

        Button(
            onClick = {
                val message = """
                    {
                      "type": "device",
                      "data": {
                        "name": "Pixel 8 Pro",
                        "ipAddress": "$ipAddress",
                        "port": ${port.toIntOrNull() ?: 6996}
                      }
                    }
                """.trimIndent()
                send(message)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Send Device Info")
        }

        Button(
            onClick = {
                val message = """
                    {
                      "type": "notification",
                      "data": {
                        "title": "Test Message",
                        "body": "This is a simulated notification.",
                        "app": "Telegram"
                      }
                    }
                """.trimIndent()
                send(message)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Send Notification")
        }

        Button(
            onClick = {
                val message = """
                    {
                      "type": "status",
                      "data": {
                        "battery": { "level": 42, "isCharging": false },
                        "isPaired": true,
                        "music": {
                          "isPlaying": true,
                          "title": "Test Song",
                          "artist": "Test Artist",
                          "volume": 70,
                          "isMuted": false
                        }
                      }
                    }
                """.trimIndent()
                send(message)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Send Device Status")
        }

        Divider()

        OutlinedTextField(
            value = customMessage,
            onValueChange = { customMessage = it },
            label = { Text("Custom Raw JSON") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 4
        )

        Button(
            onClick = { send(customMessage) },
            enabled = !isLoading && ipAddress.isNotBlank() && port.isNotBlank() && customMessage.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(if (isLoading) "Sending..." else "Send Custom Message")
        }

        if (response.isNotEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(response, modifier = Modifier.padding(16.dp))
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