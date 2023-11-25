package com.invincible.jedishare

import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.util.Log
import com.invincible.jedishare.data.chat.toByteArray
import com.invincible.jedishare.data.chat.toFileInfo
import com.invincible.jedishare.domain.chat.FileInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

public class CommunicationService(): Service() {
    private val NOTIFICATION_CHANNEL_ID = "communication_service_channel"
    val TAG = "myDebugTag"

    val SERVER_ROLE = 0
    val CLIENT_ROLE = 1

    private val CONNECTED = 0
    private val NOT_CONNECTED = 1
    private val CONNECTING = 2
    private val IS_SENDING = 3

    val ONGOING_NOTIFICATION_ID = 22

    private val CHUCK_FILE_SIZE = 8192 //8 KB

    val FILE_DELIMITER = "----FILE_DELIMITER----"
    val repeatedString = FILE_DELIMITER.repeat(40)


    val ACTION_SEND_MSG = "com.invincible.jedishare.ACTION_SEND_MSG"
    val ACTION_START_COMMUNICATION = "com.invincible.jedishare.ACTION_START_COMMUNICATION"
    val ACTION_CHECK_COMMUNICATION = "com.invincible.jedishare.ACTION_CHECK_COMMUNICATION"
    val ACTION_STOP_COMMUNICATION = "com.invincible.jedishare.STOP_COMMUNICATION"
    val RESTART_COUNTING_NEWMSG = "com.invincible.jedishare.RESTART_COUNTING_NEWMSG"

    val EXTRAS_COMMUNICATION_ROLE = "com.invincible.jedishare.EXTRAS_COMMUNICATION_ROLE"
    val EXTRAS_GROUP_OWNER_ADDRESS = "com.invincible.jedishare.EXTRAS_GROUP_OWNER_ADDRESS"
    val EXTRAS_GROUP_OWNER_PORT = "com.invincible.jedishare.EXTRAS_GROUP_OWNER_PORT"
    val EXTRAS_DEVICE_NAME = "com.invincible.jedishare.EXTRAS_DEVICE_NAME"
    val EXTRAS_MSG_TYPE = "com.invincible.jedishare.EXTRAS_MSG_TYPE"
    val EXTRAS_TEXT_CONTENT = "com.invincible.jedishare.EXTRAS_TEXT_CONTENT"
    val EXTRAS_CONTENT_URI = "com.invincible.jedishare.EXTRAS_CONTENT_URI"

    private var newMessageNumber: AtomicInteger? = null
    private var isChatActivityOpen: AtomicBoolean? = null
    private var currentSendingProgress: AtomicInteger? = null
    private var currentFileNameSending: String? = null
    private var deviceToManage: String? = null
    private var serviceState: AtomicInteger? = null

