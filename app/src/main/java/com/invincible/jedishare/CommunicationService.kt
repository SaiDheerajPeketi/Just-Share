package com.invincible.jedishare

import timber.log.Timber

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import com.invincible.jedishare.data.chat.toByteArray
import com.invincible.jedishare.data.chat.toFileInfo
import com.invincible.jedishare.data.db.TransferHistoryEntity
import com.invincible.jedishare.data.repository.TransferHistoryRepository
import com.invincible.jedishare.domain.chat.FileInfo
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

data class WifiTransferUpdate(
    val progress: Int,
    val fileName: String,
    val fileSize: Long,
    val currentFileIndex: Int,
    val totalFiles: Int,
    val remoteDeviceName: String?,
    val mimeType: String?,
    val manifest: List<com.invincible.jedishare.domain.chat.FileInfo>? = null
)

/**
 * Android Service for WiFi-Direct file transfer over raw TCP sockets.
 * Runs as a foreground service (required on API 34+ with foregroundServiceType=dataSync).
 *
 * Fixes applied (TODO-07):
 * - Removed all runBlocking{} + delay() calls that were blocking executor threads.
 * - Removed the 5s pre-send delay which was causing noticeable lag.
 * - Replaced file-size progress condition `while(currSize < size || progress != 100)`
 *   with a proper EOF sentinel approach (matching the Bluetooth path).
 * - MediaStore entries now saved into /JediShare subfolders (consistent with FileTransferRepository).
 * - File writing uses copyOfRange(0, bytesRead) to avoid writing stale buffer bytes.
 * - All dead commented-out code removed.
 * - Added proper null-safety throughout.
 */
@AndroidEntryPoint
class CommunicationService : Service() {

    @Inject
    lateinit var historyRepository: TransferHistoryRepository

    companion object {
        private const val TAG = "CommService"
        const val SERVER_ROLE = 0
        const val CLIENT_ROLE = 1
        const val CHUNK_SIZE = 65536 // 64 KB — matches BluetoothDataTransferService

        const val ACTION_SEND_MSG            = "com.invincible.jedishare.ACTION_SEND_MSG"
        const val ACTION_START_COMMUNICATION = "com.invincible.jedishare.ACTION_START_COMMUNICATION"
        const val ACTION_STOP_COMMUNICATION  = "com.invincible.jedishare.STOP_COMMUNICATION"

        const val EXTRAS_COMMUNICATION_ROLE  = "com.invincible.jedishare.EXTRAS_COMMUNICATION_ROLE"
        const val EXTRAS_GROUP_OWNER_ADDRESS = "com.invincible.jedishare.EXTRAS_GROUP_OWNER_ADDRESS"
        const val EXTRAS_GROUP_OWNER_PORT    = "com.invincible.jedishare.EXTRAS_GROUP_OWNER_PORT"
        const val EXTRAS_DEVICE_NAME         = "com.invincible.jedishare.EXTRAS_DEVICE_NAME"
        const val EXTRAS_PROGRESS_STATE      = "com.invincible.jedishare.EXTRAS_PROGRESS_STATE"
        const val EXTRAS_FILE_NAME           = "com.invincible.jedishare.EXTRAS_FILE_NAME"
        const val EXTRAS_FILE_SIZE           = "com.invincible.jedishare.EXTRAS_FILE_SIZE"
        const val EXTRAS_CURRENT_FILE_INDEX  = "com.invincible.jedishare.EXTRAS_CURRENT_FILE_INDEX"
        const val EXTRAS_TOTAL_FILES         = "com.invincible.jedishare.EXTRAS_TOTAL_FILES"
        const val EXTRAS_REMOTE_DEVICE_NAME  = "com.invincible.jedishare.EXTRAS_REMOTE_DEVICE_NAME"
        const val EXTRAS_MIME_TYPE           = "com.invincible.jedishare.EXTRAS_MIME_TYPE"
        const val BROADCAST_SENDING_UPDATE   = "com.invincible.jedishare.SENDING_UPDATE"

        private val _transferUpdates = MutableStateFlow<WifiTransferUpdate?>(null)
        val transferUpdates: StateFlow<WifiTransferUpdate?> = _transferUpdates.asStateFlow()

        fun clearTransferUpdate() {
            _transferUpdates.value = null
        }

        private const val SOCKET_WAIT_TIMEOUT_MS = 10_000L
        private const val SOCKET_WAIT_STEP_MS = 100L
        private const val NOTIF_CHANNEL_ID = "jedishare_transfer"
        private const val NOTIF_ID = 42

        private const val CONNECTED     = 0
        private const val NOT_CONNECTED = 1
        private const val CONNECTING    = 2
        private const val IS_SENDING    = 3
    }

