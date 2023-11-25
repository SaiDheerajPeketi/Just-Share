package com.invincible.jedishare.presentation.components

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Send
import androidx.compose.runtime.Composable
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invincible.jedishare.AnimatedPreloader
import com.invincible.jedishare.NavBar
import com.invincible.jedishare.R
import com.invincible.jedishare.Screen1
import com.invincible.jedishare.bytesToHumanReadableSize
import com.invincible.jedishare.classifyFileType
import com.invincible.jedishare.getFileDetailsFromUri
import com.invincible.jedishare.presentation.BluetoothUiState
import com.invincible.jedishare.presentation.BluetoothViewModel
import com.invincible.jedishare.ui.theme.MyRed
import com.invincible.jedishare.ui.theme.MyRedSecondary
import com.invincible.jedishare.ui.theme.MyRedSecondaryLight
import com.invincible.jedishare.ui.theme.MyRedSecondaryLight2
import com.invincible.jedishare.ui.theme.Roboto
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import javax.security.auth.login.LoginException

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ChatScreen(
    state: BluetoothUiState? = null,
    onDisconnect: () -> Unit,
    onSendMessage: (String) -> Unit,
    uriList: List<Uri>? = null,
    viewModel: BluetoothViewModel? = null,
    intent: Intent? = null,
    contentResolver: ContentResolver? = null,
    isFromWifi: Boolean = false
){

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
    ) {
        val list: ArrayList<Uri>? = intent?.getParcelableArrayListExtra("urilist")
        Column (
            verticalArrangement = Arrangement.Top
        ){

            Spacer(modifier = Modifier.size(16.dp))

            Row (
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Top
            ){

                Text( text = "Transfer Process",
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = Roboto,
                    fontSize = 24.sp
                )

            }

            Divider(
                color = Color.LightGray,
                thickness = 2.dp,
                modifier = Modifier.padding(top = 16.dp, start = 16.dp)
            )

            AnimatedPreloader(modifier = Modifier.size(400.dp), R.raw.file_transfer_animation)

            if (list != null) {
                if (contentResolver != null) {
                    if (viewModel != null) {
                        (if(!isFromWifi) list else uriList)?.let { DisplayFileswithProgressBar(it, contentResolver, viewModel) }
                    }
                }
            }

//            while(true){
//                try{
//                    if (uriList != null) {
//                        if (viewModel != null) {
//                            viewModel.setUriList(uriList)
//                        }
//                    }
//                    uriList?.get(0)?.let { onSendMessage(it.toString()) }
//                    break
//                }catch (e: Exception){
//
//                }
//            }
        }
    }

    val message = rememberSaveable {
        mutableStateOf("")
    }
    val keyboardController = LocalSoftwareKeyboardController.current
    Log.e("fjk","reached here")
//    onSendMessage(uriList[0].toString())
//
//    message.value = ""

    Column(
        modifier = Modifier
            .fillMaxSize()
    ) {
        Row (
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ){
            IconButton(onClick = onDisconnect) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Disconnect",
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {

                if (uriList != null) {
                    if (viewModel != null) {
                        viewModel.setUriList(uriList)
                    }
                }
                uriList?.get(0)?.let { onSendMessage(it.toString()) }

                message.value = ""
                keyboardController?.hide()
            }) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send message"
                )
            }
        }
    }


}

