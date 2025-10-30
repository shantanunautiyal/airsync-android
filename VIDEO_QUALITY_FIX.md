# Video Quality & Performance Fix

## Problem Analysis

From your screenshot:
- **FPS: 7.0** (Target: 15-30)
- **Latency: 336ms** (Target: <100ms)
- **Frames: 687**
- **Dropped: 366** (53% drop rate!)

This indicates the encoder is severely overwhelmed and can't keep up with the requested settings.

## Root Causes

### 1. Bitrate Too High
- **Previous**: 12,000 kbps (12 Mbps)
- **Problem**: Way too high for real-time encoding on mobile devices
- **Result**: Encoder can't process frames fast enough, causing massive drops

### 2. Quality Parameter Ignored
- The `quality` parameter (0.0-1.0) was being passed but never used
- Bitrate was always set to the maximum regardless of quality setting
- No dynamic adjustment based on resolution or frame rate

### 3. VBR Mode Issues
- Variable Bitrate (VBR) mode was enabled
- VBR can cause inconsistent frame delivery
- Better for file encoding, not real-time streaming

## Fixes Applied

### 1. Dynamic Bitrate Calculation
Now calculates bitrate based on resolution, FPS, and quality:

```kotlin
// Formula: pixels × bits_per_pixel × fps × quality
val pixelCount = width × height
val baseBitsPerPixel = 0.1f
val calculatedBitrate = pixelCount × baseBitsPerPixel × fps × quality
```

**Example calculations:**

| Resolution | FPS | Quality | Calculated Bitrate |
|------------|-----|---------|-------------------|
| 1280×720   | 30  | 0.6     | 1,658 kbps (~1.7 Mbps) |
| 1280×720   | 15  | 0.6     | 829 kbps (~0.8 Mbps) |
| 960×540    | 30  | 0.6     | 933 kbps (~0.9 Mbps) |
| 960×540    | 15  | 0.6     | 466 kbps (~0.5 Mbps) |

### 2. Bitrate Capping
- Calculated bitrate is capped at the provided `bitrateKbps` value
- Prevents excessive bitrates even with high quality settings
- Default cap reduced from 12,000 to 4,000 kbps

### 3. Changed to CBR Mode
```kotlin
// Changed from VBR to CBR
setInteger(MediaFormat.KEY_BITRATE_MODE, BITRATE_MODE_CBR)
```
- Constant Bitrate (CBR) provides more consistent frame delivery
- Better for real-time streaming
- Reduces frame drops

### 4. Updated Defaults

**MirroringOptions defaults:**
```kotlin
fps = 30
quality = 0.8f
maxWidth = 1280
bitrateKbps = 4000  // Reduced from 12000
```

**WebSocketMessageHandler defaults:**
```kotlin
fps = 30 (clamped 10-60)
quality = 0.6 (clamped 0.3-1.0)  // Reduced from 0.8
maxWidth = 1280
bitrateKbps = 4000 (clamped 1000-8000)  // Reduced from 12000
```

### 5. Added Validation
- FPS: 10-60 range
- Quality: 0.3-1.0 range
- Bitrate: 1,000-8,000 kbps range
- Logs warnings when values are clamped

## Expected Results

### With Default Settings (30 FPS, 0.6 quality, 1280×720)
- **Calculated bitrate**: ~1,658 kbps (1.7 Mbps)
- **Expected FPS**: 25-30
- **Expected latency**: 50-100ms
- **Expected drops**: <5%

### With Lower Settings (15 FPS, 0.5 quality, 960×540)
- **Calculated bitrate**: ~388 kbps (0.4 Mbps)
- **Expected FPS**: 15
- **Expected latency**: 30-60ms
- **Expected drops**: <2%

## Recommended Settings

### For Best Quality (Good WiFi)
```kotlin
MirroringOptions(
    fps = 30,
    quality = 0.7f,
    maxWidth = 1280,
    bitrateKbps = 3000
)
```
- Expected: 25-30 FPS, ~2 Mbps, <100ms latency

### For Balanced Performance
```kotlin
MirroringOptions(
    fps = 20,
    quality = 0.6f,
    maxWidth = 1280,
    bitrateKbps = 2500
)
```
- Expected: 18-20 FPS, ~1.4 Mbps, <80ms latency

### For Low Latency (Recommended)
```kotlin
MirroringOptions(
    fps = 15,
    quality = 0.5f,
    maxWidth = 960,
    bitrateKbps = 2000
)
```
- Expected: 15 FPS, ~0.5 Mbps, <60ms latency
- **Best for remote control responsiveness**

