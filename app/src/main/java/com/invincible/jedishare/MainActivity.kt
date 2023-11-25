package com.invincible.jedishare

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import com.invincible.jedishare.ui.*
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.invincible.jedishare.ui.theme.JediShareTheme
import com.invincible.jedishare.ui.theme.MyRed
import com.invincible.jedishare.ui.theme.MyRedSecondary

class MainActivity : ComponentActivity() {
    private val bluetoothManager by lazy {
        applicationContext.getSystemService(BluetoothManager::class.java)
    }
    private val bluetoothAdapter by lazy {
        bluetoothManager?.adapter
    }

    private val requestMultiplePermissions =     registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        permissions.entries.forEach {
            Log.e("DEBUG", "${it.key} = ${it.value}")
        }
    }

    private val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    private val permissionsToRequest = if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CHANGE_NETWORK_STATE,
            Manifest.permission.INTERNET,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.NEARBY_WIFI_DEVICES,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.MANAGE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT

        )
    } else if(Build.VERSION.SDK_INT >= 29) {
        arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CHANGE_NETWORK_STATE,
            Manifest.permission.INTERNET,
            Manifest.permission.FOREGROUND_SERVICE,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.MANAGE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    }
    else{
        arrayOf(  Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CHANGE_NETWORK_STATE,
            Manifest.permission.INTERNET,
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_IMAGES,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.MANAGE_EXTERNAL_STORAGE,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    }



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestMultiplePermissions.launch(
            permissionsToRequest
        )
        val enableBluetoothLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { /* Not needed */ }


        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { perms ->
            val canEnableBluetooth = if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                perms[Manifest.permission.BLUETOOTH_CONNECT] == true
            } else true

            if(canEnableBluetooth && !isBluetoothEnabled) {
                enableBluetoothLauncher.launch(
                    Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                )
            }
        }

//        // Check if BLUETOOTH_CONNECT permission is already granted
//        val isBluetoothConnectPermissionGranted = ContextCompat.checkSelfPermission(
//            this,
//            Manifest.permission.BLUETOOTH_CONNECT
//        ) == PackageManager.PERMISSION_GRANTED
//        if(isBluetoothConnectPermissionGranted){
//            Log.e("MYTAG", "permission hai bro")
//        }else{
//            Log.e("MYTAG", "no permission hai bro")
//        }
//
//        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
//            permissionLauncher.launch(
//                arrayOf(
//                    Manifest.permission.BLUETOOTH_SCAN,
//                    Manifest.permission.BLUETOOTH_CONNECT,
//                )
//            )
//        }

        requestMultiplePermissions.launch(
            permissionsToRequest
        )


        setContent {
            JediShareTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colors.background),
                    contentAlignment = androidx.compose.ui.Alignment.BottomCenter,
                ) {
                    Screen1()
                    NavBar("MainActivity")
                }
            }
        }
    }
}

