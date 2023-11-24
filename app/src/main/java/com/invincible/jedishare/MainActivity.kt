package com.invincible.jedishare

import android.content.Context
import android.content.Intent
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.IntentFilter
import android.location.LocationManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import com.invincible.jedishare.ui.*
import androidx.activity.compose.setContent
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.getSystemService
import androidx.lifecycle.viewmodel.compose.viewModel
import com.invincible.jedishare.ui.theme.JediShareTheme
import kotlinx.coroutines.delay
import androidx.core.content.ContextCompat.startActivity
import com.invincible.jedishare.ui.theme.JediShareTheme
import com.invincible.jedishare.ui.theme.MyRed
import com.invincible.jedishare.ui.theme.MyRedSecondary

class MainActivity : ComponentActivity() {

    private var communicationService = CommunicationService()

    val TAG = "myDebugTag"

    var peers: List<WifiP2pDevice> = emptyList()
    var connectionText = ""

    private var isWiFiDirectActive = false
    private var isDiscovering = false
    private var wifiP2PdeviceName = ""
    private var connectionInfoAvailable = false

    private var receiver: WiFiDirectBroadcastReceiver? = null
    private val intentFilter: IntentFilter = IntentFilter()
    private var wifiP2pManager: WifiP2pManager? = null
    private var wifiP2pChannel: WifiP2pManager.Channel? = null
    private var actionListener: WifiP2pManager.ActionListener? = null
    var peerListListener: WifiP2pManager.PeerListListener? = null
    var connectionInfoListener: WifiP2pManager.ConnectionInfoListener? = null

    private val permissionsToRequest = if (Build.VERSION.SDK_INT >= 33) {
        arrayOf(
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CHANGE_NETWORK_STATE,
            Manifest.permission.INTERNET,
            Manifest.permission.FOREGROUND_SERVICE
        )
    } else {
        arrayOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_WIFI_STATE,
            Manifest.permission.CHANGE_WIFI_STATE,
            Manifest.permission.ACCESS_NETWORK_STATE,
            Manifest.permission.CHANGE_NETWORK_STATE,
            Manifest.permission.INTERNET,
            Manifest.permission.FOREGROUND_SERVICE
        )
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        initializeWiFiDirect()

        // Indicates a change in the Wi-Fi Direct status.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)

        // Indicates a change in the list of available peers.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)

        // Indicates the state of Wi-Fi Direct connectivity has changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)

