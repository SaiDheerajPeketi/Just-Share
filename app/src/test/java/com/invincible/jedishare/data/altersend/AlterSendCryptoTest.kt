package com.invincible.jedishare.data.altersend

import com.invincible.jedishare.domain.altersend.AlterSendInvite
import com.invincible.jedishare.domain.altersend.AlterSendInviteMode
import com.invincible.jedishare.domain.altersend.AlterSendProtocol
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AlterSendCryptoTest {
    @Test
    fun peersDeriveOppositeDirectionalKeys() {
        val client = AlterSendCrypto.generateKeyPair()
        val server = AlterSendCrypto.generateKeyPair()
        val topic = AlterSendProtocol.generateTopicHex()

        val clientKeys = AlterSendCrypto.deriveKeys(
            privateKey = client.private,
            clientPublic = client.public.encoded,
            serverPublic = server.public.encoded,
            topicHex = topic,
            isClient = true
        )
        val serverKeys = AlterSendCrypto.deriveKeys(
            privateKey = server.private,
            clientPublic = client.public.encoded,
            serverPublic = server.public.encoded,
            topicHex = topic,
            isClient = false
        )

        assertArrayEquals(clientKeys.sendKey, serverKeys.receiveKey)
        assertArrayEquals(clientKeys.receiveKey, serverKeys.sendKey)
        assertEquals(clientKeys.sessionId, serverKeys.sessionId)
        assertNotEquals(clientKeys.sendKey.toList(), clientKeys.receiveKey.toList())
    }

    @Test
    fun encryptedPayloadRoundTripsAndRejectsWrongDirectionKey() {
        val client = AlterSendCrypto.generateKeyPair()
        val server = AlterSendCrypto.generateKeyPair()
        val topic = AlterSendProtocol.generateTopicHex()
        val clientKeys = AlterSendCrypto.deriveKeys(client.private, client.public.encoded, server.public.encoded, topic, true)
        val serverKeys = AlterSendCrypto.deriveKeys(server.private, client.public.encoded, server.public.encoded, topic, false)

        val encrypted = AlterSendCrypto.encrypt(clientKeys.sendKey, 0, "hello".encodeToByteArray())

        assertEquals("hello", AlterSendCrypto.decrypt(serverKeys.receiveKey, 0, encrypted).decodeToString())
        val failed = runCatching {
            AlterSendCrypto.decrypt(serverKeys.sendKey, 0, encrypted)
        }.exceptionOrNull()
        assertTrue(failed != null)
    }

    @Test
    fun inviteRoundTripsEndpointAndTopic() {
        val topic = AlterSendProtocol.generateTopicHex()
        val invite = AlterSendInvite("192.168.1.10", 45678, topic)

        assertEquals(invite, AlterSendInvite.decode(invite.encode()))
    }

    @Test
    fun relayInviteRoundTripsEndpointSessionAndTopic() {
        val topic = AlterSendProtocol.generateTopicHex()
        val invite = AlterSendInvite(
            host = "10.0.2.2",
            port = 41404,
            topicHex = topic,
            mode = AlterSendInviteMode.Relay,
            relaySessionId = "abc123"
        )

        assertEquals(invite, AlterSendInvite.decode(invite.encode()))
    }

    @Test
    fun hybridInviteRoundTripsDirectAndRelayEndpoints() {
        val topic = AlterSendProtocol.generateTopicHex()
        val invite = AlterSendInvite(
            host = "192.168.1.10",
            port = 45678,
            topicHex = topic,
            mode = AlterSendInviteMode.Hybrid,
            relayHost = "relay.just-share.example",
            relayPort = 443,
            relaySessionId = "abc123"
        )

        assertEquals(invite, AlterSendInvite.decode(invite.encode()))
    }
}
