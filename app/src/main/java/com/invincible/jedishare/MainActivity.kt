package com.invincible.jedishare

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invincible.jedishare.ui.theme.JediShareTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Screen1()
        }
    }
}

@Composable
fun Screen1() {
    val context = LocalContext.current
    Column (
        modifier = Modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ){
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
        }
    }
}