        // Indicates this device's details have changed.
        intentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)

        intentFilter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION)
        intentFilter.addAction(LocationManager.PROVIDERS_CHANGED_ACTION)

        receiver = WiFiDirectBroadcastReceiver(this)

        actionListener = object : WifiP2pManager.ActionListener {
            override fun onSuccess() {}
            override fun onFailure(reason: Int) {
                var errorMsg = "Wi-fi direct Failed: "
                errorMsg += when (reason) {
                    WifiP2pManager.BUSY -> "Framework busy"
                    WifiP2pManager.ERROR -> "Internal error"
                    WifiP2pManager.P2P_UNSUPPORTED -> "Unsupported"
                    else -> "Unknown message"
                }
                disconnectP2P()
                Log.d(TAG, errorMsg)
            }
        }

        peerListListener = WifiP2pManager.PeerListListener { peerList ->
            peers = emptyList()
            Log.d(TAG, "onCreate: $peerList")
            var localList = mutableListOf<WifiP2pDevice>()
            peerList.deviceList.forEach { device ->
                localList.add(device)
            }
            peers = localList
        }

        connectionInfoListener = WifiP2pManager.ConnectionInfoListener { wifiP2pInfo ->
            Log.d("TAG", "onConnectionInfoAvailable : " + wifiP2pInfo.toString())
            connectionText = wifiP2pInfo.toString()

            if (wifiP2pInfo.groupFormed) {
                Log.d(TAG, "connectionInfoListener")
                var role: Int
                if (wifiP2pInfo.isGroupOwner) {
                    Log.d(TAG, "I am host")
                    role = communicationService.SERVER_ROLE
                } else {
                    Log.d(TAG, "I am client")
                    role = communicationService.CLIENT_ROLE
                }

                var groupOwnerAddress: String = wifiP2pInfo.groupOwnerAddress.hostAddress
                var groupOwnerPort: Int = 8888
                connectionInfoAvailable = true

                var i: Intent = Intent(applicationContext, CommunicationService::class.java)
                i.setAction(communicationService.ACTION_START_COMMUNICATION)
                i.putExtra(communicationService.EXTRAS_COMMUNICATION_ROLE, role)
                i.putExtra(communicationService.EXTRAS_GROUP_OWNER_ADDRESS, groupOwnerAddress)
                i.putExtra(communicationService.EXTRAS_GROUP_OWNER_PORT, groupOwnerPort)
                i.putExtra(communicationService.EXTRAS_DEVICE_NAME, wifiP2PdeviceName)

//                if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
//                    startForegroundService(i)
//                } else {
//                    startService(i)
//                }
                startService(i)
                Log.d(TAG, "connectionInfoListener Done")
            }
        }


        setContent {

//            Screen1()

            JediShareTheme {
                val permissionViewModel = viewModel<PermissionViewModel>()
                val dialogQueue = permissionViewModel.visiblePermissionDialogQueue
                var peerListInternal by remember { mutableStateOf<List<WifiP2pDevice>>(emptyList()) }
                var connectionStatusValue by remember { mutableStateOf("Connection Status: ") }

                LaunchedEffect(key1 = Unit) {
                    while (true) {
                        delay(1000L)
                        peerListInternal = peers
                        connectionStatusValue = connectionText
                    }
                }

                val multiplePermissionResultLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions(),
                    onResult = { perms ->
                        permissionsToRequest.forEach { permission ->
                            permissionViewModel.onPermissionResult(
                                permission = permission,
                                isGranted = perms[permission] == true
                            )
                        }
                    }
                )

                SideEffect {
                    multiplePermissionResultLauncher.launch(permissionsToRequest)
                }

                dialogQueue
                    .reversed()
                    .forEach { permission ->
                        PermissionDialog(
                            permissionTextProvider = when (permission) {
                                Manifest.permission.INTERNET -> {
                                    InternetPermissionTextProvider()
                                }
                                Manifest.permission.ACCESS_NETWORK_STATE -> {
                                    InternetPermissionTextProvider()
                                }
                                Manifest.permission.CHANGE_NETWORK_STATE -> {
                                    InternetPermissionTextProvider()
                                }
                                Manifest.permission.ACCESS_COARSE_LOCATION -> {
                                    LocationPermissionTextProvider()
                                }
                                Manifest.permission.ACCESS_FINE_LOCATION -> {
                                    LocationPermissionTextProvider()
                                }
                                Manifest.permission.ACCESS_WIFI_STATE -> {
                                    WifiPermissionTextProvider()
                                }
                                Manifest.permission.CHANGE_WIFI_STATE -> {
                                    WifiPermissionTextProvider()
                                }
                                else -> return@forEach
                            },
                            isPermanentlyDeclined = !shouldShowRequestPermissionRationale(
                                permission
                            ),
                            onDismiss = permissionViewModel::dismissDialog,
                            onOkClick = {
                                permissionViewModel.dismissDialog()
                                multiplePermissionResultLauncher.launch(
                                    arrayOf(permission)
                                )
                            },
                            onGoToAppSettingsClick = ::openAppSettings
                        )
                    }

                /*****************************************************************
                 * UI STARTS HERE *
                 ******************************************************************/

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Button(onClick = {
                        val wifiManager: WifiManager =
                            getSystemService<WifiManager>(WifiManager::class.java)
                        if (wifiManager.isWifiEnabled) {
                            Toast.makeText(this@MainActivity, "Its On", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(this@MainActivity, "Turn Wifi ON", Toast.LENGTH_SHORT)
                                .show()
                        }
                    }) {
                        Text(text = "Turn Wifi On")
                    }
                    Button(onClick = {
                        startDiscovery()
                    }) {
                        Text(text = "Discover Peers")
                    }
                    Text(text = "read_msg_box")
                    Text(text = connectionStatusValue)

                    LazyColumn(
                        modifier = Modifier.defaultMinSize(minHeight = 100.dp)
                    ) {
                        //To be completed
                        items(peerListInternal) { peer ->
                            Text(
                                text = peer.deviceName, modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (peer.status == WifiP2pDevice.CONNECTED && connectionInfoAvailable) {
                                            // here
                                            Log.d(TAG, "CASE 1")
                                        } else if (peer.status == WifiP2pDevice.CONNECTED) {
                                            // here
                                            Log.d(TAG, "CASE 2")
                                        } else if (peer.status == WifiP2pDevice.AVAILABLE) {
                                            Log.d(TAG, "CASE 3")
                                            val connectedDeviceName: String? = peer.deviceName
                                            if (connectedDeviceName != null) {
                                                var config: WifiP2pConfig = WifiP2pConfig()
                                                config.deviceAddress = peer.deviceAddress
                                                wifiP2pManager?.connect(
                                                    wifiP2pChannel,
                                                    config,
                                                    actionListener
                                                )
                                            } else {
                                                Log.d(TAG, "CASE 4")
                                                Toast
                                                    .makeText(
                                                        this@MainActivity,
                                                        "You are already connected to another device. Disconnect to start another communication",
                                                        Toast.LENGTH_SHORT
                                                    )
                                                    .show()
                                            }
                                        } else if (peer.status == WifiP2pDevice.INVITED) {
                                            if (wifiP2pManager != null && wifiP2pChannel != null) {
                                                disconnectP2P()
                                            }
                                        }
                                    }, fontSize = 24.sp, textAlign = TextAlign.Center
                            )
                        }
                    }

                    var text by remember { mutableStateOf("Hello") }
                    TextField(
                        value = text,
                        onValueChange = { newText -> text = newText },
                        label = { Text("Enter text") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp)
                    )
                    Button(onClick = {
                        if (!text.trim { it <= ' ' }.isEmpty()) {
                            val i = Intent(applicationContext, CommunicationService::class.java)
                            i.action = communicationService.ACTION_SEND_MSG
                            i.putExtra(communicationService.EXTRAS_MSG_TYPE, 0)
                            i.putExtra(communicationService.EXTRAS_TEXT_CONTENT, text)
                            startService(i)
                        }
                    }) {
                        Text(text = "Send")
                    }
                }

            }


