package com.blackandblue.justshare.data.altersend

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.net.Socket

/**
 * [RelayTransport] backed by a plain TCP [Socket].
 *
 * Wire format — identical to the existing relay framing so the Python relay
 * and all in-flight sessions remain unaffected:
 *   [4 bytes big-endian length][<length> bytes payload]
 *
 * This class takes ownership of [socket] and closes it when [close] is called.
 */
class DirectSocketTransport(private val socket: Socket) : RelayTransport {

    private val input  = DataInputStream(socket.getInputStream())
    private val output = DataOutputStream(socket.getOutputStream())

    override fun sendBytes(payload: ByteArray) {
        synchronized(output) {
            output.writeInt(payload.size)
            output.write(payload)
            output.flush()
        }
    }

    override fun readBytes(): ByteArray {
        val size = try {
            input.readInt()
        } catch (e: EOFException) {
            throw EOFException("Socket closed by peer")
        }
        require(size >= 0 && size <= MAX_FRAME_BYTES) { "Invalid frame size: $size" }
        val buffer = ByteArray(size)
        input.readFully(buffer)
        return buffer
    }

    override fun close() {
        runCatching { socket.close() }
    }

    companion object {
        /** Must match SecureChannel's own limit (16 MiB). */
        private const val MAX_FRAME_BYTES = 16 * 1024 * 1024
    }
}