### For Weak Network
```kotlin
MirroringOptions(
    fps = 10,
    quality = 0.4f,
    maxWidth = 720,
    bitrateKbps = 1500
)
```
- Expected: 10 FPS, ~0.3 Mbps, <50ms latency

## Testing

### 1. Check Logs
After starting mirroring, look for:
```
Bitrate calculation: 1280x720 @ 30fps, quality=0.6
Calculated bitrate: 1658kbps, capped at: 1658kbps
Mirror request with options: fps=30, quality=0.6, maxWidth=1280, bitrate=4000kbps
```

### 2. Monitor Performance
Watch the Mac overlay for:
- **FPS**: Should match requested FPS (±2)
- **Latency**: Should be <100ms
- **Dropped frames**: Should be <10%

### 3. Adjust Settings
If still having issues:

**Too many drops?**
- Lower FPS (30 → 20 → 15)
- Lower quality (0.6 → 0.5 → 0.4)
- Lower resolution (1280 → 960 → 720)

**Too blurry?**
- Increase quality (0.6 → 0.7 → 0.8)
- Increase bitrate cap (4000 → 5000)
- But watch for frame drops!

**Too laggy?**
- Lower FPS for faster encoding
- Lower resolution
- Use CBR mode (already enabled)

## Files Modified

1. **ScreenMirroringManager.kt**
   - Added dynamic bitrate calculation
   - Changed VBR to CBR mode
   - Added bitrate logging

2. **MirroringOptions.kt**
   - Reduced default bitrate: 12000 → 4000 kbps

3. **WebSocketMessageHandler.kt**
   - Reduced default quality: 0.8 → 0.6
   - Reduced default bitrate: 12000 → 4000 kbps
   - Added bitrate validation (1000-8000 range)
   - Added quality validation (0.3-1.0 range)

## Understanding the Numbers

### Bitrate Calculation Explained
```
1280×720 pixels = 921,600 pixels
× 0.1 bits per pixel
× 30 FPS
× 0.6 quality
= 1,658,880 bits/sec
= 1,658 kbps
= ~1.7 Mbps
```

### Why 0.1 Bits Per Pixel?
- H.264 compression is very efficient
- 0.1 bpp is a good balance for real-time encoding
- Higher values = better quality but slower encoding
- Lower values = faster encoding but more artifacts

### Why CBR Instead of VBR?
- **VBR (Variable Bitrate)**: Adjusts bitrate based on scene complexity
  - Good for: File encoding, offline processing
  - Bad for: Real-time streaming, consistent latency
  
- **CBR (Constant Bitrate)**: Maintains steady bitrate
  - Good for: Real-time streaming, predictable performance
  - Bad for: File size optimization

## Troubleshooting

### Still Getting Low FPS?

1. **Check device performance**:
   ```bash
   adb shell dumpsys cpuinfo | grep airsync
   ```
   - Should be <30% CPU usage

2. **Check encoder**:
   ```
   Selected Encoder: c2.android.avc.encoder
   ```
   - Hardware encoders are faster
   - Software encoders may struggle

3. **Try lower settings**:
   - 15 FPS, 0.4 quality, 720p

### Still Getting Drops?

1. **Network issue?**
   ```bash
   ping <mac-ip>
   ```
   - Should be <10ms on local network

2. **Encoder overload?**
   - Lower bitrate cap to 2000 kbps
   - Lower resolution to 960×540

3. **Too many apps running?**
   - Close background apps
   - Disable battery saver

### Quality Too Low?

1. **Increase quality gradually**:
   - 0.6 → 0.65 → 0.7
   - Watch for frame drops

2. **Increase bitrate cap**:
   - 4000 → 5000 → 6000
   - Don't exceed 8000

3. **Check resolution**:
   - Make sure it's not being downscaled
   - 1280×720 is good balance

## Performance Targets

| Metric | Excellent | Good | Acceptable | Poor |
|--------|-----------|------|------------|------|
| FPS | 28-30 | 20-27 | 15-19 | <15 |
| Latency | <50ms | 50-100ms | 100-200ms | >200ms |
| Drops | <2% | 2-5% | 5-10% | >10% |
| Bitrate | 1-2 Mbps | 2-3 Mbps | 3-4 Mbps | >4 Mbps |

Your previous stats (7 FPS, 336ms, 53% drops) were in the "Poor" category across all metrics. With these fixes, you should reach "Good" or "Excellent" levels.
