# Health Data Debugging Guide

## Issue Identified

From the logs, there are TWO problems:

### Problem 1: Mac Client Not Parsing Data
```
[websocket] 📊 Health data dict: [:]
[websocket] ❌ Failed to parse date from health summary - type: Optional<Any>
```

**Cause:** The Mac client is receiving the JSON but failing to extract the `data` field.

**Android sends:**
```json
{"type":"healthSummary","data":{"date":1761676200000,"steps":9724,...}}
```

**Mac receives:** Empty dictionary `[:]`

**Solution:** The Mac client needs to fix its JSON parsing. The issue is likely in how it's accessing the nested `data` object.

### Problem 2: Missing New Fields in Response

The response only shows 8 fields:
```json
{"date":1761676200000,"steps":9724,"distance":4.922243250047772,"calories":1748,"activeMinutes":10,"heartRateAvg":91,"heartRateMin":null,"heartRateMax":null,"sleepDuration":null}
```

But we added 10 more fields (19 total). The new fields are missing:
- floorsClimbed
- weight
- bloodPressureSystolic
- bloodPressureDiastolic
- oxygenSaturation
- restingHeartRate
- vo2Max
- bodyTemperature
- bloodGlucose
- hydration

**Possible Causes:**
1. Old version of app still running (needs rebuild)
2. Cache returning old data structure
3. Health Connect not returning new fields (no data available)

## Debugging Steps

### Step 1: Verify Android is Sending All Fields

Add logging to see what's being sent:

```kotlin
// In WebSocketMessageHandler.kt, handleRequestHealthSummary()
Log.d(TAG, "Health summary JSON: $json")
```

### Step 2: Check if New Fields Have Data

The new fields might all be null because:
- No health data available for those metrics
- Permissions not granted for new record types
- Health Connect doesn't have data from health apps

### Step 3: Rebuild Android App

The app might be running old code. Need to:
```bash
./gradlew clean
./gradlew assembleDebug
```

### Step 4: Fix Mac Client JSON Parsing

The Mac client code likely looks like:
```swift
if let data = json["data"] as? [String: Any] {
    // This is failing - data is empty
}
```

Should be:
```swift
guard let data = json["data"] as? [String: Any] else {
    print("Failed to parse data object")
    return
}
```

## Testing the Fix

### Test 1: Verify JSON Structure

Add this to Android:
```kotlin
val summary = HealthSummary(
    date = 1234567890000,
    steps = 1000,
    distance = 5.0,
    calories = 500,
    activeMinutes = 30,
    heartRateAvg = 75,
    heartRateMin = 60,
    heartRateMax = 90,
    sleepDuration = 480,
    floorsClimbed = 10,
    weight = 70.0,
    bloodPressureSystolic = 120,
    bloodPressureDiastolic = 80,
    oxygenSaturation = 98.0,
    restingHeartRate = 65,
    vo2Max = 45.0,
    bodyTemperature = 36.5,
    bloodGlucose = 5.5,
    hydration = 2.0
)
val json = JsonUtil.createHealthSummaryJson(summary)
Log.d(TAG, "Test JSON: $json")
```

Expected output should include ALL 19 fields.

### Test 2: Check Permissions

Verify all permissions are granted:
```kotlin
val granted = healthConnectClient.permissionController.getGrantedPermissions()
Log.d(TAG, "Granted permissions: ${granted.size}")
HealthConnectUtil.PERMISSIONS.forEach { permission ->
    Log.d(TAG, "Permission ${permission}: ${permission in granted}")
}
```

### Test 3: Check Health Connect Data

Query each new metric individually:
```kotlin
val floorsClimbed = getFloorsClimbedForRange(context, start, end)
Log.d(TAG, "Floors climbed: $floorsClimbed")

val weight = getLatestWeightForRange(context, start, end)
Log.d(TAG, "Weight: $weight")

// etc for all new fields
```

## Quick Fix for Mac Client

The Mac client needs to update its parsing code. Here's what it should look like:

```swift
func handleHealthSummary(_ message: [String: Any]) {
    guard let dataDict = message["data"] as? [String: Any] else {
        print("❌ Failed to extract data object from message")
        print("Message keys: \(message.keys)")
        print("Message: \(message)")
        return
    }
    
    guard let date = dataDict["date"] as? Int64 else {
        print("❌ Failed to parse date")
        print("Data dict: \(dataDict)")
        return
    }
    
    // Parse all fields...
    let steps = dataDict["steps"] as? Int
    let distance = dataDict["distance"] as? Double
    let calories = dataDict["calories"] as? Int
    let activeMinutes = dataDict["activeMinutes"] as? Int
    let heartRateAvg = dataDict["heartRateAvg"] as? Int
    let heartRateMin = dataDict["heartRateMin"] as? Int
    let heartRateMax = dataDict["heartRateMax"] as? Int
    let sleepDuration = dataDict["sleepDuration"] as? Int64
    
    // New fields
    let floorsClimbed = dataDict["floorsClimbed"] as? Int
    let weight = dataDict["weight"] as? Double
    let bloodPressureSystolic = dataDict["bloodPressureSystolic"] as? Int
    let bloodPressureDiastolic = dataDict["bloodPressureDiastolic"] as? Int
    let oxygenSaturation = dataDict["oxygenSaturation"] as? Double
    let restingHeartRate = dataDict["restingHeartRate"] as? Int
    let vo2Max = dataDict["vo2Max"] as? Double
    let bodyTemperature = dataDict["bodyTemperature"] as? Double
    let bloodGlucose = dataDict["bloodGlucose"] as? Double
    let hydration = dataDict["hydration"] as? Double
    
    // Create HealthSummary object...
}
```

## Immediate Actions

1. **Rebuild Android app** to ensure latest code is running
2. **Check Android logs** to see what JSON is actually being sent
3. **Fix Mac client** JSON parsing to properly extract `data` object
4. **Grant all permissions** in Health Connect settings
5. **Add test data** to Health Connect to verify new fields work

## Expected Behavior

When working correctly:
- Android sends JSON with all 19 fields
- Mac receives and parses all 19 fields
- UI displays all available metrics
- Missing data shows as null (not 0)
- Cache stores all 19 fields

## Common Issues

1. **Old app version running** - Clean and rebuild
2. **Permissions not granted** - Check Health Connect settings
3. **No health data** - Add test data or use health tracking app
4. **Mac parsing error** - Update Swift code to handle nested JSON
5. **Cache has old structure** - Clear app data or update cache version
