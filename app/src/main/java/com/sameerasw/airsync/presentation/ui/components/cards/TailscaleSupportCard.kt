package com.sameerasw.airsync.presentation.ui.components.cards

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sameerasw.airsync.data.local.DataStoreManager
import com.sameerasw.airsync.ui.theme.minCornerRadius
import kotlinx.coroutines.launch

@Composable
fun TailscaleSupportCard(context: Context) {
    val ds = remember { DataStoreManager(context) }
    val scope = rememberCoroutineScope()
    val enabledFlow = ds.getTailscaleSupportEnabled()
    var enabled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        enabledFlow.collect { value ->
            enabled = value
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 0.dp, horizontal = 0.dp),
        shape = RoundedCornerShape(
            topStart = minCornerRadius,
            topEnd = minCornerRadius,
            bottomStart = minCornerRadius,
            bottomEnd = minCornerRadius
        ),
        elevation = CardDefaults.cardElevation(2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text("Enable Tailscale support")
                Text(
                    "Connection to 100.x.x.x IP",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    scope.launch {
                        ds.setTailscaleSupportEnabled(it)
                    }
                }
            )
        }
    }
}

