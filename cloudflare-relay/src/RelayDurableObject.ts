/**
 * Just Share – Relay Durable Object
 *
 * One instance per session ID. Accepts exactly one 'sender' and one 'receiver'
 * WebSocket, then blindly forwards binary frames between them.
 *
 * Design decisions:
 *  - Uses the Hibernation API so the DO costs nothing while both sockets are
 *    idle between frames (avoids per-second wall-clock billing).
 *  - Stores only tiny session metadata (no file data ever touches memory here).
 *  - Enforces a per-session byte quota read from the Worker env.
 *  - Alarms are used for session expiry / abandoned-session cleanup.
 *  - Never inspects payload contents – Cloudflare sees only ciphertext.
 */

import { DurableObject } from "cloudflare:workers";

// ── Tag constants sent by the Android client during the WebSocket handshake ──
const ROLE_SENDER   = "sender";
const ROLE_RECEIVER = "receiver";

// Close codes (4xxx = application-defined)
const CLOSE_SESSION_EXPIRED    = 4001;
const CLOSE_QUOTA_EXCEEDED     = 4002;
const CLOSE_DUPLICATE_ROLE     = 4003;
const CLOSE_PEER_DISCONNECTED  = 4004;

interface SessionMeta {
  senderAttached:   boolean;
  receiverAttached: boolean;
  bytesRelayed:     number;
  quotaBytes:       number;
  createdAt:        number;  // epoch ms
}

export class RelayDurableObject extends DurableObject {
  private meta: SessionMeta = {
    senderAttached:   false,
    receiverAttached: false,
    bytesRelayed:     0,
    quotaBytes:       Number(this.env.CF_RELAY_SESSION_QUOTA_BYTES ?? 5_368_709_120),
    createdAt:        Date.now(),
  };

  // ── WebSocket message handler (Hibernation API) ───────────────────────────

  async fetch(request: Request): Promise<Response> {
    // Upgrade check is done in the main Worker; here we accept.
    const { 0: client, 1: server } = new WebSocketPair();

    const url    = new URL(request.url);
    const role   = url.searchParams.get("role");

    if (role !== ROLE_SENDER && role !== ROLE_RECEIVER) {
      return new Response("Invalid role", { status: 400 });
    }

    // Reject duplicate roles (e.g. two senders racing)
    if (role === ROLE_SENDER   && this.meta.senderAttached)   {
      server.close(CLOSE_DUPLICATE_ROLE, "Sender already attached");
      return new Response(null, { status: 101, webSocket: client });
    }
    if (role === ROLE_RECEIVER && this.meta.receiverAttached) {
      server.close(CLOSE_DUPLICATE_ROLE, "Receiver already attached");
      return new Response(null, { status: 101, webSocket: client });
    }

    // Accept and tag the socket so we know its role in message/close callbacks.
    this.ctx.acceptWebSocket(server, [role]);

    if (role === ROLE_SENDER)   this.meta.senderAttached   = true;
    if (role === ROLE_RECEIVER) this.meta.receiverAttached = true;

    // Set an alarm to expire this session if it is abandoned.
    const ttlMs = Number(this.env.CF_RELAY_SESSION_TTL_SECONDS ?? 3600) * 1000;
    await this.ctx.storage.setAlarm(Date.now() + ttlMs);

    return new Response(null, { status: 101, webSocket: client });
  }

  // ── Hibernation callbacks ─────────────────────────────────────────────────

  async webSocketMessage(
    ws: WebSocket,
    message: string | ArrayBuffer
  ): Promise<void> {
    // Only forward binary frames — the app never sends text frames here.
    if (typeof message === "string") return;

    // Quota guard
    this.meta.bytesRelayed += message.byteLength;
    if (this.meta.bytesRelayed > this.meta.quotaBytes) {
      this.closeAll(CLOSE_QUOTA_EXCEEDED, "Session quota exceeded");
      return;
    }

    // Determine direction and forward to the other socket.
    const [role] = this.ctx.getTags(ws);
    const targetRole = role === ROLE_SENDER ? ROLE_RECEIVER : ROLE_SENDER;

    const peers = this.ctx.getWebSockets(targetRole);
    if (peers.length === 0) {
      // Peer not yet connected – queue is not used; caller handles backpressure.
      return;
    }

    for (const peer of peers) {
      try {
        peer.send(message);
      } catch {
        // Peer closed mid-send; let the close callback tidy up.
      }
    }
  }

  async webSocketClose(
    ws: WebSocket,
    code: number,
    reason: string,
    _wasClean: boolean
  ): Promise<void> {
    const [role] = this.ctx.getTags(ws);
    if (role === ROLE_SENDER)   this.meta.senderAttached   = false;
    if (role === ROLE_RECEIVER) this.meta.receiverAttached = false;

    // Notify the other side so it can surface a clean error to the user.
    const targetRole = role === ROLE_SENDER ? ROLE_RECEIVER : ROLE_SENDER;
    for (const peer of this.ctx.getWebSockets(targetRole)) {
      try {
        peer.close(CLOSE_PEER_DISCONNECTED, "Peer disconnected");
      } catch { /* already closed */ }
    }
  }

  async webSocketError(ws: WebSocket, _error: unknown): Promise<void> {
    ws.close(1011, "Internal error");
  }

  // ── Alarm: session TTL expiry ─────────────────────────────────────────────

  async alarm(): Promise<void> {
    this.closeAll(CLOSE_SESSION_EXPIRED, "Session expired");
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private closeAll(code: number, reason: string): void {
    for (const ws of this.ctx.getWebSockets()) {
      try { ws.close(code, reason); } catch { /* already closed */ }
    }
  }
}
