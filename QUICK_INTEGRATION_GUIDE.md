# Quick Integration Guide - Permission Management

## 🚀 5-Minute Integration

### Step 1: Add to Your Main Screen (2 minutes)

Open your `MainActivity.kt` or main Compose screen and add:

```kotlin
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sameerasw.airsync.presentation.ui.components.PermissionsBanner
import com.sameerasw.airsync.presentation.viewmodel.PermissionsViewModel

@Composable
fun YourMainScreen(navController: NavController) {
    val permissionsViewModel: PermissionsViewModel = viewModel()
    val missingCount by permissionsViewModel.missingCount.collectAsState()
    val missingRequiredCount by permissionsViewModel.missingRequiredCount.collectAsState()
    
    Scaffold { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            // Add this banner at the top
            PermissionsBanner(
                missingCount = missingCount,
                missingRequiredCount = missingRequiredCount,
                onClick = { navController.navigate("permissions") }
            )
            
            // Your existing UI below
            // ...
        }
    }
}
```

### Step 2: Add Permissions Screen to Navigation (2 minutes)

In your navigation setup:

```kotlin
import com.sameerasw.airsync.presentation.ui.screens.PermissionsScreen

NavHost(navController, startDestination = "main") {
    composable("main") { YourMainScreen(navController) }
    
    // Add this route
    composable("permissions") {
        PermissionsScreen(
            onNavigateBack = { navController.popBackStack() }
        )
    }
    
    // Your other routes...
}
```

### Step 3: Test (1 minute)

1. Build and run the app
2. You should see a banner showing missing permissions
3. Click the banner to open the permissions screen
4. Grant permissions and see the banner disappear

## ✅ That's It!

Your app now has:
- ✅ Automatic permission detection
- ✅ Beautiful permission UI
- ✅ Health Connect support
- ✅ Activity Recognition support
- ✅ Location permission support
- ✅ All 17 permissions tracked

---

## 🎨 Optional: Add to Settings Screen

If you have a settings screen, add a menu item:

```kotlin
@Composable
fun SettingsScreen(navController: NavController) {
    val viewModel: PermissionsViewModel = viewModel()
    val missingCount by viewModel.missingCount.collectAsState()
    
    LazyColumn {
        item {
            ListItem(
                headlineContent = { Text("Permissions") },
                supportingContent = { 
                    Text(if (missingCount > 0) {
                        "$missingCount permissions needed"
                    } else {
                        "All permissions granted"
                    })
                },
                leadingContent = {
                    Icon(Icons.Default.Security, contentDescription = null)
                },
                trailingContent = {
                    if (missingCount > 0) {
                        Badge { Text(missingCount.toString()) }
                    }
                },
                modifier = Modifier.clickable {
                    navController.navigate("permissions")
                }
            )
        }
        
        // Your other settings items...
    }
}
```

---

## 🔔 Optional: Show on First Launch

Show permissions screen automatically on first launch:

```kotlin
@Composable
fun App() {
    val navController = rememberNavController()
    val viewModel: PermissionsViewModel = viewModel()
    val missingRequiredCount by viewModel.missingRequiredCount.collectAsState()
    
    // Check if this is first launch
    val context = LocalContext.current
    val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    val isFirstLaunch = prefs.getBoolean("first_launch", true)
    
    LaunchedEffect(Unit) {
        if (isFirstLaunch && missingRequiredCount > 0) {
            // Navigate to permissions screen
            navController.navigate("permissions")
            // Mark as not first launch
            prefs.edit().putBoolean("first_launch", false).apply()
        }
    }
    
    NavHost(navController, startDestination = "main") {
        // Your routes...
    }
}
```

---

## 📱 Testing Checklist

- [ ] Banner shows when permissions are missing
- [ ] Banner disappears when all permissions granted
- [ ] Clicking banner opens permissions screen
- [ ] "Grant All" button requests runtime permissions
- [ ] Individual "Grant" buttons work
- [ ] Health Connect opens correctly
- [ ] Accessibility settings open correctly
- [ ] Notification listener settings open correctly
- [ ] Permission status updates after granting
- [ ] Required permissions are marked clearly

---

## 🐛 Troubleshooting

### Banner Not Showing
```kotlin
// Make sure ViewModel is initialized
val viewModel: PermissionsViewModel = viewModel()

// Check if permissions are actually missing
Log.d("Permissions", "Missing count: ${viewModel.getMissingPermissionCount()}")
```

### Health Connect Not Working
```kotlin
// Check if Health Connect is available
val isAvailable = HealthConnectUtil.isAvailable(context)
Log.d("HealthConnect", "Available: $isAvailable")

// Make sure Health Connect app is installed
// Install from: https://play.google.com/store/apps/details?id=com.google.android.apps.healthdata
```

### Permissions Not Updating
```kotlin
// Call refresh after granting permissions
viewModel.refreshPermissions()

// Or use DisposableEffect to auto-refresh
DisposableEffect(Unit) {
    viewModel.refreshPermissions()
    onDispose { }
}
```

---

## 🎉 Done!

Your app now has a complete permission management system with:
- Beautiful UI
- Health Connect support
- Activity tracking support
- Location permissions
- All 17 permissions tracked

**Total Integration Time:** ~5 minutes
**Lines of Code Added:** ~20 lines
**Complexity:** Very Low

Enjoy! 🚀
