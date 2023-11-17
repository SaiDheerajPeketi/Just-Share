package com.invincible.jedishare

import android.Manifest
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.service.autofill.OnClickAction
import android.util.Log
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents
import androidx.activity.viewModels
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import coil.compose.rememberImagePainter
import coil.transform.CircleCropTransformation
import com.invincible.jedishare.presentation.Audio
import com.invincible.jedishare.presentation.AudioViewModel
import com.invincible.jedishare.presentation.Image
import com.invincible.jedishare.presentation.ImageViewModel
import com.invincible.jedishare.presentation.Video
import com.invincible.jedishare.presentation.VideoViewModel
import com.invincible.jedishare.ui.theme.JediShareTheme
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.content.ContextCompat
import coil.compose.rememberImagePainter
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.Serializable

class SelectFile : ComponentActivity() {
    private val imageViewModel by viewModels<ImageViewModel>()
    private val videoViewModel by viewModels<VideoViewModel>()
    private val audioViewModel by viewModels<AudioViewModel>()

    var list by mutableStateOf(emptyList<Uri?>())
    val data = registerForActivityResult(GetMultipleContents()){ items ->
        list += items
    }


    private val STORAGE_PERMISSION_CODE = 1

    private fun requestStoragePermission() {
        val permissions = arrayOf(
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.MANAGE_EXTERNAL_STORAGE
        )

        for (permission in permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, permissions, STORAGE_PERMISSION_CODE)
                return
            }
        }

        // Permission already granted, call your function here
        Log.e("HELLOME", "Storage permission granted")
    }



    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Log.e("HELLOME", "Storage permission granted")
                // Call your function to save the image here
            } else {
                Log.e("HELLOME", "Storage permission denied")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        requestStoragePermission()

        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ),
            0
        )

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} DESC"


        contentResolver.query(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndex(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)

            val images = mutableListOf<Image>()
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                images.add(Image(id, name, uri))
            }
            imageViewModel.updateImages(images)
        }

        contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndex(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndex(MediaStore.Video.Media.DISPLAY_NAME)

            val videos = mutableListOf<Video>()
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                videos.add(Video(id, name, uri))
            }
            videoViewModel.updateVideos(videos)
        }

        contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
            val nameColumn = cursor.getColumnIndex(MediaStore.Audio.Media.DISPLAY_NAME)

            val audios = mutableListOf<Audio>()
            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val uri = ContentUris.withAppendedId(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    id
                )

                audios.add(Audio(id, name, uri))
            }
            audioViewModel.updateAudios(audios)
        }

        setContent {
            var bitmapState by remember { mutableStateOf<Bitmap?>(null) }
            JediShareTheme {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Top
                ) {
                    item {
                        Spacer(modifier = Modifier.size(12.dp))
                        Row (
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ){
                            CustomButton(buttonText = "All") {
                                data.launch("*/*")
                            }
                            CustomButton(buttonText = "Images") {
                                data.launch("image/*")
                            }
                            CustomButton(buttonText = "Videos") {
                                data.launch("video/*")
                            }
                            CustomButton(buttonText = "Audio") {
                                data.launch("audio/*")
                            }
                        }
                        Spacer(modifier = Modifier.size(12.dp))
                    }
                    items(list){item ->
//                        TextWithBorder(
//                            text = image.toString(),
//                            borderColor = Color.LightGray,
//                            borderWidth = 2,
//                            textColor = Color.Black,
//                        )
                        val context = LocalContext.current
                        Surface(
                            modifier = Modifier
                                .wrapContentSize()
                                .padding(4.dp) // Adjust padding as needed
                                .border(width = 2.dp, color = Color.LightGray)
                                .clip(MaterialTheme.shapes.medium)
                        ) {
                            Text(
                                text = "Click Me",
                                fontSize = 40.sp,
                                modifier = Modifier
                                    .padding(30.dp) // Adjust padding as needed
                                .clickable {
                                    val uri = Uri.parse(item.toString())
                                    val fileNameAndFormat =
                                        getFileDetailsFromUri(uri, contentResolver)


                                    Toast
                                        .makeText(
                                            this@SelectFile,
                                            fileNameAndFormat.fileName,
                                            Toast.LENGTH_SHORT
                                        )
                                        .show()

                                    Toast
                                        .makeText(
                                            this@SelectFile,
                                            fileNameAndFormat.size,
                                            Toast.LENGTH_SHORT
                                        )
                                        .show()

                                    Toast
                                        .makeText(
                                            this@SelectFile,
                                            fileNameAndFormat.mimeType,
                                            Toast.LENGTH_SHORT
                                        )
                                        .show()

//                                        var byArr = serializeUriData(image.uri, contentResolver)
                                    var byArr =
                                        item?.let { convertImageToByteWithInfo(it, contentResolver) }
                                    Toast
                                        .makeText(
                                            this@SelectFile,
                                            byArr.toString(),
                                            Toast.LENGTH_LONG
                                        )
                                        .show()
                                    Log.e("HELLOME","EnterOut")
                                    var out = byArr?.let { convertByteArrayToFileAndSaveMethod2(contentResolver, it) }
                                   // var out = byArr?.let { convertByteArrayToImageAndSave(it) }
                                    Toast
                                        .makeText(
                                            this@SelectFile,
                                            out.toString(),
                                            Toast.LENGTH_LONG
                                        )
                                        .show()
//                                        bitmapState = out

                                    //bitmapState = out
                                }
                            )
                        //bitmapState?.let { ImageFromBitmap(bitmap = it) }
                }}
//                    items(imageViewModel.images) { image ->
//                        Column(
//                            modifier = Modifier
//                                .fillMaxWidth()
//                                .clickable {
//                                    Toast
//                                        .makeText(
//                                            this@SelectFile,
//                                            image.toString(),
//                                            Toast.LENGTH_SHORT
//                                        )
//                                        .show()
//                                    data.launch("image/*")
//                                },
//                            horizontalAlignment = Alignment.CenterHorizontally
//                        ) {
////                            ImageWithUri(uri = image.uri.toString())
//                            Text(text = image.name)
////                            Text(text = image.uri.toString())
//                        }
////                            Spacer(modifier = Modifier.height(25.dp))
//                    }
                }
            }
        }
    }
}

