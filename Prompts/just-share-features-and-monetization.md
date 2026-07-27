# Just-Share App — Feature List & Monetization Plan

---

## 1. Complete Feature List

### 1.1 Core Existing Screens (already built)
1. Welcome / Splash (`WelcomeActivity.kt`)
2. Main / Permissions (`MainActivity.kt`)
3. Send or Receive Selection (`SendOrReceive.kt`)
4. File Selection (`SelectFile.kt`)
5. Device Discovery – Bluetooth (`DeviceList.kt`)
6. Device Discovery – Wi-Fi Direct (`WifiDirectDeviceSelectActivity.kt`)
7. Transfer Progress / Chat Screen (`ChatScreen.kt`)
8. Transfer History (`HistoryActivity.kt`)
9. Settings (`SettingsActivity.kt`)

### 1.2 Suggested New Features (by category)

**Faster, more capable transfers**
- Group/broadcast send — push files to multiple receivers at once
- QR code pairing — scan-to-connect instead of manual device list, especially for Wi-Fi Direct
- Resume on interruption — pick up a dropped transfer instead of restarting
- Whole folder/album transfer — send a folder as a single unit
- Auto protocol fallback — try Wi-Fi Direct first, fall back to Bluetooth automatically
- Optional compression — zip large batches before sending

**Security & trust**
- Accept/reject confirmation dialog on the receiving end (sender name + file list preview before accepting)
- PIN-based pairing — short code confirmation to prevent unwanted connections
- Trusted/blocked device list — auto-accept from trusted devices, block others
- **Secure Direct Send** — true peer-to-peer, end-to-end encrypted transfer mode with session-level key exchange, authenticated encryption, and an in-app key verification step (full protocol and algorithm breakdown in section 1.3 below)

**Everyday usability**
- Share-sheet integration — appear in Android's native "Share via" menu from Gallery/Files/WhatsApp etc.
- Persistent notification with live progress — track transfers without staying in the app (background transfer service)
- File preview — thumbnail/tap-to-preview before accepting an incoming file
- Search/filter in History and Select File screens
- Recent/favorite devices — skip re-scanning for frequent contacts
- Undo on "Clear All" history (snackbar with Undo)

**Post-transfer file management**
- Auto-sort received files into type/date folders
- Storage insights — data received this month, duplicate/large file cleanup suggestions

### 1.3 Secure Direct Send — Technical Deep Dive

Direct Send is a hardened transfer mode layered **on top of** your existing Bluetooth/Wi-Fi Direct transport — it doesn't replace the transport, it wraps the payload in its own encryption so the data is unreadable even if someone captures the raw radio traffic. Below is the protocol flow and the specific algorithms involved at each step.

**Protocol flow**
1. **Discovery** — unchanged, uses your existing Bluetooth/Wi-Fi Direct scanning to find the peer device.
2. **Handshake** — the two devices perform an authenticated key exchange over the existing socket connection.
3. **Key verification (SAS)** — both users visually confirm a short code before any file data moves.
4. **Encrypted, chunked transfer** — files are streamed in encrypted blocks with per-block integrity checks.
5. **Session teardown** — all session key material is discarded when the transfer ends or the connection closes.

**Step-by-step algorithms**

- **Key exchange — X25519 (Elliptic Curve Diffie-Hellman on Curve25519).**
  Each device generates a fresh, random *ephemeral* keypair for that session only (not a long-term identity key). Both sides exchange public keys and independently compute the same shared secret. X25519 is chosen because it's fast on mobile CPUs, has small 32-byte keys (cheap to exchange over Bluetooth), and is the same primitive used in Signal and WireGuard.

- **Forward secrecy.**
  Because the keypair is ephemeral and thrown away after the session, a device compromised *later* can't be used to decrypt a transfer captured *today* — there's no long-term secret to steal that would unlock past sessions.

- **Key derivation — HKDF (HMAC-based Key Derivation Function) with SHA-256.**
  The raw ECDH shared secret isn't used directly for encryption. It's passed through HKDF to derive two separate symmetric keys — one for sender→receiver traffic, one for receiver→sender traffic — which prevents a class of reflection attacks where traffic encrypted in one direction could be replayed back in the other.

- **Key verification — Short Authentication String (SAS).**
  After the handshake, both devices hash the exchanged public keys (SHA-256) and render a short digest as a 6-digit code or a handful of emoji. The user visually compares the code shown on both screens before transfer starts. This is the step that actually defeats a man-in-the-middle attacker: without it, an attacker sitting between the two devices during pairing could swap in their own key and no one would notice. This is the same approach used by Signal and ZRTP for voice-call key verification.

- **Payload encryption — AES-256-GCM (AEAD).**
  Files are encrypted with AES in Galois/Counter Mode, an "authenticated encryption" cipher that provides confidentiality and tamper-detection in one pass — any modified byte in transit causes decryption to fail rather than silently returning corrupted data.

