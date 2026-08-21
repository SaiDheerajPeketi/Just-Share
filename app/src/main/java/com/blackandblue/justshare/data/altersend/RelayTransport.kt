package com.blackandblue.justshare.data.altersend

/**
 * Minimal bidirectional byte-transport used by [AlterSendSocketTransfer].
 *
 * The [AlterSendSocketTransfer] keeps all encryption/framing logic unchanged
 * above this interface: each call to [readBytes] / [sendBytes] carries one
 * opaque, already-encrypted payload (or one encrypted chunk of payload for
 * large frames).  Cloudflare only ever sees ciphertext.
 *
 * Two implementations exist:
 *  - [DirectSocketTransport]       — wraps the existing raw TCP [java.net.Socket]
 *  - [CloudflareWebSocketTransport] — OkHttp WebSocket to the Cloudflare DO
 */
interface RelayTransport {

    /**
     * Send a binary payload.  Must be called from the IO thread.
     * Implementations must be safe to call concurrently with [readBytes].
     * @throws java.io.IOException if the transport is closed or an error occurs.
     */
    fun sendBytes(payload: ByteArray)

    /**
     * Block until a full binary payload arrives and return it.
     * Must be called from the IO thread.
     * @throws java.io.IOException on transport error or closure.
     * @throws java.io.EOFException when the remote peer closes cleanly.
     */
    fun readBytes(): ByteArray

    /**
     * Close the underlying transport immediately.
     * Safe to call multiple times and from any thread.
     */
    fun close()
}