@Composable
fun CustomSwitch(
    width: Dp = 120.dp,
    height: Dp = 40.dp,
    // mutiplied width and height by 2
//    width: Dp = 72.dp,
//    height: Dp = 40.dp,
//    checkedTrackColor: Color = Color(0xFF35898F),
//    uncheckedTrackColor: Color = Color(0xFFe0e0e0),
    checkedTrackColor: Color = MyRed,
    uncheckedTrackColor: Color = MyRed,
    gapBetweenThumbAndTrackEdge: Dp = 8.dp,
    borderWidth: Dp = 2.dp,
    cornerSize: Int = 50,
    iconInnerPadding: Dp = 4.dp,
    thumbSize: Dp = 30.dp,
    context: Context
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
            .verticalScroll(scrollState)
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
            contentAlignment = alignment
        ) {

            // thumb with icon
            val icon: Painter = painterResource(id = R.drawable.wifi_icon)
            val icon2: Painter = painterResource(id = R.drawable.bluetooth_24)
            Icon(
                painter = if (switchOn) icon2 else icon,
                contentDescription = if (switchOn) "Enabled" else "Disabled",
                modifier = Modifier
                    .size(size = thumbSize)
                    .background(
                        color = if (switchOn) checkedTrackColor else uncheckedTrackColor,
                        shape = CircleShape
                    )
                    .padding(all = iconInnerPadding),
                tint = Color.White
            )
        }
    }

    if(scrollState.isScrollInProgress){
        DisposableEffect(Unit) {
            onDispose {
                switchOn = !switchOn
            }
        }
    }


    // gap between switch and the text
    Spacer(modifier = Modifier.height(height = 5.dp))

    Text(text = if (switchOn) "Bluetooth" else "Wifi-Direct")

    Spacer(modifier = Modifier.height(32.dp))

    Row (
        modifier = Modifier
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ){
        val sendIcon: Painter = painterResource(id = R.drawable.send)
        CircularButton(onClick = {
            val intent = Intent(context, SelectFile::class.java)
            if(switchOn){
                intent.putExtra("transferMethod", "Bluetooth")
            }else{
                intent.putExtra("transferMethod", "Wifi-Direct")
            }
            context.startActivity(intent)
        },
            buttonName = "Send",
            icon = sendIcon)
        Spacer(modifier = Modifier.size(64.dp))
        val receiveIcon: Painter = painterResource(id = R.drawable.receive)
        CircularButton(onClick = {
            val intent: Intent
            if(!switchOn){
                intent = Intent(context, WifiDirectDeviceSelectActivity::class.java)
                intent.putExtra("transferMethod", "Wifi-Direct")
            }else{
                intent = Intent(context, DeviceList::class.java)
                intent.putExtra("transferMethod", "Bluetooth")
            }
            intent.putExtra("source", true)
            intent.putExtra("source2", "1")
//            context?.finish()
            context.startActivity(intent)
        },
            buttonName = "Recieve",
            icon = receiveIcon
        )
    }
}

fun isBluetoothOkay(){

}

@Composable
private fun animateAlignmentAsState(
    targetBiasValue: Float
): State<BiasAlignment> {
    val bias by animateFloatAsState(targetBiasValue)
    return derivedStateOf { BiasAlignment(horizontalBias = bias, verticalBias = 0f) }
}

@Composable
fun CircularButton(onClick: () -> Unit, buttonName: String, icon: Painter, color: Color = MaterialTheme.colors.primary) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .padding(start = 20.dp, end = 20.dp, top = 10.dp, bottom = 2.dp)
                .size(55.dp)
                .clip(CircleShape)
                .background(color)
        ) {
            androidx.compose.material.Icon(
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colors.background,
                modifier = Modifier.size(30.dp)
            )
        }


        Text(
            text = buttonName,
            fontSize = 16.sp,
            style = MaterialTheme.typography.h6,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun Screen1() {
    val context = LocalContext.current
    Column (
        modifier = Modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally
    ){

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(0.dp, 0.dp, 50.dp, 50.dp))
                .background( MyRedSecondary)
                .fillMaxWidth()
                .height(400.dp)
            ,
            contentAlignment = androidx.compose.ui.Alignment.BottomCenter,
        ) {
            Column (
                modifier = Modifier
                    .fillMaxSize(),
                verticalArrangement = Arrangement.SpaceEvenly,
                horizontalAlignment = Alignment.CenterHorizontally
            ){
                val context = LocalContext.current as? ComponentActivity
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                )
                {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.CenterStart)
                            .padding(start = 16.dp, top = 12.dp), horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.ArrowBack, contentDescription = null,
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null, // Disable the click animation
                                ) {
                                    context?.finish()
                                }
                                .size(35.dp)
                                .fillMaxSize(),
                            tint = MyRed
                        )
                    }
                    Text(
                        text = "Home",
                        fontWeight = FontWeight.Bold,
                        color = MyRed,
                        style = MaterialTheme.typography.h3,
                        modifier = Modifier.align(Alignment.Center).padding(top = 8.dp)
                    )
                }
                Image(
                    painter = painterResource(id = R.drawable.main_activity_illustration),
                    contentDescription = "Image with Shadow",
                    modifier = Modifier
                        .size(400.dp)
//                        .shadow(elevation = 600.dp, CircleShape),
                )
                Text(
                    text = "Jedi Share",
                    fontSize = 40.sp,
                    style = MaterialTheme.typography.h1,
                    color = MyRed,
                    textAlign = TextAlign.Center,
                    textDecoration = TextDecoration.Underline
                )
            }
        }

        Spacer(modifier = Modifier.size(30.dp))

