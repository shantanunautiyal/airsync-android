Call Activities - Implementation Guide

Status: Draft — this document explains a robust, privacy-aware implementation for collecting call activities (incoming, outgoing, missed), matching numbers to contacts, and sending call events and call-log updates to a companion Mac app. It covers required permissions, manifest entries, runtime permission flow, how to observe call events in modern Android, data formats and networking, and implementation guidance with code snippets.

Table of contents
- Goals and scope
- High-level architecture
- Requirements (permissions, manifest & runtime policies)
- Data model and network protocol
- Detecting call activity (incoming, outgoing, ringing, connected, ended)
  - Recommended modern approach
  - Backward compatibility notes
  - Handling call logs and de-duplication
- Contact matching and normalization
- Implementation: services, observers, receivers and UI flow
- File & class map (suggested files to add)
- Gradle / dependency reminders
- Testing, debugging and QA checklist
- Edge cases, privacy and security considerations
- Appendix: code snippets


Goals and scope

This feature captures call activity metadata (not call audio) and synchronizes it to a paired Mac app so the user can see calls made and received by their Android device. We’ll capture:
- Timestamps (start/end)
- Phone number(s)
- Direction: incoming / outgoing / missed
- Duration (if available)
- Contact display name (matched locally)
- Call type (voice) and SIM slot (if multi-SIM)
- A unique event id for de-duplication and reconciliation

We explicitly avoid capturing call audio (very sensitive; platform restrictions and user expectations). If you need live call audio streaming in future, consult Telephony/VoIP rules and user consent — this is out of scope here.

High-level architecture

- CallMonitorService (foreground or bound service) monitors Telephony state and observes the CallLog content provider for new/changed rows.
- A ContentObserver on CallLog.Calls watches for insert/update events and triggers synchronization logic.
- Telephony callbacks (TelephonyManager/TelephonyCallback) provide real-time state changes (RINGING/IDLE/OFFHOOK) so we can report immediate events (incoming ring, answered, ended) while call-log arrives later.
- ContactLookup helper maps numbers to display names using ContactsContract.PhoneLookup and caches results.
- SyncClient (WebSocket or HTTP) transmits call events to the Mac app using authenticated TLS connection. WebSocket recommended for real-time events; HTTP can be used for periodic sync.
- A local database (Room or simple SQLite) optionally stores recent events and a last-synced watermark to avoid duplicates.


Requirements (permissions, manifest & runtime)

Manifest entries (app/src/main/AndroidManifest.xml):

- Permissions (add these as needed):

```xml
<!-- Add the following to app/src/main/AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.READ_CALL_LOG" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.READ_CONTACTS" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" /> <!-- if re-registering observers after reboot -->
```

Notes on permissions and Google Play policy:
- READ_CALL_LOG is a sensitive permission and Google Play restricts its use. If publishing to Play Store, you must justify usage with a policy-compliant declaration and may need to use the new APIs (CallLogProvider) or request an alternative flow. If the app is not distributed through Play, READ_CALL_LOG still requires runtime consent.
- For Android 10+ (API 29+) background access has additional constraints. Use foreground service for continuous monitoring.

Runtime permission flow:
- Prompt the user at runtime for READ_CALL_LOG and READ_CONTACTS, using ActivityCompat.requestPermissions.
- Explain why these are needed (detect calls and match contacts) and provide a settings fallback when the user denies permanently.
- For READ_PHONE_STATE: request at runtime on API levels that require it to read subscription info.

Privacy note:
- Keep the scope minimal. Only send fields required for the Mac app UI. Allow user opt-out and an on/off toggle in settings.


Data model & network protocol

A simple JSON event schema for real-time events (over WebSocket) and for call-log sync (HTTP POST) is recommended. Use wss:// for encryption and attach an Authorization token (Bearer) or custom pairing token.

Real-time event JSON (sent when Telephony reports ring/connect/end):
{
  "type": "call_event",
  "eventId": "uuid-v4",
  "deviceId": "<device-uuid-or-paired-id>",
  "timestamp": 1680000000000,
  "direction": "incoming" | "outgoing",
  "state": "ringing" | "offhook" | "idle" | "missed",
  "number": "+15551234567",
  "normalizedNumber": "+15551234567",
  "contactName": "Alice Smith", // optional if available
  "simSlot": 1, // if available
  "callLogId": 12345 // optional if known from CallLog
}

Call-log sync JSON (batch, POST /api/calllogs):
{
  "deviceId": "...",
  "events": [ { ...callEvent... }, { ... } ],
  "lastSyncTimestamp": 1680000000000
}

