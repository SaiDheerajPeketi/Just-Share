package com.invincible.jedishare

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.invincible.jedishare.ui.theme.JediShareTheme
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream

class Waiting : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JediShareTheme {
                var temp = intent.getStringExtra("Data")
                Column (
                    modifier = Modifier
                        .fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ){
                    if(temp == null){
                        temp = "oh no"
                    }
                    if (temp != null) {
                        Text(
                            text = temp!!,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}