    private var executorService: ExecutorService? = null
    private var communicationSocket: Socket? = null
    private var serverSocket: ServerSocket? = null
    private var dataOutputStream: DataOutputStream? = null



    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "service created")
        executorService = Executors.newCachedThreadPool()
        communicationSocket = null
        dataOutputStream = null
        deviceToManage = null
        newMessageNumber = AtomicInteger(0)
        currentSendingProgress = AtomicInteger(0)
        currentFileNameSending = ""
        isChatActivityOpen = AtomicBoolean(false)
        serviceState = AtomicInteger(NOT_CONNECTED)
    }

    override fun onBind(intent: Intent?): IBinder? {
        throw UnsupportedOperationException("Not yet implemented")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val currentServiceState = serviceState!!.get()
        if (intent != null) {
            when(intent.action){
                // To be filled
                ACTION_CHECK_COMMUNICATION -> {
                    if(currentServiceState == CONNECTED){

                    }
                    else if(currentServiceState == IS_SENDING){

                    }
                    else if(currentServiceState == NOT_CONNECTED){

                    }
                }
                ACTION_START_COMMUNICATION -> {
                    Log.d(TAG, "Start communication")
                    if (currentServiceState == CONNECTED || currentServiceState == IS_SENDING || currentServiceState == CONNECTING) return START_NOT_STICKY
                    executorService?.execute {
                        serviceState!!.set(CONNECTING)
                        var groupOwnerPort: Int = intent.getIntExtra(EXTRAS_GROUP_OWNER_PORT, 8888)
                        var groupOwnerAddress: String? =
                            intent.getStringExtra(EXTRAS_GROUP_OWNER_ADDRESS)
                        var role: Int = intent.getIntExtra(EXTRAS_COMMUNICATION_ROLE, 1)
                        var thisDeviceName: String? = intent.getStringExtra(EXTRAS_DEVICE_NAME)
                        try {
                            if(role == SERVER_ROLE){
                                startListeningForMsgServer(groupOwnerPort, thisDeviceName)
                            }
                            else{
                                startListeningForMsgClient(groupOwnerAddress, groupOwnerPort, thisDeviceName)
                            }
                        } catch (e: IOException) {

                        } finally {

                        }
                    }
                }
                ACTION_SEND_MSG -> {
                    executorService?.execute{
                        try {
                            sendMsg(intent)
                        }
                        catch (e: IOException){
                            closeAllAndStop()
                            e.printStackTrace()
                        }
                    }
                }

            }
        }
        return START_NOT_STICKY
    }

    private fun closeAllAndStop() {
        try {
            if (communicationSocket != null) {
                Log.d(TAG, "Closing communicationSocket")
                communicationSocket!!.close()
                communicationSocket = null
            }
            if (serverSocket != null) {
                serverSocket!!.close()
                serverSocket = null
            }
            if (dataOutputStream != null) {
                dataOutputStream!!.close()
                dataOutputStream = null
            }
        } catch (e: IOException) {
            // Give up
            Log.e(TAG, e.message!!)
        } finally {
            if (executorService != null) {
                executorService!!.shutdownNow()
                executorService = null
            }
            stopForeground(true)
            serviceState!!.set(NOT_CONNECTED)
            stopSelf()
        }
    }

    @Throws(IOException::class)
    private fun messageReadingLoop(thisDeviceName: String?) {
        dataOutputStream = DataOutputStream(communicationSocket!!.getOutputStream())
        dataOutputStream!!.writeUTF(thisDeviceName)

        DataInputStream(communicationSocket!!.getInputStream()).use { dataInputStream ->
            deviceToManage = dataInputStream.readUTF()

            //Some Part of Code left intentionally
            serviceState!!.set(CONNECTED)

            var msgType: Int
            var size: Long = 0
            var fileTitle: String = ""

            var isFirst: Boolean = true
            var currSize: Long = -1
            var fileUri: Uri? = null
            var progress: Int = 0

            var buffer = ByteArray(CHUCK_FILE_SIZE)
            val i = Intent("com.invincible.jedishare.SENDING_UPDATE")

            while (true){
                if(isFirst){
                    isFirst = false
                    currSize = 0
                    val bytesRead = dataInputStream.read(buffer)

                    var fileInfo = buffer.toFileInfo()
                    Log.e(TAG, "START messageReadingLoop: ${fileInfo.toString()}")
                    if (fileInfo != null) {
                        size = fileInfo.size?.toLong() ?: 0
                    }
                    if (fileInfo != null) {
                        fileTitle = fileInfo.fileName.toString()
                    }
                    i.putExtra("com.invincible.jedishare.EXTRAS_PROGRESS_STATE", 0)
                    if (fileInfo != null) {
                        i.putExtra("com.invincible.jedishare.EXTRAS_FILE_NAME", fileInfo.fileName)
                    }
                    if (fileInfo != null) {
                        i.putExtra("com.invincible.jedishare.EXTRAS_FILE_SIZE", fileInfo.size)
                    }
                    sendBroadcast(i)

                        // Create a content values to store file information
                        val values = ContentValues().apply {
                            if (fileInfo != null) {
                                put(MediaStore.Files.FileColumns.DISPLAY_NAME, "${fileInfo.fileName}")
                            }
                            put(MediaStore.Files.FileColumns.MIME_TYPE, fileInfo?.mimeType)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                when {
                                    fileInfo?.mimeType?.startsWith("image/") == true -> put(
                                        MediaStore.Images.Media.RELATIVE_PATH,
                                        Environment.DIRECTORY_PICTURES
                                    )
                                    fileInfo?.mimeType?.startsWith("audio/") == true -> put(
                                        MediaStore.Audio.Media.RELATIVE_PATH,
                                        Environment.DIRECTORY_MUSIC
                                    )
                                    fileInfo?.mimeType?.startsWith("video/") == true -> put(
                                        MediaStore.Video.Media.RELATIVE_PATH,
                                        Environment.DIRECTORY_MOVIES
                                    )
                                }
                            }
                            Log.d(TAG, "messageReadingLoop: $fileUri")
                        }

                        // Get the content URI for the new media entry
                        val contentUri = when {
                            fileInfo?.mimeType?.startsWith("image/") == true -> MediaStore.Images.Media.getContentUri(
                                MediaStore.VOLUME_EXTERNAL)
                            fileInfo?.mimeType?.startsWith("audio/") == true -> MediaStore.Audio.Media.getContentUri(
                                MediaStore.VOLUME_EXTERNAL)
                            fileInfo?.mimeType?.startsWith("video/") == true -> MediaStore.Video.Media.getContentUri(
                                MediaStore.VOLUME_EXTERNAL)
                            else -> MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
                        }

                        fileUri = contentResolver?.insert(contentUri, values)

                }
                else{
                    while(currSize < size || progress!=100) {
                        try{
                            val bytesRead = dataInputStream.read(buffer)
                            currSize += bytesRead
                            Log.d(TAG,"messageReadingLoop: " + buffer.toString()+" $bytesRead")
                            progress = (currSize.toFloat() / (size.toFloat() ?: currSize.toFloat()) * 100).toInt()
                            Log.e(TAG, "sendMsg: $progress", )
                            i.putExtra("com.invincible.jedishare.EXTRAS_PROGRESS_STATE", progress)
                            i.putExtra("com.invincible.jedishare.EXTRAS_FILE_NAME", fileTitle)
                            i.putExtra("com.invincible.jedishare.EXTRAS_FILE_SIZE", size)
                            sendBroadcast(i)

                            fileUri?.let {
                                contentResolver.openOutputStream(it, "wa")?.use { outputStream ->
                                    outputStream.write(buffer.copyOfRange(0,bytesRead))
                                }
                            }
                            Log.e(TAG, "Receiver End: $currSize")
                        }
                        catch (e: java.lang.Exception){
                            e.printStackTrace()
                            Log.e("MYTAG", e.toString())
                        }
                    }

                    runBlocking {
                        launch(Dispatchers.IO){
                            isFirst = true
                            currSize = -1
                            delay(1000)
                        }
                    }
                }
            }
        }


    }

    @Throws(IOException::class)
    open fun startListeningForMsgClient(
        groupOwnerAddress: String?,
        groupOwnerPort: Int,
        thisDeviceName: String?
    ) {
        communicationSocket = Socket()
        communicationSocket!!.bind(null)
        // race condition if the client starts before the server --> I wait a second before connecting
        try {
            Thread.sleep(1000)
        }
        catch (e: InterruptedException){
            Log.d(TAG, "Reading thread interrupted")
        }

        Log.d(TAG, "Client: Try to connect")

        try{
            communicationSocket!!.connect(
                InetSocketAddress(groupOwnerAddress, groupOwnerPort),
                6000
            )
        }
        catch (e: IOException){
            Log.d(TAG, e.message!!)
            return
        }
        Log.d(TAG, "Socket client connected")
        messageReadingLoop(thisDeviceName)
    }

    @Throws(IOException::class)
    private fun startListeningForMsgServer(groupOwnerPort: Int, thisDeviceName: String?) {
        serverSocket = ServerSocket(groupOwnerPort)
        Log.d(TAG, "Server: Try to connect")
        serverSocket!!.soTimeout = 6000
        try {
            communicationSocket = serverSocket!!.accept()
        }
        catch (e: SocketTimeoutException){
            return
        }
        Log.d(TAG, "Socket server connected")
        serverSocket!!.close()
        serverSocket = null
        messageReadingLoop(thisDeviceName)
    }

    @Throws(IOException::class)
    private fun sendMsg(intent: Intent){
//        val txtMsg = intent.getStringExtra(EXTRAS_TEXT_CONTENT)
//        dataOutputStream!!.writeUTF(txtMsg)
        var fileUriList = intent?.getParcelableArrayListExtra<Uri>("urilist") ?: emptyList<Uri>()
        for(uri in fileUriList){
            val stream: InputStream? = this.contentResolver.openInputStream(uri)
            var fileInfo: FileInfo? = null

            runBlocking {
                launch(Dispatchers.IO){
                    delay(5000)
                }
            }

            // Get file information
            fileInfo = getFileDetailsFromUri(uri, this.contentResolver)
            fileInfo.toByteArray()?.let {
                dataOutputStream!!.write(it)
            }
            Log.e(TAG, "START sendMsg: ${fileInfo.toString()}")

            val i = Intent("com.invincible.jedishare.SENDING_UPDATE")
            i.putExtra("com.invincible.jedishare.EXTRAS_PROGRESS_STATE", 0)
            i.putExtra("com.invincible.jedishare.EXTRAS_FILE_NAME", fileInfo.fileName)
            fileInfo.size?.let { i.putExtra("com.invincible.jedishare.EXTRAS_FILE_SIZE", it.toLong()) }
            sendBroadcast(i)
//            LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(i)

            stream.use { inputStream ->
                val buffer = ByteArray(CHUCK_FILE_SIZE)
                var bytesRead: Int = 0
                var iterationCount = 0L
                var currSize = 0L
                var progress = 0

                while (inputStream?.read(buffer).also {
                        if (it != null) {
                            bytesRead = it
                        }
                    } != -1) {
                    Log.e("MYTAG", "Bytes Read : $iterationCount " + bytesRead.toString())
                    currSize += bytesRead
                    runBlocking {
                        launch(Dispatchers.IO){
                            progress = (currSize.toFloat() / (fileInfo.size?.toLong()?.toFloat() ?: currSize.toFloat()) * 100).toInt()
                            Log.e(TAG, "sendMsg: $progress", )
                            i.putExtra("com.invincible.jedishare.EXTRAS_PROGRESS_STATE", progress)
                            i.putExtra("com.invincible.jedishare.EXTRAS_FILE_NAME", fileInfo.fileName)
                            fileInfo.size?.let { i.putExtra("com.invincible.jedishare.EXTRAS_FILE_SIZE", it.toLong()) }
                            sendBroadcast(i)
                            delay(20)
                        }
                    }
                    dataOutputStream!!.write(buffer.copyOfRange(0,bytesRead))
                    iterationCount++
                }
            }
        }
//        runBlocking {
//            launch(Dispatchers.IO){
//                delay(1000)
//            }
//        }
//        dataOutputStream!!.writeBytes(repeatedString)
//        Log.e(TAG,repeatedString.toByteArray().toString() + " ${repeatedString.toByteArray().size}")
//        runBlocking {
//            launch(Dispatchers.IO){
//                delay(100)
//            }
//        }
    }
}