Server-side expectations (Mac app):
- Accept real-time WebSocket events for immediate UI popups and notifications.
- Accept batch POSTs to reconcile missed events (call-log sometimes arrives late on device).
- Use eventId + callLogId + timestamp to deduplicate.

Transport choices
- WebSocket (wss://): real-time push for immediately reporting ring/answer/end. Uses OkHttp WebSocket client for Android.
- HTTP POST (https://): periodic reconciliation with full call-log. Useful when the Mac app was offline.

Retry & backoff
- Implement exponential backoff for reconnect attempts with jitter. Persist queued events locally (bounded) to avoid message loss during transient network outages.


Detecting call activity

Recommended modern approach (Android 12+/API 31+):
- Use TelephonyManager.registerTelephonyCallback(Executor, TelephonyCallback) with TelephonyCallback.CallStateListener to get call state updates. This is the preferred modern API.
- For real-time accuracy, also watch CallLog.Calls content provider via ContentObserver — the call log row may arrive a few seconds after the call ends; use it to get duration and callLogId.

Example call states mapping:
- Telephony state RINGING -> incoming ringing event
- Telephony state OFFHOOK -> call answered / connected
- Telephony state IDLE -> call ended or missed (infer missed if there was a ringing event and no OFFHOOK)

Backward compatibility (older APIs):
- For API < 31, use PhoneStateListener and override onCallStateChanged.
- To detect outgoing calls on older devices, register a BroadcastReceiver for ACTION_NEW_OUTGOING_CALL. Note: this broadcast is deprecated on newer OS versions.

Important note on outgoing detection on modern Android:
- ACTION_NEW_OUTGOING_CALL may not be delivered to non-foreground apps on recent Android OS versions. A reliable approach is to watch CallLog changes and correlate with Telephony state for direction inference.

CallLog ContentObserver
- Register a ContentObserver on CallLog.Calls.CONTENT_URI to watch for INSERT/UPDATE events.
- On change, query the CallLog for entries with date >= lastSyncedTimestamp or with new _id. Use projection columns: _ID, NUMBER, DATE, DURATION, TYPE, NEW, NUMBER_PRESENTATION, SUBSCRIPTION_ID.
- Map CallLog.TYPE to direction: INCOMING_TYPE, OUTGOING_TYPE, MISSED_TYPE.

De-duplication strategy
- For each event send, include a generated eventId (UUID) and, when available, the callLogId from CallLog._ID. Use both on server to deduplicate.
- Maintain a local recentEvent cache (small LRU) to avoid re-sending identical real-time events when the call-log sync runs.


Contact matching and normalization

Contact lookup
- Use ContactsContract.PhoneLookup to find a contact record for a given phone number:
  Uri uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number));
  query for DISPLAY_NAME, PHOTO_URI, CONTACT_ID
- Cache mapping results in-memory and optionally persist in a small SQLite/Room table keyed by E.164 normalized number.

Number normalization
- Normalize phone numbers to E.164 where possible using libphonenumber (Google's libphonenumber) or Android's PhoneNumberUtils.formatNumberToE164 (API 21+). libphonenumber is more reliable across locales.
- Normalize before lookup so lookups match stored contact formats.

Matching heuristics
- If exact match fails, try matching by suffix (last 7-9 digits) to account for numbers without country codes.
- Cache failure results (negative cache) briefly to avoid repeated expensive lookups.


Implementation: services, observers and UI flow

Contract (small):
- Inputs: system call state changes and call-log content provider changes
- Outputs: real-time call event JSON over WebSocket and periodic batch POST of call-log events
- Error modes: permission denied, background restrictions, Play policy restrictions

Suggested components
1) CallMonitorService (foreground when active for continuous monitoring)
  - Registers Telephony callback / PhoneStateListener
  - Registers ContentObserver on CallLog.Calls
  - Keeps a small in-memory state machine for current call (ringing -> offhook -> idle)
  - Emits real-time events (SyncClient.sendEvent)
  - Persists events into local DB if network unavailable
  - Exposes start/stop public API (via Intent actions)

2) CallLogObserver : ContentObserver
  - OnChange -> query for new call logs since last sync -> build call event objects -> send batch sync

3) ContactLookupHelper
  - Exposes suspend fun findContactForNumber(number): ContactInfo
  - Caches results and handles normalization

4) SyncClient (WebSocket + HTTP fallback)
  - WebSocket connection for real-time events
  - HTTP client to POST call-log batch
  - Authenticated via pairing token; stores connection & reconnection logic

5) PermissionHelper
  - Centralized runtime permission checks for READ_CALL_LOG, READ_CONTACTS, READ_PHONE_STATE
  - Guides UI to explain permission rationale

