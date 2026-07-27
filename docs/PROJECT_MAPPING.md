# Repository Mapping & Analysis

## Overview
This document maps the Just-Share repository, identifying what is built, the architecture, unoptimized logic, and migration statuses across all branches. 
Note: The `main` branch has recently undergone a massive refactor that resolved most architectural issues, but this document also maps the state of the feature branches (`origin/UI`, `origin/WifiDirect`, etc.) to provide a holistic view.

## 1. What is Built
- **Core Features**: 
  - Media querying via `MediaStore` for Images, Videos, Audio (`SelectFile.kt`).
  - Bluetooth File Transfer (socket communication via `BluetoothDataTransferService`).
  - Wi-Fi Direct File Transfer (`CommunicationService.kt`, `WifiDirectDeviceSelectActivity.kt`).
- **Data Persistence**:
  - Room Database for transfer history (`JediShareDatabase`).
  - DataStore for user preferences (Dark mode, default transfer method).
- **UI & Theming**:
  - Jetpack Compose-based UI across the app.
  - Lottie animations for loading states and transfer progress.

## 2. What Has to Be Built / Missing Features (from Feature Branches)
- **Granular Error Handling**: While retries exist in `main`, edge cases like sudden Wi-Fi Direct group owner disconnection need better UI feedback.
- **Robust Progress Resumption**: If a large file transfer fails midway, there is no resume capability.
- **Cross-Platform Support**: Currently Android-only.

## 3. Broken Logic & Unoptimized Logic (Historical / Feature Branches)
- **File I/O on Main Thread**: In earlier branches (`UI`, `WifiDirect`), `contentResolver.query(...)` and `FileOutputStream` writes were performed on the main thread or directly in ViewModels. (Fixed in `main` via `MediaRepository` and `Dispatchers.IO`).
- **Memory Leaks**: The Bluetooth transfer logic on feature branches read entire files into `ByteArray` before sending, causing OutOfMemory (OOM) errors on large files. (Fixed in `main` using streaming chunks).
- **Permissions Handling**: Requesting `MANAGE_EXTERNAL_STORAGE` and duplicates of location permissions caused crashes or denial on API 33+. (Fixed in `main`).

## 4. Broken MVVM Architecture (Feature Branches)
On branches like `UI` and `WifiDirect`:
- **View holding State**: Activities (`SelectFile.kt`) held global state (`var list by mutableStateOf()`) instead of using `StateFlow`.
- **ViewModel violating Clean Architecture**: ViewModels directly referenced `ContentResolver` and Android Context. 
- **Missing Dependency Injection**: Manual instantiation of repositories and services instead of using Hilt.
*(Note: These architectural violations were resolved on `main` via Hilt injection, `StateFlow`, and dedicated Repositories).*

## 5. Migration to Latest Stable Versions
The `main` branch has been successfully migrated. Feature branches are lagging behind:
- **AGP (Android Gradle Plugin)**: Migrated to 8.3.2 on `main`. Feature branches are on 7.3.1.
- **Kotlin**: Migrated to 1.9.24 on `main`. Feature branches are on 1.6.10.
- **JDK**: Bumped to Java 17 on `main`. Feature branches use Java 8.
- **Jetpack Compose**: Migrated to Compose BOM `2024.06.00` on `main`.

## 6. Master ToDo List for Agents
The following ToDos represent residual tasks identified across all branches:
- [ ] **TODO-A**: Implement missing `onClick` handlers in `MainActivity` across `UI`, `Extended`, `Settings`, and `WifiDirect` branches.
- [ ] **TODO-B**: Define `<include>` and `<exclude>` rules in `data_extraction_rules.xml` (flagged in multiple feature branches).
- [ ] **TODO-C**: Sync `UI` and `WifiDirect` feature branch enhancements (if any orphan commits exist) into the newly refactored MVVM structure on `main`.
- [ ] **TODO-D**: Implement a connection keep-alive or ping mechanism for Wi-Fi Direct to detect silent socket drops.
- [ ] **TODO-E**: Add file transfer resume capability for interrupted streams.
