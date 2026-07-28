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
            val nextIntent = Intent(this@ShareReceiverActivity, MainActivity::class.java).apply {
                putParcelableArrayListExtra("urilist", uris)
                putExtra("start_route", if (method == "wifi") "discover-wifi" else "discover-bt")
                action = intent.action
                
                // Attach ClipData to propagate URI read permissions
                if (uris.isNotEmpty()) {
                    val clip = android.content.ClipData.newUri(contentResolver, "shared_files", uris.first())
                    for (i in 1 until uris.size) {
                        clip.addItem(android.content.ClipData.Item(uris[i]))
                    }
                    clipData = clip
                }
                
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            startActivity(nextIntent)
            finish()
        }
    }
}
