# Just Share Repository Analysis & Migration Plan

This document serves as the master blueprint and analysis for the Just Share project across all branches.

## 1. Current State: What is Built

### Core Features
- **File Selection & Media Querying**: The app can query `MediaStore` for Images, Videos, and Audio (implemented in `SelectFile.kt`).
- **Bluetooth File Transfer**: 
  - Device discovery and pairing (`BluetoothController`, `AndroidBluetoothController`).
  - Transferring byte streams over Bluetooth (`BluetoothDataTransferService`).
  - Storing received files into MediaStore (`BluetoothViewModel.kt`).
- **Wi-Fi Direct File Transfer**: 
  - Device discovery and P2P connection via Wi-Fi Direct.
- **UI & Theming**: 
  - Built using Jetpack Compose with custom theming (`JediShareTheme`, `MyRed`, `MyRedSecondary`).
  - Bottom Navigation bar implemented.
  - Custom animations via Lottie (e.g., file transfer animations, welcome screen).

### Branches Overview
- `main`: Core stable branch containing Bluetooth and Wi-Fi Direct integration.
- `origin/UI`: Contains large UI overhauls (Dark mode, NavBar changes, File Selection UI upgrades). Many of these were merged, but there are still orphaned commits.
- `origin/WifiDirect` & `origin/WifiDirectIntegration`: Feature branches containing the bulk of the P2P Wi-Fi logic.
- `origin/Bluetooth`: Feature branch for the Bluetooth socket connection logic.

---

## 2. What Has to Be Built (Missing Features)

- **Robust Chunking Protocol**: Current file transfer streams raw bytes without proper packet headers, checksums, or retry mechanisms.
- **Progress Tracking**: UI components for progress exist (`Progress.kt`) but lack accurate real-time byte calculation for large files.
- **Proper Navigation Graph**: The app currently uses multiple `Activity` classes (`MainActivity`, `SelectFile`, `DeviceList`, `HistoryActivity`, `SettingsActivity`) and launches Intents to navigate instead of a single-activity Compose Navigation graph.
- **History & Settings Persistence**: `HistoryActivity` and `SettingsActivity` are present as stubs or basic UIs but need robust local DB (Room) and DataStore integration.
- **Error Handling & Reconnection**: Connection drops (Bluetooth/Wi-Fi Direct) do not have auto-reconnect logic.

---

## 3. Broken Logic & Unoptimized Logic

### Unoptimized Logic
- **File I/O on Main Thread / ViewModel**: In `BluetoothViewModel.kt`, writing to `MediaStore` output streams is happening directly in the `listen()` flow collector within the ViewModel, which is an architectural violation and can cause UI stutter.
- **Memory Leaks in Bluetooth Transfer**: `result.message.size` is appended recursively. Large files (e.g., 1GB video) transferred over Bluetooth will cause `OutOfMemoryError` (OOM) because the buffer handling is inefficient. It reads entire byte arrays into memory rather than streaming chunks directly to disk.
- **Lottie Animations**: Some Lottie animations are instantiated repeatedly during recomposition.

### Broken Logic
- **Permissions Handling**: `MainActivity.kt` requests a massive block of permissions simultaneously on startup. On Android 13+ (SDK 33), requesting granular media permissions alongside location and nearby devices without context often leads to user denial.
- **File Size Calculation**: In `SelectFile.kt`, `bytesToHumanReadableSize` calculation was commented out because of `Math.pow` type mismatches and `digitGroups` nullability issues.

---

## 4. Broken MVVM Architecture

The project attempts MVVM but violates it in several critical areas:

1. **View/Activity doing Data Layer work**: `SelectFile.kt` (an Activity) directly executes `contentResolver.query(...)` for MediaStore items in `onCreate`. This should be moved to a Repository and exposed via a UseCase to the `ViewModel`.
2. **ViewModel doing Context/Data Layer work**: `BluetoothViewModel.kt` holds a reference to `ContentResolver` and directly manages `FileOutputStream` and `ContentValues`. ViewModels should not know about Android Contexts or manage File I/O directly.
3. **State Management**: State is often hoisted improperly. `SelectFile.kt` uses `var list by mutableStateOf(emptyList<Uri?>())` globally within the activity instead of utilizing a `StateFlow` from a ViewModel.
4. **Dependency Injection**: While Hilt is present (`@HiltViewModel`, `AppModule`), it is underutilized. ViewModels are instantiated, but Repositories and Services are manually created in many places.

---

## 5. Migration to Latest Stable Versions

The `build.gradle` dependencies are heavily outdated. 

| Dependency | Current Version | Target Stable Version (2024+) |
| :--- | :--- | :--- |
| Android Gradle Plugin | 7.3.1 | 8.3.x+ (Requires JDK 17) |
| Kotlin | 1.6.10 | 1.9.23+ or 2.0.x |
| Target SDK | 33 | 34 (Android 14) |
| Jetpack Compose | 1.1.1 | 1.6.x (BOM: 2024.02.00+) |
| Hilt | 2.42 | 2.51+ |
| Room | 2.5.0 | 2.6.1+ |
| Lifecycle/ViewModel | 2.3.1 / 2.5.1 | 2.7.x+ |
| Coil | 1.3.2 | 2.6.x+ |

**Action Items for Migration:**
- Upgrade JDK from 1.8 to 17 (Required for AGP 8+).
- Migrate Compose dependencies to use the Compose BOM.
- Update `AndroidManifest.xml` to support Android 14 (`FOREGROUND_SERVICE_DATA_SYNC` types).
