# Mac Side Integration Guide for Android Remote Control

## Quick Start

The Android side is ready! Here's what you need to implement on the Mac side.

## WebSocket Message Format

### Sending Input Events to Android

#### 1. Tap Event
```json
{
  "type": "inputEvent",
  "data": {
    "inputType": "tap",
    "x": 500.0,
    "y": 800.0
  }
}
```

#### 2. Long Press Event
```json
{
  "type": "inputEvent",
  "data": {
    "inputType": "longPress",
    "x": 500.0,
    "y": 800.0
  }
}
```

#### 3. Swipe Event
```json
{
  "type": "inputEvent",
  "data": {
    "inputType": "swipe",
    "startX": 500.0,
    "startY": 1000.0,
    "endX": 500.0,
    "endY": 300.0,
    "duration": 300
  }
}
```

#### 4. Scroll Event
```json
{
  "type": "inputEvent",
  "data": {
    "inputType": "scroll",
    "x": 500.0,
    "y": 800.0,
    "deltaX": 0.0,
    "deltaY": -50.0
  }
}
```

#### 5. Navigation Actions
```json
// Back button
{ "type": "inputEvent", "data": { "inputType": "back" } }

// Home button
{ "type": "inputEvent", "data": { "inputType": "home" } }

// Recents/Overview
{ "type": "inputEvent", "data": { "inputType": "recents" } }

// Notifications panel
{ "type": "inputEvent", "data": { "inputType": "notifications" } }

// Quick Settings
{ "type": "inputEvent", "data": { "inputType": "quickSettings" } }

// Power Dialog
{ "type": "inputEvent", "data": { "inputType": "powerDialog" } }
```

### Receiving Responses from Android

Android will respond with:
```json
{
  "type": "inputEventResponse",
  "data": {
    "inputType": "tap",
    "success": true,
    "message": "Tap injected at (500.0, 800.0)"
  }
}
```

## Coordinate Mapping

You need to map Mac screen coordinates to Android screen coordinates:

```swift
struct CoordinateMapper {
    let videoWidth: CGFloat
    let videoHeight: CGFloat
    let androidWidth: Int
    let androidHeight: Int
    
    func mapToAndroid(point: CGPoint) -> (x: Float, y: Float) {
        let scaleX = Float(androidWidth) / Float(videoWidth)
        let scaleY = Float(androidHeight) / Float(videoHeight)
        
        return (
            x: Float(point.x) * scaleX,
            y: Float(point.y) * scaleY
        )
    }
}
```

## Video Quality Settings

Send quality parameters when starting mirroring:

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

### Recommended Presets

**High Quality**
- fps: 60
- quality: 0.9
- maxWidth: 1920
- bitrateKbps: 20000

**Balanced (Default)**
- fps: 30
- quality: 0.8
- maxWidth: 1280
- bitrateKbps: 12000

**Low Latency**
- fps: 30
- quality: 0.6
- maxWidth: 720
- bitrateKbps: 6000

## Swift Implementation Example

### 1. Handle Mouse Events

```swift
class MirrorViewController: NSViewController {
    var webSocket: WebSocket?
    var coordinateMapper: CoordinateMapper?
    
    override func mouseDown(with event: NSEvent) {
        let point = videoView.convert(event.locationInWindow, from: nil)
        handleTap(at: point)
    }
    
    func handleTap(at point: CGPoint) {
        guard let mapper = coordinateMapper else { return }
        let android = mapper.mapToAndroid(point: point)
        
        let message: [String: Any] = [
            "type": "inputEvent",
            "data": [
                "inputType": "tap",
                "x": android.x,
                "y": android.y
            ]
        ]
        
        sendWebSocketMessage(message)
    }
}
```

### 2. Handle Scroll Events

```swift
override func scrollWheel(with event: NSEvent) {
    let point = videoView.convert(event.locationInWindow, from: nil)
    guard let mapper = coordinateMapper else { return }
    let android = mapper.mapToAndroid(point: point)
    
    let message: [String: Any] = [
        "type": "inputEvent",
        "data": [
            "inputType": "scroll",
            "x": android.x,
            "y": android.y,
            "deltaX": Float(event.scrollingDeltaX),
            "deltaY": Float(event.scrollingDeltaY)
        ]
    ]
    
    sendWebSocketMessage(message)
}
```

### 3. Handle Gesture Recognizers

```swift
func setupGestureRecognizers() {
    // Pan gesture for swipe
    let panGesture = NSPanGestureRecognizer(target: self, action: #selector(handlePan(_:)))
    videoView.addGestureRecognizer(panGesture)
    
    // Long press gesture
    let longPressGesture = NSPressGestureRecognizer(target: self, action: #selector(handleLongPress(_:)))
    longPressGesture.minimumPressDuration = 0.5
    videoView.addGestureRecognizer(longPressGesture)
}

@objc func handlePan(_ gesture: NSPanGestureRecognizer) {
    guard gesture.state == .ended else { return }
    
    let startPoint = gesture.location(in: videoView)
    let translation = gesture.translation(in: videoView)
    let endPoint = CGPoint(x: startPoint.x + translation.x, y: startPoint.y + translation.y)
    
    guard let mapper = coordinateMapper else { return }
    let androidStart = mapper.mapToAndroid(point: startPoint)
    let androidEnd = mapper.mapToAndroid(point: endPoint)
    
    let message: [String: Any] = [
        "type": "inputEvent",
        "data": [
            "inputType": "swipe",
            "startX": androidStart.x,
            "startY": androidStart.y,
            "endX": androidEnd.x,
            "endY": androidEnd.y,
            "duration": 300
        ]
    ]
    
    sendWebSocketMessage(message)
}

@objc func handleLongPress(_ gesture: NSPressGestureRecognizer) {
    guard gesture.state == .began else { return }
    
    let point = gesture.location(in: videoView)
    guard let mapper = coordinateMapper else { return }
    let android = mapper.mapToAndroid(point: point)
    
    let message: [String: Any] = [
        "type": "inputEvent",
        "data": [
            "inputType": "longPress",
            "x": android.x,
            "y": android.y
        ]
    ]
    
    sendWebSocketMessage(message)
}
```

