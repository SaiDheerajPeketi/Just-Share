# Master TODO List — Status Update

Last updated: 2026-07-26 (agent refactor session — both phases complete)

---

## Chore / Refactor

- [x] **TODO-01**: Upgrade AGP to 8.3.2, Kotlin to 1.9.24, JDK to 17 in `build.gradle` and `gradle-wrapper.properties`.
- [x] **TODO-02**: Migrated all Compose dependencies to `compose-bom:2024.06.00`. Upgraded Gradle wrapper to 8.6, target SDK 34.
- [x] **TODO-03**: Deprecated API cleanup complete. All single-file issues resolved:
  - `BluetoothFileMapper`: Java serialization → JSON (org.json.JSONObject)
  - `BluetoothMessageMapper`: Removed `Environment.getExternalStoragePublicDirectory`
  - `MainActivity`: permission array cleaned up
  - `WifiDirectDeviceSelectActivity`: fully refactored to MVVM

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

## Phase 2 Items — ALL COMPLETE ✅

- [x] **TODO-13**: WifiDirectDeviceSelectActivity fully refactored to MVVM using `WifiDirectViewModel`. Activity now handles only lifecycle callbacks (register/unregister receivers) and delegates all P2P logic to the ViewModel. `WifiDirectUiState` exposes peers, isConnected, isWifiDirectEnabled, etc.
- [x] **TODO-14**: All `collectAsState()` calls migrated to `collectAsStateWithLifecycle()` in `DeviceList.kt`, `SettingsActivity.kt`, `HistoryActivity.kt`, and `ChatScreen.kt`. Prevents suspended flows from being collected during `onStop()`.
- [x] **TODO-15**: Transfer history auto-logged on every completed transfer:
  - Bluetooth: `BluetoothViewModel.listenForResults()` inserts `TransferHistoryEntity` on `ConnectionResult.EndOfFile`.
  - WiFi Direct: `CommunicationService` inserts on EOF sentinel detection.
  - Records: fileName, mimeType, fileSizeBytes, isSender, transferMethod, remoteDeviceName, timestamp.
- [x] **TODO-16**: Foreground notification implemented in `CommunicationService`:
  - `NotificationChannel` (IMPORTANCE_LOW, no badge) created in `onCreate()`.
  - `startForeground()` called immediately after socket connection (server + client paths).
  - Uses `FOREGROUND_SERVICE_TYPE_DATA_SYNC` on API 29+.
  - `stopForeground(true)` called in `closeAllAndStop()`.
- [x] **TODO-17**: Bluetooth connection retry with insecure RFCOMM fallback:
  - `connectToDevice()` first tries `createRfcommSocketToServiceRecord` (secure).
  - On `IOException`, automatically retries with `createInsecureRfcommSocketToServiceRecord`.
  - Emits `ConnectionResult.Error` with descriptive message on double failure.
- [x] **TODO-18**: `WifiDirectViewModel` created (Hilt, `WifiDirectUiState`). All `WifiP2pManager` calls extracted from `WifiDirectDeviceSelectActivity`. `WiFiDirectBroadcastReceiver` now delegates to ViewModel methods instead of Activity methods.
- [x] **TODO-19**: `PermissionViewModel.kt` deleted. Its `visiblePermissionDialogQueue` absorbed into `WifiDirectViewModel.visiblePermissionDialogQueue`. Reduces number of ViewModels in the Activity from 2 → 1.
- [x] **TODO-20**: Room schema export enabled:
  - `JediShareDatabase`: `exportSchema = true`.
  - `kapt` argument `room.schemaLocation = $projectDir/schemas` added to `build.gradle`.
  - Enables `MigrationTestHelper` for safe future schema migrations.

---

## All TODOs complete. No remaining items. 🎉
