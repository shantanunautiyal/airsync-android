# Mac Client Fix Required

## Problem

The Mac client is receiving health data from Android but failing to parse it:

```
[websocket] [received] {"type":"healthSummary","data":{"date":1761676200000,"steps":9724,"distance":4.922243250047772,"calories":1748,"activeMinutes":10,"heartRateAvg":91,"heartRateMin":null,"heartRateMax":null,"sleepDuration":null}}

[websocket] 📊 Received healthSummary message
[websocket] 📊 Health data dict: [:]
[websocket] ❌ Failed to parse date from health summary - type: Optional<Any>
```

**Android is sending correctly**, but Mac is getting an empty dictionary `[:]` when trying to access the `data` field.

## Root Cause

The Mac client's JSON parsing code is failing to extract the nested `data` object from the message.

## Solution

Update the Mac client's health data parsing code:

### Current Code (Broken)
```swift
func handleHealthSummary(_ message: [String: Any]) {
    // This is likely failing
    let data = message["data"] as? [String: Any]
    // data is nil or empty
}
```

### Fixed Code
```swift
func handleHealthSummary(_ message: [String: Any]) {
    // Add debugging
    print("📊 Full message: \(message)")
    print("📊 Message keys: \(message.keys)")
    
    // Safely extract data object
    guard let dataDict = message["data"] as? [String: Any] else {
        print("❌ Failed to extract 'data' object")
        print("❌ Type of data field: \(type(of: message["data"]))")
        return
    }
    
    print("✅ Data dict extracted: \(dataDict)")
    print("✅ Data dict keys: \(dataDict.keys)")
    
    // Parse date
    guard let dateValue = dataDict["date"] else {
        print("❌ No date field in data")
        return
    }
    
    // Handle different number types
    let date: Int64
    if let intDate = dateValue as? Int64 {
        date = intDate
    } else if let intDate = dateValue as? Int {
        date = Int64(intDate)
    } else if let doubleDate = dateValue as? Double {
        date = Int64(doubleDate)
    } else {
        print("❌ Failed to parse date - type: \(type(of: dateValue))")
        return
    }
    
    // Parse all fields with proper null handling
    let steps = dataDict["steps"] as? Int
    let distance = dataDict["distance"] as? Double
    let calories = dataDict["calories"] as? Int
    let activeMinutes = dataDict["activeMinutes"] as? Int
    let heartRateAvg = dataDict["heartRateAvg"] as? Int
    let heartRateMin = dataDict["heartRateMin"] as? Int
    let heartRateMax = dataDict["heartRateMax"] as? Int
    
    // Handle sleep duration (can be Int or Int64)
    let sleepDuration: Int64?
    if let sleep = dataDict["sleepDuration"] as? Int64 {
        sleepDuration = sleep
    } else if let sleep = dataDict["sleepDuration"] as? Int {
        sleepDuration = Int64(sleep)
    } else {
        sleepDuration = nil
    }
    
    // NEW FIELDS - Add these
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
    
    // Create HealthSummary object
    let summary = HealthSummary(
        date: Date(timeIntervalSince1970: TimeInterval(date) / 1000.0),
        steps: steps,
        distance: distance,
        calories: calories,
        activeMinutes: activeMinutes,
        heartRateAvg: heartRateAvg,
        heartRateMin: heartRateMin,
        heartRateMax: heartRateMax,
        sleepDuration: sleepDuration,
        floorsClimbed: floorsClimbed,
        weight: weight,
        bloodPressureSystolic: bloodPressureSystolic,
        bloodPressureDiastolic: bloodPressureDiastolic,
        oxygenSaturation: oxygenSaturation,
        restingHeartRate: restingHeartRate,
        vo2Max: vo2Max,
        bodyTemperature: bodyTemperature,
        bloodGlucose: bloodGlucose,
        hydration: hydration
    )
    
    // Update UI
    DispatchQueue.main.async {
        self.healthSummary = summary
        print("✅ Health summary updated successfully")
    }
}
```

## Update HealthSummary Model

The Mac client's `HealthSummary` model needs to include all new fields:

