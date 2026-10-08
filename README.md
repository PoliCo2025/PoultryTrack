# PoultryTrack

PoultryTrack is an Android UI prototype for recording poultry egg sales and tracking farm inventory. It is designed around a small farm workflow, with quick access to point of sale, harvest entry, stock views, and farm reports.

> **Current status:** The Figma-inspired Android UI and the Phase 1 local Room data foundation are in place. The UI still displays presentation samples and is not connected to the database. Authentication, POS/inventory business workflows, networking, reports, and offline synchronization are not implemented.

## Screens

- Login
- Dashboard
- Point of sale and sale confirmation
- Egg inventory and harvest entry
- Inventory adjustment requests
- Reports and shift summary
- User management and egg pricing
- Sync status and More

The screens are XML layouts under `app/src/main/res/layout`. `MainActivity` handles the current screen navigation; database access is not wired into Activities.

## Local data foundation

Room database code lives under `app/src/main/java/com/example/syncore/data/local`. It includes farm and device boundaries, users, egg-size products, effective-dated price versions, shifts, sales and sale items, payments, harvests and harvest items, adjustment requests, an inventory ledger, a future sync outbox, and an audit log.

The current inventory balance is a query over signed ledger entries rather than a second mutable stock value. Money is stored as integer minor units with a currency code. Sale items keep the price and line-total snapshots used at the time of sale. A new database is seeded only with the four reference egg sizes; it does not create demo farms, users, sales, harvests, or other transactions.

The database is at schema version 2. Exported schema files are stored under `app/schemas`; future schema changes should add explicit migrations. The database does not fall back to destructive migration. Migration 1→2 adds product-level inventory event uniqueness and makes normalized usernames unique within a farm.

Instrumented database tests cover Room persistence, foreign keys, effective price history, immutable sale snapshots, unique receipts, ledger balances, shifts, adjustments, outbox/audit records, atomic sale/harvest writes, multi-product ledger events, farm-scoped usernames, and the 1→2 migration. Run them on a connected Android device or emulator with:

```shell
./gradlew :app:connectedDebugAndroidTest
```

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

## Phase 1 boundaries

This phase adds persistence structures and storage-only aggregate writes. It does not implement POS calculations, stock posting, harvest inventory updates, adjustment approval behavior, authentication, authorization, network requests, a sync engine, or report calculations. The Android namespace and application ID still use the earlier `com.example.syncore` identifier, and the theme is still named `Theme.Syncore`.

## Phase 1.5 architecture decisions

- **Farm/account scope:** The local schema explicitly supports multiple farms in one database: farm records are first-class, operational records carry `farm_id`, and farm-facing queries filter by it. Normalized usernames are therefore unique per farm. Any future sign-in flow must establish a farm context before looking up a username; authentication itself is not implemented.
- **Android backup:** The manifest enables backup and references both legacy and Android 12+ rules. The legacy `backup_rules.xml` defines no include/exclude filters. The newer `data_extraction_rules.xml` has an empty cloud-backup section and no device-transfer rules. Android Auto Backup includes app data by default when rules do not exclude it, so `poultrytrack.db` is currently eligible for backup/transfer. No product backup policy is defined, and no exclusions were added. Before deploying real data, decide whether farm records, user/account metadata, sales, inventory, and audit records should be included in cloud backup and device-to-device transfer. See [Android Auto Backup](https://developer.android.com/identity/data/autobackup) and [Android 12 backup rule changes](https://developer.android.com/about/versions/12/behavior-changes-12#backup).
