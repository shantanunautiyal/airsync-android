package com.sameerasw.airsync.presentation.ui.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.Canvas
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import com.sameerasw.airsync.ui.theme.AirSyncTheme
import java.util.concurrent.Executors

class QRScannerActivity : ComponentActivity() {

    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            // Permission denied, finish the activity
            setResult(RESULT_CANCELED)
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Check camera permission
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            return
        }

        setContent {
            AirSyncTheme {
                QRScannerScreen(
                    onQrScanned = { qrData ->
                        // Return the scanned QR code data to the caller
                        val intent = Intent().apply {
                            putExtra("QR_CODE", qrData)
                        }
                        setResult(RESULT_OK, intent)
                        finish()
                    },
                    onClosed = {
                        setResult(RESULT_CANCELED)
                        finish()
                    }
                )
            }
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
private fun QRScannerScreen(
    onQrScanned: (String) -> Unit,
    onClosed: () -> Unit
) {
    var scanned by remember { mutableStateOf(false) }
    var lastScannedCode by remember { mutableStateOf("") }
    var scanMessage by remember { mutableStateOf("") }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = { Text("Scan QR Code") },
                navigationIcon = {
                    IconButton(onClick = onClosed) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color.Black)
        ) {
            QrCodeScannerView(
                modifier = Modifier.fillMaxSize(),
                onQrScanned = { qrData ->
                    if (!scanned && qrData != lastScannedCode) {
                        lastScannedCode = qrData
                        scanned = true
                        scanMessage = "QR Code detected!"
                        Log.d("QrScanner", "QR detected in screen: $qrData")
                        // Small delay to ensure UI update before finishing
                        onQrScanned(qrData)
                    }
                }
            )

            // Scanning frame overlay
            Canvas(
                modifier = Modifier
                    .size(300.dp)
                    .align(Alignment.Center)
            ) {
                drawRect(
                    color = Color(0x99FFFFFF),
                    topLeft = androidx.compose.ui.geometry.Offset(
                        (size.width - 200.dp.toPx()) / 2,
                        (size.height - 200.dp.toPx()) / 2
                    ),
                    size = androidx.compose.ui.geometry.Size(200.dp.toPx(), 200.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f)
                )
            }

            // Status messages
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (scanMessage.isNotEmpty()) {
                    Text(
                        scanMessage,
                        color = Color.Green,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(bottom = 8.dp),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Text(
                    "Position QR code in frame\nKeep phone steady",
                    color = Color.White,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun QrCodeScannerView(
    modifier: Modifier = Modifier,
    onQrScanned: (String) -> Unit
) {
    var lastScanned by remember { mutableStateOf("") }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                try {
                    val cameraProvider = cameraProviderFuture.get()
                    Log.d("QrScanner", "Camera provider obtained")

                    @Suppress("DEPRECATION")
                    val preview = androidx.camera.core.Preview.Builder()
                        .setTargetResolution(android.util.Size(1280, 720))
                        .build()
                    preview.setSurfaceProvider(previewView.surfaceProvider)

                    // Configure barcode scanner options for QR codes only
                    val options = BarcodeScannerOptions.Builder()
                        .setBarcodeFormats(256) // Barcode.FORMAT_QR_CODE
                        .build()

                    val scanner = BarcodeScanning.getClient(options)
                    Log.d("QrScanner", "Barcode scanner initialized")

                    // Configure image analysis for continuous scanning
                    @Suppress("DEPRECATION")
                    val analysis = ImageAnalysis.Builder()
                        .setTargetResolution(android.util.Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    val cameraExecutor = Executors.newSingleThreadExecutor()

                    analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                        processQrImage(scanner, imageProxy) { result ->
                            if (result != lastScanned && result.isNotEmpty()) {
                                lastScanned = result
                                Log.d("QrScanner", "QR scanned, calling callback with: $result")
                                onQrScanned(result)
                            }
                        }
                    }

                    try {
                        cameraProvider.unbindAll()
                        val cameraSelector = CameraSelector.Builder()
                            .requireLensFacing(CameraSelector.LENS_FACING_BACK)
                            .build()

                        cameraProvider.bindToLifecycle(
                            ctx as androidx.lifecycle.LifecycleOwner,
                            cameraSelector,
                            preview,
                            analysis
                        )
                        Log.d("QrScanner", "Camera bound successfully")
                    } catch (e: Exception) {
                        Log.e("QrScanner", "Camera binding failed: ${e.message}", e)
                    }
                } catch (e: Exception) {
                    Log.e("QrScanner", "Failed to initialize camera: ${e.message}", e)
                }
            }, ContextCompat.getMainExecutor(ctx))

            previewView
        }
    )
}

@SuppressLint("UnsafeOptInUsageError")
private fun processQrImage(
    scanner: com.google.mlkit.vision.barcode.BarcodeScanner,
    imageProxy: ImageProxy,
    onResult: (String) -> Unit
) {
    try {
        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(
                mediaImage,
                imageProxy.imageInfo.rotationDegrees
            )
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    try {
                        for (barcode in barcodes) {
                            val rawValue = barcode.rawValue
                            if (rawValue != null && rawValue.isNotEmpty()) {
                                Log.d("QrScanner", "QR Code detected: $rawValue")
                                onResult(rawValue)
                                return@addOnSuccessListener
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("QrScanner", "Error processing barcode: ${e.message}", e)
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("QrScanner", "Scanner failed: ${e.message}", e)
                }
                .addOnCompleteListener {
                    try {
                        imageProxy.close()
                    } catch (e: Exception) {
                        Log.e("QrScanner", "Error closing imageProxy: ${e.message}")
                    }
                }
        } else {
            imageProxy.close()
        }
    } catch (e: Exception) {
        Log.e("QrScanner", "Exception in processQrImage: ${e.message}", e)
        try {
            imageProxy.close()
        } catch (ex: Exception) {
            Log.e("QrScanner", "Error closing imageProxy in exception handler: ${ex.message}")
        }
    }
}

