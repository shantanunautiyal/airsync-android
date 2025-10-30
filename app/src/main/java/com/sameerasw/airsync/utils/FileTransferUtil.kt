package com.sameerasw.airsync.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Base64
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.sameerasw.airsync.presentation.ui.screens.TransferStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.security.MessageDigest

object FileTransferUtil {
    private const val TAG = "FileTransferUtil"
    private const val CHUNK_SIZE = 64 * 1024 // 64KB chunks
    
    suspend fun sendFile(
        context: Context,
        uri: Uri,
        onProgress: (Float, TransferStatus) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            onProgress(0f, TransferStatus.PENDING)
            
            val fileName = getFileName(context, uri)
            val fileSize = getFileSize(context, uri)
            
            Log.d(TAG, "Sending file: $fileName, size: $fileSize bytes")
            
            // Read file content
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw Exception("Cannot open file")
            
            onProgress(0f, TransferStatus.TRANSFERRING)
            
            val buffer = ByteArray(CHUNK_SIZE)
            var bytesRead: Int
            var totalBytesRead = 0L
            val chunks = mutableListOf<String>()
            val digest = MessageDigest.getInstance("SHA-256")
            
            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                val chunk = if (bytesRead < CHUNK_SIZE) {
                    buffer.copyOf(bytesRead)
                } else {
                    buffer
                }
                
                // Update checksum
                digest.update(chunk)
                
                val base64Chunk = Base64.encodeToString(chunk, Base64.NO_WRAP)
                chunks.add(base64Chunk)
                
                totalBytesRead += bytesRead
                val progress = totalBytesRead.toFloat() / fileSize
                onProgress(progress, TransferStatus.TRANSFERRING)
            }
            
            inputStream.close()
            
            // Calculate final checksum
            val checksum = digest.digest().joinToString("") { "%02x".format(it) }
            Log.d(TAG, "File checksum: $checksum")
            
            // Send file metadata and chunks via WebSocket
            val fileTransferJson = JsonUtil.createFileTransferJson(
                fileName = fileName,
                fileSize = fileSize,
                chunks = chunks,
                checksum = checksum
            )
            
            val success = WebSocketUtil.sendMessage(fileTransferJson)
            
            if (success) {
                Log.d(TAG, "File sent successfully: $fileName")
                onProgress(1f, TransferStatus.COMPLETED)
            } else {
                throw Exception("Failed to send file via WebSocket")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending file: ${e.message}", e)
            onProgress(0f, TransferStatus.FAILED)
            throw e
        }
    }
    
    suspend fun sendFolder(
        context: Context,
        uri: Uri,
        onProgress: (Float, TransferStatus) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            onProgress(0f, TransferStatus.PENDING)
            
            // Get folder name
            val folderName = getFileName(context, uri)
            
            Log.d(TAG, "Sending folder: $folderName")
            
            // List all files in the folder
            val documentFile = DocumentFile.fromTreeUri(context, uri)
            val files = documentFile?.listFiles() ?: emptyArray()
            
            if (files.isEmpty()) {
                throw Exception("Folder is empty")
            }
            
            onProgress(0f, TransferStatus.TRANSFERRING)
            
            // Send each file
            files.forEachIndexed { index: Int, file: DocumentFile ->
                if (file.isFile) {
                    sendFile(context, file.uri) { _, _ -> }
                }
                
                val progress = (index + 1).toFloat() / files.count()
                onProgress(progress, TransferStatus.TRANSFERRING)
            }
            
            onProgress(1f, TransferStatus.COMPLETED)
            Log.d(TAG, "Folder sent successfully: $folderName")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error sending folder: ${e.message}", e)
            onProgress(0f, TransferStatus.FAILED)
            throw e
        }
    }
    
    fun getFileName(context: Context, uri: Uri): String {
        var name = "unknown"
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) {
                    name = it.getString(nameIndex)
                }
            }
        }
        return name
    }
    
    fun getFileSize(context: Context, uri: Uri): Long {
        var size = 0L
        val cursor = context.contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex != -1) {
                    size = it.getLong(sizeIndex)
                }
            }
        }
        return size
    }
}
