# airsync-android
Android app for AirSync 2.0 built with Kotlin Jetpack Compose

## Installation and troubleshooting
![Frame 1 Large](https://github.com/user-attachments/assets/24db7555-2518-44ee-83f3-6c7f84cb8b54)

## How to connect?
Use your built-in camera or Google lense or anything that can scan a QR code. I twill prompt you to open the app. Once authorized, The last device will be saved on the mobile for now for easier re-connection.


Definitely need someone to help with documentation ;)

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
