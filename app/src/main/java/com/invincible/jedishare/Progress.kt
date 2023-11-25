package com.invincible.jedishare

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Space
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.Button
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invincible.jedishare.presentation.components.ChatScreen
import com.invincible.jedishare.ui.theme.JediShareTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class Progress : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JediShareTheme {

                val data = intent.getStringExtra("transferMethod")
                if (data != null) {
                    Log.e("hhh",data)
                }

                ChatScreen(
                    onDisconnect = { /*TODO*/ },
                    onSendMessage = {},
                    uriList = intent?.getParcelableArrayListExtra<Uri>("urilist") ?: emptyList<Uri>(),
                    isFromWifi = if(data == "Wifi-Direct") true else false
                )
            }
        }
    }
}
