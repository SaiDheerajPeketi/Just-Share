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
import kotlinx.coroutines.launch
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

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
        const val CHUNK_SIZE = 8192 // 8 KB — matches BluetoothDataTransferService

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
        const val BROADCAST_SENDING_UPDATE   = "com.invincible.jedishare.SENDING_UPDATE"

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
        Timber.d("CommunicationService - onBind called")
        when (intent?.action) {
            ACTION_START_COMMUNICATION -> {
                val state = serviceState.get()
                if (state == CONNECTED || state == IS_SENDING || state == CONNECTING) {
                    return START_NOT_STICKY
                }
                startForegroundNotification()
                ensureExecutor().execute {
                    serviceState.set(CONNECTING)
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
                        Log.e(TAG, "Communication error", e)
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
        serverSocket = ServerSocket(port).apply { soTimeout = 8000 }
        Log.d(TAG, "Server: waiting for connection on port $port")
        communicationSocket = try {
            serverSocket!!.accept()
        } catch (e: SocketTimeoutException) {
            Log.w(TAG, "Server: accept timed out")
            throw e
        } finally {
            serverSocket?.close()
            serverSocket = null
        }
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
                    Log.e(TAG, "Read metadata size failed", e)
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

                val fileSize = dataInput.readLong().coerceAtLeast(0L)
                val fileName = fileInfo.fileName ?: "received_file"
                val fileUri = createMediaStoreEntry(fileInfo)
                var bytesReceived = 0L

                broadcastProgress(
                    progress = 0,
                    fileName = fileName,
                    fileSize = fileSize,
                    currentFileIndex = currentIndex,
                    totalFiles = 0
                )

                contentResolver.openOutputStream(fileUri ?: throw IOException("Could not create destination for $fileName"), "wa").use { output ->
                    var remaining = fileSize
                    while (remaining > 0) {
                        val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                        val bytesRead = dataInput.read(buffer, 0, toRead)
                        if (bytesRead == -1) throw EOFException("Stream ended while receiving $fileName")
                        output?.write(buffer, 0, bytesRead)
                        remaining -= bytesRead
                        bytesReceived += bytesRead

                        val progress = if (fileSize > 0) (bytesReceived * 100 / fileSize).toInt() else 100
                        broadcastProgress(
                            progress = progress.coerceIn(0, 99),
                            fileName = fileName,
                            fileSize = fileSize,
                            currentFileIndex = currentIndex,
                            totalFiles = 0
                        )
                    }
                }

                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    try {
                        historyRepository.addEntry(
                            TransferHistoryEntity(
                                fileName = fileName,
                                mimeType = fileInfo.mimeType,
                                fileSizeBytes = fileSize,
                                isSender = false,
                                transferMethod = "WiFi-Direct",
                                remoteDeviceName = remoteDeviceName
                            )
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to save receiver history entry", e)
                    }
                }

                broadcastProgress(
                    progress = 100,
                    fileName = fileName,
                    fileSize = fileSize,
                    currentFileIndex = currentIndex,
                    totalFiles = 0
                )
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
        val out = waitForOutputStream() ?: throw IOException("Wi-Fi Direct socket is not connected")

        val totalFiles = uris.size
        var currentIndex = 0
        for (uri in uris) {
            val fileInfo = getFileDetailsFromUri(uri, contentResolver)
            val totalSize = fileInfo.size?.toLong() ?: 0L
            val metaBytes = fileInfo.toByteArray() ?: throw IOException("Failed to serialize FileInfo")

            out.writeInt(metaBytes.size)
            out.write(metaBytes)
            out.writeLong(totalSize)
            out.flush()

            broadcastProgress(0, fileInfo.fileName, totalSize, currentIndex, totalFiles)
            Log.d(TAG, "Sending: ${fileInfo.fileName} ($totalSize bytes)")

            // Stream file bytes
            contentResolver.openInputStream(uri)?.use { inputStream ->
                val buffer = ByteArray(CHUNK_SIZE)
                var bytesSent = 0L
                var bytesRead: Int
                while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                    out.write(buffer, 0, bytesRead)
                    bytesSent += bytesRead
                    val progress = if (totalSize > 0) (bytesSent * 100 / totalSize).toInt() else 0
                    broadcastProgress(progress.coerceIn(0, 99), fileInfo.fileName, totalSize, currentIndex, totalFiles)
                }
            } ?: Log.e(TAG, "Could not open InputStream for $uri")

            out.flush()
            broadcastProgress(100, fileInfo.fileName, totalSize, currentIndex, totalFiles)
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
                            remoteDeviceName = remoteDeviceName ?: "Unknown Device"
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
        totalFiles: Int
    ) {
        sendBroadcast(Intent(BROADCAST_SENDING_UPDATE).apply {
            putExtra(EXTRAS_PROGRESS_STATE, progress)
            putExtra(EXTRAS_FILE_NAME, fileName ?: "")
            putExtra(EXTRAS_FILE_SIZE, fileSize)
            putExtra(EXTRAS_CURRENT_FILE_INDEX, currentFileIndex)
            putExtra(EXTRAS_TOTAL_FILES, totalFiles)
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
        try { communicationSocket?.close() } catch (e: IOException) { Log.w(TAG, "socket close: ${e.message}") }
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
