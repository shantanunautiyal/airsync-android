package com.sameerasw.airsync.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.sameerasw.airsync.utils.transfer.FileTransferProtocol
import com.sameerasw.airsync.utils.transfer.FileTransferUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.UUID

object FileSender {
    private const val LARGE_FILE_THRESHOLD = 10 * 1024 * 1024 // 10MB
    
    private val outgoingAcks = java.util.concurrent.ConcurrentHashMap<String, MutableSet<Int>>()
    private val transferStatus = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    fun clearAll() {
        outgoingAcks.clear()
        transferStatus.clear()
    }

    fun handleAck(id: String, index: Int) {
        outgoingAcks[id]?.add(index)
    }

    fun handleVerified(id: String, verified: Boolean) {
        transferStatus[id] = verified
    }

    fun cancelTransfer(id: String) {
        // Remove from acks to stop the loop
        if (outgoingAcks.remove(id) != null) {
            Log.d("FileSender", "Cancelling transfer $id")
            // Send cancel message
            WebSocketUtil.sendMessage(FileTransferProtocol.buildCancel(id))
            transferStatus.remove(id)
        }
    }

    fun sendFile(context: Context, uri: Uri, chunkSize: Int = 64 * 1024) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resolver = context.contentResolver
                val name = resolver.getFileName(uri) ?: "shared_file"
                val mime = resolver.getType(uri) ?: "application/octet-stream"
                
                // Get file size first
                val fileSize = resolver.openInputStream(uri)?.use { it.available() } ?: 0
                
                if (fileSize > LARGE_FILE_THRESHOLD) {
                    // Use streaming approach for large files
                    sendLargeFile(context, uri, name, mime, chunkSize)
                } else {
                    // Use in-memory approach for small files
                    sendSmallFile(context, uri, name, mime, chunkSize)
                }
                val isFileUri = uri.scheme == "file"

                val name = if (isFileUri) {
                    uri.lastPathSegment ?: "shared_file"
                } else {
                    resolver.getFileName(uri) ?: "shared_file"
                }

                val mime = if (isFileUri) {
                    val extension =
                        android.webkit.MimeTypeMap.getFileExtensionFromUrl(uri.toString())
                    android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
                        ?: "application/octet-stream"
                } else {
                    resolver.getType(uri) ?: "application/octet-stream"
                }

                // 1. Get size
                val size = if (isFileUri) {
                    java.io.File(uri.path ?: "").length()
                } else {
                    resolver.query(uri, null, null, null, null)?.use { cursor ->
                        val sizeIndex = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (sizeIndex != -1 && cursor.moveToFirst()) cursor.getLong(sizeIndex) else -1L
                    } ?: -1L
                }

                if (size < 0) {
                    Log.e("FileSender", "Could not determine file size for $uri")
                    return@launch
                }

                // 2. Compute Checksum (Streaming)
                Log.d("FileSender", "Computing checksum for $name...")
                val checksum = resolver.openInputStream(uri)?.use { input ->
                    val digest = java.security.MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(8192)
                    var read = input.read(buffer)
                    while (read > 0) {
                        digest.update(buffer, 0, read)
                        read = input.read(buffer)
                    }
                    digest.digest().joinToString("") { String.format("%02x", it) }
                } ?: return@launch

                val transferId = UUID.randomUUID().toString()
                outgoingAcks[transferId] =
                    java.util.Collections.synchronizedSet(mutableSetOf<Int>())
                transferStatus[transferId] = true

                Log.d("FileSender", "Starting transfer id=$transferId name=$name size=$size")

                // 3. Init
                WebSocketUtil.sendMessage(
                    FileTransferProtocol.buildInit(
                        transferId,
                        name,
                        size,
                        mime,
                        chunkSize,
                        checksum
                    )
                )

                // Show initial progress
                NotificationUtil.showFileProgress(
                    context,
                    transferId.hashCode(),
                    name,
                    0,
                    transferId,
                    isSending = true
                )

                // 4. Send Chunks with Sliding Window
                val windowSize = 8
                val totalChunks = if (size == 0L) 1L else (size + chunkSize - 1) / chunkSize

                // Buffer of sent chunks in the current window: index -> (base64, lastSentTime, attempts)
                data class SentChunk(val base64: String, var lastSent: Long, var attempts: Int)

                val sentBuffer = java.util.concurrent.ConcurrentHashMap<Int, SentChunk>()

                var nextIndexToSend = 0
                val ackWaitMs = 2000L
                val maxRetries = 5

