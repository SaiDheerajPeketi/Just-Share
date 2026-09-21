/**
 * Just Share – Cloudflare Relay Worker (main entry point)
 *
 * Routes:
 *   GET /v1/session/:id  (WebSocket upgrade)
 *       Query params:
 *         role   = "sender" | "receiver"
 *         token  = HMAC-SHA256 hex of "<sessionId>:<role>:<expiry>:<limit>" signed with
 *                  CF_RELAY_HMAC_SECRET  (held only by the Worker and token service)
 *         expiry = Unix epoch seconds (token lifetime; Android uses +300s)
 *         limit  = Reserved byte ceiling returned by the token service
 *
 *   GET /v1/health
 *       Returns 200 { status: "ok" } — useful for monitoring.
 *
 * Validation order:
 *   1. WebSocket upgrade present
 *   2. Session ID format (hex, 32 chars)
 *   3. Role is "sender" or "receiver"
 *   4. Token not expired
 *   5. HMAC-SHA256 signature matches
 *
 * On success the request is forwarded to the matching Durable Object, which
 * handles all further WebSocket lifecycle events.
 */

import { RelayDurableObject } from "./RelayDurableObject";

export { RelayDurableObject };

// ── Env bindings (declared in wrangler.toml / secrets) ────────────────────
export interface Env {
  /** Durable Object namespace — bound as RELAY_SESSION in wrangler.toml. */
  RELAY_SESSION: DurableObjectNamespace;

  /**
   * 32-byte hex HMAC secret.  Set via:
   *   npx wrangler secret put CF_RELAY_HMAC_SECRET
   * Must match the protected backend token-service secret. Never ship it in the app.
   */
  CF_RELAY_HMAC_SECRET: string;

  /** Per-session byte quota (default 5 GiB, set in [vars]). */
  CF_RELAY_SESSION_QUOTA_BYTES: string;

  /** Session TTL in seconds for abandoned sessions (set in [vars]). */
  CF_RELAY_SESSION_TTL_SECONDS: string;
}

// ── Constants ──────────────────────────────────────────────────────────────
const SESSION_ID_RE = /^[0-9a-f]{32}$/i;

// ── Main Worker handler ────────────────────────────────────────────────────
export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);

    // Health check endpoint
    if (url.pathname === "/v1/health") {
      return jsonResponse({ status: "ok" }, 200);
    }

    // Route: /v1/session/:id
    const sessionMatch = url.pathname.match(/^\/v1\/session\/([^/]+)$/);
    if (!sessionMatch) {
      return jsonResponse({ error: "Not found" }, 404);
    }

    // ── 1. WebSocket upgrade check ─────────────────────────────────────────
    if (request.headers.get("Upgrade") !== "websocket") {
      return jsonResponse({ error: "Expected WebSocket upgrade" }, 426);
    }

    // ── 2. Session ID validation ───────────────────────────────────────────
    const sessionId = sessionMatch[1];
    if (!SESSION_ID_RE.test(sessionId)) {
      return jsonResponse({ error: "Invalid session ID" }, 400);
    }

    // ── 3. Role validation ─────────────────────────────────────────────────
    const role = url.searchParams.get("role");
    if (role !== "sender" && role !== "receiver") {
      return jsonResponse({ error: "role must be sender or receiver" }, 400);
    }

    // ── 4 & 5. Token expiry + HMAC signature validation ───────────────────
    const token  = url.searchParams.get("token")  ?? "";
    const expiry = url.searchParams.get("expiry") ?? "";
    const limit = url.searchParams.get("limit") ?? "";

    const expiryTs = parseInt(expiry, 10);
    const limitBytes = Number(limit);
    const configuredLimit = Number(env.CF_RELAY_SESSION_QUOTA_BYTES);
    if (
      isNaN(expiryTs) ||
      Date.now() / 1000 > expiryTs ||
      !Number.isSafeInteger(limitBytes) ||
      limitBytes <= 0 ||
      limitBytes > configuredLimit
    ) {
      return jsonResponse({ error: "Token expired or missing expiry" }, 401);
    }

    const isValid = await verifyHmac(
      env.CF_RELAY_HMAC_SECRET,
      `${sessionId}:${role}:${expiry}:${limit}`,
      token
    );
    if (!isValid) {
      return jsonResponse({ error: "Invalid token" }, 401);
    }

    // ── Forward to the Durable Object for this session ────────────────────
    // Derive a deterministic DO id from the session id so that both the
    // sender and receiver always hit the exact same object instance.
    const doId    = env.RELAY_SESSION.idFromName(sessionId);
    const doStub  = env.RELAY_SESSION.get(doId);

    // Pass role to the DO via query param so it can tag the socket.
    const doUrl = new URL(request.url);
    doUrl.pathname = "/";
    doUrl.search   = `?role=${role}&limit=${limit}`;

    return doStub.fetch(new Request(doUrl.toString(), request));
  },
};

// ── Helpers ────────────────────────────────────────────────────────────────

function jsonResponse(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

/**
 * Verify an HMAC-SHA256 signature.
 * @param secret  Hex-encoded 32-byte key (from wrangler secret)
 * @param message The exact string that was signed by the backend token service
 * @param hex     The hex signature to verify
 */
async function verifyHmac(
  secret: string,
  message: string,
  hex: string
): Promise<boolean> {
  try {
    const keyBytes = hexToBytes(secret);
    const cryptoKey = await crypto.subtle.importKey(
      "raw",
      keyBytes,
      { name: "HMAC", hash: "SHA-256" },
      false,
      ["verify"]
    );
    const sigBytes  = hexToBytes(hex);
    const msgBytes  = new TextEncoder().encode(message);
    return await crypto.subtle.verify("HMAC", cryptoKey, sigBytes, msgBytes);
  } catch {
    return false;
  }
}

function hexToBytes(hex: string): Uint8Array {
  if (hex.length % 2 !== 0) throw new Error("Invalid hex string");
  const bytes = new Uint8Array(hex.length / 2);
  for (let i = 0; i < bytes.length; i++) {
    bytes[i] = parseInt(hex.slice(i * 2, i * 2 + 2), 16);
  }
  return bytes;
}
