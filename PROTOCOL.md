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

- Connection codes in `JSAS1:<host>:<port>:<topic>` format. The topic remains a random 32-byte value encoded as 64 hex characters.
- Direct peer socket transport. The sender opens a local server socket and the receiver connects to the advertised host/port. This works on the same network and across the internet only when the sender address is reachable through routing/firewall/NAT.
- Relay connection codes in `JSASR1:<relayHost>:<relayPort>:<relaySessionId>:<topic>` format. AVD builds auto-use `10.0.2.2:41404`, because `10.0.2.15` is per-emulator loopback and cannot connect two AVDs.
- `scripts/altersend_relay.py` is a tiny TCP pipe relay for emulator/manual testing. It sees only encrypted frames after pairing the sender and receiver by `relaySessionId`.
- Ephemeral ECDH handshake per connection using the connection topic as transcript context.
- HKDF-HMAC-SHA256 key derivation into separate client-to-server and server-to-client AES keys.
- AES-GCM encrypted length-prefixed frames with monotonic per-direction counters.
- AlterSend chunk geometry: 64KB under 1MB, 256KB under 100MB, 1MB under 10GB, and 4MB beyond that.
- Live transfer bitmap semantics matching upstream bit order.
- A Kotlin drive transfer engine that reads by offset, writes by offset, supports resume bits, validates expected size, and aborts on integrity errors.
- Socket transfer frames for manifest, start, need, chunk, complete, ack, and error.
- SHA-256 complete-file integrity check before the receiver saves the file.
- AlterSend mode UI entry, send code generation, receive code validation, progress display, settings persistence, and history metadata.

Compatibility note:

- This Kotlin transport is not wire-compatible with upstream AlterSend's Hyperswarm/Noise/Protomux worklet.
- It is the native Android equivalent used by Just-Share when the user selects AlterSend.
- Bluetooth and Wi-Fi Direct remain separate local transfer modes.
- QR encoding/scanning for the connection code is still a UI enhancement; manual code copy/paste is implemented.

## Integration Test Plan

1. Unit test topic validation, chunk tiers, chunk ranges, bitmap serialization, drive transfer success, resume bits, wrong size rejection, and short chunk rejection.
2. Run two Android devices or emulators on a reachable network:
   - For two AVDs, start the relay on the host first:
     `python3 scripts/altersend_relay.py`
   - Sender picks files and displays a join code.
   - Receiver enters or scans the code.
   - Verify both devices reach connected state over direct socket or relay socket.
   - Transfer a small file, a 150MB file, and a multi-GB file.
   - Kill the receiver app during transfer; verify no finalized partial file remains.
   - Pause/resume during the same live session; verify missing chunks are requested from the bitmap.
   - Corrupt a chunk in a loopback/fault-injection channel; verify the receiver fails with an integrity error.
3. Regression test Bluetooth and Wi-Fi Direct separately on physical devices, because stock Android emulators do not provide real Bluetooth or Wi-Fi Direct radios.
