package com.invincible.jedishare

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.invincible.jedishare.ui.theme.JediShareTheme

class WelcomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JediShareTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colors.background),
                ) {
                    Column (
                        modifier = Modifier
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ){
                        Column (
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ){
                            Text(
                                modifier = Modifier
                                    .padding(top = 40.dp),
                                text = "Welcome to",
                                style = MaterialTheme.typography.h3,
                                color = Color.Black,
                            )
                            Text(
                                modifier = Modifier
                                    .padding(bottom = 5.dp),
                                text = "Jedi Share",
                                color = Color(0xFFec1c22),
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.h1,
//                                textDecoration = TextDecoration.Underline
                            )
                        }

                        Column (
                            verticalArrangement = Arrangement.SpaceEvenly,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ){
                            Box(
                                modifier = Modifier
                                    .size(220.dp)
                                    .shadow(elevation = 4.dp, shape = CircleShape)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colors.primary)
                            ) {
                                Image(
                                    painter = painterResource(id = R.drawable.welcome_image),
                                    contentDescription = "Image with Shadow",
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }

                            Text(
                                modifier = Modifier
                                    .padding(top = 10.dp),
//                            text = "Share files instantly\n" +
//                                    "with Jedi Share",
                                text = "Quickly transfer photos, videos, documents, audio files",
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                                style = MaterialTheme.typography.h5)

                        }


//                        Text(
//                            text = "Jedi Share is the ultimate file sharing app, designed to make sharing files a breeze.",
//                            textAlign = TextAlign.Center,
//                            style = MaterialTheme.typography.h6)

                        Button(
                                onClick = {
                                    val intent = Intent(this@WelcomeActivity, MainActivity::class.java)
                                    startActivity(intent)
                                },
                        modifier = Modifier
                            .padding(20.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(50.dp, 50.dp, 50.dp, 50.dp)),
                            colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFec1c22)),
                        ) {
                            Text(text = "Continue", style=MaterialTheme.typography.h4, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
