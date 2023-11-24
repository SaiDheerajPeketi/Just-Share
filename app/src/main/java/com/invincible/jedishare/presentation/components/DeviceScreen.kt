package com.invincible.jedishare.presentation.components

import android.os.SystemClock.sleep
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.Divider
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invincible.jedishare.domain.chat.BluetoothDevice
import com.invincible.jedishare.presentation.BluetoothUiState
import com.invincible.jedishare.ui.theme.MyRedSecondaryLight
import com.invincible.jedishare.ui.theme.Roboto
import kotlinx.coroutines.delay

@Composable
fun DeviceScreen(
    state: BluetoothUiState,
    onStartScan: () -> Unit,
    onStopScan: () -> Unit,
    onStartServer: () -> Unit,
    onDeviceClick: (BluetoothDevice) -> Unit
) {

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {

            Spacer(modifier = Modifier.size(16.dp))

            Text(
                text = "Device Select",
                fontWeight = FontWeight.ExtraBold,
                fontSize = 24.sp,
                fontFamily = Roboto
            )

            Divider(
                color = Color.LightGray,
                thickness = 2.dp,
                modifier = Modifier.padding(top = 16.dp, start = 16.dp, end = 16.dp)
            )

            BluetoothDeviceList(
                pairedDevices = state.pairedDevices,
                scannedDevices = state.scannedDevices,
                onClick = onDeviceClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
//            Row(
//                modifier = Modifier.fillMaxWidth(),
//                horizontalArrangement = Arrangement.SpaceAround
//            ) {
//                Button(onClick = onStartScan) {
//                    Text(text = "Start scan")
//                }
//                Button(onClick = onStopScan) {
//                    Text(text = "Stop scan")
//                }
//                Button(onClick = onStartServer) {
//                    Text(text = "Start server")
//                }
//            }
        }
    }
}

@Composable
fun BluetoothDeviceList(
    pairedDevices: List<BluetoothDevice>,
    scannedDevices: List<BluetoothDevice>,
    onClick: (BluetoothDevice) -> Unit,
    modifier: Modifier = Modifier
) {


    val density = LocalDensity.current.density
    val screenHeight = (LocalContext.current.resources.displayMetrics.heightPixels / density).dp / 2

    Box(
        modifier = Modifier
//                                    .height(300.dp)
            .padding(20.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MyRedSecondaryLight)
            .fillMaxWidth()
            .heightIn(max = 700.dp)
            .animateContentSize(
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioLowBouncy,
                    stiffness = Spring.StiffnessLow
                )
            ),
    contentAlignment = Alignment.Center,
    ) {
        Text(
                text = "Paired Devices",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                fontFamily = Roboto,
                modifier = Modifier.padding(16.dp).align(Alignment.TopStart),
                textDecoration = TextDecoration.Underline
            )
            LazyColumn(
                modifier = Modifier
                    .heightIn(max = 300.dp)
                    .padding(top = 55.dp)
            ) {
                items(pairedDevices) { device ->
                    Text(
                        text = device.name ?: "(No name)",
                        fontSize = 15.sp,
                        fontFamily = Roboto,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onClick(device) }
                            .padding(16.dp)
                    )
                }
            }
    }

    val scannedDevicesWithoutNoName: MutableList<Any> = emptyList<BluetoothDevice>().toMutableList()

    scannedDevices.forEach {
        if(it.name != null){
            scannedDevicesWithoutNoName += it.name
        }
    }


    if (scannedDevicesWithoutNoName.isNotEmpty()) {
        Box(
            modifier = Modifier
//                                    .height(300.dp)
                .padding(bottom = 20.dp, start = 20.dp, end = 20.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MyRedSecondaryLight)
                .fillMaxWidth()
                .heightIn(max = 660.dp)
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessLow
                    )
                ),
//        contentAlignment = Alignment.Center,
        ) {
//        Column (
//            modifier = Modifier
//                .heightIn(max = screenHeight)
//                .background(Color.Gray)
//                .wrapContentHeight()
//                .fillMaxWidth(),
//            verticalArrangement = Arrangement.Top,
//            horizontalAlignment = Alignment.Start
//        ){
            Text(
                text = "Scanned Devices",
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                fontFamily = Roboto,
                modifier = Modifier.padding(16.dp).align(Alignment.TopStart),
                textDecoration = TextDecoration.Underline
            )

            LazyColumn(
                modifier = Modifier
                    .padding(top = 55.dp)
            ) {
                items(scannedDevices) { device ->
                    if (device.name != null) {
                        Text(
                            text = device.name ?: "(No name)",
                            fontSize = 15.sp,
                            fontFamily = Roboto,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onClick(device) }
                                .padding(16.dp)
                        )
                    }
                }
            }
//        }
        }
    }
}