# Screen Mirroring Not Working - Troubleshooting Guide

## Problem
Screen mirroring is not working after enabling accessibility service.

## Required Permissions & Services

### 1. Media Projection Permission (For Screen Capture)
- **What it does**: Allows capturing the screen content
- **How to grant**: Automatically requested when you tap "Start Mirroring"
- **Status check**: Look for system dialog asking to "Start capturing everything on your screen"

### 2. Accessibility Service (For Remote Control)
- **What it does**: Allows remote touch/gesture input from Mac
- **How to enable**: 
  1. Go to Android Settings → Accessibility
  2. Find "AirSync" or "InputAccessibilityService"
  3. Toggle it ON
  4. Confirm the warning dialog
- **Status check**: Service should show as "On" in Accessibility settings

## Common Issues & Solutions

### Issue 1: Screen Stays Black / Nothing Happens

**Possible Causes:**
1. Media Projection permission not granted
2. Service failed to start
3. Encoder initialization failed

**Solutions:**

1. **Check Android Logs:**
```bash
# In Android Studio Logcat, filter for:
ScreenCaptureService
ScreenMirroringManager
MediaProjection
```

2. **Look for these log messages:**
```
✅ GOOD: "MediaProjection session started"
✅ GOOD: "Screen mirroring started"
✅ GOOD: "Encoder initialized successfully"

❌ BAD: "Invalid start parameters for screen capture"
❌ BAD: "Failed to initialize encoder"
❌ BAD: "MediaProjection is null"
```

3. **Try this sequence:**
   - Close the app completely
   - Clear app from recent apps
   - Reopen app
   - Connect to Mac
   - Try mirroring again

### Issue 2: Permission Dialog Doesn't Appear

**Possible Causes:**
1. Dialog was dismissed/denied previously
2. App doesn't have permission to show overlays
3. Another app is blocking the dialog

**Solutions:**

1. **Clear app permissions:**
   - Settings → Apps → AirSync → Permissions
   - Revoke all permissions
   - Try again

2. **Check overlay permission:**
   - Settings → Apps → AirSync → Advanced → Display over other apps
   - Enable if disabled

3. **Restart device:**
   - Sometimes Android caches permission states
   - A restart clears this

### Issue 3: "Screen mirroring already active" Message

**Possible Causes:**
1. Previous session didn't stop properly
2. Service is stuck in running state

**Solutions:**

1. **Force stop the service:**
```bash
# Via ADB:
adb shell am force-stop com.sameerasw.airsync
```

2. **Or manually:**
   - Settings → Apps → AirSync → Force Stop
   - Reopen app and try again

3. **Check notification:**
   - If you see "Screen mirroring is active" notification
   - Tap "Stop" button in notification
   - Try starting again

### Issue 4: Accessibility Service Not Showing Up

**Possible Causes:**
1. Service not properly declared in manifest
2. Config file missing
3. Android version too old (need Android 7.0+)

**Solutions:**

1. **Verify Android version:**
   - Settings → About Phone → Android version
   - Must be 7.0 (Nougat) or higher

2. **Reinstall the app:**
   - Uninstall completely
   - Reinstall from Android Studio
   - Check Settings → Accessibility again

3. **Check manifest:**
   - Verify `InputAccessibilityService` is declared
   - Verify `accessibility_service_config.xml` exists

### Issue 5: Screen Mirrors But Touch Doesn't Work

**Possible Causes:**
1. Accessibility service not enabled
2. Service crashed
3. Mac not sending touch events

**Solutions:**

1. **Verify accessibility service is ON:**
   - Settings → Accessibility → AirSync
   - Should show "On"
   - If "Off", enable it

2. **Check service instance:**
```kotlin
// In code, check:
InputAccessibilityService.instance != null
```

3. **Check Android logs:**
```
Filter for: InputAccessibilityService
Look for: "InputAccessibilityService connected"
```

4. **Test from Mac:**
   - Open Mac console
   - Try clicking on mirrored screen
   - Check if touch events are being sent

### Issue 6: Service Keeps Stopping

**Possible Causes:**
1. Android killing service due to battery optimization
2. Service crashing
3. MediaProjection being revoked

**Solutions:**