                // Speed / ETA tracking
                var lastUpdateTime = System.currentTimeMillis()
                var bytesAtLastUpdate = 0L
                var totalBytesSent = 0L
                var smoothedSpeed: Double? = null
                var etaString: String? = null

                resolver.openInputStream(uri)?.use { input ->
                    while (true) {
                        // Check cancellation
                        if (!transferStatus.containsKey(transferId)) {
                            Log.d("FileSender", "Transfer cancelled by user/receiver")
                            NotificationManagerCompat.from(context).cancel(transferId.hashCode())
                            break
                        }

                        val acks = outgoingAcks[transferId] ?: break

                        // find baseIndex = smallest unacked index
                        var baseIndex = 0
                        while (acks.contains(baseIndex)) {
                            sentBuffer.remove(baseIndex)
                            baseIndex++
                        }

                        // Update Notification logic (Once per second)
                        val now = System.currentTimeMillis()
                        val timeDiff = (now - lastUpdateTime) / 1000.0

                        val currentBytesSent = baseIndex * chunkSize.toLong()

                        if (timeDiff >= 1.0) {
                            val bytesDiff = currentBytesSent - bytesAtLastUpdate
                            val intervalSpeed = if (timeDiff > 0) bytesDiff / timeDiff else 0.0

                            val alpha = 0.4
                            val lastSpeed = smoothedSpeed
                            val newSpeed = if (lastSpeed != null) {
                                alpha * intervalSpeed + (1.0 - alpha) * lastSpeed
                            } else {
                                intervalSpeed
                            }
                            smoothedSpeed = newSpeed

                            if (newSpeed > 0) {
                                val remainingBytes = (size - currentBytesSent).coerceAtLeast(0)
                                val secondsRemaining = (remainingBytes / newSpeed).toLong()

                                etaString = if (secondsRemaining < 60) {
                                    "$secondsRemaining sec remaining"
                                } else {
                                    val mins = secondsRemaining / 60
                                    "$mins min remaining"
                                }
                            }

                            lastUpdateTime = now
                            bytesAtLastUpdate = currentBytesSent

                            val progress =
                                if (totalChunks > 0L) ((baseIndex.toLong() * 100) / totalChunks).toInt() else 0
                            NotificationUtil.showFileProgress(
                                context,
                                transferId.hashCode(),
                                name,
                                progress,
                                transferId,
                                isSending = true,
                                etaString = etaString
                            )
                        } else if (baseIndex == 0) {
                            // Force initial update
                            NotificationUtil.showFileProgress(
                                context,
                                transferId.hashCode(),
                                name,
                                0,
                                transferId,
                                isSending = true,
                                etaString = "Calculating..."
                            )
                        }

                        if (baseIndex >= totalChunks) break

                        // Fill window
                        while (nextIndexToSend < totalChunks && (nextIndexToSend - baseIndex) < windowSize) {
                            val chunk = ByteArray(chunkSize)
                            val read = input.read(chunk)
                            if (read > 0) {
                                val actualChunk =
                                    if (read < chunkSize) chunk.copyOf(read) else chunk
                                val base64 = FileTransferUtils.base64NoWrap(actualChunk)
                                WebSocketUtil.sendMessage(
                                    FileTransferProtocol.buildChunk(
                                        transferId,
                                        nextIndexToSend,
                                        base64
                                    )
                                )
                                sentBuffer[nextIndexToSend] =
                                    SentChunk(base64, System.currentTimeMillis(), 1)
                                nextIndexToSend++
                                totalBytesSent += read
                            } else if (nextIndexToSend < totalChunks) {
                                break
                            }
                        }

                        // Retransmit logic
                        val nowTx = System.currentTimeMillis()
                        var failed = false
                        for ((idx, sent) in sentBuffer) {
                            if (acks.contains(idx)) continue
                            if (nowTx - sent.lastSent > ackWaitMs) {
                                if (sent.attempts >= maxRetries) {
                                    Log.e(
                                        "FileSender",
                                        "Failed to send chunk $idx after $maxRetries attempts"
                                    )
                                    failed = true
                                    break
                                }
                                Log.d(
                                    "FileSender",
                                    "Retransmitting chunk $idx (attempt ${sent.attempts + 1})"
                                )
                                WebSocketUtil.sendMessage(
                                    FileTransferProtocol.buildChunk(
                                        transferId,
                                        idx,
                                        sent.base64
                                    )
                                )
                                sent.lastSent = nowTx
                                sent.attempts++
                            }
                        }

                        if (failed) break
                        delay(10)
                    }
                }

