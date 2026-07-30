# AlterSend Protocol Notes

This app treats AlterSend as a third transfer mode. Bluetooth and Wi-Fi Direct remain local transfer modes and are not modified by AlterSend.

## Upstream AlterSend Model

Current AlterSend is implemented as a Bare worklet used by the desktop and React Native mobile shells.

- A sender generates a random 32-byte topic and displays it as a 64-character hex join code, usually also as a QR code.
- A receiver scans or enters the topic.
- The worklet derives the Hyperswarm discovery key from that topic and joins the DHT as both client and server.
- Peer sockets are Noise-encrypted by Hyperswarm / secret-stream.
- File bytes move over the `altersend/drive` Protomux channel.
- The sender announces `{ transferId, name, size, chunkSize }`.
- The receiver validates file size and chunk geometry, allocates the target file, and sends `need` messages for missing chunk indices.
- The sender streams requested chunks by `{ transferId, index, data }`.
- The receiver writes each chunk at its offset and tracks verified chunks with a bitmap.
- The sender sends `complete`; the receiver finalizes only when all chunks are present, then replies with `ack`.
- On disconnect, upstream AlterSend wipes the transfer corestore. Cross-session resume is currently out of scope upstream. In-session pause/resume uses the live chunk bitmap.

## Kotlin Port In This App

Implemented:

- 64-character topic generation and validation.
- AlterSend chunk geometry: 64KB under 1MB, 256KB under 100MB, 1MB under 10GB, and 4MB beyond that.
- Live transfer bitmap semantics matching upstream bit order.
- A Kotlin drive transfer engine that reads by offset, writes by offset, supports resume bits, validates expected size, and aborts on integrity errors.
- AlterSend mode UI entry, send code generation, receive code validation, settings persistence, and history metadata.

Not yet bundled:

- Bare Kit Android worklet runtime.
- Hyperswarm DHT discovery.
- Noise-encrypted peer sockets.
- Protomux channel bridge to the Kotlin UI state.
- QR encoding/scanning for the join code.

Do not replace the missing runtime with ad hoc Android sockets if exact AlterSend compatibility is required.

## Integration Test Plan

1. Unit test topic validation, chunk tiers, chunk ranges, bitmap serialization, drive transfer success, resume bits, wrong size rejection, and short chunk rejection.
2. After Bare Kit is bundled, run two Android devices or emulators with internet access:
   - Sender picks files and displays a join code.
   - Receiver enters or scans the code.
   - Verify both devices reach connected state through Hyperswarm.
   - Transfer a small file, a 150MB file, and a multi-GB file.
   - Kill the receiver app during transfer; verify upstream-compatible disconnect behavior and no finalized partial file.
   - Pause/resume during the same live session; verify missing chunks are requested from the bitmap.
   - Corrupt a chunk in a loopback/fault-injection channel; verify the receiver aborts or re-requests according to the final worklet bridge behavior.
3. Regression test Bluetooth and Wi-Fi Direct separately on physical devices, because stock Android emulators do not provide real Bluetooth or Wi-Fi Direct radios.