1. **Disable battery optimization:**
   - Settings → Apps → AirSync → Battery
   - Select "Unrestricted"

2. **Keep app in foreground:**
   - Don't minimize the app while mirroring
   - Service should stay alive with notification

3. **Check for crashes:**
```bash
# In Logcat:
Filter: AndroidRuntime
Look for: FATAL EXCEPTION
```

## Diagnostic Steps

### Step 1: Verify Prerequisites
- [ ] Android version 7.0 or higher
- [ ] App installed and updated
- [ ] Connected to same network as Mac
- [ ] Mac app is running and connected

### Step 2: Check Permissions
- [ ] Accessibility service enabled (Settings → Accessibility)
- [ ] Display over other apps enabled (if required)
- [ ] Battery optimization disabled

### Step 3: Test Media Projection
1. Open AirSync app
2. Connect to Mac
3. Tap "Start Mirroring"
4. **Expected**: System dialog appears asking to capture screen
5. Tap "Start now"
6. **Expected**: Notification appears "Screen mirroring is active"

### Step 4: Check Service Status
```bash
# Via ADB:
adb shell dumpsys activity services | grep -A 10 ScreenCaptureService
adb shell dumpsys accessibility | grep -A 10 InputAccessibilityService
```

### Step 5: Monitor Logs
```bash
# In Android Studio Logcat:
adb logcat -s ScreenCaptureService:D ScreenMirroringManager:D InputAccessibilityService:D WebSocketMessageHandler:D
```

## Expected Log Flow (Working)

```
[WebSocketMessageHandler] Handling message type: mirrorRequest
[MirrorRequestHelper] Sent broadcast for mirror request
[ScreenShareActivity] onCreate called
[ScreenShareActivity] Launching screen capture intent
[ScreenCaptureService] Starting screen capture
[ScreenCaptureService] MediaProjection session started
[ScreenMirroringManager] Initializing screen mirroring
[ScreenMirroringManager] Encoder initialized successfully
[ScreenMirroringManager] Screen mirroring started
[WebSocketUtil] Sending message: {"type":"mirrorStart",...}
[ScreenMirroringManager] Encoding frame...
```

## Quick Test

Run this test to verify everything is working:

1. **Test 1: Service Declaration**
```bash
adb shell pm dump com.sameerasw.airsync | grep -A 5 InputAccessibilityService
# Should show service details
```

2. **Test 2: Accessibility Status**
```bash
adb shell settings get secure enabled_accessibility_services
# Should include: com.sameerasw.airsync/.service.InputAccessibilityService
```

3. **Test 3: Start Mirroring**
```bash
# From Mac, send mirror request
# Check Android logs for "Screen mirroring started"
```

## Manual Workaround

If automatic flow doesn't work, try manual approach:

1. **Enable accessibility service first:**
   - Settings → Accessibility → AirSync → ON
   - Confirm warning

2. **Then start mirroring:**
   - Open AirSync app
   - Connect to Mac
   - Start mirroring

3. **Grant permission when prompted:**
   - Tap "Start now" on system dialog
   - Don't dismiss or deny

## Still Not Working?

If none of the above helps, provide these details:

1. **Android version**: Settings → About Phone
2. **Device model**: Settings → About Phone
3. **App logs**: Full logcat output
4. **Steps taken**: What you tried
5. **Error messages**: Any errors shown

### Collect Full Logs
```bash
adb logcat -d > airsync_logs.txt
```

Then share the `airsync_logs.txt` file for analysis.

## Known Limitations

1. **Android 6.0 and below**: Gesture injection not supported
2. **Some manufacturers**: May restrict accessibility services (Xiaomi, Huawei)
3. **Battery saver mode**: May kill background services
4. **Split screen mode**: May not capture correctly
5. **Secure screens**: Cannot capture (banking apps, password fields)

## Success Indicators

When everything is working correctly, you should see:

✅ Accessibility service shows "On" in Settings
✅ System dialog appears when starting mirroring
✅ Notification shows "Screen mirroring is active"
✅ Mac displays Android screen
✅ Clicking on Mac screen triggers touch on Android
✅ Logs show "Screen mirroring started"
✅ No error messages in logcat
