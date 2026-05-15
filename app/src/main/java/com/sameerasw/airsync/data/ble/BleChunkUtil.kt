package com.sameerasw.airsync.data.ble

import android.util.Log

object BleChunkUtil {
    private const val TAG = "BleChunkUtil"

    /**
     * Splits a string payload into chunks suitable for BLE transmission.
     * Each chunk starts with a 2-byte header: [currentIndex, totalChunks]
     */
    fun splitIntoChunks(payload: String, mtu: Int): List<ByteArray> {
        val data = payload.toByteArray(Charsets.UTF_8)
        val maxPayloadSize = mtu - BleConstants.CHUNK_HEADER_SIZE
        
        if (maxPayloadSize <= 0) {
            Log.e(TAG, "MTU too small: $mtu")
            return emptyList()
        }

        val totalChunks = (data.size + maxPayloadSize - 1) / maxPayloadSize
        val chunks = mutableListOf<ByteArray>()
        
        for (i in 0 until totalChunks) {
            val start = i * maxPayloadSize
            val end = minOf(start + maxPayloadSize, data.size)
            val chunkData = data.sliceArray(start until end)
            
            val chunk = ByteArray(BleConstants.CHUNK_HEADER_SIZE + chunkData.size)
            val buffer = java.nio.ByteBuffer.wrap(chunk)
            buffer.putShort(i.toShort())
            buffer.putShort(totalChunks.toShort())
            
            chunkData.copyInto(chunk, BleConstants.CHUNK_HEADER_SIZE)
            chunks.add(chunk)
        }
        
        return chunks
    }

    /**
     * Reassembles chunks into the original string.
     * Expects a map of index to chunk data (without the header).
     */
    fun reassemble(chunks: Map<Int, ByteArray>): String {
        val sortedIndices = chunks.keys.sorted()
        if (sortedIndices.isEmpty()) return ""
        
        val totalSize = chunks.values.sumOf { it.size }
        val result = ByteArray(totalSize)
        
        var offset = 0
        for (index in sortedIndices) {
            val chunk = chunks[index] ?: continue
            chunk.copyInto(result, offset)
            offset += chunk.size
        }
        
        return String(result, Charsets.UTF_8)
    }
    
    /**
     * Extracts header information from a raw BLE packet.
     */
    fun parseHeader(packet: ByteArray): Pair<Int, Int>? {
        if (packet.size < BleConstants.CHUNK_HEADER_SIZE) return null
        val buffer = java.nio.ByteBuffer.wrap(packet)
        val current = buffer.short.toInt() and 0xFFFF
        val total = buffer.short.toInt() and 0xFFFF
        return Pair(current, total)
    }

    /**
     * Extracts payload data from a raw BLE packet (strips header).
     */
    fun getPayload(packet: ByteArray): ByteArray {
        if (packet.size <= BleConstants.CHUNK_HEADER_SIZE) return byteArrayOf()
        return packet.sliceArray(BleConstants.CHUNK_HEADER_SIZE until packet.size)
    }

    /**
     * Helper class to reassemble chunks as they arrive.
     */
    class Reassembler {
        private val chunks = mutableMapOf<Int, ByteArray>()
        private var totalChunks = -1

        fun addChunk(packet: ByteArray): String? {
            val header = parseHeader(packet) ?: return null
            val current = header.first
            val total = header.second
            
            if (totalChunks != -1 && totalChunks != total) {
                // New transmission started or mismatch, reset
                chunks.clear()
            }
            totalChunks = total
            
            chunks[current] = getPayload(packet)
            
            if (chunks.size == totalChunks) {
                val result = reassemble(chunks)
                chunks.clear()
                totalChunks = -1
                return result
            }
            return null
        }

        fun clear() {
            chunks.clear()
            totalChunks = -1
        }
    }
}
