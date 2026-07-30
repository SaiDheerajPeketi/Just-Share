package com.invincible.jedishare.data.altersend

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.invincible.jedishare.data.db.TransferHistoryEntity
import com.invincible.jedishare.data.repository.TransferHistoryRepository
import com.invincible.jedishare.domain.altersend.AlterSendConnectionPhase
import com.invincible.jedishare.domain.altersend.AlterSendFileOffer
import com.invincible.jedishare.domain.altersend.AlterSendInvite
import com.invincible.jedishare.domain.altersend.AlterSendInviteMode
import com.invincible.jedishare.domain.altersend.AlterSendProtocol
import com.invincible.jedishare.domain.altersend.AlterSendTransferProgress
import com.invincible.jedishare.domain.altersend.AlterSendUiState
import com.invincible.jedishare.domain.altersend.toHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.RandomAccessFile
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.security.SecureRandom
import kotlin.coroutines.coroutineContext

class AlterSendSocketTransfer(
    private val context: Context,
    private val historyRepository: TransferHistoryRepository,
    private val onState: (AlterSendUiState) -> Unit
) {
    companion object {
        private const val MAGIC = 0x4A534153 // JSAS
        private const val VERSION = 1
        private const val FRAME_MANIFEST = 1
        private const val FRAME_START = 2
        private const val FRAME_NEED = 3
        private const val FRAME_CHUNK = 4
        private const val FRAME_COMPLETE = 5
        private const val FRAME_ACK = 6
        private const val FRAME_ERROR = 7
        private const val SOCKET_TIMEOUT_MS = 45_000
        private const val DEFAULT_RELAY_PORT = 41404
    }

    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null

    suspend fun host(topicHex: String, offers: List<AlterSendFileOffer>): AlterSendInvite =
        withContext(Dispatchers.IO) {
            if (isAndroidEmulator()) {
                return@withContext hostViaRelay(topicHex, offers)
            }
            val server = ServerSocket(0).also { serverSocket = it }
            val invite = AlterSendInvite(host = localIpv4Address(), port = server.localPort, topicHex = topicHex)
            onState(
                AlterSendUiState(
                    phase = AlterSendConnectionPhase.Hosting,
                    topicHex = invite.encode(),
                    offers = offers
                )
            )

            try {
                val accepted = server.accept().also {
                    socket = it
                    it.soTimeout = SOCKET_TIMEOUT_MS
                }
                val channel = serverHandshake(accepted, topicHex)
                onState(
                    AlterSendUiState(
                        phase = AlterSendConnectionPhase.Connected,
                        topicHex = invite.encode(),
                        offers = offers
                    )
                )
                sendFiles(channel, offers)
                invite
            } finally {
                close()
            }
        }

    private suspend fun hostViaRelay(topicHex: String, offers: List<AlterSendFileOffer>): AlterSendInvite {
        val invite = AlterSendInvite(
            host = "10.0.2.2",
            port = DEFAULT_RELAY_PORT,
            topicHex = topicHex,
            mode = AlterSendInviteMode.Relay,
            relaySessionId = randomRelaySessionId()
        )
        onState(
            AlterSendUiState(
                phase = AlterSendConnectionPhase.Hosting,
                topicHex = invite.encode(),
                offers = offers
            )
        )
        val relayed = connectRelay(invite, isSender = true).also {
            socket = it
            it.soTimeout = SOCKET_TIMEOUT_MS
        }
        val channel = serverHandshake(relayed, topicHex)
        onState(
            AlterSendUiState(
                phase = AlterSendConnectionPhase.Connected,
                topicHex = invite.encode(),
                offers = offers
            )
        )
        sendFiles(channel, offers)
        return invite
    }

    suspend fun join(invite: AlterSendInvite): Unit = withContext(Dispatchers.IO) {
        onState(
            AlterSendUiState(
                phase = AlterSendConnectionPhase.Connecting,
                topicHex = invite.encode()
            )
        )
        try {
            val connected = if (invite.mode == AlterSendInviteMode.Relay) {
                connectRelay(invite, isSender = false)
            } else {
                Socket(invite.host, invite.port)
            }.also {
                socket = it
                it.soTimeout = SOCKET_TIMEOUT_MS
            }
            val channel = clientHandshake(connected, invite.topicHex)
            receiveFiles(channel)
        } finally {
            close()
        }
    }

    fun close() {
        runCatching { socket?.close() }
        runCatching { serverSocket?.close() }
        socket = null
        serverSocket = null
    }

    private fun serverHandshake(socket: Socket, expectedTopic: String): SecureChannel {
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())

        val clientMagic = input.readInt()
        val version = input.readInt()
        val topic = input.readUTF()
        val clientPublic = input.readBytesWithLength()
        require(clientMagic == MAGIC && version == VERSION && topic == expectedTopic) {
            "AlterSend peer sent an invalid handshake"
        }

        val keyPair = AlterSendCrypto.generateKeyPair()
        val serverPublic = keyPair.public.encoded
        output.writeInt(MAGIC)
        output.writeInt(VERSION)
        output.writeUTF(expectedTopic)
        output.writeBytesWithLength(serverPublic)
        output.flush()

        val keys = AlterSendCrypto.deriveKeys(
            privateKey = keyPair.private,
            clientPublic = clientPublic,
            serverPublic = serverPublic,
            topicHex = expectedTopic,
            isClient = false
        )
        return SecureChannel(input, output, keys)
    }

    private fun clientHandshake(socket: Socket, topicHex: String): SecureChannel {
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())
        val keyPair = AlterSendCrypto.generateKeyPair()
        val clientPublic = keyPair.public.encoded

        output.writeInt(MAGIC)
        output.writeInt(VERSION)
        output.writeUTF(topicHex)
        output.writeBytesWithLength(clientPublic)
        output.flush()

        val serverMagic = input.readInt()
        val version = input.readInt()
        val topic = input.readUTF()
        val serverPublic = input.readBytesWithLength()
        require(serverMagic == MAGIC && version == VERSION && topic == topicHex) {
            "AlterSend host sent an invalid handshake"
        }

        val keys = AlterSendCrypto.deriveKeys(
            privateKey = keyPair.private,
            clientPublic = clientPublic,
            serverPublic = serverPublic,
            topicHex = topicHex,
            isClient = true
        )
        return SecureChannel(input, output, keys)
    }

    private suspend fun sendFiles(channel: SecureChannel, offers: List<AlterSendFileOffer>) {
        channel.writeFrame(FRAME_MANIFEST, offers.toManifestBytes())
        offers.forEach { offer ->
            coroutineContext.ensureActive()
            val uri = offer.uri ?: throw IllegalArgumentException("Missing sender file URI")
            val chunkSize = AlterSendProtocol.selectChunkSize(offer.sizeBytes)
            val totalChunks = AlterSendProtocol.chunkCount(offer.sizeBytes, chunkSize)

            channel.writeFrame(FRAME_START, startPayload(offer, chunkSize))
            val need = channel.readFrame()
            if (need.type != FRAME_NEED) throw EOFException("Receiver did not request chunks")
            val indices = parseNeedPayload(need.payload, offer.id)

            val digest = MessageDigest.getInstance("SHA-256")
            for (index in indices) {
                coroutineContext.ensureActive()
                if (index < 0 || index >= totalChunks) throw IllegalArgumentException("Invalid chunk index")
                val range = AlterSendProtocol.chunkRange(index, offer.sizeBytes, chunkSize)
                val bytes = readUriRange(uri, range.offset, range.length)
                digest.update(bytes)
                channel.writeFrame(FRAME_CHUNK, chunkPayload(offer.id, index, bytes))
                val sentBytes = minOf(offer.sizeBytes, (index + 1L) * chunkSize)
                onState(
                    AlterSendUiState(
                        phase = AlterSendConnectionPhase.Transferring,
                        offers = offers,
                        progress = AlterSendTransferProgress(offer.id, offer.name, sentBytes, offer.sizeBytes)
                    )
                )
            }
            channel.writeFrame(FRAME_COMPLETE, completePayload(offer.id, digest.digest()))
            val ack = channel.readFrame()
            if (ack.type != FRAME_ACK) throw EOFException("Receiver did not acknowledge ${offer.name}")
            historyRepository.addEntry(
                TransferHistoryEntity(
                    fileName = offer.name,
                    mimeType = offer.mimeType,
                    fileSizeBytes = offer.sizeBytes,
                    isSender = true,
                    transferMethod = "AlterSend",
                    remoteDeviceName = "AlterSend peer",
                    contentUri = uri.toString(),
                    isAlterSend = true
                )
            )
        }
        onState(AlterSendUiState(phase = AlterSendConnectionPhase.Complete, offers = offers))
    }

    private suspend fun receiveFiles(channel: SecureChannel) {
        val manifest = channel.readFrame()
        if (manifest.type != FRAME_MANIFEST) throw EOFException("Sender did not send a manifest")
        val offers = manifest.payload.toOffers()
        onState(AlterSendUiState(phase = AlterSendConnectionPhase.IncomingOffer, offers = offers))

        offers.forEach { offer ->
            coroutineContext.ensureActive()
            val start = channel.readFrame()
            if (start.type != FRAME_START) throw EOFException("Sender did not start ${offer.name}")
            val announced = parseStartPayload(start.payload)
            if (announced.id != offer.id || announced.sizeBytes != offer.sizeBytes) {
                throw IllegalStateException("Sender announced inconsistent file metadata")
            }
            val chunkSize = AlterSendProtocol.selectChunkSize(offer.sizeBytes)
            val totalChunks = AlterSendProtocol.chunkCount(offer.sizeBytes, chunkSize)
            val missing = (0 until totalChunks).toList()
            channel.writeFrame(FRAME_NEED, needPayload(offer.id, missing))

            val temp = File.createTempFile("altersend-", ".part", context.cacheDir)
            val digest = MessageDigest.getInstance("SHA-256")
            RandomAccessFile(temp, "rw").use { output ->
                output.setLength(offer.sizeBytes)
                var received = 0L
                repeat(totalChunks) {
                    val chunk = channel.readFrame()
                    if (chunk.type != FRAME_CHUNK) throw EOFException("Expected chunk for ${offer.name}")
                    val parsed = parseChunkPayload(chunk.payload)
                    if (parsed.id != offer.id) throw IllegalStateException("Chunk belongs to another file")
                    val range = AlterSendProtocol.chunkRange(parsed.index, offer.sizeBytes, chunkSize)
                    if (parsed.data.size != range.length) throw IllegalStateException("Corrupted chunk length")
                    output.seek(range.offset)
                    output.write(parsed.data)
                    digest.update(parsed.data)
                    received += parsed.data.size
                    onState(
                        AlterSendUiState(
                            phase = AlterSendConnectionPhase.Transferring,
                            offers = offers,
                            progress = AlterSendTransferProgress(offer.id, offer.name, received, offer.sizeBytes)
                        )
                    )
                }
            }

            val complete = channel.readFrame()
            if (complete.type != FRAME_COMPLETE) throw EOFException("Sender did not complete ${offer.name}")
            val expectedHash = parseCompletePayload(complete.payload, offer.id)
            val actualHash = digest.digest()
            if (!actualHash.contentEquals(expectedHash)) {
                temp.delete()
                channel.writeFrame(FRAME_ERROR, "Integrity check failed".encodeToByteArray())
                throw IllegalStateException("Integrity check failed for ${offer.name}")
            }

            val savedUri = saveTempFile(offer, temp)
            channel.writeFrame(FRAME_ACK, offer.id.encodeToByteArray())
            historyRepository.addEntry(
                TransferHistoryEntity(
                    fileName = offer.name,
                    mimeType = offer.mimeType,
                    fileSizeBytes = offer.sizeBytes,
                    isSender = false,
                    transferMethod = "AlterSend",
                    remoteDeviceName = "AlterSend peer",
                    contentUri = savedUri?.toString(),
                    isAlterSend = true
                )
            )
            temp.delete()
        }
        onState(AlterSendUiState(phase = AlterSendConnectionPhase.Complete, offers = offers))
    }

    private fun readUriRange(uri: Uri, offset: Long, length: Int): ByteArray {
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open $uri" }
            var skipped = 0L
            while (skipped < offset) {
                val step = input.skip(offset - skipped)
                if (step <= 0L) throw EOFException("Could not seek sender file")
                skipped += step
            }
            val out = ByteArray(length)
            var read = 0
            while (read < length) {
                val count = input.read(out, read, length - read)
                if (count == -1) throw EOFException("File ended mid-chunk")
                read += count
            }
            return out
        }
    }

    private fun saveTempFile(offer: AlterSendFileOffer, temp: File): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, offer.name)
            put(MediaStore.Files.FileColumns.MIME_TYPE, offer.mimeType ?: "*/*")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Files.FileColumns.IS_PENDING, 1)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/JustShare")
            }
        }
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Downloads.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Files.getContentUri("external")
        }
        val uri = resolver.insert(collection, values)
        if (uri != null) {
            resolver.openOutputStream(uri).use { out ->
                temp.inputStream().use { input -> input.copyTo(requireNotNull(out)) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.update(uri, ContentValues().apply { put(MediaStore.Files.FileColumns.IS_PENDING, 0) }, null, null)
            }
        }
        return uri
    }

    private fun localIpv4Address(): String {
        val interfaces = NetworkInterface.getNetworkInterfaces().toList()
        for (networkInterface in interfaces) {
            if (!networkInterface.isUp || networkInterface.isLoopback) continue
            val address = networkInterface.inetAddresses.toList()
                .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            if (address != null) return address.hostAddress ?: "127.0.0.1"
        }
        return "127.0.0.1"
    }

    private fun connectRelay(invite: AlterSendInvite, isSender: Boolean): Socket {
        val sessionId = requireNotNull(invite.relaySessionId) { "Relay invite is missing session id" }
        val relaySocket = Socket(invite.host, invite.port)
        val output = DataOutputStream(relaySocket.getOutputStream())
        output.writeUTF("JSASR1")
        output.writeUTF(sessionId)
        output.writeUTF(if (isSender) "sender" else "receiver")
        output.flush()
        return relaySocket
    }

    private fun randomRelaySessionId(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    private fun isAndroidEmulator(): Boolean {
        val fingerprint = android.os.Build.FINGERPRINT.lowercase()
        val model = android.os.Build.MODEL.lowercase()
        val product = android.os.Build.PRODUCT.lowercase()
        return fingerprint.contains("generic") ||
            fingerprint.contains("emulator") ||
            model.contains("sdk") ||
            model.contains("emulator") ||
            product.contains("sdk")
    }

    private data class Frame(val type: Int, val payload: ByteArray)

    private class SecureChannel(
        private val input: DataInputStream,
        private val output: DataOutputStream,
        private val keys: AlterSendHandshakeKeys
    ) {
        private var sendCounter = 0L
        private var receiveCounter = 0L

        @Synchronized
        fun writeFrame(type: Int, payload: ByteArray) {
            val plain = ByteArrayOutputStream().use { bytes ->
                DataOutputStream(bytes).use { out ->
                    out.writeInt(type)
                    out.writeInt(payload.size)
                    out.write(payload)
                }
                bytes.toByteArray()
            }
            val encrypted = AlterSendCrypto.encrypt(keys.sendKey, sendCounter++, plain)
            output.writeInt(encrypted.size)
            output.write(encrypted)
            output.flush()
        }

        @Synchronized
        fun readFrame(): Frame {
            val encryptedSize = input.readInt()
            require(encryptedSize >= 0 && encryptedSize <= 16 * 1024 * 1024) { "Invalid frame size" }
            val encrypted = ByteArray(encryptedSize).also { input.readFully(it) }
            val plain = AlterSendCrypto.decrypt(keys.receiveKey, receiveCounter++, encrypted)
            DataInputStream(ByteArrayInputStream(plain)).use { frame ->
                val type = frame.readInt()
                val payloadSize = frame.readInt()
                require(payloadSize >= 0 && payloadSize <= 16 * 1024 * 1024) { "Invalid payload size" }
                val payload = ByteArray(payloadSize).also { frame.readFully(it) }
                return Frame(type = type, payload = payload)
            }
        }
    }

    private fun DataInputStream.readBytesWithLength(): ByteArray {
        val size = readInt()
        require(size >= 0 && size <= 16 * 1024 * 1024) { "Invalid frame size" }
        return ByteArray(size).also { readFully(it) }
    }

    private fun DataOutputStream.writeBytesWithLength(bytes: ByteArray) {
        writeInt(bytes.size)
        write(bytes)
    }

    private fun List<AlterSendFileOffer>.toManifestBytes(): ByteArray {
        val array = JSONArray()
        forEach { offer ->
            array.put(JSONObject().apply {
                put("id", offer.id)
                put("name", offer.name)
                put("sizeBytes", offer.sizeBytes)
                put("mimeType", offer.mimeType)
            })
        }
        return array.toString().encodeToByteArray()
    }

    private fun ByteArray.toOffers(): List<AlterSendFileOffer> {
        val array = JSONArray(decodeToString())
        return buildList {
            for (index in 0 until array.length()) {
                val json = array.getJSONObject(index)
                add(
                    AlterSendFileOffer(
                        id = json.getString("id"),
                        name = json.getString("name"),
                        sizeBytes = json.getLong("sizeBytes"),
                        mimeType = json.optString("mimeType").takeIf { it.isNotBlank() && it != "null" }
                    )
                )
            }
        }
    }

    private fun startPayload(offer: AlterSendFileOffer, chunkSize: Int): ByteArray =
        JSONObject().apply {
            put("id", offer.id)
            put("name", offer.name)
            put("sizeBytes", offer.sizeBytes)
            put("chunkSize", chunkSize)
            put("mimeType", offer.mimeType)
        }.toString().encodeToByteArray()

    private fun parseStartPayload(bytes: ByteArray): AlterSendFileOffer {
        val json = JSONObject(bytes.decodeToString())
        return AlterSendFileOffer(
            id = json.getString("id"),
            name = json.getString("name"),
            sizeBytes = json.getLong("sizeBytes"),
            mimeType = json.optString("mimeType").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    private fun needPayload(id: String, indices: List<Int>): ByteArray =
        JSONObject().apply {
            put("id", id)
            put("indices", JSONArray(indices))
        }.toString().encodeToByteArray()

    private fun parseNeedPayload(bytes: ByteArray, expectedId: String): List<Int> {
        val json = JSONObject(bytes.decodeToString())
        require(json.getString("id") == expectedId) { "Need message belongs to another file" }
        val array = json.getJSONArray("indices")
        return List(array.length()) { index -> array.getInt(index) }
    }

    private fun chunkPayload(id: String, index: Int, data: ByteArray): ByteArray {
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeUTF(id)
                out.writeInt(index)
                out.writeBytesWithLength(data)
            }
            bytes.toByteArray()
        }
    }

    private data class ChunkPayload(val id: String, val index: Int, val data: ByteArray)

    private fun parseChunkPayload(bytes: ByteArray): ChunkPayload {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            return ChunkPayload(
                id = input.readUTF(),
                index = input.readInt(),
                data = input.readBytesWithLength()
            )
        }
    }

    private fun completePayload(id: String, digest: ByteArray): ByteArray {
        return ByteArrayOutputStream().use { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeUTF(id)
                out.writeBytesWithLength(digest)
            }
            bytes.toByteArray()
        }
    }

    private fun parseCompletePayload(bytes: ByteArray, expectedId: String): ByteArray {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            val id = input.readUTF()
            require(id == expectedId) { "Complete message belongs to another file" }
            return input.readBytesWithLength()
        }
    }
}
