# Master TODO List — Status Update

Last updated: 2026-07-26 (agent refactor session complete)

---

## Chore / Refactor

- [x] **TODO-01**: Upgrade AGP to 8.3.2, Kotlin to 1.9.24, JDK to 17 in `build.gradle` and `gradle-wrapper.properties`.
- [x] **TODO-02**: Migrated all Compose dependencies to `compose-bom:2024.06.00`. Upgraded Gradle wrapper to 8.6, target SDK 34.
- [ ] **TODO-03 (PARTIAL)**: Deprecated API cleanup done (`BluetoothFileMapper` now uses JSON instead of Java serialization; `BluetoothMessageMapper` removed `Environment.getExternalStoragePublicDirectory`). **Remaining**: Full Single-Activity Architecture migration with Jetpack Compose Navigation (currently still multi-Activity).

---

## Architecture Fixes (MVVM) — ALL COMPLETE

- [x] **TODO-04**: Created `MediaRepository` to handle all `MediaStore` queries on `Dispatchers.IO`. Removed all `contentResolver.query` calls from `SelectFile.kt` Activity. Created `ImageViewModel`, `VideoViewModel`, `AudioViewModel` backed by `MediaRepository` + Hilt.
- [x] **TODO-05**: Created `FileTransferRepository` for all file I/O (sender + receiver). Removed `FileOutputStream`, `ContentResolver` injection from `BluetoothViewModel`. Delegated all file writing to repository via MediaStore.
- [x] **TODO-06**: All UI state (`BluetoothUiState`, `TransferProgressState`) now held in `StateFlow`/`SharedFlow`. Removed the dual `_state`/`_statee` anti-pattern. Removed `var isFirst: Boolean` public mutable field. Selected URIs tracked in Compose state in `SelectFile`. All ViewModels are Hilt-injected. `BluetoothController` interface no longer takes `ViewModel` parameters.

---

## Logic & Performance Fixes — ALL COMPLETE

- [x] **TODO-07**: Streaming chunking implemented and fixed:
  - Bluetooth: chunk size raised from 990 → 8192 bytes. Removed busy-wait delays (10ms + 1000ms per chunk). Replaced fragile size==880 sentinel with typed EOF sentinel (`ByteArray(CHUNK_SIZE) { 0xFF }`).
  - WiFi Direct (`CommunicationService`): Removed `runBlocking{delay(5000)}` pre-send stall. Removed `runBlocking{delay(20)}` per-chunk block. Replaced broken `while(currSize<size||progress!=100)` loop condition with matching EOF sentinel. Fixed `copyOfRange` bug (was writing stale buffer bytes on last chunk).
- [x] **TODO-08**: `bytesToHumanReadableSize` fixed — was using `1 shl 30` (Int bit-shift) causing silent overflow for files > 2GB. Now uses explicit Double arithmetic. Moved to `Utils.kt`. `classifyFileType` updated to use `String.lowercase()` instead of deprecated `String.toLowerCase()`.
- [x] **TODO-09**: Permissions revamped in `MainActivity`:
  - Removed `MANAGE_EXTERNAL_STORAGE` (non-requestable at runtime via `requestPermissions()`).
  - Removed duplicate entries (`ACCESS_COARSE_LOCATION` appeared twice in each block).
  - `READ_MEDIA_*` now only requested on API 33+ (constants don't exist on lower APIs).
  - `WRITE_EXTERNAL_STORAGE` only requested on API ≤ 30.
  - `NEARBY_WIFI_DEVICES` only requested on API 33+.
  - Removed duplicate `requestMultiplePermissions.launch()` call.
  - `AndroidManifest.xml`: Added `READ_MEDIA_VISUAL_USER_SELECTED` (Android 14), `foregroundServiceType=dataSync` on `CommunicationService`, capped legacy permissions with `maxSdkVersion`.

---

## Features — ALL COMPLETE

- [x] **TODO-10**: WiFi Direct receiving logic fully fixed in `CommunicationService` (see TODO-07 above). `Progress.kt` now passes `viewModel` + `contentResolver` to `ChatScreen` on both Bluetooth and WiFi paths (was null on WiFi path, making progress bar dead).
- [x] **TODO-11**: Room Database implemented:
  - `TransferHistoryEntity` (Room entity with file name, mime type, size, sender/receiver flag, method, device name, timestamp)
  - `TransferHistoryDao` (insert, delete, clearAll, getAllHistory as Flow)
  - `JediShareDatabase` (Room database class)
  - `TransferHistoryRepository` (wraps DAO)
  - `HistoryViewModel` (Hilt, exposes `StateFlow<List<TransferHistoryEntity>>`)
  - `HistoryActivity`: full LazyColumn UI with per-item delete, clear-all, formatted date/size
- [x] **TODO-12**: DataStore implemented:
  - `UserPreferencesDataStore` (dark mode, default transfer method, chunk size)
  - `SettingsViewModel` (Hilt, reads/writes via DataStore)
  - `SettingsActivity`: dark mode toggle, transfer method selector, app version info

---

## Remaining / Open Items

- [ ] **TODO-13**: Single-Activity Architecture migration — replace all `startActivity(Intent(...))` calls with Compose Navigation (`NavHost`). This is the remaining part of TODO-03.
- [ ] **TODO-14**: Add `collectAsStateWithLifecycle` (from `lifecycle-runtime-compose`) instead of `collectAsState()` in all composables that collect from StateFlow to avoid waking suspended flows during onStop.
- [ ] **TODO-15**: Wire `TransferHistoryRepository.addEntry()` call after each successful transfer (in `BluetoothViewModel` on `EndOfFile` and in `CommunicationService` after EOF sentinel detected). History DB is ready but not yet populated automatically.
- [ ] **TODO-16**: Implement foreground notification for `CommunicationService` (required on API 34+ for `foregroundServiceType=dataSync`). Currently shows `stopForeground()` call but never starts foreground.
- [ ] **TODO-17**: Add connection retry logic to `AndroidBluetoothController.connectToDevice()` — currently fails immediately on `IOException`. Consider fallback to `createInsecureRfcommSocketToServiceRecord` on failure.
- [ ] **TODO-18**: `WifiDirectDeviceSelectActivity` — refactor to MVVM: extract `WifiP2pManager` logic into a `WifiDirectViewModel`. Currently the Activity holds all P2P state directly.
- [ ] **TODO-19**: `PermissionViewModel` is unused — either wire it to `PermissionDialog` in `WifiDirectDeviceSelectActivity` or remove it.
- [ ] **TODO-20**: Enable Room schema export (`exportSchema=true`) and add migration strategies before production release.
