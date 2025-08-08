package com.sameerasw.airsync.presentation.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sameerasw.airsync.domain.model.UpdateInfo
import com.sameerasw.airsync.domain.model.UpdateStatus

@Composable
fun UpdateDialog(
    updateInfo: UpdateInfo?,
    updateStatus: UpdateStatus,
    downloadProgress: Int,
    onDismiss: () -> Unit,
    onDownload: () -> Unit,
) {
    if (updateInfo == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = when (updateStatus) {
                    UpdateStatus.UPDATE_AVAILABLE -> if (updateInfo.isBetaUpdate) "Beta Update Available" else "Update Available"
                    UpdateStatus.DOWNLOADING -> "Downloading Update"
                    UpdateStatus.DOWNLOADED -> "Update Downloaded"
                    UpdateStatus.ERROR -> "Update Error"
                    UpdateStatus.NO_UPDATE -> "No Updates"
                    else -> "Checking for Updates"
                },
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (updateStatus) {
                    UpdateStatus.CHECKING -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Checking for updates...")
                    }

                    UpdateStatus.UPDATE_AVAILABLE -> {
                        // Version info
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = if (updateInfo.isBetaUpdate)
                                    MaterialTheme.colorScheme.tertiaryContainer
                                else MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = updateInfo.release.name,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "${updateInfo.currentVersion} → ${updateInfo.newVersion}",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                if (updateInfo.isBetaUpdate) {
                                    Text(
                                        text = "BETA",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer
                                    )
                                }
                                Text(
                                    text = "Size: ${updateInfo.downloadSize}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Release notes
                        if (updateInfo.release.changelog.isNotBlank()) {
                            Text(
                                text = "Release Notes:",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                                )
                            ) {
                                Text(
                                    text = updateInfo.release.changelog,
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }

                    UpdateStatus.DOWNLOADING -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            LinearProgressIndicator(
                                progress = { downloadProgress / 100f },
                                modifier = Modifier.fillMaxWidth(),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("$downloadProgress%")
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Downloading ${updateInfo.release.name}...",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }

                    UpdateStatus.DOWNLOADED -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = " Download Complete",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Text(
                                    text = "To install the update:\n\n" +
                                           "1. Open your notifications\n" +
                                           "2. Tap on the download completed notification\n" +
                                           "3. Follow the installation prompts",
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    textAlign = TextAlign.Start
                                )
                            }
                        }
                    }

                    UpdateStatus.ERROR -> {
                        Text(
                            text = "❌ ${updateInfo.release.changelog}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                    }

                    UpdateStatus.NO_UPDATE -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "✅ You're up to date!",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.Start
                                ) {
                                    Text(
                                        text = updateInfo.release.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Version ${updateInfo.currentVersion}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    if (updateInfo.isBetaUpdate) {
                                        Text(
                                            text = "BETA",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            when (updateStatus) {
                UpdateStatus.UPDATE_AVAILABLE -> {
                    Button(
                        onClick = onDownload,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Download")
                    }
                }

                UpdateStatus.DOWNLOADING -> {
                    // No action button while downloading
                }

                UpdateStatus.DOWNLOADED -> {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("OK")
                    }
                }

                UpdateStatus.ERROR -> {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("OK")
                    }
                }

                UpdateStatus.NO_UPDATE -> {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("OK")
                    }
                }

                else -> {
                    Button(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel")
                    }
                }
            }
        },
        dismissButton = {
            if (updateStatus == UpdateStatus.UPDATE_AVAILABLE) {
                TextButton(onClick = onDismiss) {
                    Text("Later")
                }
            }
        }
    )
}
