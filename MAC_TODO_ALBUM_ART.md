# Mac Side - Album Art Implementation Guide

## 🎨 How to Send Album Art to Android

### Quick Implementation

```swift
import Foundation
import AppKit

// 1. Get current playing track's artwork
func getCurrentAlbumArt() -> NSImage? {
    // From Apple Music/Spotify/etc.
    // This depends on your media source
    return currentTrack?.artwork
}

// 2. Convert to JPEG and encode to base64
func encodeAlbumArt(_ image: NSImage) -> String? {
    guard let tiffData = image.tiffRepresentation,
          let bitmap = NSBitmapImageRep(data: tiffData),
          let jpegData = bitmap.representation(using: .jpeg, properties: [.compressionFactor: 0.8]) else {
        return nil
    }
    
    return jpegData.base64EncodedString()
}

// 3. Send in status update
func sendMediaStatus(title: String, artist: String, isPlaying: Bool, albumArt: NSImage?) {
    var musicData: [String: Any] = [
        "isPlaying": isPlaying,
        "title": title,
        "artist": artist,
        "volume": currentVolume,
        "isMuted": isMuted
    ]
    
    // Add album art if available
    if let albumArt = albumArt,
       let base64String = encodeAlbumArt(albumArt) {
        musicData["albumArt"] = base64String
    }
    
    let message: [String: Any] = [
        "type": "status",
        "data": [
            "battery": [
                "level": batteryLevel,
                "isCharging": isCharging
            ],
            "isPaired": true,
            "music": musicData
        ]
    ]
    
    webSocket.send(message)
}
```

### Performance Optimization

```swift
// Cache album art to avoid re-encoding
private var albumArtCache: [String: String] = [:]

func getOrCacheAlbumArt(for trackId: String, image: NSImage) -> String? {
    // Check cache first
    if let cached = albumArtCache[trackId] {
        return cached
    }
    
    // Encode and cache
    if let encoded = encodeAlbumArt(image) {
        albumArtCache[trackId] = encoded
        
        // Limit cache size
        if albumArtCache.count > 10 {
            albumArtCache.removeFirst()
        }
        
        return encoded
    }
    
    return nil
}
```

### Size Optimization

```swift
func resizeAndEncodeAlbumArt(_ image: NSImage, maxSize: CGFloat = 300) -> String? {
    // Resize to max 300x300 to reduce data size
    let size = image.size
    let ratio = min(maxSize / size.width, maxSize / size.height)
    
    if ratio < 1.0 {
        let newSize = CGSize(width: size.width * ratio, height: size.height * ratio)
        let resized = NSImage(size: newSize)
        
        resized.lockFocus()
        image.draw(in: NSRect(origin: .zero, size: newSize),
                   from: NSRect(origin: .zero, size: size),
                   operation: .copy,
                   fraction: 1.0)
        resized.unlockFocus()
        
        return encodeAlbumArt(resized)
    }
    
    return encodeAlbumArt(image)
}
```

## 📱 Android Side (Already Implemented)

The Android side already handles album art:

```kotlin
// In MacDeviceStatusManager.kt
val albumArt = music?.optString("albumArt", "") ?: ""

// Decode base64 to Bitmap
val albumArtBitmap = if (albumArt.isNotEmpty()) {
    try {
        val decodedBytes = Base64.decode(albumArt, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
    } catch (e: Exception) {
        null
    }
} else {
    null
}

// Update media player with album art
MacMediaPlayerService.updateMacMedia(context, title, artist, isPlaying, albumArtBitmap)
```

## 🎵 Media Source Examples

### Apple Music (via ScriptingBridge)

```swift
import ScriptingBridge

@objc protocol MusicApplication {
    @objc optional var currentTrack: MusicTrack { get }
}

@objc protocol MusicTrack {
    @objc optional var name: String { get }
    @objc optional var artist: String { get }
    @objc optional var artworks: [MusicArtwork] { get }
}

@objc protocol MusicArtwork {
    @objc optional var data: NSImage { get }
}

func getAppleMusicAlbumArt() -> NSImage? {
    guard let music = SBApplication(bundleIdentifier: "com.apple.Music") as? MusicApplication,
          let track = music.currentTrack,
          let artworks = track.artworks,
          let firstArtwork = artworks.first,
          let image = firstArtwork.data else {
        return nil
    }
    
    return image
}
```

### Spotify (via AppleScript)

