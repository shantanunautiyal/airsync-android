package com.sameerasw.airsync.presentation.ui.components.sheets

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sameerasw.airsync.R
import com.sameerasw.airsync.domain.model.NotificationApp
import com.sameerasw.airsync.utils.HapticUtil

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppSelectionSheet(
    onDismissRequest: () -> Unit,
    apps: List<NotificationApp>,
    onAppToggle: (String, Boolean) -> Unit,
    onSaveAll: (List<NotificationApp>) -> Unit,
    isLoading: Boolean
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptics = LocalHapticFeedback.current
    var searchQuery by remember { mutableStateOf("") }
    var showSystemApps by remember { mutableStateOf(false) }

    val filteredApps = apps.filter {
        val matchesSearch = searchQuery.isEmpty() || it.appName.contains(searchQuery, ignoreCase = true)
        val isVisible = !it.isSystemApp || showSystemApps || it.isEnabled
        matchesSearch && isVisible
    }.distinctBy { it.packageName }
     .sortedWith(compareByDescending<NotificationApp> { it.isEnabled }.thenBy { it.appName.lowercase() })

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.action_select_apps),
                    style = MaterialTheme.typography.headlineSmall
                )

                IconButton(
                    onClick = {
                        HapticUtil.performClick(haptics)
                        val updatedList = apps.map { app ->
                            val isVisible = !app.isSystemApp || showSystemApps || app.isEnabled
                            if (isVisible) app.copy(isEnabled = !app.isEnabled) else app
                        }
                        onSaveAll(updatedList)
                    }
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.rounded_invert_colors_24),
                        contentDescription = "Invert Selection",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Search apps") },
                leadingIcon = {
                    Icon(
                        painter = painterResource(id = R.drawable.outline_info_24), // Fallback search icon
                        contentDescription = "Search"
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // System Apps Toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable {
                        HapticUtil.performClick(haptics)
                        showSystemApps = !showSystemApps
                    }
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.rounded_android_24),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Show system apps",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Switch(
                    checked = showSystemApps,
                    onCheckedChange = {
                        HapticUtil.performClick(haptics)
                        showSystemApps = it
                    }
                )
            }

            if (isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(24.dp)),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(filteredApps, key = { it.packageName }) { app ->
                        AppToggleItem(
                            icon = app.icon,
                            title = app.appName,
                            packageName = app.packageName,
                            isSystemApp = app.isSystemApp,
                            isChecked = app.isEnabled,
                            onCheckedChange = { isChecked ->
                                HapticUtil.performClick(haptics)
                                onAppToggle(app.packageName, isChecked)
                            }
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AppToggleItem(
    icon: Any?,
    title: String,
    modifier: Modifier = Modifier,
    description: String? = null,
    packageName: String? = null,
    isSystemApp: Boolean = false,
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    val haptics = LocalHapticFeedback.current
    val shouldShowSystemTag = isSystemApp

    ListItem(
        modifier = modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) {
                HapticUtil.performClick(haptics)
                onCheckedChange(!isChecked)
            },
        leadingContent = {
            if (icon != null) {
                AsyncImage(
                    model = icon,
                    contentDescription = title,
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
            } else {
                Spacer(modifier = Modifier.size(32.dp))
            }
        },
        headlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (shouldShowSystemTag) {
                    Icon(
                        painter = painterResource(id = R.drawable.rounded_android_24),
                        contentDescription = "System App",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        supportingContent = if (description != null) {
            {
                Text(
                    text = description,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else null,
        trailingContent = {
            Switch(
                checked = if (enabled) isChecked else false,
                onCheckedChange = null,
                enabled = enabled
            )
        },
        colors = ListItemDefaults.colors(
            containerColor = MaterialTheme.colorScheme.surfaceBright
        )
    )
}
