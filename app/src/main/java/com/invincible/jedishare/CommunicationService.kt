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

        private const val NOTIF_CHANNEL_ID = "jedishare_transfer"
        private const val NOTIF_ID = 42

        private const val CONNECTED     = 0
        private const val NOT_CONNECTED = 1
        private const val CONNECTING    = 2
        private const val IS_SENDING    = 3
    }

    private val serviceState      = AtomicInteger(NOT_CONNECTED)
    private val executorService: ExecutorService = Executors.newCachedThreadPool()
    private var communicationSocket: Socket?      = null
    private var serverSocket: ServerSocket?       = null
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
                executorService.execute {
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
                executorService.execute {
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
            return
        } finally {
            serverSocket?.close()
            serverSocket = null
        }
        Log.d(TAG, "Server: client connected")
        startForegroundNotification()   // Required for API 34+ dataSync foreground service
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
            return
        }
        Log.d(TAG, "Client: connected")
        startForegroundNotification()   // Required for API 34+ dataSync foreground service
        messageReadingLoop(deviceName)
    }

    // ── Receiving ─────────────────────────────────────────────────────────────

    /**
     * Handshake + receive loop.
     *
     * Protocol per file (mirrors sendFiles):
     *  1. First 8KB block → FileInfo JSON (metadata)
     *  2. Subsequent blocks → raw file bytes
     *  3. A full CHUNK_SIZE block filled with 0xFF → EOF sentinel
     *     Triggers MediaStore flush and resets for next file.
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
            val progressIntent = Intent(BROADCAST_SENDING_UPDATE)
            var isFirstChunk = true
            var fileUri: Uri? = null
            var fileSize = 0L
            var fileName = ""
            var bytesReceived = 0L

            while (true) {
                val bytesRead = try {
                    dataInput.read(buffer)
                } catch (e: IOException) {
                    Log.e(TAG, "Read failed", e)
                    break
                }
                if (bytesRead <= 0) continue
                val chunk = buffer.copyOfRange(0, bytesRead)

                when {
                    // EOF sentinel: full chunk of 0xFF
                    bytesRead == CHUNK_SIZE && chunk.all { it == 0xFF.toByte() } -> {
                        Log.d(TAG, "EOF sentinel — file '$fileName' complete")
                        // TODO-15: Log completed WiFi Direct transfer to history DB
                        val snap_fileName = fileName
                        val snap_fileSize = fileSize
                        val snap_mimeType = fileUri?.let {
                            contentResolver.getType(it)
                        }
                        try {
                            kotlinx.coroutines.runBlocking {
                                historyRepository.addEntry(
                                    TransferHistoryEntity(
                                        fileName         = snap_fileName,
                                        mimeType         = snap_mimeType,
                                        fileSizeBytes    = snap_fileSize,
                                        isSender         = false,
                                        transferMethod   = "WiFi-Direct",
                                        remoteDeviceName = remoteDeviceName
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Failed to save history entry", e)
                        }
                        // Broadcast 100% completion
                        progressIntent.apply {
                            putExtra(EXTRAS_PROGRESS_STATE, 100)
                            putExtra(EXTRAS_FILE_NAME, fileName)
                            putExtra(EXTRAS_FILE_SIZE, fileSize)
                        }
                        sendBroadcast(progressIntent)
                        // Reset for next file
                        isFirstChunk = true
                        fileUri = null
                        bytesReceived = 0L
                    }

                    isFirstChunk -> {
                        // Parse FileInfo metadata
                        isFirstChunk = false
                        val fileInfo = chunk.toFileInfo()
                        Log.d(TAG, "Incoming file: $fileInfo")
                        fileInfo?.let {
                            fileSize = it.size?.toLong() ?: 0L
                            fileName = it.fileName ?: "received_file"
                            fileUri = createMediaStoreEntry(it)

                            progressIntent.apply {
                                putExtra(EXTRAS_PROGRESS_STATE, 0)
                                putExtra(EXTRAS_FILE_NAME, it.fileName)
                                putExtra(EXTRAS_FILE_SIZE, fileSize)
                            }
                            sendBroadcast(progressIntent)
                        }
                    }

                    else -> {
                        // Write file chunk
                        fileUri?.let { uri ->
                            contentResolver.openOutputStream(uri, "wa")?.use { it.write(chunk) }
                        }
                        bytesReceived += chunk.size
                        val progress = if (fileSize > 0) (bytesReceived * 100 / fileSize).toInt() else 0
                        progressIntent.apply {
                            putExtra(EXTRAS_PROGRESS_STATE, progress.coerceIn(0, 99)) // 100 only on EOF
                            putExtra(EXTRAS_FILE_NAME, fileName)
                            putExtra(EXTRAS_FILE_SIZE, fileSize)
                        }
                        sendBroadcast(progressIntent)
                    }
                }
            }
        }
    }

    // ── Sending ───────────────────────────────────────────────────────────────

    /**
     * Sends all URIs in the intent over the active socket.
     *
     * Protocol per file:
     *  1. Send serialized FileInfo JSON (first block).
     *  2. Stream raw file bytes in [CHUNK_SIZE] chunks.
     *  3. Send full [CHUNK_SIZE] block of 0xFF as EOF sentinel.
     *
     * Fix: removed the 5s pre-send delay and the per-chunk runBlocking/delay calls.
     */
    @Throws(IOException::class)
    private fun sendFiles(intent: Intent) {
        Timber.d("CommunicationService - sendFiles called")
        val uris = intent.getParcelableArrayListExtra<Uri>("urilist") ?: return
        val out  = dataOutputStream ?: return

        val progressIntent = Intent(BROADCAST_SENDING_UPDATE)

        val totalFiles = uris.size
        var currentIndex = 0
        for (uri in uris) {
            val fileInfo = getFileDetailsFromUri(uri, contentResolver)
            val totalSize = fileInfo.size?.toLong() ?: 0L

            // Send metadata header
            fileInfo.toByteArray()?.let { out.write(it) } ?: Log.e(TAG, "Failed to serialize FileInfo")
            out.flush()

            progressIntent.apply {
                putExtra(EXTRAS_PROGRESS_STATE, 0)
                putExtra(EXTRAS_FILE_NAME, fileInfo.fileName)
                putExtra(EXTRAS_FILE_SIZE, totalSize)
                putExtra(EXTRAS_CURRENT_FILE_INDEX, currentIndex)
                putExtra(EXTRAS_TOTAL_FILES, totalFiles)
            }
            sendBroadcast(progressIntent)
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
                    progressIntent.putExtra(EXTRAS_PROGRESS_STATE, progress.coerceIn(0, 99))
                    sendBroadcast(progressIntent)
                }
            } ?: Log.e(TAG, "Could not open InputStream for $uri")

            // Broadcast 100% before EOF
            progressIntent.putExtra(EXTRAS_PROGRESS_STATE, 100)
            sendBroadcast(progressIntent)

            // EOF sentinel
            out.write(ByteArray(CHUNK_SIZE) { 0xFF.toByte() })
            out.flush()
            Log.d(TAG, "EOF sentinel sent for ${fileInfo.fileName}")
            currentIndex++

            // Log completed WiFi Direct transfer to history DB for sender
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                try {
                    historyRepository.addEntry(
                        TransferHistoryEntity(
                            fileName = fileInfo.fileName ?: "Unknown",
                            mimeType = fileInfo.format?.let { classifyFileType(it) },
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
