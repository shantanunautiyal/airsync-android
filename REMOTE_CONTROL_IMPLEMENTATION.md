# Android Remote Control Implementation Guide

## Overview
This guide covers the Android implementation for remote control functionality that allows Mac to control the Android device during screen mirroring sessions.

## ✅ Completed Implementation

### 1. Input Accessibility Service (Enhanced)
**File:** `app/src/main/java/com/sameerasw/airsync/service/InputAccessibilityService.kt`

The service now supports:
- **Tap**: Single tap at coordinates
- **Long Press**: Long press at coordinates (500ms)
- **Swipe**: Swipe gesture from start to end coordinates with configurable duration
- **Scroll**: Scroll gesture with delta values
- **Navigation Actions**:
  - Back button
  - Home button
  - Recents/Overview
  - Notifications panel
  - Quick Settings
  - Power Dialog

### 2. WebSocket Message Handler (Enhanced)
**File:** `app/src/main/java/com/sameerasw/airsync/utils/WebSocketMessageHandler.kt`

Enhanced `handleInputEvent()` method now processes all input types:

```kotlin
{
  "type": "inputEvent",
  "data": {
    "inputType": "tap|longPress|swipe|scroll|back|home|recents|notifications|quickSettings|powerDialog",
    // For tap/longPress:
    "x": 100.0,
    "y": 200.0,
    
    // For swipe:
    "startX": 100.0,
    "startY": 200.0,
    "endX": 300.0,
    "endY": 400.0,
    "duration": 300,  // optional, default 300ms
    
    // For scroll:
    "x": 100.0,
    "y": 200.0,
    "deltaX": 0.0,
    "deltaY": -50.0
  }
}
```

### 3. JSON Utility (Enhanced)
**File:** `app/src/main/java/com/sameerasw/airsync/utils/JsonUtil.kt`

Added response method:
```kotlin
fun createInputEventResponse(inputType: String, success: Boolean, message: String = ""): String
```

Response format:
```json
{
  "type": "inputEventResponse",
  "data": {
    "inputType": "tap",
    "success": true,
    "message": "Tap injected at (100.0, 200.0)"
  }
}
```

### 4. Video Encoder Quality Parameters
**File:** `app/src/main/java/com/sameerasw/airsync/utils/ScreenMirroringManager.kt`

Already implemented! The encoder uses quality parameters from `MirroringOptions`:
- `fps`: Frame rate (default: 30)
- `quality`: Quality factor (default: 0.8)
- `maxWidth`: Maximum width (default: 1280)
- `bitrateKbps`: Bitrate in Kbps (default: 12000)

The Mac can send these parameters in the mirror request:
```json
{
  "type": "mirrorRequest",
  "data": {
    "options": {
      "fps": 30,
      "quality": 0.8,
      "maxWidth": 1920,
      "bitrateKbps": 15000
    }
  }
}
```

## Required Permissions

Already configured in `AndroidManifest.xml`:
- ✅ `BIND_ACCESSIBILITY_SERVICE` - For InputAccessibilityService
- ✅ `FOREGROUND_SERVICE_MEDIA_PROJECTION` - For screen capture

## User Setup Required

### Enable Accessibility Service
Users must enable the accessibility service:
1. Go to **Settings** → **Accessibility**
2. Find **AirSync** in the list
3. Toggle it **ON**
4. Confirm the permission dialog

## Testing the Implementation

### Test Input Events from Mac

```javascript
// Test tap
ws.send(JSON.stringify({
  type: "inputEvent",
  data: {
    inputType: "tap",
    x: 500,
    y: 800
  }
}));

// Test swipe (scroll down)
ws.send(JSON.stringify({
  type: "inputEvent",
  data: {
    inputType: "swipe",
    startX: 500,
    startY: 1000,
    endX: 500,
    endY: 300,
    duration: 300
  }
}));

// Test back button
ws.send(JSON.stringify({
  type: "inputEvent",
  data: {
    inputType: "back"
  }
}));

// Test home button
ws.send(JSON.stringify({
  type: "inputEvent",
  data: {
    inputType: "home"
  }
}));
```

### Test Quality Settings

```javascript
// Request mirroring with high quality
ws.send(JSON.stringify({
  type: "mirrorRequest",
  data: {
    options: {
      fps: 60,
      quality: 0.9,
      maxWidth: 1920,
      bitrateKbps: 20000
    }
  }
}));
```

## Coordinate Mapping

The Mac side needs to map mouse/trackpad coordinates to Android screen coordinates:

```javascript
// Example coordinate mapping
function mapCoordinates(macX, macY, videoWidth, videoHeight, androidWidth, androidHeight) {
  const scaleX = androidWidth / videoWidth;
  const scaleY = androidHeight / videoHeight;
  
  return {
    x: macX * scaleX,
    y: macY * scaleY
  };
}
```

## Error Handling

The implementation includes comprehensive error handling:

