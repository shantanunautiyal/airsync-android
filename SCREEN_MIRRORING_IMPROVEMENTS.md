# Screen Mirroring Improvements

## Fixed Issues

### 1. Build Errors (Health Screen) ✅
- Fixed type inference issues in `SimpleHealthScreen.kt`
- Made `healthConnectClient` public in `SimpleHealthConnectManager.kt`
- Added explicit type parameters for permission launcher
- All build errors resolved

### 2. Latency & Performance Optimizations

#### Codec Configuration
- **Changed bitrate mode**: CBR → VBR for better quality during motion
- **Increased I-frame interval**: 1s → 2s to reduce bandwidth
- **Added low-latency flags**:
  - `KEY_LATENCY = 0` (lowest latency)
  - `KEY_PRIORITY = 0` (realtime priority)
  - `KEY_LOW_LATENCY = 1` (Android Q+)

#### Buffer Processing
- **Reduced timeout**: 50ms → 10ms for faster frame delivery
- **Removed verbose logging**: Reduced overhead in encoding loop
- **Added graceful shutdown**: Prevents IllegalStateException during stop

#### Thread Safety
- Added `isStoppingCodec` flag to prevent codec errors
- Improved error handling in encoding loop
- Added 100ms grace period before codec stop

### 3. Remote Control Functionality

#### New Components

**RemoteInputHandler.kt**
- Accessibility service for gesture injection
- Supports:
  - Touch (tap, long press, double tap)
  - Swipe gestures
  - Scroll gestures
- Uses Android's GestureDescription API (Android N+)

**RemoteControlReceiver.kt**
- Processes JSON commands from client
- Command format:
```json
{
  "type": "touch",
  "x": 0.5,
  "y": 0.5,
  "action": "tap"
}
```

**accessibility_service_config.xml**
- Configuration for accessibility service
- Enables gesture performance capability

## Setup Instructions

### 1. Add to AndroidManifest.xml

```xml
<manifest>
    <!-- Add permission -->
    <uses-permission android:name="android.permission.BIND_ACCESSIBILITY_SERVICE" />
    
    <application>
        <!-- Add service -->
        <service
            android:name=".utils.RemoteInputHandler"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
            android:exported="false">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service_config" />
        </service>
    </application>
</manifest>
```

### 2. Add String Resource

In `res/values/strings.xml`:
```xml
<string name="accessibility_service_description">
    Allows AirSync to receive remote control commands for screen mirroring
</string>
```

### 3. Enable Accessibility Service

Users must manually enable the service:
1. Settings → Accessibility
2. Find "AirSync Remote Control"
3. Toggle ON

### 4. Integrate with WebSocket

In your WebSocket message handler:

```kotlin
val remoteControlReceiver = RemoteControlReceiver()

// When receiving message from client
fun onMessageReceived(message: String) {
    if (message.startsWith("{\"type\":")) {
        remoteControlReceiver.processCommand(message)
    }
}
```

### 5. Client-Side Implementation

Send touch events from web client:

```javascript
// Touch event
ws.send(JSON.stringify({
    type: 'touch',
    x: normalizedX,  // 0.0 to 1.0
    y: normalizedY,  // 0.0 to 1.0
    action: 'tap'    // or 'long_press', 'double_tap'
}));

// Swipe event
ws.send(JSON.stringify({
    type: 'swipe',
    x: startX,
    y: startY,
    endX: endX,
    endY: endY,
    duration: 300
}));

// Scroll event
ws.send(JSON.stringify({
    type: 'scroll',
    x: 0.5,
    y: 0.5,
    scrollAmount: -0.1  // negative = scroll up
}));
```

## Performance Tips

### For Best Latency
1. **Network**: Use 5GHz WiFi or wired connection
2. **Resolution**: Lower resolution = lower latency (720p recommended)
3. **Bitrate**: 2-4 Mbps for local network
4. **FPS**: 30 fps is good balance (60 fps increases latency)

### Recommended MirroringOptions
```kotlin
MirroringOptions(
    maxWidth = 1280,      // 720p
    fps = 30,             // Smooth but not excessive
    bitrateKbps = 3000    // 3 Mbps
)
```

### Quality vs Latency Trade-offs
- **Lower latency**: Reduce resolution, increase I-frame interval
- **Better quality**: Increase bitrate, reduce I-frame interval
- **Smooth scrolling**: Use VBR mode (already enabled)

## Testing

### Test Latency
1. Display a timer on Android screen
2. Compare with mirrored display
3. Latency should be < 200ms on local network

### Test Touch Input
1. Enable accessibility service
2. Connect from client
3. Try tapping, swiping, scrolling
4. Actions should execute within 100ms

## Troubleshooting

### High Latency
- Check network quality (ping should be < 10ms)
- Reduce resolution or bitrate
- Ensure no other apps using camera/encoder

### Touch Not Working
- Verify accessibility service is enabled
- Check logcat for "RemoteInputHandler" messages
- Ensure Android version is N (7.0) or higher

### Codec Errors
- The improved shutdown logic should prevent most errors
- If errors persist, check device encoder capabilities
- Try different encoder (hardware vs software)

### 4. Mac Stop Request Handling ✅
- Added `handleStopMirroring()` in WebSocketMessageHandler
- Mac can now send "stopMirroring" message to stop Android mirroring
- Graceful shutdown when Mac requests stop

### 5. Duplicate Popup Prevention ✅
- Created `MirrorRequestHelper` to manage mirror requests
- Checks if mirroring is already active before showing permission dialog
- Sends status back to Mac if already mirroring
- Prevents annoying duplicate permission popups

### 6. Background Operation Support ✅
- Service continues running when app is in background
- Foreground service with notification keeps mirroring alive
- MediaProjection callback handles user-initiated stops
- Proper cleanup on service destroy

## Implementation Details

### Stop Request Flow
1. Mac sends `{"type": "stopMirroring"}` message
2. Android receives in `WebSocketMessageHandler.handleStopMirroring()`
3. Calls `ScreenCaptureService.instance?.stopMirroring()`
4. Service stops codec, releases resources, removes notification
5. Service stops itself

### Duplicate Prevention Flow
1. Mac sends `{"type": "mirrorRequest", "data": {...}}` message
2. `MirrorRequestHelper.handleMirrorRequest()` checks `ScreenCaptureService.isStreaming`
3. If already streaming: sends status to Mac, ignores request
4. If not streaming: broadcasts intent to show permission dialog
5. User grants permission, mirroring starts

### Background Operation
- Service uses `startForeground()` with `FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION`
- Notification shows "Screen mirroring is active" with Stop button
- Service continues even when app is closed or in background
- Proper lifecycle management in `onDestroy()`

## Future Improvements

1. **Adaptive bitrate**: Adjust based on network conditions
2. **Hardware acceleration**: Use device-specific optimizations
3. **Keyboard input**: Add support for text input
4. **Multi-touch**: Support pinch-to-zoom gestures
5. **Cursor display**: Show touch point on screen
6. **Screenshot support**: Implement `requestScreenshot()` handler
7. **Frame skipping**: Skip frames when encoder is backed up
