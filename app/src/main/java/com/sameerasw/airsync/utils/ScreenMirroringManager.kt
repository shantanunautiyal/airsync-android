package com.sameerasw.airsync.utils

import android.content.Context
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.os.Build
import android.os.Handler
import android.util.Log
import com.sameerasw.airsync.domain.model.MirroringOptions // Make sure this import matches your project structure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ScreenMirroringManager(
    private val context: Context,
    private val mediaProjection: MediaProjection,
    private val backgroundHandler: Handler,
    private val sendFrame: (ByteArray, MediaCodec.BufferInfo) -> Unit,
    private val mirroringOptions: MirroringOptions
) {

    private var virtualDisplay: VirtualDisplay? = null
    private var mediaCodec: MediaCodec? = null
    private var streamingJob: Job? = null

    private var sps: ByteArray? = null
    private var pps: ByteArray? = null

    private var encoderWidth: Int = 0
    private var encoderHeight: Int = 0

    private companion object {
        private const val TAG = "ScreenMirroringManager"
        private const val MIME_TYPE = MediaFormat.MIMETYPE_VIDEO_AVC // H.264
        private val START_CODE = byteArrayOf(0, 0, 0, 1) // Annex B Start Code
    }

    private fun computeEncoderSize(
        requestedWidth: Int,
        requestedHeight: Int,
        displayWidth: Int,
        displayHeight: Int
    ): Pair<Int, Int> {
        val scale = Math.min(
            Math.min(
                displayWidth.toFloat() / requestedWidth.toFloat(),
                displayHeight.toFloat() / requestedHeight.toFloat()
            ),
            1f // Don't scale up
        )
        var w = (requestedWidth * scale).toInt()
        var h = (requestedHeight * scale).toInt()

        fun align16(x: Int) = (x + 15) / 16 * 16
        w = align16(w)
        h = align16(h)

        if (w > displayWidth) w = (displayWidth / 16) * 16
        if (h > displayHeight) h = (displayHeight / 16) * 16

        w = Math.max(16, w)
        h = Math.max(16, h)
        return w to h
    }

    fun startMirroring() {
        Log.d(TAG, "Starting mirroring (Attempting MAIN Profile)...")
        try {
            val metrics = context.resources.displayMetrics
            val displayWidth = metrics.widthPixels
            val displayHeight = metrics.heightPixels

            val requestedWidth = mirroringOptions.maxWidth
            val requestedHeight = (requestedWidth.toFloat() * displayHeight.toFloat() / displayWidth.toFloat()).toInt()

            val (width, height) = computeEncoderSize(requestedWidth, requestedHeight, displayWidth, displayHeight)
            encoderWidth = width
            encoderHeight = height
            Log.d(TAG, "Encoder Resolution: ${encoderWidth}x${encoderHeight}")

            val format = MediaFormat.createVideoFormat(MIME_TYPE, encoderWidth, encoderHeight).apply {
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
                setInteger(MediaFormat.KEY_BIT_RATE, mirroringOptions.bitrateKbps * 1000)
                setInteger(MediaFormat.KEY_FRAME_RATE, mirroringOptions.fps)

                // --- Request Main Profile ---
                Log.i(TAG, "Requesting AVCProfileMain")
                setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileMain)

                setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31) // Request 3.1
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setInteger(MediaFormat.KEY_BITRATE_MODE, MediaCodecInfo.EncoderCapabilities.BITRATE_MODE_CBR)
                }
            }

            Log.d(TAG, "Configuring MediaFormat: $format")

            mediaCodec = findBestEncoder(MIME_TYPE) // Use the updated function
            if (mediaCodec == null) {
                Log.e(TAG, "❌ No suitable AVC encoder found supporting MAIN profile and Surface input.")
                stopMirroring()
                return
            }

            Log.d(TAG, "Selected Encoder: ${mediaCodec?.name}")

            mediaCodec?.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

            val inputSurface = mediaCodec?.createInputSurface() ?: run {
                Log.e(TAG, "❌ Failed to create input surface.")
                stopMirroring()
                return
            }

            virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenCapture",
                encoderWidth, encoderHeight, metrics.densityDpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface, null, backgroundHandler
            )

            mediaCodec?.start()
            Log.d(TAG, "✅ MediaCodec started.")

            streamingJob = CoroutineScope(Dispatchers.IO).launch {
                processEncodedData()
            }
            Log.d(TAG, "✅ Streaming coroutine launched.")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start streaming setup", e)
            stopMirroring()
        }
    }

    // --- findBestEncoder still looks for MAIN Profile ---
    private fun findBestEncoder(mimeType: String): MediaCodec? {
        try {
            val codecList = MediaCodecList(MediaCodecList.ALL_CODECS)
            val qcomEncoders = mutableListOf<String>()
            val hardwareEncoders = mutableListOf<String>()
            val googleSoftwareEncoders = mutableListOf<String>()
            val otherSoftwareEncoders = mutableListOf<String>()

            Log.d(TAG, "Available Encoders for $mimeType:")
            for (codecInfo in codecList.codecInfos) {
                if (!codecInfo.isEncoder) continue
                if (!codecInfo.supportedTypes.any { it.equals(mimeType, ignoreCase = true) }) continue
                Log.d(TAG, "  - ${codecInfo.name} (SW Only: ${codecInfo.isSoftwareOnly})")
                try {
                    val capabilities = codecInfo.getCapabilitiesForType(mimeType)

                    val supportsMain = capabilities.profileLevels.any {
                        it.profile == MediaCodecInfo.CodecProfileLevel.AVCProfileMain
                    }
                    val supportsSurface = capabilities.colorFormats.any { it == MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface }

                    Log.v(TAG, "    Supports Main Profile: $supportsMain, Supports Surface: $supportsSurface")

                    if (!supportsMain || !supportsSurface) {
                        Log.v(TAG, "    Skipping: Doesn't meet Main/Surface requirement.")
                        continue
                    }

                    when {
                        codecInfo.name.contains("qcom", ignoreCase = true) || codecInfo.name.contains("qti", ignoreCase = true) -> {
                            if (!codecInfo.isSoftwareOnly) qcomEncoders.add(codecInfo.name) else otherSoftwareEncoders.add(codecInfo.name)
                        }
                        !codecInfo.isSoftwareOnly -> hardwareEncoders.add(codecInfo.name)
                        codecInfo.name.contains("google", ignoreCase = true) -> googleSoftwareEncoders.add(codecInfo.name)
                        else -> otherSoftwareEncoders.add(codecInfo.name)
                    }
                } catch (e: Exception) { Log.w(TAG, "    Could not check capabilities for ${codecInfo.name}: ${e.message}") }
            }

            val prioritizedList = googleSoftwareEncoders + hardwareEncoders + otherSoftwareEncoders + qcomEncoders
            Log.d(TAG, "Prioritized Encoder List (for Main Profile):")
            prioritizedList.forEachIndexed { index, name -> Log.d(TAG, "  ${index + 1}. $name") }

            for (encoderName in prioritizedList) {
                try {
                    Log.i(TAG, "Attempting to create encoder: $encoderName")
                    val codec = MediaCodec.createByCodecName(encoderName)
                    Log.i(TAG, "✅ Successfully created encoder: $encoderName")
                    return codec
                } catch (e: Exception) { Log.e(TAG, "❌ Failed to create $encoderName: ${e.message}") }
            }

            Log.w(TAG, "No suitable encoder from list. Falling back to createEncoderByType.")
            return MediaCodec.createEncoderByType(mimeType)
        } catch (e: Exception) { Log.e(TAG, "Encoder selection error", e); return null }
    }

    private fun stripStartCode(data: ByteArray): ByteArray {
        return when {
            data.size >= 4 && data[0] == 0.toByte() && data[1] == 0.toByte() && data[2] == 0.toByte() && data[3] == 1.toByte() -> {
                Log.v(TAG, "Stripped 4-byte start code"); data.copyOfRange(4, data.size)
            }
            data.size >= 3 && data[0] == 0.toByte() && data[1] == 0.toByte() && data[2] == 1.toByte() -> {
                Log.v(TAG, "Stripped 3-byte start code"); data.copyOfRange(3, data.size)
            }
            else -> { Log.v(TAG, "No Annex B start code found"); data }
        }
    }

    // --- ** FIX: sanitizeSPS to check for Main Profile (0x4D = 77) ** ---
    private fun sanitizeSPS(spsData: ByteArray): ByteArray {
        if (spsData.isEmpty()) {
            Log.e(TAG, "SPS Sanitization Error: Input data is empty")
            throw IllegalArgumentException("SPS data is empty")
        }

        val firstByte = spsData[0].toInt() and 0xFF
        val startsWithNALHeader = (firstByte and 0x1F) == 7

        val profileIndex = if (startsWithNALHeader) 1 else 0
        val constraintIndex = profileIndex + 1
        val levelIndex = profileIndex + 2

        if (spsData.size <= levelIndex) {
            Log.e(TAG, "SPS Sanitization Error: SPS too short (${spsData.size} bytes)")
            throw IllegalArgumentException("SPS too short: ${spsData.size} bytes")
        }

        val sanitized = spsData.copyOf()
        var modified = false

        val profileIdc = sanitized[profileIndex].toInt() and 0xFF
        val constraintFlags = sanitized[constraintIndex].toInt() and 0xFF
        val levelIdc = sanitized[levelIndex].toInt() and 0xFF

        Log.d(TAG, "Original SPS Params - Profile: 0x${profileIdc.toString(16)}, " +
                "Constraints: 0x${constraintFlags.toString(16)}, Level: 0x${levelIdc.toString(16)}")

        // 1. Fix Profile to Main (0x4D = 77) if it isn't already
        val mainProfileIdc = 77 // 0x4D
        if (profileIdc != mainProfileIdc) {
            sanitized[profileIndex] = mainProfileIdc.toByte()
            Log.i(TAG, "Sanitized profile: 0x${profileIdc.toString(16)} -> 0x4D (Main)")
            modified = true
        }

        // 2. Constraint flag sanitization (SKIPPED)
        Log.d(TAG, "Constraint flag sanitization skipped. Using original flags: 0x${constraintFlags.toString(16)}")

        // 3. Fix Level to 3.1 (0x1F = 31) if it's currently higher
        if (levelIdc > 0x1F) {
            sanitized[levelIndex] = 0x1F.toByte() // 31 corresponds to Level 3.1
            Log.i(TAG, "Sanitized level: 0x${levelIdc.toString(16)} -> 0x1F (Level 3.1)")
            modified = true
        }

        if (modified) {
            Log.i(TAG, "✅ SPS profile/level possibly modified.")
        } else {
            Log.d(TAG, "SPS parameters not modified by sanitizer.")
        }

        return if (startsWithNALHeader) {
            sanitized
        } else {
            Log.w(TAG, "Original SPS missing NAL header (0x67), prepending.")
            byteArrayOf(0x67.toByte()) + sanitized
        }
    }
    // --- End of sanitizeSPS ---


    private fun processEncodedData() {
        val bufferInfo = MediaCodec.BufferInfo()
        Log.d(TAG, "Encoding loop started on thread: ${Thread.currentThread().name}")

        while (streamingJob?.isActive == true) {
            try {
                val codec = mediaCodec ?: break
                val outputBufferIndex = codec.dequeueOutputBuffer(bufferInfo, 10000)

                when {
                    outputBufferIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        Log.i(TAG, "Output format changed. Extracting SPS/PPS...")
                        val outputFormat = codec.outputFormat
                        val rawSpsBuffer = outputFormat.getByteBuffer("csd-0")
                        val rawPpsBuffer = outputFormat.getByteBuffer("csd-1")

                        if (rawSpsBuffer != null && rawPpsBuffer != null) {
                            val rawSpsBytes = ByteArray(rawSpsBuffer.remaining())
                            rawSpsBuffer.get(rawSpsBytes)
                            val rawPpsBytes = ByteArray(rawPpsBuffer.remaining())
                            rawPpsBuffer.get(rawPpsBytes)
                            Log.d(TAG, "Raw csd-0 (SPS) [${rawSpsBytes.size}]: ${rawSpsBytes.take(20).joinToString(" ") { "%02X".format(it) }}...")
                            Log.d(TAG, "Raw csd-1 (PPS) [${rawPpsBytes.size}]: ${rawPpsBytes.take(10).joinToString(" ") { "%02X".format(it) }}...")

                            val spsWithoutStartCode = stripStartCode(rawSpsBytes)
                            val ppsWithoutStartCode = stripStartCode(rawPpsBytes)
                            Log.d(TAG, "After strip - SPS [${spsWithoutStartCode.size}]: ${spsWithoutStartCode.take(12).joinToString(" ") { "%02X".format(it) }}...")
                            Log.d(TAG, "After strip - PPS [${ppsWithoutStartCode.size}]: ${ppsWithoutStartCode.take(8).joinToString(" ") { "%02X".format(it) }}...")

                            val finalSanitizedSPS = try {
                                sanitizeSPS(spsWithoutStartCode) // Sanitize (for Main Profile + Level 3.1)
                            } catch (e: Exception) {
                                Log.e(TAG, "SPS sanitization failed: ${e.message}. Using stripped fallback.")
                                if (spsWithoutStartCode.isNotEmpty() && (spsWithoutStartCode[0].toInt() and 0x1F) == 7) spsWithoutStartCode
                                else if (spsWithoutStartCode.isNotEmpty()) byteArrayOf(0x67.toByte()) + spsWithoutStartCode
                                else { Log.e(TAG, "Fallback failed: Stripped SPS empty!"); byteArrayOf(0x67.toByte()) }
                            }

                            val finalPPS = if (ppsWithoutStartCode.isEmpty()) {
                                Log.e(TAG, "PPS empty!"); byteArrayOf(0x68.toByte())
                            } else {
                                if ((ppsWithoutStartCode[0].toInt() and 0x1F) != 8) { Log.w(TAG, "PPS missing header."); byteArrayOf(0x68.toByte()) + ppsWithoutStartCode }
                                else ppsWithoutStartCode
                            }

                            sps = START_CODE + finalSanitizedSPS
                            pps = START_CODE + finalPPS

                            Log.i(TAG, "✅ Processed SPS/PPS ready:")
                            Log.d(TAG, "  Final SPS [${finalSanitizedSPS.size}]: ${finalSanitizedSPS.take(12).joinToString(" ") { "%02X".format(it) }}...")
                            Log.d(TAG, "  Final PPS [${finalPPS.size}]: ${finalPPS.take(8).joinToString(" ") { "%02X".format(it) }}...")
                            Log.d(TAG, "  Stored with start codes - SPS total: ${sps?.size ?: 0}, PPS total: ${pps?.size ?: 0}")
                        } else { Log.e(TAG, "❌ Missing csd-0/csd-1!") }
                    }

                    outputBufferIndex >= 0 -> {
                        val outputBuffer = codec.getOutputBuffer(outputBufferIndex)
                        if (outputBuffer != null && bufferInfo.size > 0 && (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0) {
                            val data = ByteArray(bufferInfo.size)
                            outputBuffer.position(bufferInfo.offset); outputBuffer.limit(bufferInfo.offset + bufferInfo.size); outputBuffer.get(data)
                            val isKeyFrame = (bufferInfo.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME) != 0

                            val frameDataToSend: ByteArray = if (isKeyFrame) {
                                if (sps != null && pps != null) { Log.v(TAG, "Sending IDR (${data.size}) w/ SPS+PPS."); sps!! + pps!! + START_CODE + data }
                                else { Log.w(TAG, "Sending IDR (${data.size}) w/o SPS+PPS."); START_CODE + data }
                            } else { Log.v(TAG, "Sending non-IDR (${data.size})."); START_CODE + data }

                            val finalBufferInfo = MediaCodec.BufferInfo()
                            finalBufferInfo.set(0, frameDataToSend.size, bufferInfo.presentationTimeUs, bufferInfo.flags)
                            sendFrame(frameDataToSend, finalBufferInfo)
                        }
                        codec.releaseOutputBuffer(outputBufferIndex, false)
                    }
                    outputBufferIndex == MediaCodec.INFO_TRY_AGAIN_LATER -> Log.v(TAG, "dequeueOutputBuffer timed out.")
                    else -> Log.w(TAG, "Unexpected output buffer index: $outputBufferIndex")
                }
            } catch (e: IllegalStateException) { Log.e(TAG, "Codec error.", e); break }
            catch (e: Exception) { Log.e(TAG, "Encoding loop error", e); break }
        }
        Log.d(TAG, "Encoding loop finished on thread: ${Thread.currentThread().name}")
    }

    fun resendConfig() {
        backgroundHandler.post {
            if (sps != null && pps != null) {
                val configData = sps!! + pps!!
                val bufferInfo = MediaCodec.BufferInfo().apply { set(0, configData.size, 0, MediaCodec.BUFFER_FLAG_CODEC_CONFIG or MediaCodec.BUFFER_FLAG_KEY_FRAME) }
                Log.d(TAG, "Resending SPS/PPS config (${configData.size} bytes)")
                CoroutineScope(Dispatchers.IO).launch { sendFrame(configData, bufferInfo) }
            } else { Log.w(TAG, "Cannot resend config: SPS/PPS unavailable.") }
        }
    }

    fun stopMirroring() {
        Log.i(TAG, "Stopping mirroring...")
        streamingJob?.cancel(); streamingJob = null
        try { mediaCodec?.stop(); Log.d(TAG, "Codec stopped.") }
        catch (e: Exception) { Log.e(TAG, "Error stopping codec", e) }
        try { mediaCodec?.release(); Log.d(TAG, "Codec released.") }
        catch (e: Exception) { Log.e(TAG, "Error releasing codec", e) }
        mediaCodec = null
        try { virtualDisplay?.release(); Log.d(TAG, "VirtualDisplay released.") }
        catch (e: Exception) { Log.e(TAG, "Error releasing display", e) }
        virtualDisplay = null
        sps = null; pps = null; encoderWidth = 0; encoderHeight = 0
        Log.i(TAG, "✅ Mirroring stopped.")
    }
}

// Dummy data class if not imported
// data class MirroringOptions(val maxWidth: Int, val fps: Int, val bitrateKbps: Int)