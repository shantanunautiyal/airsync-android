package com.sameerasw.airsync.presentation.ui.components.sheets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sameerasw.airsync.R
import com.sameerasw.airsync.data.local.DataStoreManager
import com.sameerasw.airsync.presentation.ui.components.RoundedCardContainer
import com.sameerasw.airsync.presentation.ui.components.cards.IconToggleItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConnectionSettingsBottomSheet(
    isAutoReconnectEnabled: Boolean,
    onToggleAutoReconnect: (Boolean) -> Unit,
    onDismissRequest: () -> Unit
) {
    val context = LocalContext.current
    val dataStoreManager = remember { DataStoreManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    val bleEnabled by dataStoreManager.getBleSyncEnabled().collectAsState(initial = false)

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.bluetooth_settings_card_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 8.dp)
            )

            RoundedCardContainer {
                IconToggleItem(
                    iconRes = R.drawable.rounded_sync_desktop_24,
                    title = stringResource(R.string.setting_auto_reconnect_title),
                    description = stringResource(R.string.setting_auto_reconnect_desc),
                    isChecked = isAutoReconnectEnabled,
                    onCheckedChange = { enabled ->
                        onToggleAutoReconnect(enabled)
                    }
                )

                IconToggleItem(
                    iconRes = R.drawable.rounded_bluetooth_24,
                    title = stringResource(R.string.setting_nearby_connection_title),
                    description = stringResource(R.string.setting_nearby_connection_desc),
                    isChecked = bleEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            dataStoreManager.setBleSyncEnabled(enabled)
                            dataStoreManager.setBleAutoConnectEnabled(enabled)
                        }
                    }
                )
            }
        }
    }
}

