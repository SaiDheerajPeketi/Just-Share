package com.invincible.jedishare.domain.chat

import timber.log.Timber

/**
 * Sealed interface representing all possible results from a Bluetooth connection flow.
 *
 * Added [EndOfFile] to signal that the current file's transfer is complete,
 * replacing the fragile size-comparison sentinel that was previously used.
 */
sealed interface ConnectionResult {
    /** The physical Bluetooth connection has been successfully established. */
    object ConnectionEstablished : ConnectionResult

    /** A chunk of file data was received. */
    data class TransferSucceeded(val message: ByteArray) : ConnectionResult

    /** The current file has been fully transferred (EOF sentinel received). */
    object EndOfFile : ConnectionResult

    /** An error occurred during the connection or transfer. */
    data class Error(val message: String) : ConnectionResult
}