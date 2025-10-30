# Health Data macOS Decoding Issue

## Problem
The macOS app is failing to decode health data messages with this error:
```
typeMismatch(Swift.String, Swift.DecodingError.Context(codingPath: [CodingKeys(stringValue: "data", intValue: nil)], 
debugDescription: "Expected to decode String but found a dictionary instead.", underlyingError: nil))
```

## Root Cause
The Swift decoder on macOS is expecting the `data` field to be a String, but Android is correctly sending it as a dictionary/object.

## Android Side (Correct)
Android is sending:
```json
{
  "type": "healthSummary",
  "data": {
    "date": 1761761475421,
    "steps": 22690,
    "distance": 5.545112426519394,
    "calories": 1793,
    "activeMinutes": 0,
    "heartRateAvg": 0,
    "heartRateMin": 0,
    "heartRateMax": 0,
    "sleepDuration": 20
  }
}
```

## macOS Side Fix Needed
The Swift model for WebSocket messages likely has an incorrect definition. It should be:

```swift
struct HealthSummaryMessage: Codable {
    let type: String
    let data: HealthSummaryData
}

struct HealthSummaryData: Codable {
    let date: Int64
    let steps: Int
    let distance: Double
    let calories: Int
    let activeMinutes: Int
    let heartRateAvg: Int
    let heartRateMin: Int
    let heartRateMax: Int
    let sleepDuration: Int64
}
```

**NOT:**
```swift
struct HealthSummaryMessage: Codable {
    let type: String
    let data: String  // ❌ WRONG - this causes the error
}
```

## Changes Made on Android Side
1. Removed `null` values from JSON output - now defaults to 0 for missing values
2. Ensured all numeric fields are properly formatted

## Next Steps
The macOS Swift code needs to be updated to properly decode the `healthSummary` message type with a structured `data` field instead of expecting a String.
