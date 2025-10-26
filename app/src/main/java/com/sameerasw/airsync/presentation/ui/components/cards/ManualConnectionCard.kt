package com.sameerasw.airsync.presentation.ui.components.cards

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalHapticFeedback
import com.sameerasw.airsync.R
import com.sameerasw.airsync.domain.model.UiState
import com.sameerasw.airsync.ui.theme.ExtraCornerRadius
import com.sameerasw.airsync.ui.theme.minCornerRadius
import com.sameerasw.airsync.utils.HapticUtil

@Composable
fun ManualConnectionCard(
    isConnected: Boolean,
    lastConnected: Boolean,
    uiState: UiState,
    onIpChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onPcNameChange: (String) -> Unit,
    onIsPlusChange: (Boolean) -> Unit,
    onSymmetricKeyChange: (String) -> Unit,
    onConnect: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var expanded by remember { mutableStateOf(false) }
    val cardShape =if (lastConnected) {
        RoundedCornerShape(minCornerRadius)
    } else {
        RoundedCornerShape(
            topStart = minCornerRadius,
            topEnd = minCornerRadius,
            bottomStart = ExtraCornerRadius,
            bottomEnd = ExtraCornerRadius
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = cardShape,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable {
                    HapticUtil.performLightTick(haptics)
                    expanded = !expanded
                }
            ) {
                Text("Manual Connection", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.weight(1f))
                Icon(
                    painter = painterResource(
                        if (expanded) R.drawable.outline_expand_circle_up_24 else R.drawable.outline_expand_circle_down_24
                    ),
                    contentDescription = if (expanded) "Collapse" else "Expand"
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 16.dp)) {
                    OutlinedTextField(
                        value = uiState.ipAddress,
                        onValueChange = onIpChange,
                        label = { Text("IP Address") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = uiState.port,
                        onValueChange = onPortChange,
                        label = { Text("Port") },
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    OutlinedTextField(
                        value = uiState.manualPcName,
                        onValueChange = onPcNameChange,
                        label = { Text("PC Name (Optional)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = uiState.symmetricKey ?: "",
                        onValueChange = onSymmetricKeyChange,
                        label = { Text("Encryption Key") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("AirSync+")
                        Spacer(Modifier.weight(1f))
                        Switch(
                            checked = uiState.manualIsPlus,
                            onCheckedChange = { enabled ->
                                if (enabled) HapticUtil.performToggleOn(haptics) else HapticUtil.performToggleOff(haptics)
                                onIsPlusChange(enabled)
                            }
                        )
                    }
                    Button(
                        onClick = {
                            HapticUtil.performClick(haptics)
                            onConnect()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(
                            topStart = minCornerRadius,
                            topEnd = minCornerRadius,
                            bottomStart = ExtraCornerRadius - minCornerRadius,
                            bottomEnd = ExtraCornerRadius - minCornerRadius
                        )
                    ) {
                        Text("Connect")
                    }
                }
            }
        }
    }
}
