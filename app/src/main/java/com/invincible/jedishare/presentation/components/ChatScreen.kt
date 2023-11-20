package com.invincible.jedishare.presentation.components

import android.net.Uri
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invincible.jedishare.R
import com.invincible.jedishare.presentation.BluetoothUiState
import com.invincible.jedishare.presentation.BluetoothViewModel
import com.invincible.jedishare.ui.theme.MyRed
import com.invincible.jedishare.ui.theme.MyRedSecondary

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ChatScreen(
    state: BluetoothUiState,
    onDisconnect: () -> Unit,
    onSendMessage: (String) -> Unit,
    uriList: List<Uri>,
    viewModel: BluetoothViewModel
){
    val message = rememberSaveable {
        mutableStateOf("")
    }
    val keyboardController = LocalSoftwareKeyboardController.current

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
            Text(
                text = "Messages",
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDisconnect) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Disconnect"
                )
            }
//            LazyColumn(
//                modifier = Modifier
//                    .fillMaxWidth()
//                    .weight(1f),
//                contentPadding = PaddingValues(16.dp),
//                verticalArrangement = Arrangement.spacedBy(16.dp)
//            ){
//                items(state.messages) { message ->
//                    Column(
//                        modifier = Modifier.fillMaxWidth()
//                    ){
//                        ChatMessage(
//                            message = message,
//                            modifier = Modifier
//                                .align(
//                                    if(message.isFromLocalUser) Alignment.End else Alignment.Start
//                                )
//                        )
//                    }
//                }
//            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = message.value,
                onValueChange = { message.value = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(text = "Message")
                }
            )
            IconButton(onClick = {
//                onSendMessage(message.value)
                uriList.forEach { it ->
                    onSendMessage(it.toString())
                }
                message.value = ""
                keyboardController?.hide()
            }) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = "Send message"
                )
            }
        }
        CustomProgressIndicator(viewModel = viewModel)
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
    uncheckedTrackColor: Color = Color.Black,
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

    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .border(
                width = borderWidth,
                color = if (switchOn) checkedTrackColor else uncheckedTrackColor,
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
        Box(
            modifier = Modifier
                .padding(
                    start = gapBetweenThumbAndTrackEdge,
                    end = gapBetweenThumbAndTrackEdge
                )
                .fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {

//            Box(
//                modifier = Modifier.fillMaxSize().background(Color(0x22606060))
//            ){
                LinearProgressIndicator(
                    progress = progressFromViewModel.value.currSize.toFloat() / progressFromViewModel.value.globalSize.toFloat(),
                modifier = Modifier
                    .height(15.dp)
                    .fillMaxWidth()
                    .padding(top = 2.dp, bottom = 2.dp)
                    .clip(RoundedCornerShape(percent = 50)),
                color = Color.Black,
                backgroundColor = Color.White,
                )
//            }
        }
    }

    // gap between switch and the text
    Spacer(modifier = Modifier.height(height = 5.dp))

    Text(text = if (switchOn) "Bluetooth" else "Wifi-Direct")
    Text(text = ((progressFromViewModel.value.currSize*100 / progressFromViewModel.value.globalSize)).toString()+"%" )
    Text(text = progressFromViewModel.value.globalSize.toString())

    Spacer(modifier = Modifier.height(32.dp))
}

@Composable
private fun animateAlignmentAsState(
    targetBiasValue: Float
): State<BiasAlignment> {
    val bias by animateFloatAsState(targetBiasValue)
    return derivedStateOf { BiasAlignment(horizontalBias = bias, verticalBias = 0f) }
}