- **Chunked streaming with per-chunk nonces.**
  Large files are split into fixed-size blocks (e.g. 256KB–1MB) and each block is encrypted independently with a unique nonce (a base IV combined with an incrementing chunk counter), each carrying its own authentication tag. This lets the receiver verify and write each chunk as it arrives instead of buffering the whole file, and it's what makes a "resume interrupted transfer" feature possible — you know exactly which chunk indices were verified before the drop.

- **End-to-end integrity check.**
  Beyond the per-chunk GCM tags, the sender computes a SHA-256 hash of the full original file and sends it (encrypted) as part of the file's metadata. After reassembly, the receiver re-hashes the decrypted file and compares — this catches chunk-ordering or reassembly bugs that are logically distinct from cryptographic tampering.

- **Replay/session binding.**
  A random session ID generated during the handshake is mixed into the key derivation, so a captured handshake transcript can't be replayed later to open a fresh session with a stolen key.

**Suggested Android implementation**
- **Google Tink** is the recommended library — it wraps X25519, HKDF, and AES-GCM behind a high-level API specifically designed to avoid the common implementation mistakes (nonce reuse, missing authentication) that come from calling `javax.crypto` primitives directly.
- **libsodium** (via the `lazysodium-android` binding) is a solid alternative, offering the same primitive set and a long audit history.
- Hand-rolling raw AES/ECDH calls without a vetted wrapper library is not recommended — nonce reuse and silent tag-verification bypass are the most common real-world bugs in DIY crypto code.

**What this does and doesn't protect against**
- ✅ Protects against passive eavesdropping on the Bluetooth/Wi-Fi Direct channel itself.
- ✅ Protects against an active attacker trying to intercept the connection during pairing — *as long as the user actually checks the verification code.*
- ✅ Detects corruption or tampering of file data in transit.
- ❌ Does not protect against a compromised device where malware already has access to the files after they're decrypted and saved to disk — end-to-end encryption secures the *transfer*, not the endpoints.

### 1.4 New Screens Required (consolidated)
10. Secure Direct Send (`SecureP2PActivity.kt`) — encrypted mode + key verification UI
11. QR Scan/Display screen — pairing via camera scan
12. Incoming Transfer Confirmation dialog — accept/reject with file list preview
13. Trusted Devices management screen (under Settings)
14. Pro / Upgrade screen — paywall presenting premium features (see Monetization below)

---

## 2. Monetization Plan

### 2.1 Business Model
**Freemium: free app with ads, one-time "Pro" unlock to remove ads and access premium features.**

Rationale: Apps in this category (SHAREit, Xender, Zapya) compete almost entirely on install volume, since users expect a free local-transfer tool. A fully paid app would lose the download/discovery race before it starts. Pure ads-with-no-upgrade leaves revenue on the table from your heaviest users. Freemium captures both — casual users stay free and ad-supported, frequent/power users pay once for a better experience.

### 2.2 Free Tier (ad-supported)
- Core send/receive via Bluetooth and Wi-Fi Direct
- File selection, transfer progress, basic history (capped, e.g. last 20 entries or 30 days)
- Basic settings: dark mode, default transfer method
- Basic pairing confirmation (simple accept/reject dialog) — kept free for baseline safety/trust
- Ads shown at non-intrusive points (see 2.4)

### 2.3 Premium Tier — "Just-Share Pro"
- 🔒 Secure Direct Send (E2E encrypted mode + key verification)
- Group/broadcast send to multiple devices at once
- No ads, anywhere in the app
- Resume interrupted transfers
- Trusted device list with auto-accept
- Unlimited history with search & filter
- Priority/optimized transfer speed (if compression or protocol tuning is added later)
- Early access to future premium features as they ship

### 2.4 Ad Implementation
| Placement | Ad type | Notes |
|---|---|---|
| Send/Receive home screen | Banner | Always visible, low friction |
| History screen | Banner | Static, doesn't interrupt a task |
| After a completed transfer | Interstitial | Natural break point — **never** during an active transfer or on the progress screen, since that's where users are waiting and most likely to leave a bad review |
| Optional: unlock a one-off premium action (e.g. one group-send) without buying Pro | Rewarded video | Lets curious users try a premium feature before committing to purchase |

### 2.5 Pricing Strategy
- **One-time unlock recommended over subscription** (e.g. $2.99–$4.99): file-transfer is a utility, not an ongoing service in the user's mind, so recurring billing invites cancellations and poor reviews.
- Subscription only makes sense if you commit to shipping premium features regularly (e.g. cloud fallback, cross-platform sync) — otherwise stick to one-time Pro unlock.
- Consider a short free trial of Pro (e.g. 3 days) triggered from the paywall screen to increase conversion.

### 2.6 Revenue Optimization Ideas (additional suggestions)
- **Referral unlock**: invite N friends who install the app → unlock Pro free (drives organic installs at zero ad cost)
- **Rewarded-ad trial**: watch a short video to unlock one premium action (e.g. one encrypted transfer or one group send) — good funnel into a full purchase
- **Seasonal/launch discount** on the Pro unlock to drive early conversion and reviews
- **B2B/event mode (longer-term idea)**: a "Business" tier for retail/event use cases (e.g. sharing marketing files with many nearby devices at once) — could be a separate paid SKU down the line, not needed for v1

