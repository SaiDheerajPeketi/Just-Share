# Master TODO List (For Worker Agents)

## Chore / Refactor
- [ ] **TODO-01**: Upgrade AGP to 8.3+, Kotlin to 1.9.23+, and JDK to 17 in `build.gradle`.
- [ ] **TODO-02**: Migrate all Compose dependencies to use `compose-bom`.
- [ ] **TODO-03**: Refactor `MainActivity`, `SelectFile`, `HistoryActivity`, etc., into a Single-Activity Architecture using Jetpack Compose Navigation.

## Architecture Fixes (MVVM)
- [ ] **TODO-04**: Create a `MediaRepository` to handle `MediaStore` queries. Remove `contentResolver.query` from `SelectFile.kt`.
- [ ] **TODO-05**: Move File I/O stream logic out of `BluetoothViewModel.kt` into a dedicated `FileTransferRepository`.
- [ ] **TODO-06**: Ensure all UI states (like selected URIs) are held in ViewModels as `StateFlow` and observed in Compose safely using `collectAsStateWithLifecycle`.

## Logic & Performance Fixes
- [ ] **TODO-07**: Implement streaming chunking for file transfers to fix OOM issues during large file transfers over Bluetooth/Wi-Fi Direct.
- [ ] **TODO-08**: Fix the `bytesToHumanReadableSize` algorithm in `SelectFile.kt` and `ChatScreen.kt` and uncomment the UI code.
- [ ] **TODO-09**: Revamp permissions request flow to request permissions only when necessary (e.g., location/nearby devices only when initiating connection).

## Features
- [ ] **TODO-10**: Implement the missing Wi-Fi Direct file receiving logic to match the Bluetooth implementation.
- [ ] **TODO-11**: Implement Room Database for `HistoryActivity` to log past transfers.
- [ ] **TODO-12**: Implement DataStore for `SettingsActivity` to save user preferences.
