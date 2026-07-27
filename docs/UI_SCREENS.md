# App Screens Design Mapping

This document provides a detailed breakdown of every screen in the Just-Share app, intended for recreation in Stitch or Figma.

## 1. Welcome Screen (`WelcomeActivity.kt`)
- **Purpose**: Splash/Onboarding screen.
- **UI Elements**:
  - **Header**: "Welcome to Just Share" (Red and Black Typography, `h3` and `h1`).
  - **Animation**: Lottie animation (`R.raw.welcome_activity_animation`) positioned in the center.
  - **Subtitle**: "Quickly transfer photos, videos, documents, audio files".
  - **Action**: Large pill-shaped red button at the bottom labeled "Continue".

## 2. Main/Permissions Screen (`MainActivity.kt`)
- **Purpose**: Landing screen to ensure all necessary runtime permissions are granted.
- **UI Elements**:
  - **Animation**: Lottie animation (`R.raw.bluetooth_lottie`) representing connectivity.
  - **Text**: Prompt explaining why location and nearby devices permissions are required.
  - **Action**: Button to trigger the OS permission request dialogs.
  - **Navigation**: Once granted, redirects automatically to `SendOrReceive`.

## 3. Send or Receive Selection (`SendOrReceive.kt`)
- **Purpose**: Core fork in the user journey.
- **UI Elements**:
  - **Layout**: Two massive, equally sized vertical cards/buttons.
  - **Top Card**: "SEND" with an upward arrow icon.
  - **Bottom Card**: "RECEIVE" with a downward arrow icon.
  - **Bottom Navigation**: Custom navigation bar linking to History and Settings.

## 4. File Selection Screen (`SelectFile.kt`)
- **Purpose**: Media picker for the sender.
- **UI Elements**:
  - **Top App Bar**: Title "Select Files" with a back button.
  - **Tab Row**: Categories for "Images", "Videos", "Audio".
  - **Grid Layout**: `LazyVerticalGrid` displaying thumbnails using Coil (for images/videos) or icons (for audio).
  - **Selection State**: Selected items get a red tint overlay and a checkmark badge.
  - **Floating Action Button (FAB)**: Shows the count of selected files. Clicking it proceeds to the Device Selection screen.

## 5. Device Discovery (Bluetooth) (`DeviceList.kt`)
- **Purpose**: Scanning and pairing with Bluetooth devices.
- **UI Elements**:
  - **Header**: "Available Devices".
  - **List**: `LazyColumn` of discovered Bluetooth devices (Name and MAC address).
  - **Paired Devices Section**: Separate list showing previously paired devices.
  - **Loading State**: Lottie radar/scanning animation while discovering.

## 6. Device Discovery (Wi-Fi Direct) (`WifiDirectDeviceSelectActivity.kt`)
- **Purpose**: Scanning for Wi-Fi Direct peers.
- **UI Elements**:
  - **Header**: "Wi-Fi Direct Peers".
  - **List**: List of available P2P devices.
  - **Status Indicator**: Text indicating whether Wi-Fi Direct is enabled or disabled.
  - **Action**: Tap on a peer to initiate a connection.

## 7. Transfer Progress / Chat Screen (`ChatScreen.kt`)
- **Purpose**: Shows real-time transfer progress.
- **UI Elements**:
  - **Header**: "Transfer Process".
  - **Animation**: File transfer Lottie animation (`R.raw.file_transfer_animation`).
  - **File List**: A card containing a `LazyColumn` of the files being transferred.
  - **List Items**: Each item shows a file type icon (Photo/Video/Audio/Doc), File Name, File Size, and a progress indicator.
  - **Progress Bar**: Custom pill-shaped `LinearProgressIndicator` underneath the currently transferring file.
  - **Completion State**: Green checkmark Lottie animation for completed files.
  - **Bottom Bar**: Disconnect button and (for Bluetooth) a text input/send button for raw messages.

## 8. Transfer History (`HistoryActivity.kt`)
- **Purpose**: Displays a log of all past transfers.
- **UI Elements**:
  - **Header**: "Transfer History" with a "Clear All" trash icon button.
  - **List**: `LazyColumn` of history entities.
  - **List Items**: 
    - File icon.
    - Title: File Name.
    - Subtitle: "Sent to [Device]" or "Received from [Device]".
    - Metadata: File Size, Date/Time, and Transfer Method (BT/WiFi).
  - **Empty State**: Shows "No transfer history" if the DB is empty.

## 9. Settings (`SettingsActivity.kt`)
- **Purpose**: App configuration.
- **UI Elements**:
  - **Header**: "Settings".
  - **Toggles**: 
    - Dark Mode toggle switch.
  - **Selectors**: 
    - Default Transfer Method (Radio buttons: Bluetooth vs. Wi-Fi Direct).
  - **Info**: App Version text.

## Theme & Styling Palette
- **Primary Color**: Red (`#EC1C22`)
- **Secondary Colors**: Light Red (`#FFCDD2`), Dark Red (`#B71C1C`)
- **Typography**: Roboto and Inter (Custom fonts).
- **Shapes**: Heavy use of `RoundedCornerShape(20.dp)` for cards and buttons.
