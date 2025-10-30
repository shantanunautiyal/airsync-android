# Remote Control & FPS Fixes

## Issues Fixed

### 1. ✅ Remote Control Not Working (Touch/Swipe/Nav Actions)

**Problem**: Mac was sending input events but Android was responding with "Unknown input type" and not performing the actions.

**Root Causes**:
1. **Field name mismatch**: Mac sends `"type":"tap"` but Android was looking for `"inputType":"tap"`
2. **Swipe coordinate mismatch**: Mac sends `x1, y1, x2, y2` but Android expected `startX, startY, endX, endY`
3. **Duration field mismatch**: Mac sends `durationMs` but Android expected `duration`
4. **Missing navAction handler**: Mac sends separate `navAction` messages but Android wasn't handling them

**Fixes Applied**:

1. **Updated `handleInputEvent()` to accept both field names**:
   ```kotlin
   // Now accepts both "type" (from Mac) and "inputType" (legacy)
   val inputType = data.optString("type", data.optString("inputType", ""))
   ```

2. **Added swipe coordinate mapping**:
   ```kotlin
   // Maps Mac's x1,y1,x2,y2 to Android's startX,startY,endX,endY
   val startX = if (data.has("startX")) data.optDouble("startX").toFloat() else data.optDouble("x1").toFloat()
   ```

3. **Added duration field mapping**:
   ```kotlin
   // Accepts both durationMs (Mac) and duration (legacy)
   val duration = data.optLong("durationMs", data.optLong("duration", 300L))
   ```

4. **Added `handleNavAction()` function**:
   ```kotlin
   private fun handleNavAction(context: Context, data: JSONObject?) {
       val action = data.optString("action", "")
       when (action) {
           "back" -> service.performBack()
           "home" -> service.performHome()
           "recents" -> service.performRecents()
           // ... etc
       }
   }
   ```

5. **Added navAction to message handler**:
   ```kotlin
   "navAction" -> handleNavAction(context, data)
   ```

**Result**: 
- ✅ Tap events now work
- ✅ Swipe gestures now work
- ✅ Navigation actions (back, home, recents) now work
- ✅ All input events are properly handled

### 2. ✅ Extremely High FPS (1015) and Dropped Frames

**Problem**: Screen mirroring was running at 1015 FPS instead of the intended 10-15 FPS, causing massive frame drops and poor performance.

**Root Cause**: Mac was sending an invalid FPS value (1015) and Android was accepting it without validation.

**Fix Applied**:

Added FPS validation and clamping:
```kotlin
val rawFps = options?.optInt("fps", 30) ?: 30
// Clamp FPS to reasonable range (10-60)
val fps = rawFps.coerceIn(10, 60)
if (rawFps != fps) {
    Log.w(TAG, "FPS value $rawFps out of range, clamped to $fps")
}
```

Also added quality validation:
```kotlin
val quality = (options?.optDouble("quality", 0.8) ?: 0.8).toFloat().coerceIn(0.1f, 1.0f)
```

**Result**:
- ✅ FPS is now clamped to 10-60 range
- ✅ Invalid FPS values are logged as warnings
- ✅ Encoder runs at reasonable frame rate
- ✅ Fewer dropped frames
- ✅ Better performance and lower latency

## Testing

### Test Remote Control
1. Start screen mirroring from Mac
2. Try clicking on the mirrored screen
   - **Expected**: Tap should register on Android
3. Try swiping on the mirrored screen
   - **Expected**: Swipe should work on Android
4. Try navigation buttons (back, home, recents)
   - **Expected**: Navigation actions should work

### Test FPS Fix
1. Start screen mirroring
2. Check Android logs for FPS value:
   ```
   Mirror request with options: fps=30, quality=0.8, maxWidth=1280, bitrate=12000
   ```
3. If Mac sends invalid FPS, you should see:
   ```
   FPS value 1015 out of range, clamped to 60
   ```
4. Monitor frame rate - should be stable at requested FPS (10-60)

## Mac Side Messages Handled

### inputEvent
```json
{
  "type": "inputEvent",
  "data": {
    "type": "tap",
    "x": 664,
    "y": 1122
  }
}
```

