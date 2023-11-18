package com.invincible.jedishare

import android.content.Intent
import android.os.Bundle
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invincible.jedishare.ui.theme.JediShareTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)


            setContent {
                JediShareTheme {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colors.background),
                        contentAlignment = androidx.compose.ui.Alignment.BottomCenter,
//                            .paint(
//                                painterResource(id = R.drawable.background),
//                                contentScale = ContentScale.FillBounds
//                            )
                            //.background(Color.Black)
                    ) {
                        Screen1()
                        NavBar()
                    }
            }
        }
    }
}

@Composable
fun CircularButton(onClick: () -> Unit, buttonName: String, icon: Painter) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colors.primary)
        ) {
            androidx.compose.material.Icon(
                painter = icon,
                contentDescription = null,
                tint = MaterialTheme.colors.background,
                modifier = Modifier.size(30.dp)
            )
        }

        Spacer(modifier = Modifier.size(8.dp))

        Text(
            text = buttonName,
            fontSize = 16.sp,
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
        Spacer(modifier = Modifier.size(100.dp))
        Text(
            text = "Jedi Share",
            fontSize = 60.sp,
            color = MaterialTheme.colors.primary
        )
        Divider(
            color = MaterialTheme.colors.primary,
            thickness = 3.dp,
            modifier = Modifier
                .width(300.dp)
        )
        Spacer(modifier = Modifier.size(200.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {

            val customDrawable: Painter = painterResource(id = R.drawable.wifi_icon)
            CircularButton(
                onClick = {
                    val intent = Intent(context, SendOrReceive::class.java)
                    val one = "Wifi Direct"
                    intent.putExtra("Data", one)
                    context.startActivity(intent)
                },
                "Wifi Direct",
                customDrawable
            )

            Spacer(modifier = Modifier.size(64.dp))

            val customDrawable2: Painter = painterResource(id = R.drawable.bluetooth_24)
            CircularButton(
                onClick = {
                    val intent = Intent(context, SendOrReceive::class.java)
                    val one = "Bluetooth"
                    intent.putExtra("Data", one)
                    context.startActivity(intent)
                },
                "Bluetooth",
                customDrawable2
            )
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
            .shadow(40.dp, CircleShape,true)
            .fillMaxWidth()
            .padding(16.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(15.dp, 15.dp, 15.dp, 15.dp))
    ) {
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
            onClick = { /*TODO*/ },
            alwaysShowLabel = true,
            selectedContentColor = Color.Black,
            unselectedContentColor = Color.Gray,
        )
        BottomNavigationItem(
            icon = {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = "Profile",
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            },
            label = { Text(text = "Profile") },
            selected = false,
            onClick = { /*TODO*/ },
            alwaysShowLabel = true,
            selectedContentColor = Color.Black,
            unselectedContentColor = Color.Gray,
        )
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
            onClick = { /*TODO*/ },
            alwaysShowLabel = true,
            selectedContentColor = Color.Black,
            unselectedContentColor = Color.Gray,
        )
        BottomNavigationItem(
            icon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "About Us",
                    tint = iconColor,
                    modifier = Modifier.size(24.dp)
                )
            },
            label = { Text(text = "About Us") },
            selected = false,
            onClick = { /*TODO*/ },
            alwaysShowLabel = true,
            selectedContentColor = Color.Black,
            unselectedContentColor = Color.Gray,
        )
    }
}