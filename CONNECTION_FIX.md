# WebSocket Connection Fix

## Issue Analysis

Based on the logs:
```
22:20:07.474 WebSocketUtil D  Connecting to ws://192.168.1.47:6996/socket
22:20:08.770 WebSocketUtil D  Already connected or connecting
```

The URL construction is **correct** (`ws://192.168.1.47:6996/socket`).

The issue is that the connection attempt is being blocked by the "Already connected or connecting" check.

## Root Cause

The `isConnecting` or `isConnected` flag is stuck in `true` state from a previous failed connection attempt.

## Fix

### Option 1: Reset Connection State Before Connecting

Update `WebSocketUtil.kt`:

```kotlin
fun connect(
    context: Context,
    ipAddress: String,
    port: Int,
    symmetricKey: String?,
    onConnectionStatus: ((Boolean) -> Unit)? = null,
    onMessage: ((String) -> Unit)? = null,
    manualAttempt: Boolean = true,
    onHandshakeTimeout: (() -> Unit)? = null
) {
    // Cache application context
    appContext = context.applicationContext

    // CHANGE: Remove the early return check for manual attempts
    // Allow manual attempts to override stuck states
    if (manualAttempt) {
        // Force reset connection state for manual attempts
        isConnecting.set(false)
        isConnected.set(false)
        isSocketOpen.set(false)
        handshakeCompleted.set(false)
        cancelAutoReconnect()
    } else if (isConnecting.get() || isConnected.get()) {
        Log.d(TAG, "Already connected or connecting")
        return
    }

    // ... rest of the code
}
```

### Option 2: Add Force Reconnect Method

Add a new method to force reconnection:

```kotlin
fun forceReconnect(
    context: Context,
    ipAddress: String,
    port: Int,
    symmetricKey: String?,
    onConnectionStatus: ((Boolean) -> Unit)? = null,
    onMessage: ((String) -> Unit)? = null
) {
    // Force disconnect first
    disconnect(context)
    
    // Wait a moment for cleanup
    Thread.sleep(500)
    
    // Reset all flags
    isConnecting.set(false)
    isConnected.set(false)
    isSocketOpen.set(false)
    handshakeCompleted.set(false)
    
    // Now connect
    connect(context, ipAddress, port, symmetricKey, onConnectionStatus, onMessage, true)
}
```

### Option 3: Add Timeout for Connection Attempts

Add a timeout to reset stuck connection states:

```kotlin
private var connectionAttemptTime: Long = 0

fun connect(...) {
    // Check if previous connection attempt is stuck
    val now = System.currentTimeMillis()
    if (isConnecting.get() && (now - connectionAttemptTime) > 30000) {
        Log.w(TAG, "Previous connection attempt stuck, resetting...")
        isConnecting.set(false)
        isConnected.set(false)
        isSocketOpen.set(false)
    }
    
    if (isConnecting.get() || isConnected.get()) {
        Log.d(TAG, "Already connected or connecting")
        return
    }
    
    connectionAttemptTime = now
    // ... rest of code
}
```

## Recommended Solution

Use **Option 1** - it's the simplest and most effective. Manual connection attempts should always be allowed to proceed.

## Additional Debugging

Add more detailed logging:

```kotlin
fun connect(...) {
    Log.d(TAG, "=== Connection Attempt ===")
    Log.d(TAG, "Target: ws://$ipAddress:$port/socket")
    Log.d(TAG, "Manual attempt: $manualAttempt")
    Log.d(TAG, "isConnecting: ${isConnecting.get()}")
    Log.d(TAG, "isConnected: ${isConnected.get()}")
    Log.d(TAG, "isSocketOpen: ${isSocketOpen.get()}")
    Log.d(TAG, "handshakeCompleted: ${handshakeCompleted.get()}")
    
    // ... rest of code
}
```

## Testing

After applying the fix:

1. **Force close the app** to reset all states
2. **Reopen the app**
3. **Try connecting again**
4. **Check logs** for successful connection

Expected logs:
```
WebSocketUtil: === Connection Attempt ===
WebSocketUtil: Target: ws://192.168.1.47:6996/socket
WebSocketUtil: Manual attempt: true
WebSocketUtil: isConnecting: false
WebSocketUtil: isConnected: false
WebSocketUtil: Connecting to ws://192.168.1.47:6996/socket
WebSocketUtil: WebSocket connected to ws://192.168.1.47:6996/socket
WebSocketUtil: Received: {"type":"macInfo",...}
```

## Quick Fix for Testing

If you want to test immediately without code changes:

1. **Force stop the app**: Settings → Apps → AirSync → Force Stop
2. **Clear app cache** (optional): Settings → Apps → AirSync → Storage → Clear Cache
3. **Reopen the app** and try connecting

This will reset all in-memory connection states.
