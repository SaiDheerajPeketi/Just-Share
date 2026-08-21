package com.blackandblue.justshare.data.altersend

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.blackandblue.justshare.BuildConfig
import com.blackandblue.justshare.data.db.TransferHistoryEntity
import com.blackandblue.justshare.data.remote.TelemetryService
import com.blackandblue.justshare.data.repository.TransferHistoryRepository
import com.blackandblue.justshare.domain.altersend.AlterSendConnectionPhase
import com.blackandblue.justshare.domain.altersend.AlterSendFileOffer
import com.blackandblue.justshare.domain.altersend.AlterSendInvite
import com.blackandblue.justshare.domain.altersend.AlterSendInviteMode
import com.blackandblue.justshare.domain.altersend.AlterSendProtocol
import com.blackandblue.justshare.domain.altersend.AlterSendRelayDirectory
import com.blackandblue.justshare.domain.altersend.AlterSendTransferProgress
import com.blackandblue.justshare.domain.altersend.AlterSendUiState
import com.blackandblue.justshare.domain.altersend.ConnectionMode
import com.blackandblue.justshare.domain.altersend.toHex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.File
import java.io.RandomAccessFile
import java.net.InetSocketAddress
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.TimeUnit
import kotlin.coroutines.coroutineContext

class AlterSendSocketTransfer(
    private val context: Context,
    private val historyRepository: TransferHistoryRepository,
    private val onState: (AlterSendUiState) -> Unit,
    private val awaitIncomingDecision: suspend (List<AlterSendFileOffer>) -> Boolean = { true },
    /** Optional telemetry sink — null-safe so existing callers don't need to change. */
    private val telemetryService: TelemetryService? = null
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
        private const val DIRECT_CONNECT_TIMEOUT_MS = 6_000
        private const val DIRECT_ACCEPT_TIMEOUT_MS = 8_000
        private const val DIRECT_ONLY_ACCEPT_TIMEOUT_MS = 20_000
        private const val RENDEZVOUS_ACCEPT_TIMEOUT_MS = 10_000
        private const val RELAY_PROBE_TIMEOUT_MS = 1_500

        // ── Cloudflare relay constants ─────────────────────────────────────
        /** Maximum binary WebSocket frame we will ever send (4 MiB). */
        private const val CF_MAX_FRAME_BYTES = 4 * 1024 * 1024
    }

    private var serverSocket: ServerSocket? = null
    private var socket: Socket? = null

    // Shared OkHttpClient — created lazily, reused for the session lifetime.
    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .pingInterval(20, TimeUnit.SECONDS)
            .build()
    }

    // Track the active transport so close() can shut it down regardless of type.
    @Volatile private var activeTransport: RelayTransport? = null

    // ── Public entry points ───────────────────────────────────────────────────

    suspend fun host(topicHex: String, offers: List<AlterSendFileOffer>): AlterSendInvite =
        withContext(Dispatchers.IO) {
            // ── Feature flag: try Cloudflare relay first ───────────────────
            if (BuildConfig.CF_RELAY_ENABLED && BuildConfig.CF_RELAY_BASE_URL.isNotBlank()) {
                return@withContext hostViaCloudflare(topicHex, offers)
            }

            if (isAndroidEmulator()) {
                val relayEndpoint = reachableRelayEndpoint(includeAndroidHostRelay = true)
                    ?: throw IllegalStateException(
                        "No Remote Transfer relay is reachable. Start a local relay on 10.0.2.2:41404 or check your configured relay."
                    )
                return@withContext hostViaRelay(topicHex, offers, relayEndpoint)
            }
            val relayEndpoint = reachableRelayEndpoint()
            if (relayEndpoint != null) {
                return@withContext hostHybrid(topicHex, offers, relayEndpoint)
            }
            val server = ServerSocket(0).also {
                serverSocket = it
                it.soTimeout = DIRECT_ONLY_ACCEPT_TIMEOUT_MS
            }
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
                val transport = DirectSocketTransport(accepted).also { activeTransport = it }
                val channel = serverHandshake(transport, topicHex)
                onState(
                    AlterSendUiState(
                        phase = AlterSendConnectionPhase.Connected,
                        topicHex = invite.encode(),
                        offers = offers
                    )
                )
                sendFiles(channel, offers)
                invite
            } catch (_: SocketTimeoutException) {
                runCatching { server.close() }
                val fallbackRelay = reachableRelayEndpoint()
                    ?: throw IllegalStateException(
                        "Direct connection timed out and no relay is reachable. Check your network and try again."
                    )
                hostViaRelay(topicHex, offers, fallbackRelay)
            } finally {
                close()
            }
        }

    private suspend fun hostHybrid(
        topicHex: String,
        offers: List<AlterSendFileOffer>,
        relayEndpoint: Pair<String, Int>
    ): AlterSendInvite {
        val server = ServerSocket(0).also {
            serverSocket = it
            it.soTimeout = DIRECT_ACCEPT_TIMEOUT_MS
        }
        val invite = AlterSendInvite(
            host = localIpv4Address(),
            port = server.localPort,
            topicHex = topicHex,
            mode = AlterSendInviteMode.Hybrid,
            relayHost = relayEndpoint.first,
            relayPort = relayEndpoint.second,
            relaySessionId = randomRelaySessionId()
        )
        onState(
            AlterSendUiState(
                phase = AlterSendConnectionPhase.Hosting,
                topicHex = invite.encode(),
                offers = offers
            )
        )

        try {
            val transport: RelayTransport = try {
                val accepted = acceptServerConnection(server)
                DirectSocketTransport(accepted).also { activeTransport = it }
            } catch (_: SocketTimeoutException) {
                val rendezvousTransport = runCatching {
                    exchangeRendezvousEndpoint(invite, isSender = true, listenPort = server.localPort)
                    server.soTimeout = RENDEZVOUS_ACCEPT_TIMEOUT_MS
                    val rendezvous = acceptServerConnection(server)
                    DirectSocketTransport(rendezvous)
                }.getOrNull()
                if (rendezvousTransport != null) {
                    activeTransport = rendezvousTransport
                    rendezvousTransport
                } else {
                    runCatching { server.close() }
                    val relayed = connectRelay(invite, isSender = true).also {
                        socket = it
                        it.soTimeout = SOCKET_TIMEOUT_MS
                    }
                    DirectSocketTransport(relayed).also { activeTransport = it }
                }
            }
            val channel = serverHandshake(transport, topicHex)
            onState(
                AlterSendUiState(
                    phase = AlterSendConnectionPhase.Connected,
                    topicHex = invite.encode(),
                    offers = offers
                )
            )
            sendFiles(channel, offers)
            return invite
        } finally {
            close()
        }
    }

    private suspend fun hostViaRelay(
        topicHex: String,
        offers: List<AlterSendFileOffer>,
        relayEndpoint: Pair<String, Int>
    ): AlterSendInvite {
        val invite = AlterSendInvite(
            host = relayEndpoint.first,
            port = relayEndpoint.second,
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
        try {
            val relayed = connectRelay(invite, isSender = true).also {
                socket = it
                it.soTimeout = SOCKET_TIMEOUT_MS
            }
            val transport = DirectSocketTransport(relayed).also { activeTransport = it }
            val channel = serverHandshake(transport, topicHex)
            onState(
                AlterSendUiState(
                    phase = AlterSendConnectionPhase.Connected,
                    topicHex = invite.encode(),
                    offers = offers
                )
            )
            sendFiles(channel, offers)
            return invite
        } finally {
            close()
        }
    }

    // ── Cloudflare host path ──────────────────────────────────────────────────

    private suspend fun hostViaCloudflare(
        topicHex: String,
        offers: List<AlterSendFileOffer>
    ): AlterSendInvite {
        val sessionId = randomHex32()
        val (token, expiry) = CloudflareRelayToken.generate(
            secretHex  = BuildConfig.CF_RELAY_HMAC_SECRET,
            sessionId  = sessionId,
            role       = "sender"
        )
        // Generate the receiver token so the Sender can embed it in the invite.
        val (receiverToken, receiverExpiry) = CloudflareRelayToken.generate(
            secretHex  = BuildConfig.CF_RELAY_HMAC_SECRET,
            sessionId  = sessionId,
            role       = "receiver"
        )
        val invite = AlterSendInvite(
            host        = "",   // unused for Cloudflare mode
            port        = 0,    // unused for Cloudflare mode
            topicHex    = topicHex,
            mode        = AlterSendInviteMode.Cloudflare,
            cfSessionId = sessionId,
            cfRelayUrl  = BuildConfig.CF_RELAY_BASE_URL,
            cfExpiry    = receiverExpiry,
            cfToken     = receiverToken   // receiver's token is what goes in the QR code
        )
        onState(
            AlterSendUiState(
                phase   = AlterSendConnectionPhase.Hosting,
                topicHex = invite.encode(),
                offers  = offers
            )
        )

        try {
            val wsUrl = CloudflareWebSocketTransport.buildUrl(
                baseUrl   = BuildConfig.CF_RELAY_BASE_URL,
                sessionId = sessionId,
                role      = "sender",
                token     = token,
                expiry    = expiry
            )
            val cfTransport = CloudflareWebSocketTransport(wsUrl, okHttpClient)
            cfTransport.connect()
            activeTransport = cfTransport

            val channel = serverHandshake(cfTransport, topicHex)
            onState(
                AlterSendUiState(
                    phase          = AlterSendConnectionPhase.Connected,
                    topicHex       = invite.encode(),
                    offers         = offers,
                    connectionMode = ConnectionMode.CLOUDFLARE_RELAY
                )
            )
            sendFiles(channel, offers, connectionMode = ConnectionMode.CLOUDFLARE_RELAY)
            return invite
        } finally {
            close()
        }
    }

    // ── Join (receiver side) ──────────────────────────────────────────────────

    suspend fun join(invite: AlterSendInvite): Unit = withContext(Dispatchers.IO) {
        onState(
            AlterSendUiState(
                phase = AlterSendConnectionPhase.Connecting,
                topicHex = invite.encode()
            )
        )
        try {
            val (transport, connectionMode) = connectTransportForInvite(invite)
            activeTransport = transport
            val channel = clientHandshake(transport, invite.topicHex)
            receiveFiles(channel, connectionMode)
        } finally {
            close()
        }
    }

    /**
     * Returns the appropriate [RelayTransport] and [ConnectionMode] for the invite.
     */
    private fun connectTransportForInvite(invite: AlterSendInvite): Pair<RelayTransport, ConnectionMode> {
        return when (invite.mode) {
            AlterSendInviteMode.Cloudflare -> {
                val sessionId  = requireNotNull(invite.cfSessionId)
                val relayUrl   = requireNotNull(invite.cfRelayUrl)
                val expiry     = requireNotNull(invite.cfExpiry)
                val token      = requireNotNull(invite.cfToken)
                val wsUrl = CloudflareWebSocketTransport.buildUrl(
                    baseUrl   = relayUrl,
                    sessionId = sessionId,
                    role      = "receiver",
                    token     = token,
                    expiry    = expiry
                )
                val transport = CloudflareWebSocketTransport(wsUrl, okHttpClient)
                transport.connect()
                transport to ConnectionMode.CLOUDFLARE_RELAY
            }
            AlterSendInviteMode.Direct -> {
                val sock = connectDirect(invite.host, invite.port).also {
                    socket = it
                    it.soTimeout = SOCKET_TIMEOUT_MS
                }
                DirectSocketTransport(sock) to ConnectionMode.DIRECT
            }
            AlterSendInviteMode.Relay -> {
                val sock = connectRelay(invite, isSender = false).also {
                    socket = it
                    it.soTimeout = SOCKET_TIMEOUT_MS
                }
                DirectSocketTransport(sock) to ConnectionMode.RELAY
            }
            AlterSendInviteMode.Hybrid -> {
                val (sock, mode) = runCatching {
                    connectDirect(invite.host, invite.port) to ConnectionMode.DIRECT
                }.getOrElse {
                    runCatching {
                        connectRendezvousDirect(invite) to ConnectionMode.DIRECT
                    }.getOrElse {
                        connectRelay(invite, isSender = false) to ConnectionMode.RELAY
                    }
                }
                sock.soTimeout = SOCKET_TIMEOUT_MS
                socket = sock
                DirectSocketTransport(sock) to mode
            }
        }
    }

    private fun acceptServerConnection(server: ServerSocket): Socket =
        server.accept().also {
            socket = it
            it.soTimeout = SOCKET_TIMEOUT_MS
        }

    private fun connectDirect(host: String, port: Int): Socket {
        return Socket().apply {
            connect(InetSocketAddress(host, port), DIRECT_CONNECT_TIMEOUT_MS)
        }
    }

    fun close() {
        runCatching { activeTransport?.close() }
        runCatching { socket?.close() }
        runCatching { serverSocket?.close() }
        activeTransport = null
        socket = null
        serverSocket = null
    }

    // ── Handshake helpers ─────────────────────────────────────────────────────
    //
    // The ECDH handshake is unchanged — it runs on top of the RelayTransport.
    // Cloudflare sees only the ciphertext that SecureChannel produces.

    private fun serverHandshake(transport: RelayTransport, expectedTopic: String): SecureChannel {
        // Read client hello
        val helloBytes = transport.readBytes()
        DataInputStream(ByteArrayInputStream(helloBytes)).use { input ->
            val clientMagic   = input.readInt()
            val version       = input.readInt()
            val topic         = input.readUTF()
            val clientPublic  = input.readBytesWithLength()
            require(clientMagic == MAGIC && version == VERSION && topic == expectedTopic) {
                "Remote Transfer peer sent an invalid handshake"
            }

            val keyPair      = AlterSendCrypto.generateKeyPair()
            val serverPublic = keyPair.public.encoded

            // Write server hello
            val helloOut = ByteArrayOutputStream()
            DataOutputStream(helloOut).use { out ->
                out.writeInt(MAGIC)
                out.writeInt(VERSION)
                out.writeUTF(expectedTopic)
                out.writeBytesWithLength(serverPublic)
            }
            transport.sendBytes(helloOut.toByteArray())

            val keys = AlterSendCrypto.deriveKeys(
                privateKey   = keyPair.private,
                clientPublic = clientPublic,
                serverPublic = serverPublic,
                topicHex     = expectedTopic,
                isClient     = false
            )
            return SecureChannel(transport, keys)
        }
    }

    private fun clientHandshake(transport: RelayTransport, topicHex: String): SecureChannel {
        val keyPair      = AlterSendCrypto.generateKeyPair()
        val clientPublic = keyPair.public.encoded

        // Write client hello
        val helloOut = ByteArrayOutputStream()
        DataOutputStream(helloOut).use { out ->
            out.writeInt(MAGIC)
            out.writeInt(VERSION)
            out.writeUTF(topicHex)
            out.writeBytesWithLength(clientPublic)
        }
        transport.sendBytes(helloOut.toByteArray())

        // Read server hello
        val helloBytes  = transport.readBytes()
        DataInputStream(ByteArrayInputStream(helloBytes)).use { input ->
            val serverMagic  = input.readInt()
            val version      = input.readInt()
            val topic        = input.readUTF()
            val serverPublic = input.readBytesWithLength()
            require(serverMagic == MAGIC && version == VERSION && topic == topicHex) {
                "Remote Transfer host sent an invalid handshake"
            }

            val keys = AlterSendCrypto.deriveKeys(
                privateKey   = keyPair.private,
                clientPublic = clientPublic,
                serverPublic = serverPublic,
                topicHex     = topicHex,
                isClient     = true
            )
            return SecureChannel(transport, keys)
        }
    }

    // ── File transfer logic ───────────────────────────────────────────────────
    // Unchanged from the original; just passes connectionMode through to telemetry.

    private suspend fun sendFiles(
        channel: SecureChannel,
        offers: List<AlterSendFileOffer>,
        connectionMode: ConnectionMode = ConnectionMode.UNKNOWN
    ) {
        channel.writeFrame(FRAME_MANIFEST, offers.toManifestBytes())
        offers.forEach { offer ->
            coroutineContext.ensureActive()
            val uri = offer.uri ?: throw IllegalArgumentException("Missing sender file URI")
            val chunkSize   = AlterSendProtocol.selectChunkSize(offer.sizeBytes)
            val totalChunks = AlterSendProtocol.chunkCount(offer.sizeBytes, chunkSize)

            channel.writeFrame(FRAME_START, startPayload(offer, chunkSize))
            val need = channel.readFrame()
            if (need.type == FRAME_ERROR) {
                throw IllegalStateException(need.payload.decodeToString())
            }
            if (need.type != FRAME_NEED) throw EOFException("Receiver did not request chunks")
            val indices = parseNeedPayload(need.payload, offer.id)

            val fileHash = sha256Uri(uri)
            for (index in indices) {
                coroutineContext.ensureActive()
                if (index < 0 || index >= totalChunks) throw IllegalArgumentException("Invalid chunk index")
                val range = AlterSendProtocol.chunkRange(index, offer.sizeBytes, chunkSize)
                val bytes = readUriRange(uri, range.offset, range.length)
                sendChunkWithRetry(channel, offer, index, bytes)
                val sentBytes = minOf(offer.sizeBytes, (index + 1L) * chunkSize)
                onState(
                    AlterSendUiState(
                        phase    = AlterSendConnectionPhase.Transferring,
                        offers   = offers,
                        progress = AlterSendTransferProgress(offer.id, offer.name, sentBytes, offer.sizeBytes),
                        connectionMode = connectionMode
                    )
                )
            }
            channel.writeFrame(FRAME_COMPLETE, completePayload(offer.id, fileHash))
            val ack = channel.readFrame()
            if (ack.type != FRAME_ACK) throw EOFException("Receiver did not acknowledge ${offer.name}")
            historyRepository.addEntry(
                TransferHistoryEntity(
                    fileName         = offer.name,
                    mimeType         = offer.mimeType,
                    fileSizeBytes    = offer.sizeBytes,
                    isSender         = true,
                    transferMethod   = "Remote Transfer",
                    remoteDeviceName = "Remote Transfer peer",
                    contentUri       = uri.toString(),
                    isAlterSend      = true
                )
            )
        }
        onState(AlterSendUiState(phase = AlterSendConnectionPhase.Complete, offers = offers))
        val totalBytes = offers.sumOf { it.sizeBytes }
        telemetryService?.onTransferCompleted(connectionMode, totalBytes)
    }

    private suspend fun receiveFiles(
        channel: SecureChannel,
        connectionMode: ConnectionMode = ConnectionMode.UNKNOWN
    ) {
        val manifest = channel.readFrame()
        if (manifest.type != FRAME_MANIFEST) throw EOFException("Sender did not send a manifest")
        val offers = manifest.payload.toOffers()
        onState(AlterSendUiState(phase = AlterSendConnectionPhase.IncomingOffer, offers = offers))
        if (!awaitIncomingDecision(offers)) {
            channel.writeFrame(FRAME_ERROR, "Receiver rejected transfer".encodeToByteArray())
            onState(AlterSendUiState(phase = AlterSendConnectionPhase.Cancelled, offers = offers))
            return
        }

        offers.forEach { offer ->
            coroutineContext.ensureActive()
            val start = channel.readFrame()
            if (start.type != FRAME_START) throw EOFException("Sender did not start ${offer.name}")
            val announced = parseStartPayload(start.payload)
            if (announced.id != offer.id || announced.sizeBytes != offer.sizeBytes) {
                throw IllegalStateException("Sender announced inconsistent file metadata")
            }
            val chunkSize   = AlterSendProtocol.selectChunkSize(offer.sizeBytes)
            val totalChunks = AlterSendProtocol.chunkCount(offer.sizeBytes, chunkSize)
            val missing     = (0 until totalChunks).toList()
            channel.writeFrame(FRAME_NEED, needPayload(offer.id, missing))

            val temp = File.createTempFile("altersend-", ".part", context.cacheDir)
            val verifiedChunks = BooleanArray(totalChunks)
            RandomAccessFile(temp, "rw").use { output ->
                output.setLength(offer.sizeBytes)
                var received = 0L
                while (verifiedChunks.count { it } < totalChunks) {
                    val chunk = channel.readFrame()
                    if (chunk.type != FRAME_CHUNK) throw EOFException("Expected chunk for ${offer.name}")
                    val parsed = parseChunkPayload(chunk.payload)
                    if (parsed.id != offer.id) throw IllegalStateException("Chunk belongs to another file")
                    if (parsed.index < 0 || parsed.index >= totalChunks) {
                        throw IllegalStateException("Chunk index is outside the announced file range")
                    }
                    val range = AlterSendProtocol.chunkRange(parsed.index, offer.sizeBytes, chunkSize)
                    if (parsed.data.size != range.length || !sha256(parsed.data).contentEquals(parsed.sha256)) {
                        channel.writeFrame(FRAME_NEED, needPayload(offer.id, listOf(parsed.index)))
                        continue
                    }
                    if (verifiedChunks[parsed.index]) {
                        channel.writeFrame(FRAME_ACK, chunkAckPayload(offer.id, parsed.index))
                        continue
                    }
                    output.seek(range.offset)
                    output.write(parsed.data)
                    verifiedChunks[parsed.index] = true
                    received += parsed.data.size
                    channel.writeFrame(FRAME_ACK, chunkAckPayload(offer.id, parsed.index))
                    onState(
                        AlterSendUiState(
                            phase    = AlterSendConnectionPhase.Transferring,
                            offers   = offers,
                            progress = AlterSendTransferProgress(offer.id, offer.name, received, offer.sizeBytes),
                            connectionMode = connectionMode
                        )
                    )
                }
            }

            val complete = channel.readFrame()
            if (complete.type != FRAME_COMPLETE) throw EOFException("Sender did not complete ${offer.name}")
            val expectedHash = parseCompletePayload(complete.payload, offer.id)
            val actualHash   = sha256File(temp)
            if (!actualHash.contentEquals(expectedHash)) {
                temp.delete()
                channel.writeFrame(FRAME_ERROR, "Integrity check failed".encodeToByteArray())
                throw IllegalStateException("Integrity check failed for ${offer.name}")
            }

            val savedUri = saveTempFile(offer, temp)
            channel.writeFrame(FRAME_ACK, offer.id.encodeToByteArray())
            historyRepository.addEntry(
                TransferHistoryEntity(
                    fileName         = offer.name,
                    mimeType         = offer.mimeType,
                    fileSizeBytes    = offer.sizeBytes,
                    isSender         = false,
                    transferMethod   = "Remote Transfer",
                    remoteDeviceName = "Remote Transfer peer",
                    contentUri       = savedUri?.toString(),
                    isAlterSend      = true
                )
            )
            temp.delete()
        }
        onState(AlterSendUiState(phase = AlterSendConnectionPhase.Complete, offers = offers))
        val totalBytes = offers.sumOf { it.sizeBytes }
        telemetryService?.onTransferCompleted(connectionMode, totalBytes)
    }

    // ── IO helpers ────────────────────────────────────────────────────────────

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

    private fun sendChunkWithRetry(
        channel: SecureChannel,
        offer: AlterSendFileOffer,
        index: Int,
        bytes: ByteArray
    ) {
        var attempts = 0
        while (attempts < 4) {
            attempts += 1
            channel.writeFrame(FRAME_CHUNK, chunkPayload(offer.id, index, bytes))
            val response = channel.readFrame()
            when (response.type) {
                FRAME_ACK -> {
                    parseChunkAckPayload(response.payload, offer.id, index)
                    return
                }
                FRAME_NEED -> {
                    val requested = parseNeedPayload(response.payload, offer.id)
                    if (index !in requested) throw EOFException("Receiver requested an unexpected chunk")
                }
                FRAME_ERROR -> throw IllegalStateException(response.payload.decodeToString())
                else -> throw EOFException("Receiver sent an unexpected chunk response")
            }
        }
        throw EOFException("Receiver could not verify chunk $index of ${offer.name}")
    }

    private fun sha256Uri(uri: Uri): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input) { "Could not open $uri" }
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest()
    }

    private fun sha256File(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read == -1) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest()
    }

    private fun sha256(bytes: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(bytes)

    private fun saveTempFile(offer: AlterSendFileOffer, temp: File): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, offer.name)
            put(MediaStore.Files.FileColumns.MIME_TYPE, offer.mimeType ?: "*/*")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Files.FileColumns.IS_PENDING, 1)
                put(MediaStore.Downloads.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/Just Share")
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
        val relayHost = invite.relayHost ?: invite.host
        val relayPort = invite.relayPort ?: invite.port
        val relaySocket = Socket().apply {
            connect(InetSocketAddress(relayHost, relayPort), DIRECT_CONNECT_TIMEOUT_MS)
        }
        val output = DataOutputStream(relaySocket.getOutputStream())
        output.writeUTF("JSASR1")
        output.writeUTF(sessionId)
        output.writeUTF(if (isSender) "sender" else "receiver")
        output.flush()
        return relaySocket
    }

    private fun connectRendezvousDirect(invite: AlterSendInvite): Socket {
        val endpoint = exchangeRendezvousEndpoint(invite, isSender = false, listenPort = 0)
        return connectDirect(endpoint.first, endpoint.second)
    }

    private fun exchangeRendezvousEndpoint(
        invite: AlterSendInvite,
        isSender: Boolean,
        listenPort: Int
    ): Pair<String, Int> {
        val sessionId = requireNotNull(invite.relaySessionId) { "Rendezvous invite is missing session id" }
        val relayHost = invite.relayHost ?: invite.host
        val relayPort = invite.relayPort ?: invite.port
        Socket().use { rendezvousSocket ->
            rendezvousSocket.connect(InetSocketAddress(relayHost, relayPort), DIRECT_CONNECT_TIMEOUT_MS)
            rendezvousSocket.soTimeout = SOCKET_TIMEOUT_MS
            val output = DataOutputStream(rendezvousSocket.getOutputStream())
            val input  = DataInputStream(rendezvousSocket.getInputStream())
            output.writeUTF("JSASHP1")
            output.writeUTF(sessionId)
            output.writeUTF(if (isSender) "sender" else "receiver")
            output.writeUTF(listenPort.coerceIn(0, 65535).toString())
            output.flush()

            val status = input.readUTF()
            if (status != "OK") throw EOFException("Remote Transfer rendezvous failed")
            val host = input.readUTF()
            val port = input.readUTF().toIntOrNull()
            if (host.isBlank() || port == null || port !in 1..65535) {
                throw EOFException("Remote Transfer rendezvous returned an invalid peer endpoint")
            }
            return host to port
        }
    }

    private fun randomRelaySessionId(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    /** Generates a 32-char hex string suitable for a Cloudflare session id. */
    private fun randomHex32(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.toHex()
    }

    private fun isAndroidEmulator(): Boolean {
        val fingerprint = android.os.Build.FINGERPRINT.lowercase()
        val model       = android.os.Build.MODEL.lowercase()
        val product     = android.os.Build.PRODUCT.lowercase()
        return fingerprint.contains("generic") ||
            fingerprint.contains("emulator") ||
            model.contains("sdk") ||
            model.contains("emulator") ||
            product.contains("sdk")
    }

    private fun reachableRelayEndpoint(includeAndroidHostRelay: Boolean = false): Pair<String, Int>? {
        return configuredRelayEndpoints(includeAndroidHostRelay).firstOrNull { endpoint ->
            runCatching {
                Socket().use { probe ->
                    probe.connect(InetSocketAddress(endpoint.first, endpoint.second), RELAY_PROBE_TIMEOUT_MS)
                }
            }.isSuccess
        }
    }

    private fun configuredRelayEndpoints(includeAndroidHostRelay: Boolean = false): List<Pair<String, Int>> {
        return AlterSendRelayDirectory.endpoints(
            publicRelayNodes     = BuildConfig.ALTERSEND_PUBLIC_RELAY_NODES,
            configuredHost       = BuildConfig.ALTERSEND_RELAY_HOST,
            configuredPort       = BuildConfig.ALTERSEND_RELAY_PORT,
            includeAndroidHostRelay = includeAndroidHostRelay
        ).map { endpoint -> endpoint.host to endpoint.port }
    }

    // ── SecureChannel — now backed by RelayTransport ──────────────────────────

    private data class Frame(val type: Int, val payload: ByteArray)

    /**
     * Encrypted frame layer, unchanged from the original.
     * Now reads/writes through a [RelayTransport] instead of raw DataStreams.
     *
     * Each write/read call corresponds to exactly one transport send/receive,
     * so this maps cleanly onto both TCP stream framing and WebSocket messages.
     */
    private class SecureChannel(
        private val transport: RelayTransport,
        private val keys: AlterSendHandshakeKeys
    ) {
        private var sendCounter    = 0L
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
            transport.sendBytes(encrypted)
        }

        @Synchronized
        fun readFrame(): Frame {
            val encrypted   = transport.readBytes()
            val counter     = receiveCounter
            val plain       = AlterSendCrypto.decrypt(keys.receiveKey, counter, encrypted)
            receiveCounter  = counter + 1
            DataInputStream(ByteArrayInputStream(plain)).use { frame ->
                val type        = frame.readInt()
                val payloadSize = frame.readInt()
                require(payloadSize >= 0 && payloadSize <= 16 * 1024 * 1024) { "Invalid payload size" }
                val payload = ByteArray(payloadSize).also { frame.readFully(it) }
                return Frame(type = type, payload = payload)
            }
        }
    }

    // ── Payload serialization helpers (unchanged) ─────────────────────────────

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
                        id       = json.getString("id"),
                        name     = json.getString("name"),
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
            id        = json.getString("id"),
            name      = json.getString("name"),
            sizeBytes = json.getLong("sizeBytes"),
            mimeType  = json.optString("mimeType").takeIf { it.isNotBlank() && it != "null" }
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
                out.writeBytesWithLength(sha256(data))
                out.writeBytesWithLength(data)
            }
            bytes.toByteArray()
        }
    }

    private data class ChunkPayload(val id: String, val index: Int, val sha256: ByteArray, val data: ByteArray)

    private fun parseChunkPayload(bytes: ByteArray): ChunkPayload {
        DataInputStream(ByteArrayInputStream(bytes)).use { input ->
            return ChunkPayload(
                id     = input.readUTF(),
                index  = input.readInt(),
                sha256 = input.readBytesWithLength(),
                data   = input.readBytesWithLength()
            )
        }
    }

    private fun chunkAckPayload(id: String, index: Int): ByteArray =
        JSONObject().apply {
            put("id", id)
            put("index", index)
        }.toString().encodeToByteArray()

    private fun parseChunkAckPayload(bytes: ByteArray, expectedId: String, expectedIndex: Int) {
        val json = JSONObject(bytes.decodeToString())
        require(json.getString("id") == expectedId && json.getInt("index") == expectedIndex) {
            "Chunk acknowledgement belongs to another chunk"
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
