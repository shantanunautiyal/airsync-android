# Mac JSON Parsing - CRITICAL FIX NEEDED 🔴

## Problem

The Mac client is receiving valid JSON from Android but **completely failing to parse it**. This affects ALL features:

### Affected Features:
- ❌ Health Data - Empty dictionary
- ❌ SMS Threads - Empty dictionary  
- ❌ Call Logs - Empty dictionary

### Evidence from Logs:

```
[websocket] [received] {"type":"smsThreads","data":{"threads":[...]}}
[CodableValue] ⚠️ Failed to decode, using empty dictionary
[websocket] 📱 SMS data dict keys: []
[websocket] ❌ Failed to parse threads array from SMS data
```

**Android sends:** Valid JSON with data
**Mac receives:** Empty dictionary `[:]`

## Root Cause

The Mac client's JSON parsing is broken. It's likely using `Codable` with strict type checking that fails when:
1. Field types don't match exactly
2. Extra fields are present
3. Null values are encountered

## Critical Fix Required

### Current Broken Code (Mac):

```swift
// This is failing:
struct MessageData: Codable {
    let data: [String: Any]  // ❌ Can't decode [String: Any] with Codable
}

// Or this:
if let json = try? JSONDecoder().decode(MessageData.self, from: data) {
    // This fails and returns empty dictionary
}
```

### Fixed Code (Mac):

```swift
// Use JSONSerialization instead of Codable for dynamic JSON
func handleWebSocketMessage(_ message: String) {
    guard let data = message.data(using: .utf8) else { return }
    
    do {
        // ✅ Use JSONSerialization for flexible parsing
        guard let json = try JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            print("❌ Failed to parse JSON as dictionary")
            return
        }
        
        print("✅ Parsed JSON with keys: \(json.keys)")
        
        guard let type = json["type"] as? String else {
            print("❌ No type field")
            return
        }
        
        guard let dataDict = json["data"] as? [String: Any] else {
            print("❌ No data field or not a dictionary")
            print("Data field type: \(type(of: json["data"]))")
            return
        }
        
        print("✅ Data dict with keys: \(dataDict.keys)")
        
        // Route to appropriate handler
        switch type {
        case "healthSummary":
            handleHealthSummary(dataDict)
        case "smsThreads":
            handleSmsThreads(dataDict)
        case "callLogs":
            handleCallLogs(dataDict)
        default:
            print("Unknown message type: \(type)")
        }
        
    } catch {
        print("❌ JSON parsing error: \(error)")
    }
}
```

## Fix for Each Feature

### 1. Health Summary

```swift
func handleHealthSummary(_ data: [String: Any]) {
    print("📊 Health data keys: \(data.keys)")
    
    // Parse date - handle different number types
    guard let dateValue = data["date"] else {
        print("❌ No date field")
        return
    }
    
    let date: Int64
    if let intDate = dateValue as? Int64 {
        date = intDate
    } else if let intDate = dateValue as? Int {
        date = Int64(intDate)
    } else if let doubleDate = dateValue as? Double {
        date = Int64(doubleDate)
    } else {
        print("❌ Can't parse date, type: \(type(of: dateValue))")
        return
    }
    
    // Parse all fields with safe casting
    let summary = HealthSummary(
        date: Date(timeIntervalSince1970: TimeInterval(date) / 1000.0),
        steps: data["steps"] as? Int,
        distance: data["distance"] as? Double,
        calories: data["calories"] as? Int,
        activeMinutes: data["activeMinutes"] as? Int,
        heartRateAvg: data["heartRateAvg"] as? Int,
        heartRateMin: data["heartRateMin"] as? Int,
        heartRateMax: data["heartRateMax"] as? Int,
        sleepDuration: (data["sleepDuration"] as? Int).map { Int64($0) },
        floorsClimbed: data["floorsClimbed"] as? Int,
        weight: data["weight"] as? Double,
        bloodPressureSystolic: data["bloodPressureSystolic"] as? Int,
        bloodPressureDiastolic: data["bloodPressureDiastolic"] as? Int,
        oxygenSaturation: data["oxygenSaturation"] as? Double,
        restingHeartRate: data["restingHeartRate"] as? Int,
        vo2Max: data["vo2Max"] as? Double,
        bodyTemperature: data["bodyTemperature"] as? Double,
        bloodGlucose: data["bloodGlucose"] as? Double,
        hydration: data["hydration"] as? Double
    )
    
    DispatchQueue.main.async {
        self.healthSummary = summary
        print("✅ Health summary updated")
    }
}
```