@Composable
fun ImageFromBitmap(bitmap: Bitmap?) {
    val imageBitmap: ImageBitmap? = bitmap?.asImageBitmap()

    if (imageBitmap != null) {
        Image(
            bitmap = imageBitmap,
            contentDescription = null,
            modifier = Modifier.fillMaxSize()
        )
    }
}

//fun getFileDetailsFromUri(
//    uri: Uri,
//    contentResolver: ContentResolver
//): List<String?> {
//    var fileName: String? = null
//    var format: String? = null
//    var size: String? = null
//
//    // Use the ContentResolver to query the MediaStore and obtain the filename and format
//    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
//        val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
//        val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
//
//        if (cursor.moveToFirst()) {
//            fileName = cursor.getString(nameColumn)
//            // Extract the format (file extension) from the filename
//            format = fileName?.substringAfterLast('.', "")
//            size = cursor.getLong(sizeColumn).toString()
//        }
//    }
//
//    return listOf(fileName, format, size)
//}
//
//fun convertImageToByteWithInfo(uri: Uri, contentResolver: ContentResolver): ByteArray {
//    val stream: InputStream? = contentResolver.openInputStream(uri)
//    var byteArray: ByteArray
//    var fileDetails: List<String?> = emptyList()
//
//    stream.use { inputStream ->
//        val outputStream = ByteArrayOutputStream()
//        inputStream?.copyTo(outputStream)
//        byteArray = outputStream.toByteArray()
//
//        // Get file information
//        fileDetails = getFileDetailsFromUri(uri, contentResolver)
//    }
//
//    // Embed file information in the byte array
//    val fileName = fileDetails[0] ?: ""
//    Log.e("HELLOME",fileName)
//    val format = fileDetails[1] ?: ""
//    val size = fileDetails[2] ?: ""
//    val fileInfoBytes = "$fileName|$format|$size|".toByteArray()
//    val resultByteArray = ByteArray(fileInfoBytes.size + byteArray.size)
//    System.arraycopy(fileInfoBytes, 0, resultByteArray, 0, fileInfoBytes.size)
//    System.arraycopy(byteArray, 0, resultByteArray, fileInfoBytes.size, byteArray.size)
//
//    return resultByteArray
//}
//
//fun convertByteArrayToImageAndSave(byteArray: ByteArray): Bitmap? {
//    // Extract file information from the byte array
//    Log.e("HELLOME", "EnterFun")
//    val fileInfoString = byteArray.toString(Charsets.UTF_8)
//    val fileInfoList = fileInfoString.split("|")
//    val fileName = fileInfoList[0]
//    val format = fileInfoList[1]
//    val size = fileInfoList[2]
//    val info = fileInfoList[3]
//    Log.e("HELLOME", "EnterDir")
//
//    // Create a file in the app's local storage directory
//    val directory = File(Environment.getExternalStorageDirectory(), "com.invincible.bluetoothtransfer")
//    if (!directory.exists()) {
//        Log.e("HELLOME", "Dir doesn't exist")
//        if (!directory.mkdirs()) {
//            Log.e("HELLOME", "Failed to create directory")
//            return null
//        }
//    }
//    Log.e("HELLOME", "Dir = $directory")
//    val file = File(directory, "$fileName.$format")
//    Log.e("HELLOME", "File = $file")
//
//    try {
//        val fileOutputStream = FileOutputStream(file)
//        // Write the image data to the file
//        fileOutputStream.write(byteArray, fileInfoString.length, byteArray.size - fileInfoString.length)
//        fileOutputStream.close()
//
//        // Decode the byte array into a Bitmap
//        val bitmap = BitmapFactory.decodeByteArray(byteArray, fileInfoString.length, byteArray.size - fileInfoString.length)
//
//        Log.e("HELLOME", "ExitFun")
//        return bitmap
//    } catch (e: IOException) {
//        e.printStackTrace()
//        Log.e("HELLOME", "Failed to write to file")
//        return null
//    }
//}

