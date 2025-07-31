# airsync-android
Android app for AirSync 2.0 built with Kotlin Jetpack Compose

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
