
package com.sameerasw.airsync.service

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaCodec
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.util.Base64
import android.util.Log
import com.sameerasw.airsync.R
import com.sameerasw.airsync.utils.JsonUtil
import com.sameerasw.airsync.utils.ScreenMirroringManager
import com.sameerasw.airsync.utils.WebSocketUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ScreenCaptureService : Service() {

    private var mediaProjection: MediaProjection? = null
    private var screenMirroringManager: ScreenMirroringManager? = null

    private var handlerThread: HandlerThread? = null
    private var backgroundHandler: Handler? = null

    private val mediaProjectionCallback = object : MediaProjection.Callback() {
        override fun onStop() {
            super.onStop()
            Log.d(TAG, "MediaProjection session stopped by user.")
            stopMirroring()
        }
    }

    companion object {
        private const val TAG = "ScreenCaptureService"
        private const val NOTIFICATION_ID = 123
        private const val NOTIFICATION_CHANNEL_ID = "ScreenCaptureChannel"
        const val ACTION_START = "com.sameerasw.airsync.service.ScreenCaptureService.START"
        const val ACTION_STOP = "com.sameerasw.airsync.service.ScreenCaptureService.STOP"
        const val EXTRA_RESULT_CODE = "resultCode"
        const val EXTRA_DATA = "data"
        const val EXTRA_MIRRORING_OPTIONS = "mirroringOptions"

        private val _isStreaming = MutableStateFlow(false)
        val isStreaming = _isStreaming.asStateFlow()

        var instance: ScreenCaptureService? = null
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
        handlerThread = HandlerThread("ScreenCaptureThread").also { it.start() }
        backgroundHandler = Handler(handlerThread!!.looper)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                // Prevent duplicate mirroring sessions
                if (_isStreaming.value) {
                    Log.w(TAG, "Screen mirroring already active, ignoring duplicate start request")
                    return START_NOT_STICKY
                }

                startForegroundWithServiceType()
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
                val mirroringOptions = intent.getParcelableExtra<com.sameerasw.airsync.domain.model.MirroringOptions>(EXTRA_MIRRORING_OPTIONS)

                if (resultCode == Activity.RESULT_OK && data != null && mirroringOptions != null) {
                    _isStreaming.value = true
                    initializeMediaProjection(resultCode, data, mirroringOptions)
                    screenMirroringManager?.startMirroring()
                } else {
                    Log.e(TAG, "Invalid start parameters for screen capture. Stopping service.")
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopMirroring()
            }
        }
        return START_NOT_STICKY
    }

    private fun startForegroundWithServiceType() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }
    }

    private fun initializeMediaProjection(resultCode: Int, data: Intent, mirroringOptions: com.sameerasw.airsync.domain.model.MirroringOptions) {
        val mediaProjectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = mediaProjectionManager.getMediaProjection(resultCode, data)
        mediaProjection?.registerCallback(mediaProjectionCallback, backgroundHandler)
        screenMirroringManager = ScreenMirroringManager(this, mediaProjection!!, backgroundHandler!!, ::sendMirrorFrame, mirroringOptions)
    }

    fun stopMirroring() {
        if (!_isStreaming.compareAndSet(expect = true, update = false)) return
        Log.d(TAG, "Stopping screen capture.")

        screenMirroringManager?.stopMirroring()
        mediaProjection?.unregisterCallback(mediaProjectionCallback)
        mediaProjection?.stop()
        mediaProjection = null

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun resendConfig() {
        backgroundHandler?.post {
            screenMirroringManager?.resendConfig()
        }
    }

    private fun sendMirrorFrame(frame: ByteArray, bufferInfo: MediaCodec.BufferInfo) {
        CoroutineScope(Dispatchers.IO).launch {
            val isConfig = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) != 0
            val base64Frame = Base64.encodeToString(frame, Base64.NO_WRAP)
            val json = JsonUtil.createMirrorFrameJson(base64Frame, bufferInfo.presentationTimeUs, isConfig)
            WebSocketUtil.sendMessage(json)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID, "Screen Capture",
            NotificationManager.IMPORTANCE_LOW
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val stopIntent = Intent(this, ScreenCaptureService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStopIntent = PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        return builder
            .setContentTitle("AirSync")
            .setContentText("Screen mirroring is active.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .addAction(0, "Stop", pendingStopIntent)
            .build()
    }

    override fun onDestroy() {
        stopMirroring()
        handlerThread?.quitSafely()
        instance = null
        super.onDestroy()
    }
}
