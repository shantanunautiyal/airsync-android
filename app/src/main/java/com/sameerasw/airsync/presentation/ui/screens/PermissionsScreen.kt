package com.sameerasw.airsync.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sameerasw.airsync.presentation.ui.components.PermissionGroupsList
import com.sameerasw.airsync.presentation.ui.components.PermissionsOverviewCard
import com.sameerasw.airsync.presentation.viewmodel.PermissionsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionsScreen(
    onNavigateBack: () -> Unit,
    viewModel: PermissionsViewModel = viewModel()
) {
    val context = LocalContext.current
    val permissionGroups by viewModel.permissionGroups.collectAsState()
    val missingCount by viewModel.missingCount.collectAsState()
    
    // Refresh permissions when screen is resumed
    DisposableEffect(Unit) {
        viewModel.refreshPermissions()
        onDispose { }
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Permissions") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Overview card
            PermissionsOverviewCard(
                permissionGroups = permissionGroups,
                onRefresh = { viewModel.refreshPermissions() }
            )
            
            // Permission groups list
            PermissionGroupsList(
                permissionGroups = permissionGroups,
                onRefresh = { viewModel.refreshPermissions() }
            )
        }
    }
}

@Composable
fun PermissionsBanner(
    missingCount: Int,
    missingRequiredCount: Int,
    onClick: () -> Unit
) {
    if (missingCount > 0) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (missingRequiredCount > 0) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
            ),
            onClick = onClick
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (missingRequiredCount > 0) {
                            "Required Permissions Missing"
                        } else {
                            "Optional Permissions Available"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = if (missingRequiredCount > 0) {
                            "$missingRequiredCount required, ${missingCount - missingRequiredCount} optional"
                        } else {
                            "$missingCount optional permissions"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                
                TextButton(onClick = onClick) {
                    Text("Review")
                }
            }
        }
    }
}
