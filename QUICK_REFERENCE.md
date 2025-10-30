# AirSync - Quick Reference Card

## 🚀 WebSocket Commands Cheat Sheet

### Remote Control
```json
// Tap
{"type":"inputEvent","data":{"inputType":"tap","x":500,"y":800}}

// Swipe
{"type":"inputEvent","data":{"inputType":"swipe","startX":500,"startY":1000,"endX":500,"endY":300}}

// Back button
{"type":"inputEvent","data":{"inputType":"back"}}
```

### SMS/Messaging
```json
// Get threads
{"type":"requestSmsThreads","data":{"limit":50}}

// Get messages
{"type":"requestSmsMessages","data":{"threadId":"123","limit":100}}

// Send SMS
{"type":"sendSms","data":{"address":"+1234567890","message":"Hello!"}}
```

### Call Logs
```json
// Get call logs
{"type":"requestCallLogs","data":{"limit":100}}

// Get recent calls
{"type":"requestCallLogs","data":{"since":1234567890000}}
```

### Health Data
```json
// Get summary
{"type":"requestHealthSummary"}

// Get detailed data
{"type":"requestHealthData","data":{"hours":24}}
```

## 📱 Permissions Checklist

### Required Permissions
- [ ] READ_SMS
- [ ] SEND_SMS
- [ ] RECEIVE_SMS
- [ ] READ_CALL_LOG
- [ ] READ_PHONE_STATE
- [ ] ANSWER_PHONE_CALLS
- [ ] READ_CONTACTS
- [ ] Accessibility Service
- [ ] Health Connect (10 permissions)

## 🔔 Real-time Notifications

### Automatic Events (Android → Mac)

**New SMS:**
```json
{"type":"smsReceived","data":{...}}
```

**Call Ringing:**
```json
{"type":"callNotification","data":{"state":"ringing",...}}
```

**Call Active:**
```json
{"type":"callNotification","data":{"state":"active",...}}
```

**Call Ended:**
```json
{"type":"callNotification","data":{"state":"disconnected",...}}
```

## 📊 Response Types

### Success Response
```json
{"type":"smsSendResponse","data":{"success":true,"message":"..."}}
```

### Error Response
```json
{"type":"smsSendResponse","data":{"success":false,"message":"..."}}
```

## 🧪 Testing Commands

### Test SMS
```bash
# Request threads
echo '{"type":"requestSmsThreads","data":{"limit":10}}' | websocat ws://ANDROID_IP:PORT

# Send SMS
echo '{"type":"sendSms","data":{"address":"+1234567890","message":"Test"}}' | websocat ws://ANDROID_IP:PORT
```

### Test Call Logs
```bash
echo '{"type":"requestCallLogs","data":{"limit":20}}' | websocat ws://ANDROID_IP:PORT
```

### Test Health
```bash
echo '{"type":"requestHealthSummary"}' | websocat ws://ANDROID_IP:PORT
```

### Monitor Logs
```bash
adb logcat -s PhoneStateReceiver SmsReceiver WebSocketMessageHandler
```

## 📁 File Structure

```
app/src/main/java/com/sameerasw/airsync/
├── models/
│   ├── CallLog.kt          (NEW)
│   ├── SmsMessage.kt       (NEW)
│   └── HealthData.kt       (NEW)
├── receiver/
│   ├── PhoneStateReceiver.kt  (NEW)
│   └── SmsReceiver.kt         (NEW)
├── service/
│   └── InputAccessibilityService.kt  (UPDATED)
└── utils/
    ├── CallLogUtil.kt         (NEW)
    ├── SmsUtil.kt             (NEW)
    ├── HealthConnectUtil.kt   (NEW)
    ├── JsonUtil.kt            (UPDATED)
    └── WebSocketMessageHandler.kt  (UPDATED)
```

## 🎯 Common Use Cases

### 1. Send SMS from Mac
```swift
let message: [String: Any] = [
    "type": "sendSms",
    "data": [
        "address": "+1234567890",
        "message": "Hello from Mac!"
    ]
]
webSocket.send(message)
```

