package com.invincible.jedishare.domain.chat

sealed interface ConnectionResult {
    object ConnectionEstablished: ConnectionResult
    data class TransferSucceeded(val message: ByteArray): ConnectionResult
    data class Error(val message: String): ConnectionResult
}