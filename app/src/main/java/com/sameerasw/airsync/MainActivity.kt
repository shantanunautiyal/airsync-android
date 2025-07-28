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
    var message by remember { mutableStateOf("Hello from Android!") }
    var response by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "AirSync",
            style = MaterialTheme.typography.headlineMedium
        )

        OutlinedTextField(
            value = ipAddress,
            onValueChange = { ipAddress = it },
            label = { Text("IP Address") },
            placeholder = { Text("192.168.x.x") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = port,
            onValueChange = { port = it },
            label = { Text("Port") },
            placeholder = { Text("6996") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = message,
            onValueChange = { message = it },
            label = { Text("Message") },
            placeholder = { Text("Hello from Android!") },
            modifier = Modifier.fillMaxWidth(),
            maxLines = 3
        )

        Button(
            onClick = {
                scope.launch {
                    testSocket(ipAddress, port.toIntOrNull() ?: 6996, message) { result ->
                        response = result
                        isLoading = false
                    }
                }
                isLoading = true
                response = ""
            },
            enabled = !isLoading && ipAddress.isNotBlank() && port.isNotBlank() && message.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(if (isLoading) "Testing..." else "Test Socket Connection")
        }

        if (response.isNotEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = response,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
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