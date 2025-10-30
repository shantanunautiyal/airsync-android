package com.sameerasw.airsync.utils

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.security.MessageDigest

object FileReceiveManager {
    private const val TAG = "FileReceiveManager"
    
    // Store ongoing file transfers
    private val activeTransfers = mutableMapOf<String, FileTransferState>()
    
    data class FileTransferState(
        val fileName: String,
        val fileSize: Long,
        val totalChunks: Int,
        val receivedChunks: MutableList<ByteArray>,
        val expectedChecksum: String?,
        var bytesReceived: Long = 0
    )
    
    /**
     * Initialize a new file transfer
     */
    fun initFileTransfer(
        transferId: String,
        fileName: String,
        fileSize: Long,
        totalChunks: Int,
        checksum: String?
    ) {
        Log.d(TAG, "Initializing file transfer: $fileName ($fileSize bytes, $totalChunks chunks)")
        
        activeTransfers[transferId] = FileTransferState(
            fileName = fileName,
            fileSize = fileSize,
            totalChunks = totalChunks,
            receivedChunks = MutableList(totalChunks) { ByteArray(0) },
            expectedChecksum = checksum
        )
    }
    
    /**
     * Receive a file chunk
     */
    fun receiveChunk(
        transferId: String,
        chunkIndex: Int,
        chunkData: String
    ): Boolean {
        val transfer = activeTransfers[transferId]
        if (transfer == null) {
            Log.e(TAG, "No active transfer found for ID: $transferId")
            return false
        }
        
        try {
            // Decode base64 chunk
            val decodedChunk = Base64.decode(chunkData, Base64.NO_WRAP)
            
            // Store chunk
            if (chunkIndex >= 0 && chunkIndex < transfer.totalChunks) {
                transfer.receivedChunks[chunkIndex] = decodedChunk
                transfer.bytesReceived += decodedChunk.size
                
                Log.d(TAG, "Received chunk $chunkIndex/${transfer.totalChunks} for ${transfer.fileName} (${transfer.bytesReceived}/${transfer.fileSize} bytes)")
                return true
            } else {
                Log.e(TAG, "Invalid chunk index: $chunkIndex (expected 0-${transfer.totalChunks - 1})")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error receiving chunk: ${e.message}", e)
            return false
        }
    }
    
    /**
     * Complete file transfer and save to disk
     */
    fun completeTransfer(
        context: Context,
        transferId: String
    ): Result<Uri> {
        val transfer = activeTransfers[transferId]
        if (transfer == null) {
            return Result.failure(Exception("No active transfer found for ID: $transferId"))
        }
        
        try {
            // Check if all chunks received
            val missingChunks = transfer.receivedChunks.withIndex()
                .filter { it.value.isEmpty() }
                .map { it.index }
            
            if (missingChunks.isNotEmpty()) {
                return Result.failure(Exception("Missing chunks: $missingChunks"))
            }
            
            // Combine all chunks
            val fileData = transfer.receivedChunks.fold(ByteArray(0)) { acc, chunk ->
                acc + chunk
            }
            
            // Verify file size
            if (fileData.size.toLong() != transfer.fileSize) {
                return Result.failure(
                    Exception("File size mismatch: expected ${transfer.fileSize}, got ${fileData.size}")
                )
            }
            
            // Verify checksum if provided
            transfer.expectedChecksum?.let { expectedChecksum ->
                val actualChecksum = calculateChecksum(fileData)
                if (actualChecksum != expectedChecksum) {
                    Log.e(TAG, "Checksum mismatch!")
                    Log.e(TAG, "Expected: $expectedChecksum")
                    Log.e(TAG, "Actual:   $actualChecksum")
                    return Result.failure(
                        Exception("Checksum mismatch: expected $expectedChecksum, got $actualChecksum")
                    )
                }
                Log.d(TAG, "Checksum verified: $actualChecksum")
            }
            
            // Save file
            val uri = saveFile(context, transfer.fileName, fileData)
            
            // Clean up
            activeTransfers.remove(transferId)
            
            Log.d(TAG, "File transfer completed successfully: ${transfer.fileName}")
            return Result.success(uri)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error completing transfer: ${e.message}", e)
            activeTransfers.remove(transferId)
            return Result.failure(e)
        }
    }
    
    /**
     * Cancel an ongoing transfer
     */
    fun cancelTransfer(transferId: String) {
        activeTransfers.remove(transferId)
        Log.d(TAG, "Transfer cancelled: $transferId")
    }
    
    /**
     * Get transfer progress
     */
    fun getProgress(transferId: String): Float {
        val transfer = activeTransfers[transferId] ?: return 0f
        return if (transfer.fileSize > 0) {
            transfer.bytesReceived.toFloat() / transfer.fileSize.toFloat()
        } else {
            0f
        }
    }
    
    /**
     * Calculate SHA-256 checksum
     */
    private fun calculateChecksum(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(data)
        return hash.joinToString("") { "%02x".format(it) }
    }
    
    /**
     * Save file to Downloads directory
     */
    private fun saveFile(context: Context, fileName: String, data: ByteArray): Uri {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Use MediaStore for Android 10+
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, getMimeType(fileName))
                put(MediaStore.Downloads.IS_PENDING, 1)
            }
            
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                ?: throw Exception("Failed to create MediaStore entry")
            
            resolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(data)
            } ?: throw Exception("Failed to open output stream")
            
            // Mark as not pending
            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
            
            uri
        } else {
            // Use direct file access for older Android versions
            val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            
            val file = File(downloadsDir, fileName)
            FileOutputStream(file).use { outputStream ->
                outputStream.write(data)
            }
            
            Uri.fromFile(file)
        }
    }
    
    /**
     * Get MIME type from file name
     */
    private fun getMimeType(fileName: String): String {
        return when (fileName.substringAfterLast('.', "").lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "pdf" -> "application/pdf"
            "txt" -> "text/plain"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "zip" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }
}
