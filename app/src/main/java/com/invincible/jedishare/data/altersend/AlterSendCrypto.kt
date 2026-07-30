package com.invincible.jedishare.data.altersend

import com.invincible.jedishare.domain.altersend.toHex
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PublicKey
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

data class AlterSendHandshakeKeys(
    val sendKey: ByteArray,
    val receiveKey: ByteArray,
    val sessionId: String
)

object AlterSendCrypto {
    private const val AES_KEY_BYTES = 32
    private const val GCM_TAG_BITS = 128
    private const val GCM_NONCE_BYTES = 12

    fun generateKeyPair(): KeyPair {
        val generator = KeyPairGenerator.getInstance("EC")
        generator.initialize(java.security.spec.ECGenParameterSpec("secp256r1"))
        return generator.generateKeyPair()
    }

    fun decodePublicKey(bytes: ByteArray): PublicKey {
        return KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(bytes))
    }

    fun deriveKeys(
        privateKey: java.security.PrivateKey,
        clientPublic: ByteArray,
        serverPublic: ByteArray,
        topicHex: String,
        isClient: Boolean
    ): AlterSendHandshakeKeys {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(decodePublicKey(if (isClient) serverPublic else clientPublic), true)
        val sharedSecret = agreement.generateSecret()

        val transcript = MessageDigest.getInstance("SHA-256").digest(
            topicHex.encodeToByteArray() + clientPublic + serverPublic
        )
        val c2s = hkdf(sharedSecret, transcript, "JustShare-AlterSend-v1-client-to-server".encodeToByteArray(), AES_KEY_BYTES)
        val s2c = hkdf(sharedSecret, transcript, "JustShare-AlterSend-v1-server-to-client".encodeToByteArray(), AES_KEY_BYTES)
        val sessionId = MessageDigest.getInstance("SHA-256").digest(transcript + sharedSecret).toHex()

        return if (isClient) {
            AlterSendHandshakeKeys(sendKey = c2s, receiveKey = s2c, sessionId = sessionId)
        } else {
            AlterSendHandshakeKeys(sendKey = s2c, receiveKey = c2s, sessionId = sessionId)
        }
    }

    fun encrypt(key: ByteArray, counter: Long, plaintext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce(counter)))
        return cipher.doFinal(plaintext)
    }

    fun decrypt(key: ByteArray, counter: Long, ciphertext: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(GCM_TAG_BITS, nonce(counter)))
        return cipher.doFinal(ciphertext)
    }

    private fun hkdf(secret: ByteArray, salt: ByteArray, info: ByteArray, size: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(salt, "HmacSHA256"))
        val prk = mac.doFinal(secret)
        val out = ByteArray(size)
        var previous = ByteArray(0)
        var written = 0
        var counter = 1
        while (written < size) {
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            previous = mac.doFinal(previous + info + counter.toByte())
            val toCopy = minOf(previous.size, size - written)
            previous.copyInto(out, destinationOffset = written, endIndex = toCopy)
            written += toCopy
            counter++
        }
        return out
    }

    private fun nonce(counter: Long): ByteArray {
        val nonce = ByteArray(GCM_NONCE_BYTES)
        for (i in 0 until 8) {
            nonce[GCM_NONCE_BYTES - 1 - i] = (counter ushr (i * 8)).toByte()
        }
        return nonce
    }
}