//        Row(
//            modifier = Modifier
//                .fillMaxWidth(),
//            horizontalArrangement = Arrangement.Center,
//            verticalAlignment = Alignment.CenterVertically
//        ) {
//
//            val customDrawable: Painter = painterResource(id = R.drawable.wifi_icon)
//            CircularButton(
//                onClick = {
//                    val intent = Intent(context, SendOrReceive::class.java)
//                    val one = "Wifi Direct"
//                    intent.putExtra("Data", one)
//                    context.startActivity(intent)
//                },
//                "Wifi Direct",
//                customDrawable
//            )
//
//            Spacer(modifier = Modifier.size(64.dp))
//
//            val customDrawable2: Painter = painterResource(id = R.drawable.bluetooth_24)
//            CircularButton(
//                onClick = {
//                    val intent = Intent(context, SendOrReceive::class.java)
//                    val one = "Bluetooth"
//                    intent.putExtra("Data", one)
//                    context.startActivity(intent)
//                },
//                "Bluetooth",
//                customDrawable2
//            )
//        }
        CustomSwitch(context = context)




    }
}

@Composable
fun NavBar(isMainActivity: String? = null) {

    val iconColor = Color(0xFF555555)

    BottomNavigation(
        backgroundColor = Color.White,
        elevation = 10.dp,
        modifier = Modifier
            .shadow(elevation = 80.dp, CircleShape)
//            .clip(RoundedCornerShape(15.dp, 15.dp, 15.dp, 15.dp))
            .padding(16.dp)
            .clip(RoundedCornerShape(percent = 50))
            .border(width = 2.dp, color = Color.Black, shape = CircleShape)
            .fillMaxWidth()
//            .height(56.dp)
    ) {
        val context = LocalContext.current as? ComponentActivity

//        BottomNavigationItem(
//            icon = {
//                Icon(
//                    imageVector = Icons.Default.Person,
//                    contentDescription = "Profile",
//                    tint = iconColor,
//                    modifier = Modifier.size(24.dp)
//                )
//            },
//            label = { Text(text = "Profile") },
//            selected = false,
//            onClick = { /*TODO*/ },
//            alwaysShowLabel = true,
//            selectedContentColor = Color.Black,
//            unselectedContentColor = Color.Gray,
//        )
        BottomNavigationItem(
            icon = {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Settings",
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            },
            label = { Text(text = "Settings") },
            selected = false,
            onClick = {
                context?.finish()
                val intent = Intent(context, SettingsActivity ::class.java)
                context?.startActivity(intent)
            },
            alwaysShowLabel = true,
            selectedContentColor = Color.Black,
            unselectedContentColor = Color.Gray,
        )
        BottomNavigationItem(
            icon = {
                Icon(
                    imageVector = Icons.Default.Home,
                    contentDescription = "Home",
                    tint = iconColor,
                    modifier = Modifier.size(24.dp),
                )
            },
            label = { Text(text = "Home") },
            selected = false,
            onClick = {
                if(isMainActivity != "MainActivity"){
                    context?.finish()
                    val intent = Intent(context, MainActivity::class.java)
                    context?.startActivity(intent)
                }
            },
            alwaysShowLabel = true,
            selectedContentColor = Color.Black,
            unselectedContentColor = Color.Gray,
        )
        BottomNavigationItem(
            icon = {
                val icon: Painter = painterResource(id = R.drawable.history_icon)
                Icon(
                    painter = icon ,
                    contentDescription = "History",
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )


            },
            label = { Text(text = "History") },
            selected = false,
            onClick = {
                context?.finish()
                val intent = Intent(context, HistoryActivity ::class.java)
                context?.startActivity(intent)
            },
            alwaysShowLabel = true,
            selectedContentColor = Color.Black,
            unselectedContentColor = Color.Gray,
        )
    }
}