# airsync-android
Android app for AirSync 2.0 built with Kotlin Jetpack Compose

## Installation and troubleshooting
![Frame 1 Large](https://github.com/user-attachments/assets/24db7555-2518-44ee-83f3-6c7f84cb8b54)

## How to connect?
Use your built-in camera or Google lense or anything that can scan a QR code. I twill prompt you to open the app. Once authorized, The last device will be saved on the mobile for now for easier re-connection.


## ADB setup

### 📡 Step-by-Step Instructions

1. **Enable Developer Options on your Android**

    - Open `Settings` → `About phone`
    - Tap **Build number** 7 times until you see _“You are now a developer!”_

2. **Enable Wireless Debugging**

    - Go to `Settings` → `System` → `Developer options`
    - Scroll down and **enable** `Wireless debugging`

3. **Pair your device with your Mac**

    - Tap on `Wireless debugging`
    - Tap `Pair device with pairing code`
    - Note down the **IP address and port**, and the **6-digit pairing code**

    **Example:**
    ```
    IP:Port → 192.168.1.35:37099  
    Pairing Code → 123456
    ```

4. **On your Mac, open Terminal**

    Run the pairing command using the IP and port:

    ```bash
    adb pair 192.168.1.35:37099
    ```

    When prompted, enter the 6-digit pairing code:

    ```text
    Enter pairing code: 123456
    ```

    You should see:

    ```text
    Successfully paired to 192.168.1.35:37099 [guid=...]
    ```

5. **Finalize and connect**

    - Close the pairing dialog on Android
    - You'll return to the `Wireless debugging` screen
    - Use the **IP:port shown there** (usually `:5555`) in the **AirSync Mac app**
    - Enter it, then start ADB connection

✅ It should now connect successfully!

You can expand the console in the Mac app for more information. Once connected, the **Mirror** button will appear automatically.

---

🎉 **Done!** You’re now connected over Wi-Fi.


Definitely need someone to help with documentation ;)


---
---
---
# Documentation

## Communication

- Currently utilizing a web socket for the communication between the mac which is the server and the Android device as the client.

## Security

- Non encrypted local network usage for now via websocket protocol.
- Will be looking into device to device encryption in the future as it’s very possible, just that I’m needing to do more research on it.

## Authentication

- Currently done through a QR code scan which includes the websocket host IP, Port, Device name and the AirSync+ availability.
- Once scanned with any camera, Google lens based or any scanner that can handle Android intents, It will prompt the user to open the AirSync app.
- It will present the user with an authentication dialog to accept the connection and once done, it will start communication

## How it communicate?

- JSON objects via websocket, As an example, the initial authentication response from the Android will be it’s IP, Name and some other details.
- Then there will be another a bit heavy network call which gathers, encodes and sends all the app icons to the mac client for caching them for easier usage when displaying notifications. This will get improved over time.
- Along with that, the initial call with device status will be shared which includes battery %, volume and currently playing media and related info. This will be a less frequent polling but if there is a detected action like a change of media playback, it may update instantly.
- Then at last, the confirmation message will appear from the android to the mac after the setup is completed.
- Pretty much the same with the clipboard sync. It detects, syncs the text.

## Permissions usage

- This is an important topic on explaining the sensitive permission usage on mostly the Android app in order to provide features with transparency.

<aside>
💡

### Android app

### Notification read access

- This permission is necessary in order to read and listen to new notifications on Android and then to continue syncing them. Also extends it’s usage to media player as it’s also a notification that holds playback information.

### Notification post permission

- Starting from Android 14, apps now have to request permission in order to send notifications. This is not yet utilized well in AirSync but the current usage is to display the connected device’s status as a persistent notification. There will be more uses of this.

### Network access

- Well, that is self explanatory
</aside>

<aside>
💡

### macOS app

### Network access

- App requires incoming and outgoing local network access for the websocket to perform and connect to the android devices
- App may use the internet for updates which are now built-in tot he app itself for easier updating process.

### Notification post permission

- To display Android notifications

### Local cache/ storage

- To store app data for persistence.
</aside>

## Inspiration?

- AirSync 1.0, Yes, I had an initial version of the application vibe coded a while ago which was not close to being user friendly. But it is the base
- The ACTUAL inspiration, It’s been a thing I wanted to do for a while.. for many reasons
    - I could just leave my phone somewhere in the home and continue on my day without missing anything
    - Less distractions
    - I don’t have to log into my work account on everywhere to get notified
    - Sometimes, mobile apps are more reliable when it comes to notifying
    - Less apps to run on my poor mac
    - Also why not?

## Features

Now we into the goodies,

- Sync Android notifications to mac
- Take actions with the notifications (currently dismissing)
- Clipboard sync (there are improvements to be made)
- Sync Android battery status to mac
- Show Android now playing and control
- Share text by sharing it tot he AirSync app in android

And more… Especially more to come and planned.

- Wireless ADB assist
- scrcpy integration
- ~~iPhone~~ Android Mirroring and remote access assist
- Synced widgets on both devices
- QS tiles on Android for easier actions
- Run in macOS menubar
- Sync mac device status to Android
- Low battery alerts
- Take actions with notifications, maybe reply
- Maybe file share, but use Blip instead
- Live activities?
- Remotely lock devices?

The list goes on, You are welcome to open feature requests and inspire me for new ideas. 

## Thanks!

- To you, seriously…
- To my community mostly known as TIDWIB (Things I Do When I’m Bored ….. see the connection)
- May sound weird but AI, Yeah, without that deep research, assistant in helpless topics, explanations, occasional detonations and all lead to the rapid development of AirSync. Without this, how da hell do I implement a notification listener without weeks of study? Yeah, if you know how to use it efficiently, it is a damn good buddy to do things that you never could imagine.
- All the reddit peeps for showing that this is actually a thing that they wanted.
- All the libraries, apps and tools used for development and their creators. Issue volunteers, reddit community helpers…
- My sleep schedule… the lack of.

  

### File structure

```
com.sameerasw.airsync/
├── data/
│   ├── local/
│   │   └── DataStoreManager.kt       # Local data storage
│   └── repository/
│       └── AirSyncRepositoryImpl.kt  # Repository implementation
├── domain/
│   ├── model/
│   │   └── Models.kt                 # All data models
│   └── repository/
│       └── AirSyncRepository.kt      # Repository interface
├── presentation/
│   ├── viewmodel/
│   │   └── AirSyncViewModel.kt       # ViewModel
│   └── ui/
│       ├── components/               # Reusable UI components
│       │   ├── ActionCards.kt
│       │   ├── Dialogs.kt
│       │   └── StatusCards.kt
│       └── screens/
│           └── AirSyncMainScreen.kt  # Main screen composable
├── service/
│   └── MediaNotificationListener.kt  # Notification service
└── utils/
    ├── DeviceInfoUtil.kt
    ├── JsonUtil.kt
    └── PermissionUtil.kt
```
