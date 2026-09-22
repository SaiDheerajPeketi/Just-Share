# Google Play permission and foreground-service evidence

This directory is a runbook, not completed review evidence. `draft-debug/` contains development
screenshots only. The old audio and photo/video permission images describe superseded builds; the
current candidate uses Android's document picker and declares no broad media-library permission.
Never submit those images as evidence.

Record final evidence only from the exact signed App Bundle installed through a licensed Play
internal track. Use synthetic files and keep credentials, device identifiers, account addresses,
purchase tokens, connection codes and notifications from other apps out of frame.

## Candidate record

Keep the following beside the final videos without exposing secret or user-specific values:

- package: `com.blackandblue.justshare`
- version name and version code
- signed AAB SHA-256
- Play internal-release name and install date
- device model, Android version and capture date
- tester confirmation that the installed build came from Google Play

Use `review-evidence/fixtures/just-share-review-payload.txt` as the selected document. It is a
2,935-byte synthetic text file with SHA-256
`8fd703d5fa48bfa9b2ef554a4d5d2ec16fb2e6ffe506dbea98f754bdbcb11f0a`. Verify the checksum before
recording, and confirm the received copy starts and ends with the fixture's visible markers. Never
replace it with a tester's personal file.

Generate every Remote Transfer session code and QR code live inside the Play-installed app during
the capture. Never commit, reuse, or publish a real connection code, QR payload, relay credential,
or purchase token. The manual-code and camera flows must join the same disposable test session.

List connected devices first, choose one exact serial, and pass that serial to every install,
launch, log, screenshot and recording command. Before accepting evidence, confirm the foreground
component is `com.blackandblue.justshare/.MainActivity`; reject any capture if another app or an
implicitly selected device took focus.

## Current artifact status — 22 September 2026

The approved replacement candidate comes from source commit `4b95f2e`. Its signed AAB has SHA-256
`e4eb7db0dec2358b8ee5b8f8cd61d16a4c64ed64a132217d83eac1d5c8441e81`; its matching signed APK has
SHA-256 `e0cbec10ac589b741d8322e0d7b651ba616471603da1f718cee6da904adc7e1e`.

The exact signed APK was clean-installed on explicitly selected emulator `emulator-5554`, a
Pixel-class Android 15 (API 35) device. It cold-launched in 2,792 ms, remained alive and focused on
`com.blackandblue.justshare/.MainActivity`, and produced no `AndroidRuntime` failure. The first-run
explanation and onboarding rendered at 1080 x 2400 with no text overlap or clipping. This local
install validates the release artifact but is not Play review evidence; final recordings must use
the unchanged AAB installed through Play.

The earlier otherwise-valid candidate from source commit `a877cc7`, AAB SHA-256
`70db8fe32bfe096ff83680a2c8ad0f2b96ede502a359812356008fdcc3f0afc3`, is superseded and must not be
uploaded. It still contained a hardcoded US fallback price on the unavailable data-pack button;
the current candidate displays an explicit unavailable state until Google Play supplies the
localized price.

The earlier signed AAB from source commit `3ab7d5f`, SHA-256
`cde1e03f1442a36de0badb3c7f63f630a03364ce6705ce62ca565e3b8353d6a6`, remains **rejected and must
never be uploaded**. Its matching minified release APK crashed immediately on Android 15 because R8
horizontally merged structurally similar Hilt ViewModels, causing duplicate lazy class keys.

## Permission-to-feature matrix

| Surface | User-visible purpose | Required evidence |
| --- | --- | --- |
| Bluetooth scan, connect and advertise on Android 12+ | Discover and connect to a nearby device for a user-started Bluetooth transfer | Show Just Share's first-run explanation before the Android prompt, grant or deny it, then enter Bluetooth discovery from the normal transfer flow |
| Nearby Wi-Fi devices on Android 13+ | Discover and connect to a peer for a user-started Wi-Fi Direct transfer | Show the same explanation, Android's nearby-device prompt, peer discovery and the transfer flow |
| Fine/coarse location through Android 12 only | Android's legacy prerequisite for Bluetooth or Wi-Fi Direct discovery; Just Share does not use coordinates | Record only when Play asks for legacy-device evidence; show the discovery feature and explain that the permission is capped at API 32 |
| Notifications on Android 13+ | Show user-visible transfer progress while the app is backgrounded | Show the explanation, Android prompt, ongoing notification and transfer terminal state; denial must not block the app |
| Camera | Scan a Remote Transfer QR code | From **Remote Transfer**, tap **Scan QR**, show the Android prompt, scan a synthetic code and continue; manual code entry must remain available after denial |
| System document picker | Choose only files the user explicitly sends | Show the picker and selected synthetic file; no photo, video, audio or broad-storage prompt should appear on current Android versions |

## Foreground-service declaration copy

### Local transfer — `dataSync`

**Functionality:** A user selects a file, chooses Bluetooth or Wi-Fi Direct, selects a nearby
recipient and explicitly starts the transfer. Just Share keeps that transfer alive in a
`dataSync` foreground service and shows an ongoing progress notification while the app is
backgrounded.

**Impact of deferral:** The requested peer-to-peer transfer would not begin when the user presses
send. **Impact of interruption:** The active transfer would stop before the recipient receives the
complete file and the user would need to retry.

### Remote Transfer — `dataSync`

**Functionality:** A user opens **Remote Transfer**, selects a file or joins with a code/QR, and
explicitly starts an end-to-end encrypted transfer. A separate `dataSync` foreground service keeps
the user-visible upload/download active while the app is backgrounded. The relay receives opaque
ciphertext, not the file key or plaintext contents.

**Impact of deferral:** The requested remote transfer would not start. **Impact of interruption:**
The encrypted session would end before completion and the user would need to reconnect or retry.

## Final capture plan

Record separate continuous clips because local and Remote Transfer are materially different
features even though both use the `dataSync` service type:

1. **Permission and denial path (Android 13+):** start from a fresh Play install, show the complete
   first-run explanation, request nearby-device access and notifications, then repeat with denial
   to show Just Share remains usable and displays an actionable error rather than looping or
   crashing.
2. **Bluetooth local transfer:** choose a small synthetic file with the system picker, discover and
   select a second Play-installed device, start the transfer, show the ongoing notification,
   background the app, return to visible progress, and show completion or the user's stop action.
3. **Wi-Fi Direct local transfer:** repeat with Wi-Fi Direct on two Play-installed devices, showing
   the user action that starts discovery, the peer selection, notification, active transfer and
   terminal state.
4. **Remote Transfer and camera:** show manual code entry as the no-camera fallback, then tap
   **Scan QR**, show the in-context camera prompt, scan a synthetic code, start a small encrypted
   transfer, show the remote-transfer notification while backgrounded, and end on completion or a
   user-requested stop.
5. **Current permission boundary:** show that selecting a document uses Android's picker and does
   not request broad photo, video or audio access. If Play does not request this clip, retain it as
   internal declaration evidence rather than uploading unrelated footage.

Keep disclosure and Android system text readable at normal playback speed. Upload final videos as
unlisted review-only links and store their URLs in the protected release record, not source
control. Re-record after any change to the signed artifact, permission copy, service types,
transfer trigger, notification behavior or denial path.