### 2. Show Incoming Call Notification
```swift
func handleCallNotification(_ data: [String: Any]) {
    if data["state"] as? String == "ringing" {
        let name = data["contactName"] as? String ?? data["number"] as? String ?? "Unknown"
        showNotification(title: "Incoming Call", body: name)
    }
}
```

### 3. Display Health Summary
```swift
func handleHealthSummary(_ data: [String: Any]) {
    stepsLabel.text = "\(data["steps"] ?? 0) steps"
    distanceLabel.text = "\(data["distance"] ?? 0) km"
    caloriesLabel.text = "\(data["calories"] ?? 0) kcal"
}
```

### 4. List SMS Threads
```swift
func handleSmsThreads(_ data: [String: Any]) {
    guard let threads = data["threads"] as? [[String: Any]] else { return }
    tableView.reloadData()
}
```

## 🔧 Troubleshooting

### SMS Not Working
1. Check READ_SMS permission granted
2. Check SEND_SMS permission granted
3. Verify SmsReceiver registered in manifest
4. Check logs: `adb logcat -s SmsReceiver`

### Calls Not Detected
1. Check READ_PHONE_STATE permission granted
2. Verify PhoneStateReceiver registered
3. Check logs: `adb logcat -s PhoneStateReceiver`

### Health Data Empty
1. Check Health Connect app installed
2. Check health permissions granted
3. Verify data exists in Health Connect
4. Check logs: `adb logcat -s HealthConnectUtil`

### Remote Control Not Working
1. Check Accessibility Service enabled
2. Go to Settings → Accessibility → AirSync
3. Toggle ON
4. Check logs: `adb logcat -s InputAccessibilityService`

## 📞 Quick Links

- **Full Documentation:** NEW_FEATURES_IMPLEMENTATION.md
- **Mac Integration:** MAC_INTEGRATION_GUIDE.md
- **Testing Guide:** TEST_COMMANDS.md
- **Architecture:** ARCHITECTURE.md

## 💡 Pro Tips

1. **Batch Requests:** Request multiple threads/logs at once
2. **Caching:** Cache contact names on Mac side
3. **Pagination:** Use limit parameter for large datasets
4. **Real-time:** Keep WebSocket connected for live updates
5. **Error Handling:** Always check success field in responses

## 🎨 UI Components Needed (Mac)

### Messaging
- Thread list view
- Message detail view
- Compose message view
- Contact picker

### Calls
- Call history list
- Call detail view
- Incoming call notification
- Call action buttons

### Health
- Dashboard with cards
- Chart views for trends
- Goal tracking
- Data source indicators

### Remote Control
- Video view with touch handling
- Navigation buttons
- Quality settings panel
- Connection status

## ⚡ Performance Tips

### For SMS
- Limit thread queries to 50
- Limit message queries to 100
- Cache contact names
- Use pagination

### For Call Logs
- Query with timestamp filter
- Limit to recent calls (100)
- Cache contact names

### For Health Data
- Request summary instead of raw data
- Limit hours parameter (24-48)
- Cache daily summaries
- Update periodically, not continuously

## 🔐 Security Best Practices

1. **Permissions:** Request only when needed
2. **Data:** Don't store sensitive data unnecessarily
3. **Transmission:** Use secure WebSocket (WSS) in production
4. **Validation:** Validate all input from Mac
5. **Privacy:** Inform users what data is accessed

## 📊 Data Limits

- SMS Threads: Recommended limit 50
- SMS Messages: Recommended limit 100
- Call Logs: Recommended limit 100
- Health Data: Recommended 24-48 hours

## 🎯 Feature Status

| Feature | Android | Mac | Status |
|---------|---------|-----|--------|
| Remote Control | ✅ | ⏳ | Ready for Mac |
| SMS/Messaging | ✅ | ⏳ | Ready for Mac |
| Call Logs | ✅ | ⏳ | Ready for Mac |
| Live Calls | ✅ | ⏳ | Ready for Mac |
| Health Data | ✅ | ⏳ | Ready for Mac |

## 🚀 Ready to Build!

All Android features are implemented and tested. Start with MAC_INTEGRATION_GUIDE.md to build the Mac side!
