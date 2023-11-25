package com.invincible.jedishare

import android.content.Intent
import android.graphics.drawable.VectorDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.modifier.modifierLocalConsumer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invincible.jedishare.ui.theme.MyRed
import com.invincible.jedishare.ui.theme.MyRedSecondary
import com.invincible.jedishare.ui.theme.Roboto
import com.invincible.jedishare.ui.theme.White
import com.invincible.jedishare.ui.theme.ui.theme.JediShareTheme
import java.util.Vector

class SettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JediShareTheme {
                Scaffold(
                    topBar = {

                        SettingsTopBar()
                    },
                    content = {
                        SettingsList()
                    } ,
                    bottomBar = {
                        NavBar()
                    }
                )

            }

        }

    }
}

@Composable
fun SettingsTopBar() {
    val context = LocalContext.current
    JediShareTheme() {

        TopAppBar(
            backgroundColor = Color.White,
            title = {
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Settings", fontWeight = FontWeight.Bold, fontFamily = Roboto, fontSize =  25.sp)
                }
            },
//            navigationIcon = {
//                IconButton(onClick = {
//                    val intent = Intent(context, MainActivity::class.java)
//                    context.startActivity(intent)
//                }) {
//                    Box(
//
//
//                    ) {
//
//                            Icon(
//                                imageVector = Icons.Default.ArrowBack,
//                                contentDescription = "Back"
//                            )
//
//
//
//                    }
//                    }
//            }
        )
    }
}



@Composable
fun SettingsList() {
    val context = LocalContext.current
    val settingsIntent = Intent(
        android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:${context.packageName}")
    )
    // State to manage whether the dialog is shown or not
    var showDialog by remember { mutableStateOf(false) }

    // Function to show or hide the dialog
    fun toggleDialog(show: Boolean) {
        showDialog = show
    }

    // Function to create the dialog content
    @Composable
    fun DialogContent() {
        AlertDialog(
            onDismissRequest = { toggleDialog(false) },
            title = {
                Text("About Us")
            },
            text = {
                Text("Welcome to Just Share! An app made to make your sharing process the easiest and fastest.\nThis app has been developed by Team 176, Cerberus")
            },
            confirmButton = {
                Button(onClick = { toggleDialog(false) },colors = ButtonDefaults.buttonColors(backgroundColor = MyRedSecondary)) {
                    Text("Close")
                }
            }
        )
    }



    Column(
        modifier = Modifier
            .fillMaxSize()
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {

        Box(modifier = Modifier
            .fillMaxWidth()
            .clickable { showDialog = true }
            .padding(16.dp)
        ){
            Row() {
                Text(text = "About Us ", fontSize = 20.sp)
                Icon(imageVector = Icons.Outlined.Info, contentDescription ="Info", modifier = Modifier.align(Alignment.CenterVertically))
            }
        }
        Divider(modifier = Modifier.fillMaxWidth())
        Box(
            modifier = Modifier
                .fillMaxWidth() // Make the Box fill the entire width
                .clickable {
                    context.startActivity(settingsIntent)
                }
                .padding(16.dp) // Optional: Apply padding to the Box
        ){
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "More Settings", fontSize = 20.sp)
                val icon: Painter = painterResource(id = R.drawable.external_link)
                Icon(painter = icon, contentDescription = "App Settings")
            }
        }
        if (showDialog) {
            DialogContent()
        }
    }
}


@Preview
@Composable
fun PreviewSettingsScreen() {
    JediShareTheme {
        Scaffold(
            topBar = { SettingsTopBar() },
            content = { SettingsList() }
        )
    }
}

@Preview
@Composable
fun PreviewSettingsActivity() {
    JediShareTheme {
        Scaffold(
            topBar = { SettingsTopBar() },
            content = { SettingsList() }
        )
    }
}