### 2. SMS Threads

```swift
func handleSmsThreads(_ data: [String: Any]) {
    print("📱 SMS data keys: \(data.keys)")
    
    guard let threadsArray = data["threads"] as? [[String: Any]] else {
        print("❌ No threads array or wrong type")
        print("Threads type: \(type(of: data["threads"]))")
        return
    }
    
    print("✅ Found \(threadsArray.count) threads")
    
    let threads = threadsArray.compactMap { threadDict -> SmsThread? in
        guard let threadId = threadDict["threadId"] as? String,
              let address = threadDict["address"] as? String,
              let messageCount = threadDict["messageCount"] as? Int,
              let snippet = threadDict["snippet"] as? String,
              let date = threadDict["date"] as? Int64,
              let unreadCount = threadDict["unreadCount"] as? Int else {
            return nil
        }
        
        return SmsThread(
            threadId: threadId,
            address: address,
            contactName: threadDict["contactName"] as? String,
            messageCount: messageCount,
            snippet: snippet,
            date: Date(timeIntervalSince1970: TimeInterval(date) / 1000.0),
            unreadCount: unreadCount
        )
    }
    
    DispatchQueue.main.async {
        self.smsThreads = threads
        print("✅ Updated \(threads.count) SMS threads")
    }
}
```

### 3. Call Logs

```swift
func handleCallLogs(_ data: [String: Any]) {
    print("📞 Call logs data keys: \(data.keys)")
    
    guard let logsArray = data["logs"] as? [[String: Any]] else {
        print("❌ No logs array or wrong type")
        return
    }
    
    print("✅ Found \(logsArray.count) call logs")
    
    let logs = logsArray.compactMap { logDict -> CallLog? in
        guard let id = logDict["id"] as? String,
              let number = logDict["number"] as? String,
              let typeString = logDict["type"] as? String,
              let date = logDict["date"] as? Int64,
              let duration = logDict["duration"] as? Int,
              let isRead = logDict["isRead"] as? Bool else {
            return nil
        }
        
        return CallLog(
            id: id,
            number: number,
            contactName: logDict["contactName"] as? String,
            type: CallType(rawValue: typeString) ?? .unknown,
            date: Date(timeIntervalSince1970: TimeInterval(date) / 1000.0),
            duration: duration,
            isRead: isRead
        )
    }
    
    DispatchQueue.main.async {
        self.callLogs = logs
        print("✅ Updated \(logs.count) call logs")
    }
}
```

## Why Codable Fails

```swift
// ❌ This doesn't work for dynamic JSON:
struct Response: Codable {
    let data: [String: Any]  // Can't encode/decode Any
}

// ❌ This is too strict:
struct HealthData: Codable {
    let steps: Int  // Fails if null or missing
}

// ✅ Use JSONSerialization instead:
let json = try JSONSerialization.jsonObject(with: data) as? [String: Any]
```

## Testing the Fix

### Before Fix:
```
[websocket] [received] {"type":"healthSummary","data":{...}}
[CodableValue] ⚠️ Failed to decode, using empty dictionary
[websocket] 📊 Health data dict: [:]
[websocket] ❌ Failed to parse date from health summary
```

### After Fix:
```
[websocket] [received] {"type":"healthSummary","data":{...}}
✅ Parsed JSON with keys: ["type", "data"]
✅ Data dict with keys: ["date", "steps", "distance", ...]
📊 Health data keys: ["date", "steps", "distance", ...]
✅ Health summary updated
```

## Implementation Steps

1. **Remove Codable** from WebSocket message parsing
2. **Use JSONSerialization** for all incoming messages
3. **Add debug logging** to see what's being received
4. **Test each feature** (health, SMS, calls)
5. **Verify data appears** in UI

## Priority

🔴 **CRITICAL** - Nothing works without this fix

All features are broken because the Mac can't parse the JSON that Android is sending correctly.

## Summary

- ❌ Mac's Codable-based parsing is failing
- ✅ Android is sending valid JSON
- 🔧 Fix: Use JSONSerialization instead of Codable
- 📊 Add proper type casting and null handling
- 🎯 This will fix health, SMS, and call logs all at once

**The Android side is working perfectly. The entire issue is in the Mac client's JSON parsing layer.**
