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
fun ExpandNetworkingCard(context: Context) {
    val ds = remember { DataStoreManager(context) }
    val scope = rememberCoroutineScope()
    val enabledFlow = ds.getExpandNetworkingEnabled()
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
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text("Expand networking", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Allow connecting to device outside the local network",
                    modifier = Modifier.padding(top = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = {
                    enabled = it
                    scope.launch {
                        ds.setExpandNetworkingEnabled(it)
                    }
                }
            )
        }
    }
}
