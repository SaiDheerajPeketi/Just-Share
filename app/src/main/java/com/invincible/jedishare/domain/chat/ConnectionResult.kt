package com.invincible.jedishare.domain.chat

sealed interface ConnectionResult {
    object ConnectionEstablished: ConnectionResult
    data class TransferSucceeded(val message: FileData?): ConnectionResult
    data class Error(val message: String): ConnectionResult
}