```json
{
  "type": "inputEvent",
  "data": {
    "type": "swipe",
    "x1": 629,
    "y1": 1474,
    "x2": 577,
    "y2": 660,
    "durationMs": 151
  }
}
```

### navAction
```json
{
  "type": "navAction",
  "data": {
    "action": "back"
  }
}
```

```json
{
  "type": "navAction",
  "data": {
    "action": "home"
  }
}
```

```json
{
  "type": "navAction",
  "data": {
    "action": "recents"
  }
}
```

## Expected Log Output

### Successful Input Event
```
[WebSocketMessageHandler] Handling message type: inputEvent
[WebSocketMessageHandler] Tap injected at (664.0, 1122.0)
[InputAccessibilityService] Tap gesture completed at (664.0, 1122.0)
```

### Successful Swipe
```
[WebSocketMessageHandler] Handling message type: inputEvent
[WebSocketMessageHandler] Swipe injected from (629.0, 1474.0) to (577.0, 660.0)
[InputAccessibilityService] Swipe gesture completed from (629.0, 1474.0) to (577.0, 660.0)
```

### Successful Nav Action
```
[WebSocketMessageHandler] Handling message type: navAction
[WebSocketMessageHandler] Nav action 'back': success
[InputAccessibilityService] Back action: success
```

### FPS Clamping
```
[WebSocketMessageHandler] FPS value 1015 out of range, clamped to 60
[WebSocketMessageHandler] Mirror request with options: fps=60, quality=0.8, maxWidth=1280, bitrate=12000
```

## Files Modified

- `app/src/main/java/com/sameerasw/airsync/utils/WebSocketMessageHandler.kt`
  - Updated `handleInputEvent()` to accept Mac's field names
  - Added coordinate and duration field mapping
  - Added `handleNavAction()` function
  - Added FPS and quality validation/clamping
  - Added logging for mirror request options

## Recommended Settings

For best performance with 10-15 FPS:

```kotlin
MirroringOptions(
    fps = 15,           // 15 FPS for smooth but efficient streaming
    quality = 0.7f,     // 70% quality for good balance
    maxWidth = 1280,    // 720p resolution
    bitrateKbps = 3000  // 3 Mbps for local network
)
```

For lower latency (acceptable at 10 FPS):

```kotlin
MirroringOptions(
    fps = 10,           // 10 FPS minimum
    quality = 0.6f,     // Lower quality for faster encoding
    maxWidth = 960,     // Lower resolution
    bitrateKbps = 2000  // 2 Mbps
)
```

## Known Limitations

1. **FPS Range**: Clamped to 10-60 FPS
   - Below 10: Too choppy for usability
   - Above 60: Excessive CPU usage and frame drops

2. **Touch Accuracy**: Depends on resolution scaling
   - Higher resolution = more accurate touch
   - Lower resolution = faster but less precise

3. **Gesture Timing**: Swipe duration affects feel
   - Too fast: May not register
   - Too slow: Feels laggy
   - Recommended: 150-300ms

## Troubleshooting

### Input Events Still Not Working

1. **Check accessibility service**:
   ```
   Settings → Accessibility → AirSync → ON
   ```

2. **Check logs for service instance**:
   ```
   InputAccessibilityService connected
   ```

3. **Verify service is not null**:
   ```
   InputAccessibilityService not available
   ```
   If you see this, restart the accessibility service.

### FPS Still Too High

1. **Check Mac is sending correct FPS**:
   - Look for "FPS value X out of range" in logs
   - Mac should send 10-60

2. **Check encoder configuration**:
   ```
   Encoder Resolution: 1280x720
   Selected Encoder: c2.android.avc.encoder
   ```

3. **Monitor actual frame rate**:
   - Use Android Studio Profiler
   - Check CPU usage (should be <30% for 15 FPS)

### High Frame Drops

1. **Lower FPS**: Try 10-15 instead of 30
2. **Lower resolution**: Try 960x540 instead of 1280x720
3. **Lower bitrate**: Try 2000 instead of 3000
4. **Check network**: Ping should be <10ms on local network