```swift
struct HealthSummary: Codable, Identifiable {
    let id = UUID()
    let date: Date
    
    // Existing fields
    let steps: Int?
    let distance: Double?
    let calories: Int?
    let activeMinutes: Int?
    let heartRateAvg: Int?
    let heartRateMin: Int?
    let heartRateMax: Int?
    let sleepDuration: Int64?
    
    // NEW FIELDS - Add these
    let floorsClimbed: Int?
    let weight: Double?
    let bloodPressureSystolic: Int?
    let bloodPressureDiastolic: Int?
    let oxygenSaturation: Double?
    let restingHeartRate: Int?
    let vo2Max: Double?
    let bodyTemperature: Double?
    let bloodGlucose: Double?
    let hydration: Double?
    
    enum CodingKeys: String, CodingKey {
        case date, steps, distance, calories, activeMinutes
        case heartRateAvg, heartRateMin, heartRateMax, sleepDuration
        case floorsClimbed, weight
        case bloodPressureSystolic, bloodPressureDiastolic
        case oxygenSaturation, restingHeartRate, vo2Max
        case bodyTemperature, bloodGlucose, hydration
    }
}
```

## Update UI to Display New Fields

Add views for the new metrics:

```swift
// Vitals Section
if let hrMin = summary.heartRateMin, let hrMax = summary.heartRateMax {
    HealthMetricRow(
        icon: "heart.fill",
        label: "Heart Rate Range",
        value: "\(hrMin)-\(hrMax) bpm"
    )
}

if let restingHR = summary.restingHeartRate {
    HealthMetricRow(
        icon: "heart",
        label: "Resting Heart Rate",
        value: "\(restingHR) bpm"
    )
}

if let systolic = summary.bloodPressureSystolic,
   let diastolic = summary.bloodPressureDiastolic {
    HealthMetricRow(
        icon: "waveform.path.ecg",
        label: "Blood Pressure",
        value: "\(systolic)/\(diastolic) mmHg"
    )
}

if let spo2 = summary.oxygenSaturation {
    HealthMetricRow(
        icon: "lungs.fill",
        label: "Blood Oxygen",
        value: String(format: "%.1f%%", spo2)
    )
}

// Body Metrics Section
if let weight = summary.weight {
    HealthMetricRow(
        icon: "scalemass.fill",
        label: "Weight",
        value: String(format: "%.1f kg", weight)
    )
}

if let temp = summary.bodyTemperature {
    HealthMetricRow(
        icon: "thermometer",
        label: "Body Temperature",
        value: String(format: "%.1f°C", temp)
    )
}

if let glucose = summary.bloodGlucose {
    HealthMetricRow(
        icon: "drop.fill",
        label: "Blood Glucose",
        value: String(format: "%.1f mmol/L", glucose)
    )
}

if let vo2 = summary.vo2Max {
    HealthMetricRow(
        icon: "figure.run",
        label: "VO2 Max",
        value: String(format: "%.1f mL/kg/min", vo2)
    )
}

// Activity Section
if let floors = summary.floorsClimbed {
    HealthMetricRow(
        icon: "figure.stairs",
        label: "Floors Climbed",
        value: "\(floors)"
    )
}

if let hydration = summary.hydration {
    HealthMetricRow(
        icon: "drop.fill",
        label: "Hydration",
        value: String(format: "%.2f L", hydration)
    )
}
```

## Testing Steps

1. **Add debug logging** to see what's being received
2. **Test with simple data** first
3. **Verify all fields** are parsed correctly
4. **Update UI** to show new metrics
5. **Test null handling** - ensure nil values don't crash

## Expected Behavior After Fix

✅ Mac receives health data from Android
✅ Mac successfully parses the `data` object
✅ All 19 health fields are extracted
✅ Null values are handled properly
✅ UI displays all available metrics
✅ No parsing errors in logs

## Android Side is Working

The Android side is correctly:
- ✅ Fetching data from Health Connect
- ✅ Creating JSON with all 19 fields
- ✅ Sending via WebSocket
- ✅ Using proper null handling

**The fix is needed only on the Mac client side.**
