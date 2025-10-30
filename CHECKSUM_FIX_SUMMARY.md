# File Transfer Checksum Mismatch Fix

## Problem
File transfers between Android and Mac were occasionally failing with checksum mismatches, causing the transfer to be rejected even though all data was received.

## Root Causes Identified

### 1. **Buffer Reuse Issue**
- The sender was reusing the same buffer array for all chunks
- When `bytesRead < CHUNK_SIZE`, the buffer contained old data from previous reads
- This could cause incorrect data to be included in chunks

### 2. **Base64 Encoding Inconsistencies**
- Using `Base64.NO_WRAP` but not handling potential decode failures
- No fallback strategy if decoding failed

### 3. **Chunk Assembly Issues**
- Simple concatenation using `fold` could be inefficient for large files
- No validation during assembly process

### 4. **Checksum Comparison Issues**
- No normalization of checksum strings (whitespace, case)
- Limited debugging information when mismatches occurred

## Fixes Applied

### FileTransferUtil.kt (Sender Side)

1. **Fixed Buffer Handling**
   ```kotlin
   // OLD: Reused buffer
   val chunk = if (bytesRead < CHUNK_SIZE) {
       buffer.copyOf(bytesRead)
   } else {
       buffer  // ❌ Reuses buffer reference
   }
   
   // NEW: Always create fresh copy
   val chunk = buffer.copyOf(bytesRead)  // ✓ Always exact size
   ```

2. **Enhanced Logging**
   - Added detailed checksum calculation logging
   - Log chunk progress every 100 chunks
   - Log total bytes processed

### FileReceiveManager.kt (Receiver Side)

1. **Robust Base64 Decoding**
   ```kotlin
   val decodedChunk = try {
       Base64.decode(chunkData, Base64.NO_WRAP)
   } catch (e: Exception) {
       // Fallback to DEFAULT if NO_WRAP fails
       Base64.decode(chunkData, Base64.DEFAULT)
   }
   ```

2. **Improved Chunk Assembly**
   - Pre-allocate exact-size byte array
   - Use `System.arraycopy` for efficient copying
   - Validate each chunk during assembly
   - Track offset to ensure correct positioning

3. **Enhanced Checksum Verification**
   - Normalize checksums (trim, lowercase)
   - Detailed error logging with hex dump of first 100 bytes
   - Log file size, chunk count, and assembly details

4. **Duplicate Chunk Handling**
   - Detect and handle duplicate chunks
   - Adjust byte counter when replacing chunks

## Testing Recommendations

1. **Test with various file sizes:**
   - Small files (< 64KB) - single chunk
   - Medium files (1-10 MB) - multiple chunks
   - Large files (> 50 MB) - many chunks

2. **Test with different file types:**
   - Images (JPEG, PNG)
   - Documents (PDF, TXT)
   - Videos (MP4)
   - Archives (ZIP)

3. **Test under different conditions:**
   - Good network connection
   - Poor/unstable network
   - Background/foreground app states

## Expected Behavior

- ✓ Checksums should now match consistently
- ✓ Detailed logs help diagnose any remaining issues
- ✓ Better error messages if problems occur
- ✓ Handles edge cases (duplicate chunks, decode failures)

## Monitoring

Check logs for:
- `"Calculated checksum for X bytes: ..."` - Sender side
- `"✓ Checksum verified: ..."` - Receiver side (success)
- `"=== CHECKSUM MISMATCH ==="` - Receiver side (failure with details)
