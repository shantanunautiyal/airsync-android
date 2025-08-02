package com.sameerasw.airsync.presentation.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sameerasw.airsync.R


@Composable
fun AboutDialog(
    onDismissRequest: () -> Unit,
    onToggleDeveloperMode: () -> Unit = {},
    appName: String = "AirSync BETA",
    developerName: String = "Sameera Wijerathna",
    description: String = "AirSync enables seamless synchronization between your Android device and mac. Share notifications, clipboard content, and device status wirelessly over your local network.",
    githubUsername: String = "sameerasw"
) {
    val context = LocalContext.current
    val hapticFeedback = LocalHapticFeedback.current

    val versionName = try {
        context.packageManager
            .getPackageInfo(context.packageName, 0)
            .versionName
    } catch (e: Exception) {
        "Unknown"
    }


    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = {
            Text(
                "About $appName",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Left
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Website button
                Button(
                    onClick = {
                        val websiteUrl = "https://www.sameerasw.com/airsync"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(websiteUrl))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Website")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Get the mac app button
                OutlinedButton(
                    onClick = {
                        val macAppUrl = "https://github.com/sameerasw/airsync-mac/releases/latest"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(macAppUrl))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Get the mac app")
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Get AirSync+ button
                OutlinedButton(
                    onClick = {
                        val airSyncPlusUrl = "https://airsync.sameerasw.com"
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(airSyncPlusUrl))
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Get AirSync+")
                }

                Spacer(modifier = Modifier.height(48.dp))

                Image(
                    painter = painterResource(id = R.drawable.avatar),
                    contentDescription = "Developer Avatar",
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onLongPress = {
                                    // Trigger haptic feedback
                                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)

                                    // Show toast
                                    Toast.makeText(context, "Developer Mode", Toast.LENGTH_SHORT).show()

                                    // Toggle developer mode visibility
                                    onToggleDeveloperMode()
                                }
                            )
                        }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Developed by $developerName",
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center
                )
                
                Text(
                    "With ❤\uFE0F",
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Version $versionName BETA",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        dismissButton = {
            Button(onClick = {
                val websiteUrl = "https://www.sameerasw.com"
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(websiteUrl))
                context.startActivity(intent)
            }) {
                Text("My website")
            }
        },
        confirmButton = {
            Button(onClick = onDismissRequest) {
                Text("OK")
            }
        }
    )
}