1. **Service Not Available**: Returns error response if accessibility service is not enabled
2. **Invalid Coordinates**: Logs warning but attempts gesture anyway
3. **Gesture Cancellation**: Logs cancellation and sends failure response
4. **Unknown Input Types**: Returns error response with unknown type message

## Performance Considerations

### Input Latency
- Tap gestures: ~1ms duration
- Long press: 500ms duration
- Swipe: 300ms default duration (configurable)
- Scroll: 100ms duration for smooth scrolling

### Video Quality vs Performance
Recommended settings for different scenarios:

**High Quality (WiFi, powerful devices)**
```json
{
  "fps": 60,
  "quality": 0.9,
  "maxWidth": 1920,
  "bitrateKbps": 20000
}
```

**Balanced (Default)**
```json
{
  "fps": 30,
  "quality": 0.8,
  "maxWidth": 1280,
  "bitrateKbps": 12000
}
```

**Low Latency (Older devices)**
```json
{
  "fps": 30,
  "quality": 0.6,
  "maxWidth": 720,
  "bitrateKbps": 6000
}
```

## Troubleshooting

### Gestures Not Working
1. Check if accessibility service is enabled
2. Verify `InputAccessibilityService.instance` is not null
3. Check logcat for gesture completion/cancellation messages

### Poor Video Quality
1. Increase `bitrateKbps` (try 15000-20000)
2. Increase `quality` factor (try 0.9)
3. Increase `maxWidth` if network allows (try 1920)
4. Check encoder selection in logs (prefer hardware encoders)

### High Latency
1. Reduce `fps` (try 24-30)
2. Reduce `maxWidth` (try 720-1080)
3. Use CBR (Constant Bitrate) mode - already enabled
4. Check network conditions

## Implementation Checklist

- ✅ Input event handler for tap
- ✅ Input event handler for long press
- ✅ Input event handler for swipe
- ✅ Input event handler for scroll
- ✅ Navigation action handler for back
- ✅ Navigation action handler for home
- ✅ Navigation action handler for recents
- ✅ Navigation action handler for notifications
- ✅ Navigation action handler for quick settings
- ✅ Navigation action handler for power dialog
- ✅ Accessibility service implementation
- ✅ WebSocket message handling
- ✅ Response messages for input events
- ✅ Video encoder quality parameters
- ✅ Error handling and logging
- ✅ Documentation

## Next Steps for Mac Side

1. **Implement Mouse/Trackpad Event Capture**
   - Capture click events on video view
   - Map coordinates to Android screen space
   - Send tap/longPress events via WebSocket

2. **Implement Gesture Recognition**
   - Detect swipe gestures
   - Detect scroll gestures (trackpad two-finger scroll)
   - Send appropriate input events

3. **Implement Keyboard Shortcuts**
   - Map keyboard shortcuts to navigation actions
   - Example: Cmd+[ for back, Cmd+H for home

4. **Add UI Controls**
   - Buttons for back/home/recents
   - Quality settings slider
   - Connection status indicator

5. **Handle Responses**
   - Listen for `inputEventResponse` messages
   - Show error messages if gestures fail
   - Provide feedback to user

## Example Mac Implementation (Swift)

```swift
// Handle mouse click on video view
func handleMouseClick(at point: CGPoint, isLongPress: Bool = false) {
    guard let androidSize = currentAndroidScreenSize else { return }
    
    // Map coordinates
    let scaleX = CGFloat(androidSize.width) / videoView.bounds.width
    let scaleY = CGFloat(androidSize.height) / videoView.bounds.height
    
    let androidX = point.x * scaleX
    let androidY = point.y * scaleY
    
    // Send input event
    let message: [String: Any] = [
        "type": "inputEvent",
        "data": [
            "inputType": isLongPress ? "longPress" : "tap",
            "x": androidX,
            "y": androidY
        ]
    ]
    
    webSocket.send(message)
}

// Handle scroll gesture
func handleScroll(deltaX: CGFloat, deltaY: CGFloat, at point: CGPoint) {
    guard let androidSize = currentAndroidScreenSize else { return }
    
    let scaleX = CGFloat(androidSize.width) / videoView.bounds.width
    let scaleY = CGFloat(androidSize.height) / videoView.bounds.height
    
    let androidX = point.x * scaleX
    let androidY = point.y * scaleY
    
    let message: [String: Any] = [
        "type": "inputEvent",
        "data": [
            "inputType": "scroll",
            "x": androidX,
            "y": androidY,
            "deltaX": deltaX * scaleX,
            "deltaY": deltaY * scaleY
        ]
    ]
    
    webSocket.send(message)
}
```

## Summary

The Android side is now fully implemented with:
- ✅ Complete input event handling (tap, long press, swipe, scroll)
- ✅ Navigation actions (back, home, recents, etc.)
- ✅ Quality parameter support in video encoder
- ✅ Comprehensive error handling and logging
- ✅ Response messages for all operations

The Mac side needs to implement the UI and event capture to send these commands to Android.
