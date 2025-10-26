package com.sameerasw.airsync.presentation.ui.activities

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.sameerasw.airsync.domain.model.MirroringOptions
import com.sameerasw.airsync.service.ScreenCaptureService

class ScreenShareActivity : ComponentActivity() {

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data
            if (data != null) {
                val mirroringOptions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(ScreenCaptureService.EXTRA_MIRRORING_OPTIONS, MirroringOptions::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(ScreenCaptureService.EXTRA_MIRRORING_OPTIONS)
                }

                mirroringOptions?.let {
                    val serviceIntent = Intent(this, ScreenCaptureService::class.java).apply {
                        action = ScreenCaptureService.ACTION_START
                        putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                        putExtra(ScreenCaptureService.EXTRA_DATA, data)
                        putExtra(ScreenCaptureService.EXTRA_MIRRORING_OPTIONS, it)
                    }
                    startForegroundService(serviceIntent)
                }
            }
        }
        finish() // Finish the activity regardless of the result
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val screenCaptureIntent = mediaProjectionManager.createScreenCaptureIntent()
        screenCaptureLauncher.launch(screenCaptureIntent)
    }
}