### 2.7 Trust Note
Don't lock *all* security behind the paywall — keep a basic accept/reject confirmation free. Reviewers and users react badly to safety features being pay-walled; it should feel like Pro adds *convenience and power features*, not baseline protection.

---

## 3. Figma / Stitch Design Prompt (copy-paste ready)

Copy everything below into Stitch or a Figma AI prompt field to generate the full UI set, extending your existing screens with the new features and monetization elements.

```
Design a complete mobile app UI kit for "Just-Share," a local peer-to-peer file transfer app (Android) for photos, videos, documents, and audio files transferred via Bluetooth and Wi-Fi Direct — no internet/cloud required.

THEME
- Primary color: Red (#EC1C22)
- Secondary colors: Light Red (#FFCDD2), Dark Red (#B71C1C)
- Typography: Roboto and Inter
- Shape language: RoundedCornerShape(20dp) heavily used on cards, buttons, and list items
- Style: clean, modern, minimal, high contrast, generous whitespace, bold pill-shaped buttons

EXISTING SCREENS TO DESIGN (maintain visual consistency across all of these)
1. Welcome/Splash — bold red/black headline "Welcome to Just Share," centered Lottie-style animation placeholder, subtitle describing quick transfer of photos/videos/docs/audio, large pill red "Continue" button at bottom
2. Main/Permissions — connectivity-themed animation placeholder, permission explanation text, single CTA button to trigger permission requests
3. Send or Receive Selection — two large equally-sized vertical cards: "SEND" (up arrow icon) on top, "RECEIVE" (down arrow icon) below, plus a bottom navigation bar linking to History and Settings
4. File Selection — top app bar "Select Files" with back button, tab row for Images/Videos/Audio, LazyVerticalGrid of thumbnails, selected items show red tint overlay + checkmark badge, floating action button showing selected file count
5. Device Discovery (Bluetooth) — header "Available Devices," list of discovered devices (name + MAC address), separate "Paired Devices" section, scanning/radar animation placeholder during discovery
6. Device Discovery (Wi-Fi Direct) — header "Wi-Fi Direct Peers," list of available P2P devices, status indicator for Wi-Fi Direct enabled/disabled, tap to connect
7. Transfer Progress screen — header "Transfer Process," file-transfer animation placeholder, card containing a scrollable list of files being transferred (icon, name, size, progress), pill-shaped progress bar under the active file, green checkmark for completed files, bottom bar with Disconnect button and (Bluetooth only) a message input row
8. Transfer History — header "Transfer History" with a trash "Clear All" icon, scrollable list with file icon, file name, "Sent to [Device]" / "Received from [Device]" subtitle, size/date/method metadata, empty state illustration for no history
9. Settings — header "Settings," dark mode toggle, default transfer method radio buttons (Bluetooth vs Wi-Fi Direct), app version text

NEW SCREENS TO ADD (extend the existing design system to these)
10. Secure Direct Send — connection screen with a shield/padlock badge reading "End-to-End Encrypted," a key-verification step showing a short numeric/emoji code on both devices for the user to visually confirm, a toggle in Settings for "Always require encryption verification," and an "🔒 Encrypted" tag on file rows during transfer
11. QR Scan/Display — full-screen camera scan view with a rounded scan-frame overlay, and a companion "Show My QR Code" screen for the receiving device
12. Incoming Transfer Confirmation — modal/bottom-sheet dialog showing sender device name, a preview list of incoming files (icons + sizes), and Accept/Reject pill buttons
13. Trusted Devices — settings sub-screen listing trusted and blocked devices with toggle/remove actions, and an "Add Trusted Device" flow
14. Pro / Upgrade screen — paywall screen listing premium features (Secure Direct Send, Group Send, No Ads, Resume Transfers, Trusted Devices, Unlimited History) each with a small icon, a price and "Unlock Pro" pill CTA, and a "Restore Purchase" text link

MONETIZATION UI ELEMENTS TO INCLUDE
- Banner ad placeholder (labeled "Ad Banner") docked at the bottom of the Send/Receive home screen and the History screen — never on the Transfer Progress screen
- Interstitial ad placeholder screen shown after a completed transfer, with a visible close (X) button and a "Remove Ads — Go Pro" link beneath it
- Small "PRO" badge component (gold/dark-red pill) used to mark locked premium features throughout the app (e.g. on Secure Direct Send, Group Send, Trusted Devices) that links to the Upgrade screen when tapped
- Rewarded-ad prompt component: "Watch an ad to try this feature once" button shown when a free user taps a locked Pro feature

DELIVERABLE
Produce a consistent design system (color tokens, type scale, spacing scale, corner-radius token, button/card/list-item components) applied across all 14 screens above, plus the reusable PRO badge, ad placeholder, and paywall components. Keep visual language consistent with the existing red/black Just-Share brand.
```