    private val serviceState = AtomicInteger(NOT_CONNECTED)
    private var executorService: ExecutorService = Executors.newCachedThreadPool()
    private var communicationSocket: Socket?      = null
    private var serverSocket: ServerSocket?       = null
    @Volatile
    private var dataOutputStream: DataOutputStream? = null

    // Tracks remote device name for history logging
    private var remoteDeviceName: String? = null

    override fun onCreate() {
        Timber.d("CommunicationService - onCreate called")
        super.onCreate()
        createNotificationChannel()
        Log.d(TAG, "Service created")
    }

    private fun createNotificationChannel() {
        Timber.d("CommunicationService - createNotificationChannel called")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIF_CHANNEL_ID,
                getString(R.string.notif_channel_transfer_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notif_channel_transfer_desc)
                setShowBadge(false)
            }
            val mgr = getSystemService(NotificationManager::class.java)
            mgr?.createNotificationChannel(channel)
        }
    }

    private fun startForegroundNotification() {
        Timber.d("CommunicationService - startForegroundNotification called")
        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            packageManager.getLaunchIntentForPackage(packageName),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notification = NotificationCompat.Builder(this, NOTIF_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notif_transfer_title))
            .setContentText(getString(R.string.notif_transfer_text))
            .setContentIntent(openAppIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.d("CommunicationService - onStartCommand called with action ${intent?.action}")
        when (intent?.action) {
            ACTION_START_COMMUNICATION -> {
                val state = serviceState.get()
                if (state == CONNECTED || state == IS_SENDING || state == CONNECTING) {
                    return START_NOT_STICKY
                }
                serviceState.set(CONNECTING)
                startForegroundNotification()
                ensureExecutor().execute {
                    val port    = intent.getIntExtra(EXTRAS_GROUP_OWNER_PORT, 8888)
                    val address = intent.getStringExtra(EXTRAS_GROUP_OWNER_ADDRESS)
                    val role    = intent.getIntExtra(EXTRAS_COMMUNICATION_ROLE, CLIENT_ROLE)
                    val name    = intent.getStringExtra(EXTRAS_DEVICE_NAME)
                    try {
                        if (role == SERVER_ROLE) {
                            startServer(port, name)
                        } else {
                            startClient(address, port, name)
                        }
                    } catch (e: IOException) {
                        if (e is java.net.SocketException && e.message?.contains("Socket closed") == true) {
                            Log.d(TAG, "Server/Client socket closed, stopping communication loop")
                        } else {
                            Log.e(TAG, "Communication error", e)
                        }
                        closeAllAndStop()
                    }
                }
            }
            ACTION_SEND_MSG -> {
                startForegroundNotification()
                ensureExecutor().execute {
                    try {
                        sendFiles(intent)
                    } catch (e: IOException) {
                        Log.e(TAG, "Send error", e)
                        closeAllAndStop()
                    }
                }
            }
            ACTION_STOP_COMMUNICATION -> closeAllAndStop()
        }
        return START_NOT_STICKY
    }

    // ── Socket Setup ──────────────────────────────────────────────────────────

    @Throws(IOException::class)
    private fun startServer(port: Int, deviceName: String?) {
        Timber.d("CommunicationService - startServer called")
        serverSocket = ServerSocket(port)
        Log.d(TAG, "Server: waiting for connection on port $port")
        communicationSocket = try {
            serverSocket!!.accept()
        } finally {
            serverSocket?.close()
            serverSocket = null
        }
        communicationSocket?.soTimeout = 30000
        Log.d(TAG, "Server: client connected")
        messageReadingLoop(deviceName)
    }

    @Throws(IOException::class)
    private fun startClient(address: String?, port: Int, deviceName: String?) {
        Timber.d("CommunicationService - startClient called")
        communicationSocket = Socket().apply { bind(null) }
        Log.d(TAG, "Client: connecting to $address:$port")
        try {
            communicationSocket!!.connect(InetSocketAddress(address, port), 8000)
            communicationSocket?.soTimeout = 30000
        } catch (e: IOException) {
            Log.e(TAG, "Client: connect failed — ${e.message}")
            communicationSocket?.close()
            communicationSocket = null
            throw e
        }
        Log.d(TAG, "Client: connected")
        messageReadingLoop(deviceName)
    }

    // ── Receiving ─────────────────────────────────────────────────────────────

    /**
     * Handshake + receive loop.
     *
     * Protocol per file mirrors Bluetooth:
     *  1. 4-byte metadata length.
     *  2. Serialized FileInfo JSON.
     *  3. 8-byte file size.
     *  4. Exactly file-size bytes of raw content.
     */
    @Throws(IOException::class)
    private fun messageReadingLoop(deviceName: String?) {
        Timber.d("CommunicationService - messageReadingLoop called")
        val socket = communicationSocket ?: return
        dataOutputStream = DataOutputStream(socket.getOutputStream())
        dataOutputStream!!.writeUTF(deviceName ?: "Unknown")

        DataInputStream(socket.getInputStream()).use { dataInput ->
            val remoteDevice = dataInput.readUTF()
            remoteDeviceName = remoteDevice
            Log.d(TAG, "Connected to: $remoteDevice")
            serviceState.set(CONNECTED)

            val buffer = ByteArray(CHUNK_SIZE)
            var currentIndex = 0

            while (true) {
                val metaSize = try {
                    dataInput.readInt()
                } catch (e: EOFException) {
                    Log.d(TAG, "Remote closed Wi-Fi Direct stream")
                    break
                } catch (e: IOException) {
                    if (e is java.net.SocketException && e.message?.contains("Socket closed") == true) {
                        Log.d(TAG, "Socket closed locally, stopping reading loop")
                    } else {
                        Log.e(TAG, "Read metadata size failed", e)
                    }
                    break
                }

                if (metaSize <= 0 || metaSize > 64 * 1024) {
                    Log.e(TAG, "Invalid metadata size: $metaSize")
                    break
                }

                val metaBytes = ByteArray(metaSize)
                dataInput.readFully(metaBytes)
                val fileInfo = metaBytes.toFileInfo()
                if (fileInfo == null) {
                    Log.e(TAG, "Could not parse incoming file metadata")
                    break
                }

                val rawFileSize = dataInput.readLong()
                
                if (rawFileSize == -1L) {
                    Log.e(TAG, "File was skipped by sender: ${fileInfo.fileName}")
                    broadcastProgress(-1, fileInfo.fileName, 0L, currentIndex, fileInfo.manifest?.size ?: 0, fileInfo.mimeType, fileInfo.manifest)
                    currentIndex++
                    continue
                }

                val fileSize = rawFileSize.coerceAtLeast(0L)
                
                if (fileInfo.manifest != null) {
                    val totalSizeNeeded = fileInfo.manifest.sumOf { it.size?.toLongOrNull() ?: 0L }
                    val availableSpace = android.os.Environment.getExternalStorageDirectory().usableSpace
                    if (totalSizeNeeded > availableSpace) {
                        Log.e(TAG, "Not enough storage. Needed: $totalSizeNeeded, Available: $availableSpace")
                        throw IOException("Not enough storage on receiver device")
                    }
                }
                
                broadcastProgress(
                    progress = 0,
                    fileName = fileInfo.fileName,
                    fileSize = fileSize,
                    currentFileIndex = currentIndex,
                    totalFiles = fileInfo.manifest?.size ?: 0,
                    mimeType = fileInfo.mimeType,
                    manifest = fileInfo.manifest
                )
                val fileUri = createMediaStoreEntry(fileInfo)
                var bytesReceived = 0L

                var isEofReceived = false
                try {
                    contentResolver.openOutputStream(fileUri ?: throw IOException("Could not create destination for ${fileInfo.fileName ?: "received_file"}"), "wa").use { output ->
                        while (true) {
                            val chunkSize = dataInput.readInt()
                            if (chunkSize == 0) {
                                isEofReceived = true
                                break
                            }
                            if (chunkSize == -1) {
                                Log.e(TAG, "Sender aborted file mid-transfer")
                                isEofReceived = false
                                break
                            }
                            if (chunkSize < 0 || chunkSize > CHUNK_SIZE) {
                                throw IOException("Invalid chunk size: $chunkSize")
                            }
                            
                            var remainingChunk = chunkSize
                            var offset = 0
                            while (remainingChunk > 0) {
                                val bytesRead = dataInput.read(buffer, offset, remainingChunk)
                                if (bytesRead == -1) throw EOFException("Stream ended while receiving chunk")
                                offset += bytesRead
                                remainingChunk -= bytesRead
                            }
                            output?.write(buffer, 0, chunkSize)
                            bytesReceived += chunkSize
                            val pct = if (fileSize > 0) ((bytesReceived * 100) / fileSize).toInt() else (bytesReceived / (1024 * 1024)).toInt()
                            broadcastProgress(pct, fileInfo.fileName ?: "received_file", fileSize, currentIndex, 0, fileInfo.mimeType)
                        }
                    }
                } catch (e: Exception) {
                    fileUri?.let { uri -> 
                        try {
                            contentResolver.delete(uri, null, null)
                            Log.d(TAG, "Deleted partial file after error: $uri")
                        } catch (delEx: Exception) {
                            Log.e(TAG, "Failed to delete partial file", delEx)
                        }
                    }
                    broadcastProgress(-1, fileInfo.fileName ?: "received_file", fileSize, currentIndex, fileInfo.manifest?.size ?: 0, fileInfo.mimeType, null)
                    throw e
                }
                
                if (!isEofReceived) {
                    fileUri?.let { uri -> 
                        try {
                            contentResolver.delete(uri, null, null)
                        } catch (e: Exception) {}
                    }
                    broadcastProgress(-1, fileInfo.fileName ?: "received_file", fileSize, currentIndex, fileInfo.manifest?.size ?: 0, fileInfo.mimeType, null)
                    throw IOException("Connection closed before EOF marker")
                }
                
                fileUri?.let { uri ->
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        try {
                            val updateValues = ContentValues().apply { put(MediaStore.Files.FileColumns.IS_PENDING, 0) }
                            contentResolver.update(uri, updateValues, null, null)
                            scanMediaUri(this@CommunicationService, uri)
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to clear IS_PENDING for $uri", e)
                        }
                    }
                }

                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        historyRepository.addEntry(
                            TransferHistoryEntity(
                                fileName = fileInfo.fileName ?: "Unknown",
                                mimeType = fileInfo.mimeType,
                                fileSizeBytes = fileSize,
                                isSender = false,
                                transferMethod = "WiFi-Direct",
                                remoteDeviceName = remoteDeviceName,
                                contentUri = fileUri?.toString()
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save receiver history entry", e)
                    }
                }

                broadcastProgress(
                    progress = 100,
                    fileName = fileInfo.fileName ?: "received_file",
                    fileSize = fileSize,
                    currentFileIndex = currentIndex,
                    totalFiles = fileInfo.manifest?.size ?: 0,
                    mimeType = fileInfo.mimeType
                )
                Log.d(TAG, "Received: ${fileInfo.fileName ?: "received_file"} ($bytesReceived bytes)")
                currentIndex++
            }
        }
    }

    // ── Sending ───────────────────────────────────────────────────────────────

    /**
     * Sends all URIs in the intent over the active socket.
     *
     * Protocol per file mirrors Bluetooth:
     *  1. Send 4-byte metadata length.
     *  2. Send serialized FileInfo JSON.
     *  3. Send 8-byte file size.
     *  4. Stream exactly file-size bytes.
     *
     * Fix: removed the 5s pre-send delay, sentinel EOF, and per-chunk runBlocking/delay calls.
     */
    @Throws(IOException::class)
    private fun sendFiles(intent: Intent) {
        Timber.d("CommunicationService - sendFiles called")
        val uris = intent.getParcelableArrayListExtra<Uri>("urilist") ?: return
        if (uris.isEmpty()) return
        val outputStream = waitForOutputStream() ?: throw IOException("Wi-Fi Direct socket is not connected")

        val totalFiles = uris.size
        val allFileInfos = uris.map { getFileDetailsFromUri(it, contentResolver) }
        var currentIndex = 0
        for (i in uris.indices) {
            val uri = uris[i]
            val baseFileInfo = allFileInfos[i]
            val fileInfo = if (i == 0) baseFileInfo.copy(manifest = allFileInfos) else baseFileInfo
            val totalSize = fileInfo.size?.toLong() ?: 0L
            val metaBytes = fileInfo.toByteArray() ?: throw IOException("Failed to serialize FileInfo")

            outputStream.writeInt(metaBytes.size)
            outputStream.write(metaBytes)
            
            val inputStream = try {
                contentResolver.openInputStream(uri)
            } catch (e: Exception) {
                null
            }
            
            if (inputStream == null) {
                Log.e(TAG, "File skipped (deleted or inaccessible): $uri")
                outputStream.writeLong(-1L)
                outputStream.flush()
                broadcastProgress(-1, fileInfo.fileName, totalSize, currentIndex, totalFiles, fileInfo.mimeType, null)
                currentIndex++
                continue
            }

            outputStream.writeLong(totalSize)
            outputStream.flush()

            broadcastProgress(0, fileInfo.fileName, totalSize, currentIndex, totalFiles, fileInfo.mimeType, null)
            Log.d(TAG, "Sending: ${fileInfo.fileName} ($totalSize bytes)")

            try {
                inputStream.use { stream ->
                    val buffer = ByteArray(CHUNK_SIZE)
                    var bytesSent = 0L

                    while (true) {
                        val bytesRead = stream.read(buffer)
                        if (bytesRead == -1) break
                        
                        outputStream.writeInt(bytesRead)
                        outputStream.write(buffer, 0, bytesRead)
                        
                        bytesSent += bytesRead
                        val pct = if (totalSize > 0) ((bytesSent * 100) / totalSize).toInt() else (bytesSent / (1024 * 1024)).toInt()
                        broadcastProgress(pct.coerceIn(0, 99), fileInfo.fileName, totalSize, currentIndex, totalFiles, fileInfo.mimeType, null)
                    }
                    
                    val eofMarker = java.nio.ByteBuffer.allocate(4).putInt(0).array()
                    outputStream.write(eofMarker)
                    outputStream.flush()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Socket closed during transfer: $uri", e)
                broadcastProgress(-1, fileInfo.fileName, totalSize, currentIndex, totalFiles, fileInfo.mimeType, null)
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Failed reading file mid-transfer: $uri", e)
                val cancelMarker = java.nio.ByteBuffer.allocate(4).putInt(-1).array()
                try {
                    outputStream.write(cancelMarker)
                    outputStream.flush()
                } catch (ioe: IOException) {
                    // Ignore, connection probably already closed
                }
                broadcastProgress(-1, fileInfo.fileName, totalSize, currentIndex, totalFiles, fileInfo.mimeType, null)
                currentIndex++
                continue
            }

            broadcastProgress(100, fileInfo.fileName, totalSize, currentIndex, totalFiles, fileInfo.mimeType)
            Log.d(TAG, "Sent: ${fileInfo.fileName} ($totalSize bytes)")
            currentIndex++

            // Log completed WiFi Direct transfer to history DB for sender
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    historyRepository.addEntry(
                            TransferHistoryEntity(
                                fileName = fileInfo.fileName ?: "Unknown",
                                mimeType = fileInfo.mimeType,
                                fileSizeBytes = totalSize,
                                isSender = true,
                                transferMethod = "WiFi-Direct",
                                remoteDeviceName = remoteDeviceName ?: "Unknown Device",
                                contentUri = uri.toString()
                            )
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to save sender history entry", e)
                }
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun waitForOutputStream(): DataOutputStream? {
        val deadline = System.currentTimeMillis() + SOCKET_WAIT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            dataOutputStream?.let { return it }
            if (serviceState.get() == NOT_CONNECTED) return null
            try {
                Thread.sleep(SOCKET_WAIT_STEP_MS)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                return null
            }
        }
        return dataOutputStream
    }

    @Synchronized
    private fun ensureExecutor(): ExecutorService {
        if (executorService.isShutdown || executorService.isTerminated) {
            executorService = Executors.newCachedThreadPool()
        }
        return executorService
    }

    private fun broadcastProgress(
        progress: Int,
        fileName: String?,
        fileSize: Long,
        currentFileIndex: Int,
        totalFiles: Int,
        mimeType: String?,
        manifest: List<com.invincible.jedishare.domain.chat.FileInfo>? = null
    ) {
        val update = WifiTransferUpdate(
            progress = progress,
            fileName = fileName ?: "",
            fileSize = fileSize,
            currentFileIndex = currentFileIndex,
            totalFiles = totalFiles,
            remoteDeviceName = remoteDeviceName,
            mimeType = mimeType,
            manifest = manifest
        )
        _transferUpdates.value = update
        sendBroadcast(Intent(BROADCAST_SENDING_UPDATE).apply {
            putExtra(EXTRAS_PROGRESS_STATE, progress)
            putExtra(EXTRAS_FILE_NAME, update.fileName)
            putExtra(EXTRAS_FILE_SIZE, fileSize)
            putExtra(EXTRAS_CURRENT_FILE_INDEX, currentFileIndex)
            putExtra(EXTRAS_TOTAL_FILES, totalFiles)
            putExtra(EXTRAS_REMOTE_DEVICE_NAME, remoteDeviceName)
            putExtra(EXTRAS_MIME_TYPE, mimeType)
        })
    }

    /**
     * Creates a MediaStore entry for the incoming file.
     * Stores files in app-specific subfolders (/JediShare) for clean organization.
     */
    private fun createMediaStoreEntry(fileInfo: FileInfo): Uri? {
        Timber.d("CommunicationService - createMediaStoreEntry called")
        val mimeType = fileInfo.mimeType ?: "*/*"
        val fileName = fileInfo.fileName ?: "received_file"
        val values = ContentValues().apply {
            put(MediaStore.Files.FileColumns.DISPLAY_NAME, fileName)
            put(MediaStore.Files.FileColumns.MIME_TYPE, mimeType)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Files.FileColumns.IS_PENDING, 1)
                when {
                    mimeType.startsWith("image/") -> put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/JustShare")
                    mimeType.startsWith("audio/") -> put(MediaStore.Audio.Media.RELATIVE_PATH,  "${Environment.DIRECTORY_MUSIC}/JustShare")
                    mimeType.startsWith("video/") -> put(MediaStore.Video.Media.RELATIVE_PATH,  "${Environment.DIRECTORY_MOVIES}/JustShare")
                    else                          -> put(MediaStore.Downloads.RELATIVE_PATH,     "${Environment.DIRECTORY_DOWNLOADS}/JustShare")
                }
            }
        }
        val contentUri = when {
            mimeType.startsWith("image/") -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            mimeType.startsWith("audio/") -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            mimeType.startsWith("video/") -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            else                          -> MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        }
        return try {
            contentResolver.insert(contentUri, values)
        } catch (e: Exception) {
            Log.e(TAG, "MediaStore insert failed", e)
            null
        }
    }

    private fun closeAllAndStop() {
        Timber.d("CommunicationService - closeAllAndStop called")
        try { 
            communicationSocket?.setSoLinger(true, 0)
            communicationSocket?.close() 
        } catch (e: IOException) { Log.w(TAG, "socket close: ${e.message}") }
        try { serverSocket?.close()        } catch (e: IOException) { Log.w(TAG, "serverSocket close: ${e.message}") }
        try { dataOutputStream?.close()    } catch (e: IOException) { Log.w(TAG, "dos close: ${e.message}") }
        communicationSocket = null
        serverSocket = null
        dataOutputStream = null
        executorService.shutdownNow()
        serviceState.set(NOT_CONNECTED)
        @Suppress("DEPRECATION")
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        Timber.d("CommunicationService - onDestroy called")
        super.onDestroy()
        closeAllAndStop()
    }
}
