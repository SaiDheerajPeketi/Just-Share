package com.invincible.jedishare

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invincible.jedishare.ui.theme.JediShareTheme

class SelectFile : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JediShareTheme {
                Screen4()
            }
        }
    }
}
@Composable
fun Screen4() {
    val context = LocalContext.current
    Column (
        modifier = Modifier
            .fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ){
        Text(
            text = "File 1",
            fontSize = 50.sp
        )
        Spacer(modifier = Modifier.size(16.dp))
        Text(
            text = "File 2",
            fontSize = 50.sp,
            modifier = Modifier.clickable {
                context.startActivity(Intent(context, Progress::class.java))
            }
        )
        Spacer(modifier = Modifier.size(16.dp))
        Text(
            text = "File 3",
            fontSize = 50.sp
        )
        Spacer(modifier = Modifier.size(16.dp))
        Text(
            text = "File 4",
            fontSize = 50.sp
        )
        Spacer(modifier = Modifier.size(16.dp))
        Text(
            text = "File 4",
            fontSize = 50.sp
        )
        Spacer(modifier = Modifier.size(16.dp))
    }
}
@Composable
fun openProgressActivity() {
    val context = LocalContext.current
    context.startActivity(Intent(context, Progress::class.java))

}
