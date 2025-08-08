package com.sameerasw.airsync.presentation.ui.components

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
import com.sameerasw.airsync.R
import com.sameerasw.airsync.domain.model.UiState
import com.sameerasw.airsync.ui.theme.minCornerRadius

@Composable
fun ManualConnectionCard(
    uiState: UiState,
    onIpChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onPcNameChange: (String) -> Unit,
    onIsPlusChange: (Boolean) -> Unit,
    onSymmetricKeyChange: (String) -> Unit,
    onConnect: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(
             minCornerRadius
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { expanded = !expanded }
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
                            onCheckedChange = onIsPlusChange
                        )
                    }
                    Button(
                        onClick = onConnect,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(
                            minCornerRadius
                    ),
                    ) {
                        Text("Connect")
                    }
                }
            }
        }
    }
}
