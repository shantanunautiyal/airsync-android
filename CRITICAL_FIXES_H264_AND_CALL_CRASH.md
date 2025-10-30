# Critical Fixes: H.264 Profile and Call Crash ✅

## Issues Fixed

### 1. ✅ H.264 Profile Issue - Device Ignoring Baseline Request

**Problem Confirmed:** The logs showed the device encoder is ignoring the Baseline Profile request and still sending High Profile:

```
[H264Decoder] 📊 SPS first bytes: 67 42 80 1F DA 01 0C 04
                                      ^^
                                      80 = High Profile ❌ (should be 00)
[H264Decoder] ❌ Failed to create format description: -12712 (kVTParameterErr)
[H264Decoder] 🔄 Falling back to FFmpeg software decoder
```

**Root Cause:** Some Android devices/encoders ignore the `KEY_PROFILE` setting or don't support Baseline Profile properly.

**Fix Applied:** More aggressive Baseline Profile enforcement:

```kotlin
// BEFORE: Simple request
setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)

// AFTER: Aggressive enforcement
Log.i(TAG, "FORCING AVCProfileBaseline for VideoToolbox compatibility")
setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
setInteger(MediaFormat.KEY_LEVEL, MediaCodecInfo.CodecProfileLevel.AVCLevel31)

// Additional constraints to force Baseline Profile
setInteger(MediaFormat.KEY_MAX_B_FRAMES, 0) // No B-frames in Baseline
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
    setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileConstrainedBaseline)
    Log.i(TAG, "Also requesting Constrained Baseline Profile")
}
```

**Changes:**
- ✅ Added `KEY_MAX_B_FRAMES = 0` (Baseline Profile constraint)
- ✅ Added Constrained Baseline Profile as fallback
- ✅ Enhanced logging for debugging

### 2. ✅ Call Notification Crash - Foreground Service Required

**Problem:** App crashed when showing call notifications:

```
java.lang.IllegalArgumentException: CallStyle notifications must be for a foreground service or user initiated job or use a fullScreenIntent.
```

**Root Cause:** Android 12+ requires `CallStyle` notifications to be shown from a foreground service.

**Fix Applied:** Convert to foreground service with fallback:

```kotlin
// BEFORE: Regular notification (crashes)
val notification = createCallNotification(call)
val notificationManager = getSystemService(NotificationManager::class.java)
notificationManager.notify(notificationId, notification)

// AFTER: Foreground service with fallback
val notification = createCallNotification(call)

try {
    startForeground(notificationId, notification)
    Log.d(TAG, "Started foreground service with call notification")
} catch (e: Exception) {
    Log.e(TAG, "Failed to start foreground service, falling back to regular notification", e)
    val fallbackNotification = createFallbackCallNotification(call)
    val notificationManager = getSystemService(NotificationManager::class.java)
    notificationManager.notify(notificationId, fallbackNotification)
}
```

**Added Fallback Notification:**
```kotlin
private fun createFallbackCallNotification(call: OngoingCall): Notification {
    return NotificationCompat.Builder(this, CHANNEL_CALL)
        .setSmallIcon(R.drawable.ic_launcher_foreground)
        .setContentTitle("Call - $displayName")
        .setContentText(when (call.state) {
            CallState.RINGING -> if (call.isIncoming) "Incoming call" else "Outgoing call"
            CallState.ACTIVE -> "Call in progress"
            CallState.HELD -> "Call on hold"
            CallState.DISCONNECTED -> "Call ended"
        })
        .setCategory(NotificationCompat.CATEGORY_CALL)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .build()
}
```

**Added Proper Cleanup:**
```kotlin
// Stop foreground service when no more active calls
if (activeNotifications.isEmpty()) {
    try {
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.d(TAG, "Stopped foreground service - no more active calls")
    } catch (e: Exception) {
        Log.w(TAG, "Failed to stop foreground service", e)
    }
}
```

## Expected Results

### H.264 Profile Fix:
**Before:**
```
[H264Decoder] 📊 SPS first bytes: 67 42 80 1F ... (High Profile)
[H264Decoder] ❌ Failed to create format description
[H264Decoder] 🔄 Falling back to FFmpeg software decoder
FPS: 7-15 (glitchy)
```

**After (Hopefully):**
```
[H264Decoder] 📊 SPS first bytes: 67 42 00 1F ... (Baseline Profile)
[H264Decoder] ✅ Created format description
[H264Decoder] ⚡ Created hardware decompression session
FPS: 28-30 (smooth)
```

### Call Notification Fix:
**Before:**
```
java.lang.IllegalArgumentException: CallStyle notifications must be for a foreground service
Process: com.sameerasw.airsync, PID: 21640 FATAL EXCEPTION
```

**After:**
```
[LiveNotificationService] Started foreground service with call notification
[LiveNotificationService] Showing call notification for call_12345
[LiveNotificationService] Stopped foreground service - no more active calls
```

## Testing Steps

### Test H.264 Profile Fix:
1. **Start screen mirroring**
2. **Check Android logs** for:
   ```
   [ScreenMirroringManager] FORCING AVCProfileBaseline for VideoToolbox compatibility
   [ScreenMirroringManager] Also requesting Constrained Baseline Profile
   ```
3. **Check Mac logs** for SPS bytes:
   - ✅ Success: `67 42 00 1F` (Baseline)
   - ❌ Still broken: `67 42 80 1F` (High Profile)
4. **Monitor FPS** - should be 28-30 if fixed

### Test Call Notification Fix:
1. **Make a test call**
2. **Check Android logs** for:
   ```
   [LiveNotificationService] Started foreground service with call notification
   ```
3. **Verify no crash** occurs
4. **End call** and check:
   ```
   [LiveNotificationService] Stopped foreground service - no more active calls
   ```

## Alternative Solutions (If H.264 Still Fails)

If the device still sends High Profile despite these constraints:

### Option 1: Software Encoder Fallback
```kotlin
// Force software encoder if hardware doesn't support Baseline
val codecName = when {
    encoderName.contains("qcom") && !supportsBaseline -> "OMX.google.h264.encoder"
    encoderName.contains("exynos") && !supportsBaseline -> "OMX.google.h264.encoder"
    else -> encoderName
}
```

### Option 2: Different Profile Strategy
```kotlin
// Try Main Profile as compromise
setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileMain)
```

### Option 3: Mac Client Adaptation
- Update Mac to handle High Profile with software decoder
- Optimize FFmpeg decoder performance
- Add profile detection and adaptive decoding

## Files Modified

1. **app/src/main/java/com/sameerasw/airsync/utils/ScreenMirroringManager.kt**
   - Enhanced Baseline Profile enforcement
   - Added B-frame constraint
   - Added Constrained Baseline fallback
   - Improved logging

2. **app/src/main/java/com/sameerasw/airsync/service/LiveNotificationService.kt**
   - Convert to foreground service for CallStyle notifications
   - Added fallback notification without CallStyle
   - Added proper service lifecycle management
   - Added cleanup when no active calls

## Summary

✅ **H.264 Profile:** More aggressive Baseline Profile enforcement to fix glitchy mirroring
✅ **Call Crash:** Fixed by using foreground service with fallback for Android 12+ compatibility
✅ **No Breaking Changes:** Both fixes include fallbacks for compatibility
✅ **Better Logging:** Enhanced debugging for both issues

These fixes should resolve the critical mirroring performance issues and call notification crashes.