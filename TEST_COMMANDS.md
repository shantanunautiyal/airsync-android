# Test Commands for Remote Control

## Testing via WebSocket

You can test the Android remote control implementation using these WebSocket commands.

### Setup
1. Connect to the Android device via WebSocket
2. Start screen mirroring
3. Send these test commands

## Basic Input Tests

### Test 1: Tap in Center of Screen
```json
{
  "type": "inputEvent",
  "data": {
    "inputType": "tap",
    "x": 540,
    "y": 960
  }
}
```
**Expected:** Tap registered at center of 1080x1920 screen

### Test 2: Long Press
```json
{
  "type": "inputEvent",
  "data": {
    "inputType": "longPress",
    "x": 540,
    "y": 960
  }
}
```
**Expected:** Context menu or long-press action appears

### Test 3: Swipe Down (Scroll)
```json
{
  "type": "inputEvent",
  "data": {
    "inputType": "swipe",
    "startX": 540,
    "startY": 1500,
    "endX": 540,
    "endY": 500,
    "duration": 300
  }
}
```
**Expected:** Screen scrolls down

### Test 4: Swipe Up (Scroll)
```json
{
  "type": "inputEvent",
  "data": {
    "inputType": "swipe",
    "startX": 540,
    "startY": 500,
    "endX": 540,
    "endY": 1500,
    "duration": 300
  }
}
```
**Expected:** Screen scrolls up

### Test 5: Swipe Left (Navigate)
```json
{
  "type": "inputEvent",
  "data": {
    "inputType": "swipe",
    "startX": 900,
    "startY": 960,
    "endX": 180,
    "endY": 960,
    "duration": 300
  }
}
```
**Expected:** Navigate to next page/screen (if applicable)

### Test 6: Swipe Right (Navigate Back)
```json
{
  "type": "inputEvent",
  "data": {
    "inputType": "swipe",
    "startX": 180,
    "startY": 960,
    "endX": 900,
    "endY": 960,
    "duration": 300
  }
}
```
**Expected:** Navigate back or open drawer (if applicable)

### Test 7: Scroll with Delta
```json
{
  "type": "inputEvent",
  "data": {
    "inputType": "scroll",
    "x": 540,
    "y": 960,
    "deltaX": 0,
    "deltaY": -100
  }
}
```
**Expected:** Smooth scroll down

## Navigation Tests

### Test 8: Back Button
```json
{
  "type": "inputEvent",
  "data": {
    "inputType": "back"
  }
}
```
**Expected:** Navigate back or close current app

### Test 9: Home Button
```json
{
  "type": "inputEvent",
  "data": {
    "inputType": "home"
  }
}
```
**Expected:** Return to home screen

### Test 10: Recents/Overview
```json
{
  "type": "inputEvent",
  "data": {
    "inputType": "recents"
  }
}
```
**Expected:** Show recent apps

### Test 11: Notifications Panel
```json
{
  "type": "inputEvent",
  "data": {
    "inputType": "notifications"
  }
}
```
**Expected:** Pull down notification shade

### Test 12: Quick Settings
```json
{
  "type": "inputEvent",
  "data": {
    "inputType": "quickSettings"
  }
}
```
**Expected:** Open quick settings panel

### Test 13: Power Dialog
```json
{
  "type": "inputEvent",
  "data": {
    "inputType": "powerDialog"
  }
}
```
**Expected:** Show power menu (may not work on all devices)

## Quality Settings Tests

### Test 14: High Quality Mirroring
```json
{
  "type": "mirrorRequest",
  "data": {
    "options": {
      "fps": 60,
      "quality": 0.9,
      "maxWidth": 1920,
      "bitrateKbps": 20000
    }
  }
}
```
**Expected:** High quality, smooth video stream

### Test 15: Low Latency Mirroring
```json
{
  "type": "mirrorRequest",
  "data": {
    "options": {
      "fps": 30,
      "quality": 0.6,
      "maxWidth": 720,
      "bitrateKbps": 6000
    }
  }
}
```
**Expected:** Lower quality but reduced latency

### Test 16: Balanced Mirroring
```json
{
  "type": "mirrorRequest",
  "data": {
    "options": {
      "fps": 30,
      "quality": 0.8,
      "maxWidth": 1280,
      "bitrateKbps": 12000
    }
  }
}
```
**Expected:** Good balance of quality and performance

