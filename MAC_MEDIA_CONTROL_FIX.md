# Mac Media Control Fix

## Problem
Mac was sending media control commands (`macMediaControl`) to Android, but Android wasn't responding. The commands were being sent but ignored.

**Symptoms:**
- Mac logs showed: `[websocket] 🎵 Sent macMediaControl action: playPause`
- No Android logs showing the message was received
- No `macMediaControlResponse` sent back to Mac
- Media controls from Mac to Android didn't work

## Root Cause
Android's `WebSocketMessageHandler` was missing the handler for `macMediaControl` message type. The message type existed on the Mac side but wasn't implemented on Android.

## Solution

### 1. Added Message Handler
**File:** `WebSocketMessageHandler.kt`

Added `"macMediaControl"` to the message type switch:
```kotlin
when (type) {
    // ... existing handlers
    "macMediaControl" -> handleMacMediaControl(context, data)
    // ... more handlers
}
```

### 2. Implemented Handler Function
**File:** `WebSocketMessageHandler.kt`

Created `handleMacMediaControl()` function that:
- Receives the action from Mac (playPause, next, previous, like, unlike, etc.)
- Executes the corresponding media control using `MediaControlUtil`
- Sends back a response with success status
- Updates media state after successful control

Supported actions:
- `playPause` - Toggle play/pause
- `play` - Start playback
- `pause` - Pause playback
- `next` - Skip to next track (with suppression to avoid feedback)
- `previous` - Skip to previous track (with suppression to avoid feedback)
- `stop` - Stop playback
- `toggleLike` - Toggle like status
- `like` - Like current track
- `unlike` - Unlike current track

### 3. Added Response Function
**File:** `JsonUtil.kt`

Created `createMacMediaControlResponse()` to format the response:
```kotlin
fun createMacMediaControlResponse(action: String, success: Boolean, message: String = ""): String {
    return """{"type":"macMediaControlResponse","data":{"action":"${escape(action)}","success":$success,"message":"${escape(message)}"}}"""
}
```

**File:** `WebSocketMessageHandler.kt`

Added `sendMacMediaControlResponse()` helper function to send the response back to Mac.

## How It Works

### Flow:
1. **Mac sends command:**
   ```json
   {
     "type": "macMediaControl",
     "data": {
       "action": "playPause"
     }
   }
   ```

2. **Android receives and processes:**
   - `handleIncomingMessage()` routes to `handleMacMediaControl()`
   - Handler executes the media control action
   - Logs the result

3. **Android sends response:**
   ```json
   {
     "type": "macMediaControlResponse",
     "data": {
       "action": "playPause",
       "success": true,
       "message": "Play/pause toggled"
     }
   }
   ```

4. **Android updates media state:**
   - After successful control, waits appropriate delay
   - Sends updated media status to Mac

## Testing

To verify the fix works:

1. **Play/Pause:**
   - Play music on Android
   - Click play/pause button on Mac
   - Android should pause/resume

2. **Skip Tracks:**
   - Click next/previous on Mac
   - Android should skip tracks
   - Mac should receive updated track info

3. **Like Controls:**
   - Click like/unlike on Mac
   - Android should update like status
   - Mac should receive confirmation

## Expected Logs

**Android side:**
```
WebSocketMessageHandler: Handling message type: macMediaControl
WebSocketMessageHandler: Handling Mac media control action: playPause
WebSocketMessageHandler: Mac media control result: action=playPause, success=true, message=Play/pause toggled
WebSocketMessageHandler: Sent Mac media control response: action=playPause, success=true
```

**Mac side:**
```
[websocket] 🎵 Sent macMediaControl action: playPause
[websocket] [received] {"type":"macMediaControlResponse","data":{"action":"playPause","success":true,"message":"Play/pause toggled"}}
```

## Status
✅ **Fixed** - Android now handles Mac media control commands and responds appropriately.

## Related
- Complements existing `mediaControl` handler (Android → Mac controls)
- Uses same `MediaControlUtil` for consistency
- Follows same pattern as other bidirectional features (volume, notifications)