@Composable
fun DisplayFileswithProgressBar(list: List<Uri>, contentResolver: ContentResolver, viewModel: BluetoothViewModel) {

    Log.e("hhh",list.size.toString())
    Box(
        modifier = Modifier
            .padding(bottom = 20.dp, start = 20.dp, end = 20.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MyRedSecondaryLight)
            .fillMaxWidth()
            .heightIn(max = 660.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.size(12.dp))

            LazyColumn(
                modifier = Modifier
                    .heightIn(max = 570.dp)
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                items(list) { item ->

                    Log.e("MYTAG3", "items list")
                    Column(
                        modifier = Modifier
                            .padding(
                                top = 8.dp,
                                bottom = 8.dp,
                                end = 8.dp
                            ) // Adjust padding as needed
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        val currFileCount = viewModel.currFileCount.collectAsState()
                        Row(
                            modifier = Modifier
                                .wrapContentSize()
                                .fillMaxWidth()
                                .padding(
                                    top = 4.dp,
                                    bottom = 4.dp,
                                    end = 4.dp
                                ), // Adjust padding as needed,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {


                            Spacer(modifier = Modifier.size(10.dp))

                            val uri = Uri.parse(item.toString())
                            val fileNameAndFormat =
                                getFileDetailsFromUri(uri, contentResolver)
                            val fileType = fileNameAndFormat.format?.let {
                                classifyFileType(it)
                            }

                            val icon: Painter
                            val iconTint: Color
                            val iconBackground: Color

                            if (fileType == "Photo") {
                                icon =
                                    painterResource(id = R.drawable.photo_icon)
                                iconTint = Color(0xFF33A850)
                                iconBackground = Color(0x2233A850)
                            } else if (fileType == "Video") {
                                icon =
                                    painterResource(id = R.drawable.video_icon)
                                iconTint = Color(0xFFC54EE6)
                                iconBackground = Color(0x22C54EE6)
                            } else if(fileType == "Music"){
                                icon =
                                    painterResource(id = R.drawable.music_icon)
                                iconTint = Color(0xFFFF0000)
                                iconBackground = Color(0x22FF0000)
                            }else {
                                icon =
                                    painterResource(id = R.drawable.document_icon)
                                iconTint = Color(0xFF4187E6)
                                iconBackground = Color(0x224187E6)
                            }

                            Row {
                                Box(
                                    modifier = Modifier
                                        .background(
                                            iconBackground,
                                            shape = CircleShape
                                        )
                                        .size(50.dp),
                                    contentAlignment = androidx.compose.ui.Alignment.Center,
                                ) {
                                    Icon(
                                        painter = icon,
                                        contentDescription = null,
                                        tint = iconTint,
                                        modifier = Modifier.size(30.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.size(10.dp))

                                Column(
                                    modifier = Modifier
                                        .width(180.dp),
                                    horizontalAlignment = Alignment.Start,
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {

                                    var expanded by remember{
                                        mutableStateOf(false)
                                    }
                                    // this is to disable the ripple effect
                                    val interactionSource = remember {
                                        MutableInteractionSource()
                                    }

                                    fileNameAndFormat.fileName?.let {
                                        Text(
                                            text = if(fileNameAndFormat.format != "Document") it else it.substring(0, it.length - 4),
                                            fontSize = 15.sp,
//                                                        style = MaterialTheme.typography.h4,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .animateContentSize()
                                                .clickable(
                                                    indication = null,
                                                    interactionSource = interactionSource
                                                ) {
                                                    expanded = !expanded
                                                },
                                            maxLines = if(expanded) 10 else 2
//                                                            .padding(16.dp) // Adjust padding as needed
                                        )

                                        val sizeInBytes = fileNameAndFormat.size

                                        val units = arrayOf(
                                            "B",
                                            "KB",
                                            "MB",
                                            "GB",
                                            "TB",
                                            "PB",
                                            "EB",
                                            "ZB",
                                            "YB"
                                        )
                                        val digitGroups = (sizeInBytes?.let { it1 ->
                                            Math.log10(
                                                it1.toDouble()
                                            )
                                        }?.div(Math.log10(1024.0)))?.toInt()

//                                                    val humanReadableSize = String.format("%.1f %s", sizeInBytes / digitGroups?.let { it1 -> Math.pow(1024.0, it1.toDouble()) }, units[digitGroups!!])

                                        Text(
                                            text = fileNameAndFormat.size?.let { it1 ->
                                                bytesToHumanReadableSize(
                                                    it1.toDouble()
                                                )
                                            }.toString(),
                                            fontSize = 15.sp,
//                                                        style = MaterialTheme.typography.h6,
                                        )

                                    }
                                }
                                if(currFileCount.value > list.indexOf(item)){
                                    Spacer(modifier = Modifier.width(50.dp))
                                    AnimatedPreloader(modifier = Modifier.size(50.dp), R.raw.done_tick_animation, 1)
                                    Log.e("MYTAG3", "image")
                                }else if(currFileCount.value == list.indexOf(item)){
                                    val progressFromViewModel = viewModel.statee.collectAsState()
                                    val currSizeReceived by viewModel.getIterationCountFlow().collectAsState(initial = 0)
                                    val fileSize by viewModel.fileInfoState.collectAsState()
                                    val receiverPercent = currSizeReceived*990*100 / fileSize
                                    val sentPercent = progressFromViewModel.value.currSize*100 / progressFromViewModel.value.globalSize
                                    
                                    Spacer(modifier = Modifier.width(40.dp))

                                    Text(
                                        text = if(receiverPercent > sentPercent) receiverPercent.toString()+ "%" else sentPercent.toString() + "%",
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.Black,
                                        fontFamily = Roboto,
                                        fontSize = 15.sp,
//                    modifier = Modifier.align(Alignment.CenterEnd)
                                    )
                                }
                            }


                        }
                        if( currFileCount.value == list.indexOf(item)){
                            CustomProgressIndicator(viewModel = viewModel)
                        }
                        Log.e("MYTAG3", currFileCount.toString())


//                                                Divider(
////                                                    color = Color(0xFFEEEEEE),
//                                                    color = Color.LightGray,
//                                                    thickness = 1.dp,
//                                                    modifier = Modifier.padding(
//                                                        vertical = 16.dp,
//                                                        horizontal = 16.dp
//                                                    )
//                                                )
                    }
                }


            }

//                                    Spacer(modifier = Modifier.weight(1f))
        }

    }
}

@Composable
fun CustomProgressIndicator(
    width: Dp = 270.dp,
    height: Dp = 20.dp,
    // mutiplied width and height by 2
//    width: Dp = 72.dp,
//    height: Dp = 40.dp,
//    checkedTrackColor: Color = Color(0xFF35898F),
//    uncheckedTrackColor: Color = Color(0xFFe0e0e0),
    checkedTrackColor: Color = Color.Black,
    uncheckedTrackColor: Color = MyRedSecondary,
    gapBetweenThumbAndTrackEdge: Dp = 5.dp,
    borderWidth: Dp = 3.dp,
    cornerSize: Int = 50,
    iconInnerPadding: Dp = 4.dp,
    thumbSize: Dp = 30.dp,
    viewModel: BluetoothViewModel
) {

    // this is to disable the ripple effect
    val interactionSource = remember {
        MutableInteractionSource()
    }

    // state of the switch
    var switchOn by remember {
        mutableStateOf(true)
    }

    // for moving the thumb
    val alignment by animateAlignmentAsState(if (switchOn) 1f else -1f)

    // outer rectangle with border
    val scrollState = rememberScrollState()


    val progressFromViewModel = viewModel.statee.collectAsState()
    val currSizeReceived by viewModel.getIterationCountFlow().collectAsState(initial = 0)
    val fileSize by viewModel.fileInfoState.collectAsState()
    val receiverPercent = currSizeReceived*990*100 / fileSize
    val sentPercent = progressFromViewModel.value.currSize*100 / progressFromViewModel.value.globalSize

    var progress: Long
    if(sentPercent.toInt() == -100){
        progress = receiverPercent
    }else{
        progress = sentPercent
    }
    var indColor by remember{
        mutableStateOf(Color.Black)
    }
    if(progress.toInt() == 100){
        indColor = MyRed
    }else{
        indColor = Color.Black
    }

    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .border(
                width = borderWidth,
                color = indColor,
//                color = MyRedSecondaryLight2,
//                color = if (switchOn) checkedTrackColor else uncheckedTrackColor,
                shape = RoundedCornerShape(percent = cornerSize)
            )
            .clickable(
                indication = null,
                interactionSource = interactionSource
            ) {
                switchOn = !switchOn
            }
        ,
        contentAlignment = Alignment.Center
    ) {

        // this is to add padding at the each horizontal side
        Row (
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {

            Box(
                modifier = Modifier
                    .padding(
                        start = gapBetweenThumbAndTrackEdge,
                        end = gapBetweenThumbAndTrackEdge
                    )
                    .background(MyRedSecondaryLight2)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LinearProgressIndicator(
                    progress = (progress.toFloat() / 100),
                    modifier = Modifier
                        .height(15.dp)
//                        .width(50.dp)
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 2.dp)
                        .clip(RoundedCornerShape(percent = 50)),
                    color = indColor,
                    backgroundColor = MyRedSecondaryLight2,
                )
            }

        }
    }

    // gap between switch and the text
    Spacer(modifier = Modifier.height(height = 5.dp))
//    viewModel.statee.collectAsState().value.copy(currSize = 10)

//
//    Text(text = "receivedPercent = " + receiverPercent.toString())
//    Text(text = "sentPercent = " + sentPercent.toString())
//
//    Text(text = "currSizeReceived = " + currSizeReceived.toString())
//    Text(text = "fileSize = " + fileSize.toString())
//    Text(text = ((progressFromViewModel.value.currSize*100 / progressFromViewModel.value.globalSize)).toString()+"%" )
//    Text(text = progressFromViewModel.value.globalSize.toString())
}

@Composable
private fun animateAlignmentAsState(
    targetBiasValue: Float
): State<BiasAlignment> {
    val bias by animateFloatAsState(targetBiasValue)
    return derivedStateOf { BiasAlignment(horizontalBias = bias, verticalBias = 0f) }
}