6) UI (Settings / Mirroring-like control)
  - Toggle to enable/disable call sync
  - Button to re-sync call-log history
  - Permission status indicators


File & class map (suggested)
- app/src/main/java/com/sameerasw/airsync/calls/CallMonitorService.kt
- app/src/main/java/com/sameerasw/airsync/calls/CallLogObserver.kt
- app/src/main/java/com/sameerasw/airsync/calls/ContactLookupHelper.kt
- app/src/main/java/com/sameerasw/airsync/calls/SyncClient.kt
- app/src/main/java/com/sameerasw/airsync/calls/PermissionHelper.kt
- app/src/main/java/com/sameerasw/airsync/calls/CallEvent.kt (data class)
- app/src/main/java/com/sameerasw/airsync/calls/CallRepository.kt (optional Room DB)
- app/src/main/res/xml/filepaths.xml (if you store temporary exports)

Manifest additions

```xml
<!-- ...existing manifest... -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.READ_CALL_LOG" />
<uses-permission android:name="android.permission.READ_CONTACTS" />
<uses-permission android:name="android.permission.READ_PHONE_STATE" />
<uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />

<application>
    <!-- ...existing app elements ... -->
    <service android:name=".calls.CallMonitorService" android:exported="false" />
    <receiver android:name=".calls.BootCompletedReceiver">
        <intent-filter>
            <action android:name="android.intent.action.BOOT_COMPLETED" />
        </intent-filter>
    </receiver>
</application>
```

Gradle / dependencies

- implementation("com.squareup.okhttp3:okhttp:4.11.0")
- implementation("com.googlecode.libphonenumber:libphonenumber:8.13.17") // number normalization
- implementation("androidx.room:room-runtime:2.5.1") and annotationProcessor/kapt if you use Room for local event storage

Security: use TLS (wss/https) and pairing tokens. Avoid sending raw device identifiers; use an app-generated deviceId and allow user to revoke pairing.


Testing, debugging and QA checklist

- Permissions
  - Deny READ_CALL_LOG: verify graceful degraded behavior and clear user messaging.
  - Revoke permission after granting: verify the service stops and appropriate UI updates.
- Real-time vs call-log sync
  - Simulate incoming calls (emulator supports GSM commands) and verify Telephony callbacks and events sent.
  - Verify call-log observer picks up the entry and that the batch sync reconciles durations.
- Contact matching
  - Test numbers with various formats (+country, local numeric) and verify matching and fallback heuristics.
- Offline & sync
  - Turn off network and create calls, then reconnect and verify queued events are delivered and deduplicated.
- Multi-SIM
  - Test calls on devices/devices with dual-SIM and verify subscriptionId / simSlot recorded.
- Performance
  - Ensure ContentObserver queries are efficient (query only recently added rows) and run off the main thread.


Edge cases, privacy and security considerations

- Play Store policy for CALL_LOG: if you plan to publish on Play, read the policy and possibly implement alternative features to avoid sensitive permissions.
- Provide a clear privacy dialog and settings entry for the user to disable call sync and delete synced data from the server.
- Limit the retention of call data and offer a manual purge option.
- Avoid storing or transmitting audio or full contact raw details beyond name and URI.
- Respect Do Not Disturb and user privacy: do not auto-answer or interact with calls.


Appendix: Code snippets

Data classes

```kotlin
data class CallEvent(
    val eventId: String = UUID.randomUUID().toString(),
    val deviceId: String,
    val timestamp: Long,
    val direction: String, // "incoming" | "outgoing"
    val state: String, // "ringing" | "offhook" | "idle" | "missed"
    val number: String,
    val normalizedNumber: String? = null,
    val contactName: String? = null,
    val simSlot: Int? = null,
    val callLogId: Long? = null,
    val durationSec: Long? = null
)
```

PermissionHelper (runtime check example)

```kotlin
object PermissionHelper {
    val CALL_PERMISSIONS = arrayOf(
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.READ_PHONE_STATE
    )

    fun hasCallPermissions(context: Context): Boolean {
        return CALL_PERMISSIONS.all { perm ->
            ContextCompat.checkSelfPermission(context, perm) == PackageManager.PERMISSION_GRANTED
        }
    }
}
```

Registering a Telephony callback (modern API)

```kotlin
class CallStateListener(private val onState: (state: Int, incomingNumber: String?) -> Unit) : TelephonyCallback(), TelephonyCallback.CallStateListener {
    override fun onCallStateChanged(state: Int, phoneNumber: String?) {
        onState(state, phoneNumber)
    }
}

// in service
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    telephonyManager.registerTelephonyCallback(executor, callStateListener)
} else {
    telephonyManager.listen(phoneStateListener, PhoneStateListener.LISTEN_CALL_STATE)
}
```