//fun convertByteArrayToImageAndSave(contentResolver: ContentResolver, byteArray: ByteArray): String? {
//    Log.e("HELLOME", "EnterFun")
//
//    // Extract file information from the byte array
//    val fileInfoString = String(byteArray, Charsets.UTF_8)
//    val fileInfoList = fileInfoString.split("|")
//    val fileName = fileInfoList[0]
//    val format = fileInfoList[1]
//    val size = fileInfoList[2]
//    val info = fileInfoList[3]
//
//    Log.e("HELLOME", "EnterDir")
//
//    // Create a content values to store file information
//    val values = ContentValues().apply {
//        put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName.$format")
//        put(MediaStore.Images.Media.MIME_TYPE, "image/$format")
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
//        }
//    }
//
//    // Get the content URI for the new media entry
//    val contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
//    val imageUri = contentResolver.insert(contentUri, values)
//
//    try {
//        imageUri?.let {
//            // Open an output stream to write image data
//            contentResolver.openOutputStream(it)?.use { outputStream ->
//                outputStream.write(byteArray, 0, byteArray.size)
//            }
//
//            Log.e("HELLOME", "Image saved to MediaStore: $it")
//            return it.toString()
//        }
//    } catch (e: IOException) {
//        e.printStackTrace()
//        Log.e("HELLOME", "Failed to write to MediaStore")
//    }
//
//    return null
//}

//fun convertByteArrayToImageAndSave(byteArray: ByteArray): String? {
//    // Extract file information from the byte array
//    Log.e("HELLOME","EnterFun")
//    val fileInfoString = byteArray.toString(Charsets.UTF_8)
//    val fileInfoList = fileInfoString.split("|")
//    val fileName = fileInfoList[0]
//    Log.e("HELLOME",fileName)
//    val format = fileInfoList[1]
//    Log.e("HELLOME",format)
//    val size = fileInfoList[2]
//    Log.e("HELLOME",size)
//    val info = fileInfoList[3]
//    Log.e("HELLOME","EnterDir")
//    // Create a file in the app's local storage directory
//    val directory = File(Environment.getExternalStorageDirectory().toString() + "/JediShare")
//    if (!directory.exists()) {
//        directory.mkdirs()
//    }
//
//    val file = File(directory, "$fileName.$format")
//
//    try {
//        var fileOutputStream=FileOutputStream("")
//        try {
//            Log.e("HELLOME","oh no")
//            fileOutputStream = FileOutputStream(file)
//        } catch (e: IOException) {
//            Log.e("HELLOME","oh no")
//            Log.e("HELLOME", e.toString())
//        }
//        // Write the image data to the file
////            Log.e("HELLOME","TRYING")
//        fileOutputStream.write(byteArray, fileInfoString.length, byteArray.size - fileInfoString.length)
//        fileOutputStream.close()
//    } catch (e: IOException) {
//        e.printStackTrace()
//        Log.e("HELLOME","TRYING")
//        Log.e("HELLOME", e.toString())
//        return null
//    }
//    Log.e("HELLOME","ExitFun")
//    return info
//}

object ByteToBitmapConverter {

