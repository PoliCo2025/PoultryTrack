# PoultryTrack

PoultryTrack is an Android UI prototype for recording poultry egg sales and tracking farm inventory. It is designed around a small farm workflow, with quick access to point of sale, harvest entry, stock views, and farm reports.

> **Current status:** The Figma-inspired Android UI is in place. This repository is currently a UI prototype: screens use sample presentation data, and there is no Room database, authentication, networking, or offline synchronization yet.

## Screens

- Login
- Dashboard
- Point of sale and sale confirmation
- Egg inventory and harvest entry
- Inventory adjustment requests
- Reports and shift summary
- User management and egg pricing
- Sync status and More

The screens are XML layouts under `app/src/main/res/layout`. `MainActivity` handles the current screen navigation.

## Open the project

1. Clone this repository or open it with **File → Open** in Android Studio.
2. Allow Gradle to sync and install Android SDK Platform 37 if Android Studio requests it.
3. Select an emulator or connected Android device, then run the `app` configuration.

The Gradle wrapper is included, so a local Gradle installation is not required.

## Build from the command line

From the project root, run:

```shell
./gradlew :app:assembleDebug
```

On Windows:

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Project details

- **Language:** Java
- **UI:** Android XML layouts, AppCompat, Material Components, ConstraintLayout
- **Minimum Android version:** API 24
- **Compile / target SDK:** API 37
- **App version:** 1.0

The app is branded PoultryTrack, while the Android namespace and application ID still use the earlier `com.example.syncore` identifier. That package cleanup has not been done yet.

## Planned foundation

The next development phase can add local Room persistence for users, egg sizes, price history, harvests, sales, shifts, inventory ledger entries, adjustment requests, an outbox, and audit records. Those backend features are not part of the current UI build.