ContentObserver for CallLog

```kotlin
class CallLogObserver(handler: Handler, private val onNewLogs: () -> Unit) : ContentObserver(handler) {
    override fun onChange(selfChange: Boolean) {
        super.onChange(selfChange)
        onNewLogs()
    }
}

// registration in service
contentResolver.registerContentObserver(
    CallLog.Calls.CONTENT_URI,
    true,
    callLogObserver
)
```

Querying recent call logs

```kotlin
fun queryRecentCallLogs(context: Context, sinceMillis: Long): List<CallEvent> {
    val resolver = context.contentResolver
    val projection = arrayOf(
        CallLog.Calls._ID,
        CallLog.Calls.NUMBER,
        CallLog.Calls.TYPE,
        CallLog.Calls.DATE,
        CallLog.Calls.DURATION,
        CallLog.Calls.NEW,
        CallLog.Calls.SUBSCRIPTION_ID
    )
    val selection = "${CallLog.Calls.DATE} >= ?"
    val cursor = resolver.query(
        CallLog.Calls.CONTENT_URI,
        projection,
        selection,
        arrayOf(sinceMillis.toString()),
        "${CallLog.Calls.DATE} ASC"
    ) ?: return emptyList()

    val results = mutableListOf<CallEvent>()
    cursor.use { c ->
        while (c.moveToNext()) {
            val id = c.getLong(0)
            val number = c.getString(1) ?: ""
            val type = c.getInt(2)
            val date = c.getLong(3)
            val duration = c.getLong(4)
            val sim = c.getIntOrNull(6)
            val direction = when (type) {
                CallLog.Calls.INCOMING_TYPE -> "incoming"
                CallLog.Calls.OUTGOING_TYPE -> "outgoing"
                CallLog.Calls.MISSED_TYPE -> "missed"
                else -> "unknown"
            }
            results.add(
                CallEvent(
                    deviceId = getDeviceId(context),
                    timestamp = date,
                    direction = direction,
                    state = if (direction == "missed") "missed" else "idle",
                    number = number,
                    callLogId = id,
                    durationSec = duration,
                    simSlot = sim
                )
            )
        }
    }
    return results
}
```

Contact lookup helper (simple)

```kotlin
suspend fun findContactName(context: Context, number: String): String? = withContext(Dispatchers.IO) {
    try {
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                return@withContext cursor.getString(0)
            }
        }
    } catch (e: SecurityException) {
        // permission missing
    }
    return@withContext null
}
```

SyncClient example (OkHttp WebSocket event send)

```kotlin
class SyncClient(private val serverUrl: String, private val token: String) {
    private val client = OkHttpClient.Builder().build()
    private var webSocket: WebSocket? = null

    fun connect() {
        val req = Request.Builder()
            .url(serverUrl)
            .header("Authorization", "Bearer $token")
            .build()
        webSocket = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                // send hello / pairing
            }
            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                // schedule reconnect
            }
        })
    }

    fun sendEvent(e: CallEvent) {
        val json = Gson().toJson(e)
        webSocket?.send(json)
    }

    fun sendBatch(events: List<CallEvent>) {
        val payload = mapOf("deviceId" to getDeviceId(), "events" to events)
        val json = Gson().toJson(payload)
        // use POST when not connected
        webSocket?.send(json) ?: run {
            // fallback to HTTP POST
        }
    }
}
```

Security & pairing

- Use an initial pairing flow to exchange a long-lived token between the Mac app and phone app. Store token in EncryptedSharedPreferences.
- Use this token for all subsequent calls (Authorization header). Allow token revocation from the phone UI.


Requirements coverage checklist

- Detect incoming/outgoing/missed calls (real-time): documented & modern Telephony API + PhoneStateListener fallback
- Read CallLog for durations and callLogId: documented with ContentObserver and query
- Match contacts: ContactsContract.PhoneLookup + libphonenumber normalization documented
- Send events to Mac: WebSocket real-time + HTTP batch reconciliation documented
- Runtime permissions & manifesto: done (README entries and examples)


Next steps / scaffolding offer

I can scaffold these source files in the project and run a quick static check (lint / compile) for obvious issues. I can also implement a minimal local server (node or small Kotlin app) that acts as the Mac side to demonstrate end-to-end sync.

Which part would you like implemented first? I recommend starting with:
1) `CallEvent` data model, `PermissionHelper`, and `SyncClient` WebSocket wiring, and
2) `CallMonitorService` that registers Telephony callback and ContentObserver and emits basic events.

If you want I’ll create the scaffolding Kotlin files next and run a static error check.