    fun byteArrayToBitmap(byteArray: ByteArray): Bitmap? {
        if (byteArray.isEmpty()) {
            return null
        }

        return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.size)
    }
}

@Composable
fun TextWithBorder(text: String, borderColor: Color, borderWidth: Int, textColor: Color) {
    Surface(
        modifier = Modifier
            .wrapContentSize()
            .padding(4.dp) // Adjust padding as needed
            .border(width = borderWidth.dp, color = borderColor)
            .clip(MaterialTheme.shapes.medium)
    ) {
        Text(
            text = text,
            color = textColor,
            modifier = Modifier
                .padding(8.dp) // Adjust padding as needed
//                .background(Color.LightGray)
        )
    }
}

@Composable
fun CustomButton(buttonText: String, onClickAction: () -> Unit) {
    Button(onClick = {
                     onClickAction.invoke()
    },
        //colors = ButtonDefaults.buttonColors(backgroundColor = Color.DarkGray),
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, Color.Gray),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Gray),
        elevation = ButtonDefaults.elevation(defaultElevation = 10.dp,
            pressedElevation = 15.dp,
            disabledElevation = 0.dp)
    )

    {
        Text(text = buttonText,color = Color.Gray, fontSize = 15.sp)
    }
}

//fun getFileDetailsFromUri(
//    uri: Uri,
//    contentResolver: ContentResolver
//): FileInfo {
//    var fileName: String? = null
//    var format: String? = null
//    var size: String? = null
//    var mimeType: String? = null
//
//    contentResolver.query(uri, null, null, null, null)?.use { cursor ->
//        //val nameColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
//        val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
//
//        val sizeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
//        val mimeTypeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
//
//        if (cursor.moveToFirst()) {
//            fileName = cursor.getString(nameColumn)
//            Log.e("HELLOMEE", fileName.toString())
//            format = fileName?.substringAfterLast('.', "")
//            size = cursor.getLong(sizeColumn).toString()
//            mimeType = cursor.getString(mimeTypeColumn)
//
//            if (mimeType.isNullOrBlank()) {
//                mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(format?.toLowerCase())
//            }
//        }
//    }
//
//    return FileInfo(fileName, format, size, mimeType)
//}

//fun convertImageToByteWithInfo(uri: Uri, contentResolver: ContentResolver): ByteArray {
//    val stream: InputStream? = contentResolver.openInputStream(uri)
//    var fileInfo: FileInfo? = null
//    var byteArray: ByteArray
//
//    stream.use { inputStream ->
//        val outputStream = ByteArrayOutputStream()
//        inputStream?.copyTo(outputStream)
//        byteArray = outputStream.toByteArray()
//        Log.e("HEELLOME", "IN ByteArray = " + byteArray.size.toString())
//
//        // Get file information
//        fileInfo = getFileDetailsFromUri(uri, contentResolver)
//    }
//
//    // Serialize FileInfo and image data to byte array
//    val fileDataBytes = serializeObject(FileData(fileInfo!!, byteArray))
//
//    return fileDataBytes
//}

//fun convertByteArrayToImageAndSave(contentResolver: ContentResolver, byteArray: ByteArray): String? {
//    val fileData = deserializeObject(byteArray)
//
//    val fileInfo = fileData?.fileInfo
//    val imageData = fileData?.imageData
//
//    val fileName = fileInfo?.fileName ?: ""
//    val format = fileInfo?.format ?: ""
//    val size = fileInfo?.size ?: ""
//    val mimeType = fileInfo?.mimeType ?: ""
//
//
//    // Create a content values to store file information
//    val values = ContentValues().apply {
//        put(MediaStore.Images.Media.DISPLAY_NAME, "$fileName")
//        put(MediaStore.Images.Media.MIME_TYPE, "image/$format")
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
//        }
//    }
//
//    // Get the content URI for the new media entry
//    val contentUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
//    val imageUri = contentResolver.insert(contentUri, values)
//
//    try {
//        imageUri?.let {
//            // Open an output stream to write image data
//            contentResolver.openOutputStream(it)?.use { outputStream ->
//                if (imageData != null) {
//                    outputStream.write(imageData, 0, imageData.size)
//                }
//            }
//
//            Log.e("HELLOME", "Image saved to MediaStore: $it")
//            return it.toString()
//        }
//    } catch (e: IOException) {
//        e.printStackTrace()
//        Log.e("HELLOME", "Failed to write to MediaStore")
//    }
//
//    return null
//}

