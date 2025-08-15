package com.sameerasw.airsync.utils

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.InputStream
import java.util.UUID
import com.sameerasw.airsync.utils.transfer.FileTransferProtocol
import com.sameerasw.airsync.utils.transfer.FileTransferUtils

object FileSender {
    fun sendFile(context: Context, uri: Uri, chunkSize: Int = 64 * 1024) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val resolver = context.contentResolver
                val name = resolver.getFileName(uri) ?: "shared_file"
                val mime = resolver.getType(uri) ?: "application/octet-stream"

                val input: InputStream? = resolver.openInputStream(uri)
                if (input == null) return@launch

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

                // Chunks
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
                }

                // Complete
                val completeJson = FileTransferProtocol.buildComplete(
                    id = transferId,
                    name = name,
                    size = bytes.size,
                    checksum = checksum
                )
                WebSocketUtil.sendMessage(completeJson)

            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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