                // 5. Complete
                // Check if we exited due to cancel or success
                if (transferStatus.containsKey(transferId)) {
                    Log.d("FileSender", "Transfer $transferId completed")
                    WebSocketUtil.sendMessage(
                        FileTransferProtocol.buildComplete(
                            transferId,
                            name,
                            size,
                            checksum
                        )
                    )
                    NotificationUtil.showFileComplete(
                        context,
                        transferId.hashCode(),
                        name,
                        success = true,
                        isSending = true
                    )
                }
                outgoingAcks.remove(transferId)
                transferStatus.remove(transferId)

            } catch (e: Exception) {
                Log.e("FileSender", "Error sending file: ${e.message}")
                e.printStackTrace()
            }
        }
    }
    
    private suspend fun sendSmallFile(context: Context, uri: Uri, name: String, mime: String, chunkSize: Int) {
        val resolver = context.contentResolver
        val input: InputStream? = resolver.openInputStream(uri)
        if (input == null) return

        val bytes = input.readBytes().also { input.close() }
        val checksum = FileTransferUtils.sha256Hex(bytes)

        val transferId = UUID.randomUUID().toString()

        // Init
        val initJson = FileTransferProtocol.buildInit(
            id = transferId,
            name = name,
            size = bytes.size,
            mime = mime,
            checksum = checksum
        )
        WebSocketUtil.sendMessage(initJson)

        // Chunks with rate limiting to prevent network overload
        var offset = 0
        var index = 0
        while (offset < bytes.size) {
            val end = minOf(offset + chunkSize, bytes.size)
            val chunk = bytes.copyOfRange(offset, end)
            val base64 = FileTransferUtils.base64NoWrap(chunk)
            val chunkJson = FileTransferProtocol.buildChunk(transferId, index, base64)
            WebSocketUtil.sendMessage(chunkJson)

            index += 1
            offset = end
            
            // Small delay every 10 chunks to prevent overwhelming the network
            if (index % 10 == 0) {
                kotlinx.coroutines.delay(10)
            }
        }

        // Complete
        val completeJson = FileTransferProtocol.buildComplete(
            id = transferId,
            name = name,
            size = bytes.size,
            checksum = checksum
        )
        WebSocketUtil.sendMessage(completeJson)
    }
    
    private suspend fun sendLargeFile(context: Context, uri: Uri, name: String, mime: String, chunkSize: Int) {
        val resolver = context.contentResolver
        val transferId = UUID.randomUUID().toString()
        
        // Calculate checksum using streaming (memory-efficient)
        val checksum = resolver.openInputStream(uri)?.use { stream ->
            FileTransferUtils.sha256HexFromStream(stream)
        } ?: return
        
        // Get file size
        val fileSize = resolver.openInputStream(uri)?.use { stream ->
            var size = 0L
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                size += bytesRead
            }
            size.toInt()
        } ?: return
        
        // Init
        val initJson = FileTransferProtocol.buildInit(
            id = transferId,
            name = name,
            size = fileSize,
            mime = mime,
            checksum = checksum
        )
        WebSocketUtil.sendMessage(initJson)
        
        // Stream chunks
        resolver.openInputStream(uri)?.use { stream ->
            val buffer = ByteArray(chunkSize)
            var bytesRead: Int
            var index = 0
            
            while (stream.read(buffer).also { bytesRead = it } != -1) {
                val chunk = if (bytesRead == chunkSize) buffer else buffer.copyOf(bytesRead)
                val base64 = FileTransferUtils.base64NoWrap(chunk)
                val chunkJson = FileTransferProtocol.buildChunk(transferId, index, base64)
                WebSocketUtil.sendMessage(chunkJson)
                
                index += 1
                
                // Small delay every 10 chunks to prevent overwhelming the network
                if (index % 10 == 0) {
                    kotlinx.coroutines.delay(10)
                }
            }
        }
        
        // Complete
        val completeJson = FileTransferProtocol.buildComplete(
            id = transferId,
            name = name,
            size = fileSize,
            checksum = checksum
        )
        WebSocketUtil.sendMessage(completeJson)
    }
}

// Extension helper to get filename
fun android.content.ContentResolver.getFileName(uri: Uri): String? {
    var name: String? = null
    val returnCursor = this.query(uri, null, null, null, null)
    returnCursor?.use { cursor ->
        val nameIndex = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
        if (nameIndex >= 0 && cursor.moveToFirst()) {
            name = cursor.getString(nameIndex)
        }
    }
    return name
}

// get mime type
fun android.content.ContentResolver.getType(uri: Uri): String? = this.getType(uri)
