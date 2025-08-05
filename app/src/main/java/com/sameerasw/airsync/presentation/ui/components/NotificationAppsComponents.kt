package com.sameerasw.airsync.presentation.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.sameerasw.airsync.domain.model.NotificationApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationAppsContent(
    apps: List<NotificationApp>,
    searchQuery: String,
    showSystemApps: Boolean,
    isLoading: Boolean,
    error: String?,
    onSearchQueryChange: (String) -> Unit,
    onToggleApp: (String, Boolean) -> Unit,
    onToggleAllApps: (Boolean) -> Unit,
    onToggleSystemApps: (Boolean) -> Unit,
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Search and filters
        SearchAndFiltersSection(
            searchQuery = searchQuery,
            showSystemApps = showSystemApps,
            onSearchQueryChange = onSearchQueryChange,
            onToggleSystemApps = onToggleSystemApps,
            onToggleAllApps = onToggleAllApps
        )

        // Error display
        error?.let { errorMessage ->
            ErrorCard(
                message = errorMessage,
                onDismiss = onClearError
            )
        }

        // Apps list
        if (searchQuery.length < 3) {
            PromptSearchMessage()
        } else if (isLoading) {
            LoadingSection()
        } else {
            AppsListSection(
                apps = apps,
                showSystemApps = showSystemApps,
                onToggleApp = onToggleApp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchAndFiltersSection(
    searchQuery: String,
    showSystemApps: Boolean,
    onSearchQueryChange: (String) -> Unit,
    onToggleSystemApps: (Boolean) -> Unit,
    onToggleAllApps: (Boolean) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            label = { Text("Search apps") },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = "Search")
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Filter options
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // System apps toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Checkbox(
                    checked = showSystemApps,
                    onCheckedChange = onToggleSystemApps
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "Show system apps",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Bulk actions
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = { onToggleAllApps(true) },
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Enable All", style = MaterialTheme.typography.bodySmall)
                }
                OutlinedButton(
                    onClick = { onToggleAllApps(false) },
                    modifier = Modifier.height(36.dp)
                ) {
                    Text("Disable All", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun LoadingSection() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            CircularProgressIndicator()
            Text(
                text = "Loading installed apps...",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AppsListSection(
    apps: List<NotificationApp>,
    showSystemApps: Boolean,
    onToggleApp: (String, Boolean) -> Unit
) {
    val filteredApps = if (showSystemApps) {
        apps
    } else {
        apps.filter { !it.isSystemApp }
    }

    if (filteredApps.isEmpty()) {
        EmptyAppsMessage(showSystemApps = showSystemApps)
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text(
                    text = "${filteredApps.size} apps found",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                )
            }

            items(
                items = filteredApps,
                key = { it.packageName }
            ) { app ->
                NotificationAppItem(
                    app = app,
                    onToggle = { isEnabled ->
                        onToggleApp(app.packageName, isEnabled)
                    }
                )
            }
        }
    }
}

@Composable
private fun EmptyAppsMessage(showSystemApps: Boolean) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = if (showSystemApps) "No apps found" else "No user apps found",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (!showSystemApps) {
                Text(
                    text = "Try enabling 'Show system apps' to see more",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun NotificationAppItem(
    app: NotificationApp,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // App icon
            AppIcon(
                icon = app.icon,
                contentDescription = "${app.appName} icon",
                modifier = Modifier.size(40.dp)
            )

            // App info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (app.isSystemApp) {
                    Text(
                        text = "System app",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Toggle switch
            Switch(
                checked = app.isEnabled,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun AppIcon(
    icon: android.graphics.drawable.Drawable?,
    contentDescription: String?,
    modifier: Modifier = Modifier
) {
    var iconBitmap by remember(icon) {
        mutableStateOf<androidx.compose.ui.graphics.ImageBitmap?>(null)
    }
    var hasError by remember(icon) { mutableStateOf(false) }

    LaunchedEffect(icon) {
        if (icon != null && !hasError) {
            try {
                val bitmap = icon.toBitmap()
                iconBitmap = bitmap.asImageBitmap()
            } catch (_: Exception) {
                hasError = true
                iconBitmap = null
            }
        }
    }

    if (iconBitmap != null && !hasError) {
        Image(
            bitmap = iconBitmap!!,
            contentDescription = contentDescription,
            modifier = modifier.clip(CircleShape)
        )
    } else {
        DefaultAppIcon(modifier = modifier)
    }
}

@Composable
private fun DefaultAppIcon(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Settings,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun ErrorCard(
    message: String,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Error",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) {
                    Text(
                        text = "Dismiss",
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}

@Composable
private fun PromptSearchMessage() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Enter at least 3 characters to search",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
