package com.blackandblue.justshare.data.altersend

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Generates the short-lived HMAC-SHA256 token that the Android app sends to
 * the Cloudflare Worker for authentication.
 *
 * The signed message is: "<sessionId>:<role>:<expiry>"
 * This matches the string the Worker verifies in src/index.ts → verifyHmac().
 *
 * The secret ([BuildConfig.CF_RELAY_HMAC_SECRET]) is a 32-byte hex string set
 * in local.properties and must match the Wrangler secret on the Worker side.
 */
object CloudflareRelayToken {

    /** Default token lifetime: 5 minutes (300 seconds). */
    private const val DEFAULT_TTL_SECONDS = 300L

    /**
     * Generate a signed token for the given [sessionId] and [role].
     *
     * @param secretHex  [BuildConfig.CF_RELAY_HMAC_SECRET] — hex-encoded key
     * @param sessionId  32-char hex session id
     * @param role       "sender" or "receiver"
     * @param ttlSeconds How long the token is valid (default 5 minutes)
     * @return Pair of (token hex string, expiry unix epoch seconds)
     */
    fun generate(
        secretHex:  String,
        sessionId:  String,
        role:       String,
        ttlSeconds: Long = DEFAULT_TTL_SECONDS
    ): Pair<String, Long> {
        val expiry  = System.currentTimeMillis() / 1000 + ttlSeconds
        val message = "$sessionId:$role:$expiry"
        val keyBytes = hexToBytes(secretHex)
        val mac = Mac.getInstance("HmacSHA256").apply {
            init(SecretKeySpec(keyBytes, "HmacSHA256"))
        }
        val token = mac.doFinal(message.encodeToByteArray()).toHex()
        return token to expiry
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Invalid hex string length" }
        return ByteArray(hex.length / 2) { i ->
            hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
    }

    private fun ByteArray.toHex(): String =
        joinToString("") { "%02x".format(it.toInt() and 0xff) }
}
