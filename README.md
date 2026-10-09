# PoultryTrack

PoultryTrack is an Android UI prototype for recording poultry egg sales and tracking farm inventory. It is designed around a small farm workflow, with quick access to point of sale, harvest entry, stock views, and farm reports.

> **Current status:** The Figma-inspired Android UI, Room data foundation, and Phase 2 local business services are in place. The UI still displays presentation samples and is not connected to the database. Authentication, runtime authorization, networking, reports, and offline synchronization are not implemented.

> **Not production-ready:** Do not use the current app or local services to process real farm transactions. Authentication and role-based authorization are not implemented, the UI is not connected to these services, and local validation does not establish a trusted user or device.

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

The database is at schema version 4. Exported schema files are stored under `app/schemas`; future schema changes should add explicit migrations. The database does not fall back to destructive migration. Migration 1→2 adds product-level inventory event uniqueness and makes normalized usernames unique within a farm. Migration 2→3 adds a unique open-shift slot per farm and user while retaining closed shift history. Migration 3→4 prevents DAO inserts or updates from bypassing that constraint with a missing or stale open-shift marker.

## Phase 2 business services

Business rules live in `app/src/main/java/com/example/syncore/domain`; they are independent of Activities and are not yet connected to the XML screens.

- `SalesService` checks the active farm/user/open shift, non-empty positive cart quantities, active products, effective prices, and available stock. Checkout and shift reconciliation currently support PHP cash only, and checkout rejects a price version in another currency. It calculates totals with integer minor units, snapshots each price, validates cash/change, and writes the completed sale, items, payment, and signed stock movements in one Room transaction. The caller supplies a stable sale ID as the idempotency key and must reuse that same ID on retries; harvest and adjustment request IDs follow the same caller-supplied pattern.
- `HarvestService` validates positive quantities and farm/user/device references, then writes the harvest, items, and positive ledger entries atomically. The harvest ID prevents repeat posting.
- `InventoryAdjustmentService` creates pending requests and permits a single pending-to-approved or pending-to-rejected transition. Approval checks stock and writes the reviewer metadata and ledger movement in one transaction.
- `PricingService` assigns the effective timestamp from the application clock when a price is saved, so the change takes effect immediately. It rejects a timestamp that is not later than the latest version (including a device-clock rollback), requires a reason, and atomically records a `PRICE_CHANGED` audit entry with the new version. A correction is another version and audited action; existing sale-item price and total snapshots are never rewritten. Closing the previous version's open-ended effective interval only sets its end boundary.
- `ShiftService` starts and closes cash sessions and calculates expected cash and variance from completed cash payments. A schema-level unique open-shift slot prevents duplicate active shifts.

Each service uses Room transactions for related writes. The ledger's unique event/source/product key is the final duplicate-movement guard, and stock validation is performed inside the same write transaction as checkout or adjustment approval. Farm ownership is checked for farms, staff, devices, prices, shifts, and transactions; inventory balances are always queried with a farm ID. The current catalog products are global egg-size references, while prices and stock are farm-specific.

Pricing policy for the MVP: backdated and future-scheduled price changes are not supported. A saved price applies immediately, and its effective timestamp comes from the device's application clock. Offline devices have no trusted server time; the local clock can be wrong or manipulated, so audit timestamps are useful records but are not tamper-proof. If the clock is at or before the latest price version, the service rejects the change until the clock advances. Existing sale rows and snapshots are retained as recorded; correcting a price requires a new price version with an audit reason and does not recalculate prior sales.

Authentication and runtime authorization remain future responsibilities. Before calling these services from a future ViewModel/controller, that layer must establish and validate the signed-in user and active farm, enforce role permissions for price changes, adjustment approvals, user management, and shift administration, and prevent callers from supplying another farm's identifiers. These local service checks enforce data consistency; they do not authenticate a person or constitute a security boundary against code running on the device.

Run local unit tests and connected Room/service instrumentation tests with:

```shell
./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest
```

Instrumented database tests cover Room persistence, foreign keys, effective price history, immutable sale snapshots, unique receipts, ledger balances, shifts, adjustments, outbox/audit records, atomic sale/harvest writes, multi-product ledger events, farm-scoped usernames, and schema migrations. Phase 2 service tests additionally cover validation, rollback, stock isolation, idempotency, approvals, immediate price changes, rejected backdating, preserved sale-price snapshots, cash reconciliation, and migrations. Run them on a connected Android device or emulator with:

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

## Scope boundaries

Phase 2 adds local business calculations and service operations only. It does not connect services to UI screens or implement authentication, runtime role authorization, network requests, a sync engine, or report calculations. The Android namespace and application ID still use the earlier `com.example.syncore` identifier, and the theme is still named `Theme.Syncore`.

## Phase 1.5 architecture decisions

- **Farm/account scope:** The local schema explicitly supports multiple farms in one database: farm records are first-class, operational records carry `farm_id`, and farm-facing queries filter by it. Normalized usernames are therefore unique per farm. Any future sign-in flow must establish a farm context before looking up a username; authentication itself is not implemented.
- **Android backup:** The manifest enables backup and references both legacy and Android 12+ rules. The legacy `backup_rules.xml` defines no include/exclude filters. The newer `data_extraction_rules.xml` has an empty cloud-backup section and no device-transfer rules. Android Auto Backup includes app data by default when rules do not exclude it, so `poultrytrack.db` is currently eligible for backup/transfer. No product backup policy is defined, and no exclusions were added. Before deploying real data, decide whether farm records, user/account metadata, sales, inventory, and audit records should be included in cloud backup and device-to-device transfer. See [Android Auto Backup](https://developer.android.com/identity/data/autobackup) and [Android 12 backup rule changes](https://developer.android.com/about/versions/12/behavior-changes-12#backup).