### 4. Add Navigation Buttons

```swift
@IBAction func backButtonTapped(_ sender: Any) {
    let message: [String: Any] = [
        "type": "inputEvent",
        "data": ["inputType": "back"]
    ]
    sendWebSocketMessage(message)
}

@IBAction func homeButtonTapped(_ sender: Any) {
    let message: [String: Any] = [
        "type": "inputEvent",
        "data": ["inputType": "home"]
    ]
    sendWebSocketMessage(message)
}

@IBAction func recentsButtonTapped(_ sender: Any) {
    let message: [String: Any] = [
        "type": "inputEvent",
        "data": ["inputType": "recents"]
    ]
    sendWebSocketMessage(message)
}
```

### 5. Handle WebSocket Responses

```swift
func handleWebSocketMessage(_ message: String) {
    guard let data = message.data(using: .utf8),
          let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
          let type = json["type"] as? String else {
        return
    }
    
    switch type {
    case "inputEventResponse":
        handleInputEventResponse(json["data"] as? [String: Any])
    case "mirrorFrame":
        handleMirrorFrame(json["data"] as? [String: Any])
    default:
        break
    }
}

func handleInputEventResponse(_ data: [String: Any]?) {
    guard let data = data,
          let success = data["success"] as? Bool,
          let message = data["message"] as? String else {
        return
    }
    
    if !success {
        print("Input event failed: \(message)")
        // Show error to user
    }
}
```

### 6. Quality Settings UI

```swift
class QualitySettingsView: NSView {
    @IBOutlet weak var fpsSlider: NSSlider!
    @IBOutlet weak var qualitySlider: NSSlider!
    @IBOutlet weak var resolutionPopup: NSPopUpButton!
    
    func getCurrentSettings() -> [String: Any] {
        let fps = Int(fpsSlider.intValue)
        let quality = qualitySlider.doubleValue
        let maxWidth = getSelectedResolution()
        let bitrateKbps = calculateBitrate(fps: fps, quality: quality, width: maxWidth)
        
        return [
            "fps": fps,
            "quality": quality,
            "maxWidth": maxWidth,
            "bitrateKbps": bitrateKbps
        ]
    }
    
    func getSelectedResolution() -> Int {
        switch resolutionPopup.indexOfSelectedItem {
        case 0: return 720
        case 1: return 1280
        case 2: return 1920
        default: return 1280
        }
    }
    
    func calculateBitrate(fps: Int, quality: Double, width: Int) -> Int {
        // Simple bitrate calculation
        let baseRate = width * fps / 10
        return Int(Double(baseRate) * quality)
    }
}
```

## Keyboard Shortcuts

Implement keyboard shortcuts for common actions:

```swift
override func keyDown(with event: NSEvent) {
    guard let characters = event.charactersIgnoringModifiers else {
        super.keyDown(with: event)
        return
    }
    
    let modifiers = event.modifierFlags
    
    if modifiers.contains(.command) {
        switch characters {
        case "[":
            // Back button
            sendNavigationAction("back")
        case "h":
            // Home button
            sendNavigationAction("home")
        case "r":
            // Recents
            sendNavigationAction("recents")
        default:
            super.keyDown(with: event)
        }
    } else {
        super.keyDown(with: event)
    }
}

func sendNavigationAction(_ action: String) {
    let message: [String: Any] = [
        "type": "inputEvent",
        "data": ["inputType": action]
    ]
    sendWebSocketMessage(message)
}
```

## Testing Checklist

- [ ] Mouse click sends tap event
- [ ] Long press gesture works
- [ ] Scroll wheel sends scroll events
- [ ] Swipe gestures work
- [ ] Back button works
- [ ] Home button works
- [ ] Recents button works
- [ ] Coordinate mapping is accurate
- [ ] Quality settings are applied
- [ ] Error responses are handled
- [ ] Keyboard shortcuts work

## Troubleshooting

### Coordinates are off
- Check that you're using the video view bounds, not window bounds
- Verify the Android screen size is correct
- Make sure to account for aspect ratio differences

### Gestures not working
- Check that accessibility service is enabled on Android
- Verify WebSocket connection is active
- Check for error responses from Android

### Poor video quality
- Increase bitrate and quality settings
- Check network bandwidth
- Try different resolution settings

## User Instructions

Users need to enable the accessibility service on Android:
1. Open Android Settings
2. Go to Accessibility
3. Find "AirSync" in the list
4. Toggle it ON
5. Confirm the permission dialog

Without this, remote control will not work!