```swift
func getSpotifyAlbumArt() -> NSImage? {
    let script = """
    tell application "Spotify"
        set artworkUrl to artwork url of current track
        return artworkUrl
    end tell
    """
    
    guard let artworkUrl = runAppleScript(script),
          let url = URL(string: artworkUrl),
          let data = try? Data(contentsOf: url),
          let image = NSImage(data: data) else {
        return nil
    }
    
    return image
}

func runAppleScript(_ source: String) -> String? {
    let script = NSAppleScript(source: source)
    var error: NSDictionary?
    let result = script?.executeAndReturnError(&error)
    return result?.stringValue
}
```

### System Media (via MPNowPlayingInfoCenter)

```swift
import MediaPlayer

func getSystemMediaAlbumArt() -> NSImage? {
    let center = MPNowPlayingInfoCenter.default()
    
    guard let artwork = center.nowPlayingInfo?[MPMediaItemPropertyArtwork] as? MPMediaItemArtwork else {
        return nil
    }
    
    // Get image at desired size
    let image = artwork.image(at: CGSize(width: 300, height: 300))
    return image
}
```

## 🔄 Update Flow

### When Track Changes

```swift
func onTrackChanged(newTrack: Track) {
    // Get album art
    let albumArt = getAlbumArt(for: newTrack)
    
    // Send update with album art
    sendMediaStatus(
        title: newTrack.title,
        artist: newTrack.artist,
        isPlaying: true,
        albumArt: albumArt
    )
}
```

### When Playback State Changes

```swift
func onPlaybackStateChanged(isPlaying: Bool) {
    // Keep current album art
    sendMediaStatus(
        title: currentTrack.title,
        artist: currentTrack.artist,
        isPlaying: isPlaying,
        albumArt: currentAlbumArt
    )
}
```

## 📊 Data Size Considerations

### Recommended Settings

```swift
// Image size: 300x300 pixels
// JPEG quality: 0.8 (80%)
// Expected size: 20-50 KB per image
// Base64 overhead: +33%
// Final size: 25-65 KB

// Example calculation:
// 300x300 JPEG @ 80% = ~30 KB
// Base64 encoded = ~40 KB
// WebSocket message = ~40 KB
```

### Optimization Tips

1. **Resize images** to 300x300 or smaller
2. **Use JPEG** with 80% quality
3. **Cache encoded strings** to avoid re-encoding
4. **Only send when changed** (compare track IDs)
5. **Compress if needed** (though base64 is already efficient)

## 🧪 Testing

### Test Album Art Sending

```swift
// 1. Get test image
let testImage = NSImage(named: "test_album_art")!

// 2. Encode
let base64 = encodeAlbumArt(testImage)
print("Base64 length: \(base64?.count ?? 0)")

// 3. Send test message
let testMessage: [String: Any] = [
    "type": "status",
    "data": [
        "music": [
            "isPlaying": true,
            "title": "Test Song",
            "artist": "Test Artist",
            "albumArt": base64 ?? ""
        ]
    ]
]

webSocket.send(testMessage)
```

### Verify on Android

```bash
# Check logs
adb logcat -s MacMediaPlayerService MacDeviceStatusManager

# Should see:
# MacDeviceStatusManager: Received Mac device status: {...}
# MacDeviceStatusManager: Album art decoded successfully
# MacMediaPlayerService: Updating album art
```

## ✅ Checklist

- [ ] Implement album art retrieval from media source
- [ ] Add image resizing (300x300 recommended)
- [ ] Add JPEG encoding with 80% quality
- [ ] Add base64 encoding
- [ ] Add caching to avoid re-encoding
- [ ] Send album art in status updates
- [ ] Test with real music playback
- [ ] Verify on Android notification
- [ ] Check data size (should be <100 KB)
- [ ] Optimize if needed

## 🎉 Result

When implemented, Android will show:

**Lock Screen:**
```
┌─────────────────────────────────────┐
│ 🖼️  Song Title                      │
│ [Art] Artist Name                   │
│      Playing on Mac                 │
│                                     │
│  [⏮️]    [⏸️]    [⏭️]               │
└─────────────────────────────────────┘
```

**Notification Shade:**
```
┌─────────────────────────────────────┐
│ 🖼️  AirSync                         │
│ [Art] Song Title                    │
│      Artist Name                    │
│      Playing on Mac                 │
│                                     │
│  [⏮️]    [⏸️]    [⏭️]               │
└─────────────────────────────────────┘
```

**Quick Settings:**
```
┌─────────────────────────────────────┐
│ 🖼️  Song Title                      │
│ [Art] Artist Name                   │
│  [⏮️]    [⏸️]    [⏭️]               │
└─────────────────────────────────────┘
```

Perfect! 🎨
