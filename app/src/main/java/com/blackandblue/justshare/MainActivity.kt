package com.blackandblue.justshare

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.MaterialTheme
import androidx.compose.ui.Modifier
import com.blackandblue.justshare.data.UserPreferencesDataStore
import com.blackandblue.justshare.navigation.AppNavGraph
import com.blackandblue.justshare.ui.theme.JediShareTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import timber.log.Timber

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val transferViewModel: com.blackandblue.justshare.presentation.TransferViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("MainActivity - onCreate called")
        super.onCreate(savedInstanceState)

        var startRoute = intent.getStringExtra("start_route") ?: com.blackandblue.justshare.navigation.Screen.Splash.route
        var initialUris = intent.getParcelableArrayListExtra<Uri>("urilist")?.toList().orEmpty()
        var initialMethod = when (startRoute) {
            com.blackandblue.justshare.navigation.Screen.DiscoverWifi.route -> "wifi"
            com.blackandblue.justshare.navigation.Screen.DiscoverBT.route -> "bt"
            else -> null
        }

        val action = intent.action
        val isShareIntent = action == Intent.ACTION_SEND || action == Intent.ACTION_SEND_MULTIPLE
        
        if (isShareIntent) {
            val dataStore = UserPreferencesDataStore(this)
            val method = runBlocking { dataStore.defaultTransferMethod.first() }
            initialMethod = method
            startRoute = if (method == "wifi") com.blackandblue.justshare.navigation.Screen.DiscoverWifi.route else com.blackandblue.justshare.navigation.Screen.DiscoverBT.route
            
            initialUris = when (action) {
                Intent.ACTION_SEND -> {
                    val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                    if (uri != null) listOf(uri) else emptyList()
                }
                Intent.ACTION_SEND_MULTIPLE -> {
                    intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.toList().orEmpty()
                }
                else -> emptyList()
            }

            // Most share-sheet grants are temporary. Retain them when the
            // sending provider explicitly allows a persistable document grant.
            if ((intent.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION) != 0) {
                initialUris.forEach { persistReadUriPermission(this, it) }
            }
        }

        if (initialUris.isNotEmpty()) {
            transferViewModel.setUris(initialUris)
        }
        initialMethod?.let { transferViewModel.setMethod(it) }

        setContent {
            JediShareTheme {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MaterialTheme.colors.background)
                ) {
                    AppNavGraph(
                        startDestination = startRoute,
                        transferViewModel = transferViewModel,
                        initialUris = initialUris,
                        initialMethod = initialMethod
                    )
                }
            }
        }
    }
}
