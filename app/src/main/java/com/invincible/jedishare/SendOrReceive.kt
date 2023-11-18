package com.invincible.jedishare

import android.app.ActivityManager
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.Context.ACTIVITY_SERVICE
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.util.Log
import android.widget.EditText
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.getSystemService
import com.invincible.jedishare.ui.theme.JediShareTheme
import java.util.Formatter
import java.util.UUID

class SendOrReceive : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JediShareTheme {
                val data = intent.getStringExtra("Data")

                Text(text = data?:"")

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colors.background),
                    contentAlignment = androidx.compose.ui.Alignment.BottomCenter,
                ) {
                    Screen2()
                    NavBar()
                }
            }
        }
    }
}

@Composable
fun Screen2() {
    val context = LocalContext.current
    Row (
        modifier = Modifier
            .fillMaxSize(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ){
        val sendIcon: Painter = painterResource(id = R.drawable.send)
        CircularButton(onClick = {
            context.startActivity(Intent(context, SelectFile::class.java))
        },
            buttonName = "Send",
            icon = sendIcon)
//        Button(onClick = {
//            context.startActivity(Intent(context, SelectFile::class.java))
//        }) {
//            Text(
//                text = "Send",
//                fontSize = 20.sp
//            )
//        }
        Spacer(modifier = Modifier.size(128.dp))
        val receiveIcon: Painter = painterResource(id = R.drawable.receive)
        CircularButton(onClick = {
            val intent = Intent(context, DeviceList::class.java)
            intent.putExtra("source", true)
            context.startActivity(intent)
        },
            buttonName = "Recieve",
            icon = receiveIcon
        )
    }
}
