package com.invincible.jedishare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.Button
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.invincible.jedishare.presentation.ui.theme.JediShareTheme

class WifiDirectDeviceSelectActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JediShareTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colors.background),
                    contentAlignment = Alignment.BottomCenter,
                ) {

                    // True is the user clicked the receive button but you should prolly check it once
                    val isFromReceive = intent.getBooleanExtra("source", false)

                    val message = rememberSaveable {
                        mutableStateOf("")
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ){
                        Text(text = isFromReceive.toString())
                        Button(onClick = { /*TODO*/ }) {
                           Text(text = "Button 1")
                        }
                        TextField(
                            value = message.value,
                            onValueChange = { message.value = it },
                            modifier = Modifier.weight(1f),
                            placeholder = {
                                Text(text = "Edit Text")
                            }
                        )
                        Button(onClick = { /*TODO*/ }) {
                            Text(text = "Button 2")
                        }
                    }

                }
            }
        }
    }
}