//fun convertByteArrayToFileAndSave(contentResolver: ContentResolver, byteArray: ByteArray): String? {
//    Log.e("HELLOME", "EnterFun")
//
//    val fileData = deserializeObject(byteArray)
//
//    val fileInfo = fileData?.fileInfo
//    val fileName = fileInfo?.fileName ?: ""
//    val format = fileInfo?.format ?: ""
//    val mimeType = fileInfo?.mimeType ?: ""
//
//    Log.e("HELLOME", "EnterDir")
//
//    // Create a content values to store file information
//    val values = ContentValues().apply {
//        put(MediaStore.Files.FileColumns.DISPLAY_NAME, "$fileName")
//        put(MediaStore.Files.FileColumns.MIME_TYPE, mimeType)
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
//            when {
//                mimeType.startsWith("image/") -> put(
//                    MediaStore.Images.Media.RELATIVE_PATH,
//                    Environment.DIRECTORY_PICTURES
//                )
//                mimeType.startsWith("audio/") -> put(
//                    MediaStore.Audio.Media.RELATIVE_PATH,
//                    Environment.DIRECTORY_MUSIC
//                )
//                mimeType.startsWith("video/") -> put(
//                    MediaStore.Video.Media.RELATIVE_PATH,
//                    Environment.DIRECTORY_MOVIES
//                )
//            }
//        }
//    }
//
//    // Get the content URI for the new media entry
//    val contentUri = when {
//        mimeType.startsWith("image/") -> MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
//        mimeType.startsWith("audio/") -> MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
//        mimeType.startsWith("video/") -> MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
//        else -> MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
//    }
//
//    val fileUri = contentResolver.insert(contentUri, values)
//
//    try {
//        fileUri?.let {
//            // Open an output stream to write file data
//            contentResolver.openOutputStream(it)?.use { outputStream ->
//                outputStream.write(byteArray)
//            }
//
//            Log.e("HELLOME", "File saved to MediaStore: $it")
//            return it.toString()
//        }
//    } catch (e: IOException) {
//        e.printStackTrace()
//        Log.e("HELLOME", "Failed to write to MediaStore")
//    }
//
//    return null
//}

//private fun serializeObject(obj: Serializable?): ByteArray {
//    val byteArrayOutputStream = ByteArrayOutputStream()
//    val objectOutputStream = ObjectOutputStream(byteArrayOutputStream)
//    objectOutputStream.writeObject(obj)
//    objectOutputStream.close()
//    return byteArrayOutputStream.toByteArray()
//}

fun convertByteArrayToFileAndSaveMethod2(contentResolver: ContentResolver, byteArray: ByteArray): String? {
    Log.e("HELLOME", "EnterFun")

    val fileData = deserializeObject(byteArray)

    val fileInfo = fileData?.fileInfo
    val fileName = fileInfo?.fileName ?: ""
    val format = fileInfo?.format ?: ""
    val mimeType = fileInfo?.mimeType ?: ""

    Log.e("HELLOME", "EnterDir")

    // Create a content values to store file information
    val values = ContentValues().apply {
        put(MediaStore.Files.FileColumns.DISPLAY_NAME, "$fileName")
        put(MediaStore.Files.FileColumns.MIME_TYPE, mimeType)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_DOCUMENTS)
        }
    }

    // Get the content URI for the new media entry
    val contentUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
    val fileUri = contentResolver.insert(contentUri, values)

    try {
        fileUri?.let {
            // Open an output stream to write file data
            contentResolver.openOutputStream(it)?.use { outputStream ->
                outputStream.write(fileData?.imageData)
            }

            Log.e("HELLOME", "File saved to MediaStore: $it")
            return it.toString()
        }
    } catch (e: IOException) {
        e.printStackTrace()
        Log.e("HELLOME", "Failed to write to MediaStore")
    }

    return null
}

//private fun deserializeObject(byteArray: ByteArray): FileData? {
//    val byteArrayInputStream = ByteArrayInputStream(byteArray)
//    val objectInputStream = ObjectInputStream(byteArrayInputStream)
//    val obj = objectInputStream.readObject() as? FileData
//    objectInputStream.close()
//    return obj
//}

//data class FileInfo(
//    val fileName: String?,
//    val format: String?,
//    val size: String?,
//    val mimeType: String?
//) : java.io.Serializable
//
//data class FileData(
//    val fileInfo: FileInfo,
//    val imageData: ByteArray
//) : java.io.Serializable