//            setContent {
//                JediShareTheme {
//                    Box(
//                        modifier = Modifier
//                            .fillMaxSize()
//                            .background(MaterialTheme.colors.background),
//                        contentAlignment = androidx.compose.ui.Alignment.BottomCenter,
//                    ) {
//                        Screen1()
//                        NavBar()
//                    }
//                }
//            }
        }
    }


    private fun initializeWiFiDirect(): Boolean {
        wifiP2pManager = getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager
        if (wifiP2pManager == null) {
            Log.e(TAG, "Cannot get Wi-Fi system service.")
            return false
        }
        wifiP2pChannel = wifiP2pManager!!.initialize(
            this, mainLooper
        ) { Log.d(TAG, "Wifi P2P Channel disconnected") }
        if (wifiP2pChannel == null) {
            Log.e(TAG, "Cannot initialize Wi-Fi Direct.")
            return false
        }
        wifiP2pManager!!.requestConnectionInfo(
            wifiP2pChannel
        ) { wifiP2pInfo ->
            if (wifiP2pInfo.groupOwnerAddress != null) {
                Log.d(TAG, "Info Available")
                connectionInfoAvailable = true
            }
        }
        return true
    }

    @SuppressLint("MissingPermission")
    fun startDiscovery() {
        if (isWiFiDirectActive && !isDiscovering) {
            wifiP2pManager?.discoverPeers(wifiP2pChannel, actionListener)
        }
    }

    @SuppressLint("MissingPermission")
    fun requestPeerList() {
        wifiP2pManager?.requestPeers(wifiP2pChannel, peerListListener)
    }

    fun newWiFiDirectConnection() {
        wifiP2pManager?.requestConnectionInfo(wifiP2pChannel, connectionInfoListener)
    }

    fun disconnectP2P() {
        if (wifiP2pManager != null && wifiP2pChannel != null) {
            wifiP2pManager!!.cancelConnect(wifiP2pChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "cancelConnect onSuccess -")
                }

                override fun onFailure(reason: Int) {
                    Log.d(TAG, "cancelConnect onFailure -$reason")
                }
            })
            wifiP2pManager!!.removeGroup(wifiP2pChannel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Log.d(TAG, "removeGroup onSuccess -")
                }

                override fun onFailure(reason: Int) {
                    Log.d(TAG, "removeGroup onFailure -$reason")
                }
            })
        }
        connectionInfoAvailable = false
        peers = emptyList()
    }

    fun setWiFiDirectActive(wiFiDirectActive: Boolean) {
        this.isWiFiDirectActive = wiFiDirectActive
        if (wiFiDirectActive) {
            startDiscovery()
            Log.d(TAG, "Its ON Its Working")
        } else {
            Log.d(TAG, "Wifi Direct is inactive")
            peers = emptyList()

        }
    }

    fun setWifiP2PdeviceName(wifiP2PdeviceName: String?) {
        this.wifiP2PdeviceName = wifiP2PdeviceName!!
    }

    fun setIsDiscovering(discovering: Boolean) {
        this.isDiscovering = discovering
    }

    fun setLocationState(locationEnabled: Boolean) {
        if (!locationEnabled) {
            disconnectP2P()
        }
    }

    fun isWiFiDirectActive(): Boolean {
        return isWiFiDirectActive
    }

    fun isDiscovering(): Boolean {
        return isDiscovering
    }

    override fun onResume() {
        super.onResume()
        if (receiver != null && intentFilter != null) registerReceiver(receiver, intentFilter)
    }

    override fun onPause() {
        super.onPause()
        if (receiver != null && intentFilter != null) unregisterReceiver(receiver)
    }

}


fun Activity.openAppSettings() {
    Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.fromParts("package", packageName, null)
    ).also(::startActivity)
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
            }else{
                intent = Intent(context, DeviceList::class.java)
            }
            intent.putExtra("source", true)
            context.startActivity(intent)
        },
            buttonName = "Recieve",
            icon = receiveIcon
        )
    }
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
    Column(
        modifier = Modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.Top,
        horizontalAlignment = Alignment.CenterHorizontally

    ) {
        Button(onClick = {
            val intent = Intent(context, SendOrReceive::class.java)
            val one = "Wifi Direct"
            intent.putExtra("Data", one)
            context.startActivity(intent)
        }) {
            Text(
                text = "Wifi Direct",
                fontSize = 20.sp
            )
        }
        Spacer(modifier = Modifier.size(128.dp))
        Button(onClick = {
            val intent = Intent(context, SendOrReceive::class.java)
            val one = "Bluetooth"
            intent.putExtra("Data", one)
            context.startActivity(intent)
        }) {
            Text(
                text = "Bluetooth",
                fontSize = 20.sp
            )


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
    }


@Composable
fun NavBar() {

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
                context?.finish()
                val intent = Intent(context, MainActivity::class.java)
                context?.startActivity(intent)
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