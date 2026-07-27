package com.invincible.jedishare

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.invincible.jedishare.data.UserPreferencesDataStore
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    @Inject
    lateinit var dataStore: UserPreferencesDataStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val uris = when (intent.action) {
            Intent.ACTION_SEND -> {
                val uri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)
                if (uri != null) arrayListOf(uri) else null
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
            }
            else -> null
        }

        if (uris.isNullOrEmpty()) {
            finish()
            return
        }

        lifecycleScope.launch {
            val method = dataStore.defaultTransferMethod.first()
            val targetClass = if (method == "WiFi-Direct") {
                WifiDirectDeviceSelectActivity::class.java
            } else {
                DeviceList::class.java
            }
            
            val nextIntent = Intent(this@ShareReceiverActivity, targetClass).apply {
                putParcelableArrayListExtra("urilist", uris)
                // Forward action so they know it's a share if needed
                action = intent.action
            }
            startActivity(nextIntent)
            finish()
        }
    }
}
