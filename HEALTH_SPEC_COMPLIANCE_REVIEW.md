# Health Data Spec Compliance Review

## Current Implementation Analysis

### ✅ What's Working

1. **Message Handler Structure**
   - `handleRequestHealthSummary()` exists in WebSocketMessageHandler
   - `handleRequestHealthData()` exists for historical data
   - Both check for Health Connect availability and permissions

2. **Data Models**
   - `HealthSummary` model exists with correct fields
   - `HealthData` model exists for detailed records
   - Proper enum for `HealthDataType`

3. **JSON Serialization**
   - `createHealthSummaryJson()` exists in JsonUtil
   - `createHealthDataJson()` exists for detailed records

### ❌ Issues Found

#### 1. **Missing Date Parameter Support**
**Spec Requirement:**
```json
{"type": "requestHealthSummary", "data": {"date": 1735689600000}}
```

**Current Implementation:**
```kotlin
private fun handleRequestHealthSummary(context: Context) {
    // No date parameter - always returns today's data
    val summary = HealthConnectUtil.getTodaySummary(context)
}
```

**Problem:** Cannot request historical health data by date.

#### 2. **Incorrect Null Handling**
**Spec Requirement:**
- Use `null` or omit field for missing data
- **NEVER send 0 for heart rate if no data**

**Current Implementation:**
```kotlin
fun createHealthSummaryJson(summary: HealthSummary): String {
    val heartRateAvg = summary.heartRateAvg ?: 0  // ❌ WRONG - sends 0
    val heartRateMin = summary.heartRateMin ?: 0  // ❌ WRONG - sends 0
    val heartRateMax = summary.heartRateMax ?: 0  // ❌ WRONG - sends 0
}
```

**Problem:** Sends 0 instead of null for missing heart rate data.

#### 3. **Date Must Match Request**
**Spec Requirement:**
- Response date MUST match the requested date, not today's date

**Current Implementation:**
```kotlin
HealthSummary(
    date = System.currentTimeMillis(),  // ❌ Always today
    // ...
)
```

**Problem:** Always returns current timestamp instead of requested date.

#### 4. **No Local Storage**
**Spec Requirement (User Request):**
- Store health data locally
- Calendar selector to navigate previous days

**Current Implementation:**
- No local storage/caching
- No date navigation UI
- Always fetches from Health Connect

#### 5. **Missing Heart Rate Min/Max**
**Current Implementation:**
```kotlin
suspend fun getTodaySummary(context: Context): HealthSummary? {
    return HealthSummary(
        heartRateAvg = getLatestHeartRate(context),
        heartRateMin = null,  // ❌ Not implemented
        heartRateMax = null,  // ❌ Not implemented
    )
}
```

**Problem:** Min/Max heart rate not calculated.

## Required Changes

### 1. Update Message Handler
- Accept date parameter from Mac
- Pass date to health data fetcher
- Return data for requested date

### 2. Fix JSON Serialization
- Send `null` instead of 0 for missing data
- Properly handle optional fields

### 3. Add Date-Based Queries
- Modify HealthConnectUtil to accept date parameter
- Query data for specific date range

### 4. Implement Local Storage
- Cache health data by date
- Reduce Health Connect queries
- Enable offline viewing

### 5. Add Calendar UI
- Date picker in SimpleHealthScreen
- Navigate to previous days
- Show cached data

### 6. Calculate Heart Rate Min/Max
- Query all heart rate samples for the day
- Calculate min, max, and average

## Implementation Priority

1. **Critical (Spec Compliance)**
   - Fix null handling in JSON (sends wrong data to Mac)
   - Add date parameter support (Mac can't request historical data)
   - Return correct date in response

2. **High (User Request)**
   - Add local storage for health data
   - Add calendar date picker UI
   - Implement date-based queries

3. **Medium (Feature Complete)**
   - Calculate heart rate min/max
   - Cache optimization
