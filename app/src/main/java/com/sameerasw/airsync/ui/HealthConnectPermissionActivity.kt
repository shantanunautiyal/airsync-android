// app/src/main/java/com/sameerasw/airsync/ui/HealthConnectPermissionActivity.kt
package com.sameerasw.airsync.ui

import android.os.Bundle
import androidx.activity.ComponentActivity

// This is a placeholder activity required by Health Connect for the permission flow.
// It will be transparent and immediately finish.
class HealthConnectPermissionActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // You can leave this empty or call finish() immediately if needed,
        // but the system handles the permission UI.
    }
}