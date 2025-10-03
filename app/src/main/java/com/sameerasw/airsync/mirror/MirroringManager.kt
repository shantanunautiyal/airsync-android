package com.sameerasw.airsync.mirror

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.util.Log
import com.sameerasw.airsync.utils.WebSocketUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.nio.ByteBuffer

object MirroringManager {
    private const val TAG = "MirroringManager"
    private var mediaProjection: MediaProjection? = null
    private var encoder: MediaCodec? = null
    // cached codec configuration (SPS/PPS) in Annex-B (each NAL prefixed with 0x00 00 00 01)
    private var codecConfig: ByteArray? = null
    private var running = false

    fun requestStartProjection(activity: Activity, requestCode: Int) {
        val mgr = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val intent = mgr.createScreenCaptureIntent()
        activity.startActivityForResult(intent, requestCode)
    }

    fun startFromResult(context: Context, resultCode: Int, data: Intent?, width: Int, height: Int, bitrateMbps: Int) {
        try {
            if (data == null) {
                Log.e(TAG, "MediaProjection data is null")
                return
            }
            val mgr = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = mgr.getMediaProjection(resultCode, data)
            if (mediaProjection == null) {
                Log.e(TAG, "Failed to obtain MediaProjection")
                return
            }

            setupEncoder(context, width, height, bitrateMbps * 1_000_000)
            running = true

            // Start reading encoder output in background
            CoroutineScope(Dispatchers.IO).launch {
                drainEncoderLoop()
            }

        } catch (e: Exception) {
            Log.e(TAG, "startFromResult error: ${e.message}")
        }
    }

    private fun setupEncoder(context: Context, width: Int, height: Int, bitrate: Int) {
        try {
            val format = MediaFormat.createVideoFormat("video/avc", width, height)
            format.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            format.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)

            encoder = MediaCodec.createEncoderByType("video/avc")
            encoder?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

            val surface = encoder?.createInputSurface()
            encoder?.start()

            // Create virtual display to push frames to encoder input surface
            mediaProjection?.createVirtualDisplay(
                "AirSyncMirror",
                width,
                height,
                context.resources.displayMetrics.densityDpi,
                0,
                surface,
                null,
                null
            )

        } catch (e: Exception) {
            Log.e(TAG, "setupEncoder failed: ${e.message}")
        }
    }

    private fun drainEncoderLoop() {
        try {
            val codec = encoder ?: return
            val info = MediaCodec.BufferInfo()
            while (running) {
                val outIndex = codec.dequeueOutputBuffer(info, 10000)
                if (outIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    // Output format changed - try to extract codec config (csd-0 / csd-1)
                    try {
                        val format = codec.outputFormat
                        val pieces = mutableListOf<ByteArray>()
                        format.getByteBuffer("csd-0")?.let { bb ->
                            val a = ByteArray(bb.remaining())
                            bb.get(a)
                            pieces.add(a)
                        }
                        format.getByteBuffer("csd-1")?.let { bb ->
                            val a = ByteArray(bb.remaining())
                            bb.get(a)
                            pieces.add(a)
                        }
                        if (pieces.isNotEmpty()) {
                            // combine with Annex-B start codes
                            val total = pieces.sumOf { it.size + 4 }
                            val combined = ByteArray(total)
                            var pos = 0
                            for (p in pieces) {
                                combined[pos++] = 0
                                combined[pos++] = 0
                                combined[pos++] = 0
                                combined[pos++] = 1
                                System.arraycopy(p, 0, combined, pos, p.size)
                                pos += p.size
                            }
                            codecConfig = combined
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to read codec config from output format: ${e.message}")
                    }
                } else if (outIndex >= 0) {
                    val encoded = codec.getOutputBuffer(outIndex)
                    encoded?.let { buf ->
                        // Read only the valid slice of the buffer using offset/size
                        try {
                            buf.position(info.offset)
                            buf.limit(info.offset + info.size)
                            val bytes = ByteArray(info.size)
                            buf.get(bytes)

                            // If this buffer is codec config, cache it (don't send as a normal frame)
                            if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                                val cfg = ByteArray(4 + bytes.size)
                                cfg[0] = 0
                                cfg[1] = 0
                                cfg[2] = 0
                                cfg[3] = 1
                                System.arraycopy(bytes, 0, cfg, 4, bytes.size)
                                codecConfig = cfg
                            } else {
                                val framed: ByteArray = if (info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0 && codecConfig != null) {
                                    // Prepend cached SPS/PPS before the key frame
                                    val out = ByteArray(codecConfig!!.size + 4 + bytes.size)
                                    System.arraycopy(codecConfig!!, 0, out, 0, codecConfig!!.size)
                                    out[codecConfig!!.size] = 0
                                    out[codecConfig!!.size + 1] = 0
                                    out[codecConfig!!.size + 2] = 0
                                    out[codecConfig!!.size + 3] = 1
                                    System.arraycopy(bytes, 0, out, codecConfig!!.size + 4, bytes.size)
                                    out
                                } else {
                                    val out = ByteArray(4 + bytes.size)
                                    out[0] = 0
                                    out[1] = 0
                                    out[2] = 0
                                    out[3] = 1
                                    System.arraycopy(bytes, 0, out, 4, bytes.size)
                                    out
                                }

                                WebSocketUtil.sendVideoFrame(framed)
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Error reading encoded buffer: ${e.message}")
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "drainEncoderLoop error: ${e.message}")
        }
    }

    fun stop() {
        try {
            running = false
            encoder?.stop()
            encoder?.release()
            encoder = null
            mediaProjection?.stop()
            mediaProjection = null
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping mirroring: ${e.message}")
        }
    }
}
