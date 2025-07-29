package com.sameerasw.airsync.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sameerasw.airsync.data.local.DataStoreManager
import com.sameerasw.airsync.data.repository.AirSyncRepositoryImpl
import com.sameerasw.airsync.domain.model.NotificationApp
import com.sameerasw.airsync.domain.repository.AirSyncRepository
import com.sameerasw.airsync.utils.AppUtil
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class NotificationAppsViewModel(
    private val repository: AirSyncRepository
) : ViewModel() {

    companion object {
        fun create(context: Context): NotificationAppsViewModel {
            val dataStoreManager = DataStoreManager(context)
            val repository = AirSyncRepositoryImpl(dataStoreManager)
            return NotificationAppsViewModel(repository)
        }
    }

    private val _uiState = MutableStateFlow(NotificationAppsUiState())
    val uiState: StateFlow<NotificationAppsUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    // Filtered apps based on search query
    val filteredApps: StateFlow<List<NotificationApp>> = combine(
        _uiState.map { it.apps },
        _searchQuery
    ) { apps, query ->
        if (query.isBlank()) {
            apps
        } else {
            apps.filter { app ->
                app.appName.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun loadApps(context: Context) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            try {
                // Get saved apps and installed apps concurrently
                val savedApps = repository.getNotificationApps().first()
                val installedApps = AppUtil.getInstalledApps(context)

                // Merge installed apps with saved preferences
                val mergedApps = AppUtil.mergeWithSavedApps(installedApps, savedApps)

                // Save the merged list to ensure new apps are persisted
                repository.saveNotificationApps(mergedApps)

                _uiState.value = _uiState.value.copy(
                    apps = mergedApps,
                    isLoading = false,
                    error = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Failed to load apps: ${e.message}"
                )
            }
        }
    }

    fun toggleApp(packageName: String, isEnabled: Boolean) {
        viewModelScope.launch {
            val currentApps = _uiState.value.apps
            val updatedApps = currentApps.map { app ->
                if (app.packageName == packageName) {
                    app.copy(isEnabled = isEnabled, lastUpdated = System.currentTimeMillis())
                } else {
                    app
                }
            }

            _uiState.value = _uiState.value.copy(apps = updatedApps)
            repository.saveNotificationApps(updatedApps)
        }
    }

    fun toggleAllApps(enabled: Boolean) {
        viewModelScope.launch {
            val currentApps = _uiState.value.apps
            val updatedApps = currentApps.map { app ->
                app.copy(isEnabled = enabled, lastUpdated = System.currentTimeMillis())
            }

            _uiState.value = _uiState.value.copy(apps = updatedApps)
            repository.saveNotificationApps(updatedApps)
        }
    }

    fun toggleSystemApps(showSystemApps: Boolean) {
        _uiState.value = _uiState.value.copy(showSystemApps = showSystemApps)
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}

data class NotificationAppsUiState(
    val apps: List<NotificationApp> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val showSystemApps: Boolean = false
)