## Complex Gesture Tests

### Test 17: Double Tap (Two Quick Taps)
Send this twice with 100ms delay:
```json
{
  "type": "inputEvent",
  "data": {
    "inputType": "tap",
    "x": 540,
    "y": 960
  }
}
```
**Expected:** Double-tap action (zoom, select, etc.)

### Test 18: Pinch Zoom (Simulated with Swipes)
Not directly supported, but can be simulated with two simultaneous swipes

### Test 19: Drag and Drop
```json
{
  "type": "inputEvent",
  "data": {
    "inputType": "swipe",
    "startX": 540,
    "startY": 500,
    "endX": 540,
    "endY": 1400,
    "duration": 800
  }
}
```
**Expected:** Drag item from top to bottom (slower swipe)

## Response Validation

All commands should return a response like:
```json
{
  "type": "inputEventResponse",
  "data": {
    "inputType": "tap",
    "success": true,
    "message": "Tap injected at (540.0, 960.0)"
  }
}
```

### Error Response Example
If accessibility service is not enabled:
```json
{
  "type": "inputEventResponse",
  "data": {
    "inputType": "tap",
    "success": false,
    "message": "Accessibility service not enabled"
  }
}
```

## JavaScript Test Script

```javascript
// WebSocket test script
const ws = new WebSocket('ws://ANDROID_IP:PORT');

ws.onopen = () => {
  console.log('Connected');
  
  // Test tap
  setTimeout(() => {
    ws.send(JSON.stringify({
      type: "inputEvent",
      data: { inputType: "tap", x: 540, y: 960 }
    }));
  }, 1000);
  
  // Test back button
  setTimeout(() => {
    ws.send(JSON.stringify({
      type: "inputEvent",
      data: { inputType: "back" }
    }));
  }, 2000);
  
  // Test swipe
  setTimeout(() => {
    ws.send(JSON.stringify({
      type: "inputEvent",
      data: {
        inputType: "swipe",
        startX: 540,
        startY: 1500,
        endX: 540,
        endY: 500,
        duration: 300
      }
    }));
  }, 3000);
};

ws.onmessage = (event) => {
  console.log('Response:', event.data);
};

ws.onerror = (error) => {
  console.error('Error:', error);
};
```

## Python Test Script

```python
import websocket
import json
import time

def on_message(ws, message):
    print(f"Response: {message}")

def on_error(ws, error):
    print(f"Error: {error}")

def on_open(ws):
    print("Connected")
    
    # Test tap
    time.sleep(1)
    ws.send(json.dumps({
        "type": "inputEvent",
        "data": {"inputType": "tap", "x": 540, "y": 960}
    }))
    
    # Test back button
    time.sleep(1)
    ws.send(json.dumps({
        "type": "inputEvent",
        "data": {"inputType": "back"}
    }))
    
    # Test swipe
    time.sleep(1)
    ws.send(json.dumps({
        "type": "inputEvent",
        "data": {
            "inputType": "swipe",
            "startX": 540,
            "startY": 1500,
            "endX": 540,
            "endY": 500,
            "duration": 300
        }
    }))

ws = websocket.WebSocketApp(
    "ws://ANDROID_IP:PORT",
    on_message=on_message,
    on_error=on_error,
    on_open=on_open
)

ws.run_forever()
```

## Logcat Monitoring

Monitor Android logs while testing:
```bash
adb logcat -s InputAccessibilityService WebSocketMessageHandler
```

Expected log output:
```
D/InputAccessibilityService: Tap gesture completed at (540.0, 960.0)
D/WebSocketMessageHandler: Handling message type: inputEvent
D/InputAccessibilityService: Back action: success
```

## Troubleshooting Tests

### If gestures don't work:
1. Check accessibility service is enabled
2. Run: `adb shell settings get secure enabled_accessibility_services`
3. Should include: `com.sameerasw.airsync/.service.InputAccessibilityService`

### If coordinates are wrong:
1. Check screen resolution: `adb shell wm size`
2. Adjust test coordinates accordingly

### If quality is poor:
1. Test with different quality settings
2. Check network bandwidth
3. Monitor encoder logs: `adb logcat -s ScreenMirroringManager`
