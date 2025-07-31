package com.sameerasw.airsync.presentation.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sameerasw.airsync.presentation.ui.components.NotificationAppsContent
import com.sameerasw.airsync.presentation.viewmodel.NotificationAppsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationAppsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val viewModel: NotificationAppsViewModel = viewModel {
        NotificationAppsViewModel.create(context)
    }

    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val filteredApps by viewModel.filteredApps.collectAsState()

    // Load apps when screen is first displayed
    LaunchedEffect(Unit) {
        viewModel.loadApps(context)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text("Notification Apps")
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }
    ) { paddingValues ->
        NotificationAppsContent(
            apps = if (searchQuery.length >= 5) filteredApps else emptyList(),
            searchQuery = searchQuery,
            showSystemApps = uiState.showSystemApps,
            isLoading = uiState.isLoading,
            error = uiState.error,
            onSearchQueryChange = viewModel::updateSearchQuery,
            onToggleApp = viewModel::toggleApp,
            onToggleAllApps = viewModel::toggleAllApps,
            onToggleSystemApps = viewModel::toggleSystemApps,
            onClearError = viewModel::clearError,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        )
    }
}
