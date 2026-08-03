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
- Hybrid connection codes in `JSASH1:<directHost>:<directPort>:<relayHost>:<relayPort>:<relaySessionId>:<topic>` format. When a relay endpoint is reachable, real devices advertise this hybrid code. The sender waits briefly for a direct TCP peer; if none arrives, it registers with the relay. The receiver tries direct TCP first and falls back automatically.
- `scripts/altersend_relay.py` is a TCP pipe relay for emulator/manual testing or a small production deployment. It handles many concurrent pending sessions, expires unmatched registrations, and sees only encrypted frames after pairing the sender and receiver by `relaySessionId`.
- Production relay configuration is build-time. Public relay nodes can be supplied as `ALTERSEND_PUBLIC_RELAY_NODES=host1:41404,host2:41404`. Your own relay can be supplied as `ALTERSEND_RELAY_HOST=<domain>` and optionally `ALTERSEND_RELAY_PORT=<port>`. Use a DNS name such as `relay.example.com` pointing to your public IP so you can move the relay later by changing DNS without rebuilding the app. Real devices probe relay endpoints with a short TCP connect check.
- Current connection order is:
  1. Direct TCP to the advertised peer endpoint.
  2. Hole punching: not implemented yet.
  3. First reachable public relay from `ALTERSEND_PUBLIC_RELAY_NODES`.
  4. Your configured relay domain from `ALTERSEND_RELAY_HOST`.
  If no relay is reachable, real devices keep using direct-only `JSAS1` codes.
- Ephemeral ECDH handshake per connection using the connection topic as transcript context.
- HKDF-HMAC-SHA256 key derivation into separate client-to-server and server-to-client AES keys.
- AES-GCM encrypted length-prefixed frames with monotonic per-direction counters.
- AlterSend chunk geometry: 64KB under 1MB, 256KB under 100MB, 1MB under 10GB, and 4MB beyond that.
- Live transfer bitmap semantics matching upstream bit order.
- A Kotlin drive transfer engine that reads by offset, writes by offset, supports resume bits, validates expected size, and aborts on integrity errors.
- Socket transfer frames for manifest, start, need, chunk, complete, ack, and error.
- Receiver-side accept/reject before any file bytes are written. Rejection is sent as an encrypted error frame and cancels the session.
- Per-chunk SHA-256 verification inside the encrypted chunk frame. If a chunk payload is malformed or its chunk hash does not match, the receiver re-sends `need` for that chunk and the sender retries it up to four times.
- SHA-256 complete-file integrity check before the receiver saves the file.
- QR generation for sender connection codes and QR scanning through Google code scanner on the receive path.
- A dataSync foreground service while AlterSend is hosting, joining, or transferring, so long-running transfers are less likely to be stopped by Android background limits.
- AlterSend mode UI entry, send code generation, receive code validation, progress display, explicit completion cleanup/navigation, settings persistence, and history metadata.

Compatibility note:

- This Kotlin transport is not wire-compatible with upstream AlterSend's Hyperswarm/Noise/Protomux worklet.
- It is the native Android equivalent used by Just-Share when the user selects AlterSend.
- It does not join the upstream Hyperswarm DHT. Direct sockets, relay-only codes, and direct-first relay fallback are implemented. True DHT discovery and UDP/TCP hole punching still require a compatible rendezvous/bootstrap protocol, peer public endpoint exchange, simultaneous punch attempts, and either UDP-based transport or carefully managed TCP simultaneous-open behavior. That hole-punching layer is not bundled with the Android app yet.
- Bluetooth and Wi-Fi Direct remain separate local transfer modes.

## Integration Test Plan

1. Unit test topic validation, chunk tiers, chunk ranges, bitmap serialization, drive transfer success, resume bits, wrong size rejection, and short chunk rejection.
2. Run two Android devices or emulators on a reachable network:
   - For two AVDs, start the relay on the host first:
     `python3 scripts/altersend_relay.py`
   - On a server, run the relay with a public interface and firewall-open port:
     `python3 scripts/altersend_relay.py --host 0.0.0.0 --port 41404`
   - For unattended hosting, run the same command under systemd, launchd, Docker, or your process manager of choice and point `ALTERSEND_RELAY_HOST` at that server.
   - Sender picks files and displays a join code.
   - Receiver enters the code, copies it from another app, or scans the sender QR code.
   - Verify both devices reach connected state over direct socket or relay socket.
   - With a production relay configured, test `JSASH1` by placing devices on the same Wi-Fi and verifying direct connection completes before fallback.
   - Put the receiver on mobile data and sender behind home NAT; verify direct fails and relay fallback completes.
   - Reject the incoming offer once; verify sender fails with "Receiver rejected transfer" and receiver returns to a cancelled state without saving a file.
   - Transfer a small file, a 150MB file, and a multi-GB file.
   - Kill the receiver app during transfer; verify no finalized partial file remains.
   - Pause/resume during the same live session; verify missing chunks are requested from the bitmap.
   - Corrupt a chunk payload in a loopback/fault-injection channel; verify the receiver re-requests that chunk and the transfer continues.
   - Corrupt a finalized file hash in a loopback/fault-injection channel; verify the receiver fails with an integrity error and does not save the file.
   - Background the app during a large transfer; verify the AlterSend foreground notification is visible until the transfer completes, fails, or is cancelled.
3. Regression test Bluetooth and Wi-Fi Direct separately on physical devices, because stock Android emulators do not provide real Bluetooth or Wi-Fi Direct radios.
