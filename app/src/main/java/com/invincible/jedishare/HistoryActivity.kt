package com.invincible.jedishare

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.invincible.jedishare.data.db.TransferHistoryEntity
import com.invincible.jedishare.presentation.HistoryViewModel
import com.invincible.jedishare.ui.theme.JediShareTheme
import com.invincible.jedishare.ui.theme.MyRed
import com.invincible.jedishare.ui.theme.MyRedSecondaryLight
import com.invincible.jedishare.ui.theme.Roboto
import dagger.hilt.android.AndroidEntryPoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * History screen showing all past file transfers.
 *
 * Was previously a stub showing only NavBar().
 * Now backed by [HistoryViewModel] and Room database.
 */
@AndroidEntryPoint
class HistoryActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            JediShareTheme {
                val viewModel = hiltViewModel<HistoryViewModel>()
                HistoryScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun HistoryScreen(viewModel: HistoryViewModel) {
    val historyList by viewModel.history.collectAsState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Spacer(modifier = Modifier.size(16.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Transfer History",
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    fontFamily = Roboto
                )
                if (historyList.isNotEmpty()) {
                    TextButton(onClick = viewModel::clearAll) {
                        Text(text = "Clear All", color = MyRed, fontFamily = Roboto)
                    }
                }
            }

            Divider(
                color = Color.LightGray,
                thickness = 2.dp,
                modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp)
            )

            if (historyList.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No transfers yet",
                        fontSize = 18.sp,
                        color = Color.Gray,
                        fontFamily = Roboto
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(historyList, key = { it.id }) { entry ->
                        HistoryItem(
                            entry = entry,
                            onDelete = { viewModel.deleteEntry(entry) }
                        )
                    }
                }
            }
        }

        // Bottom nav bar
        Box(modifier = Modifier.align(Alignment.BottomCenter)) {
            NavBar()
        }
    }
}

@Composable
private fun HistoryItem(
    entry: TransferHistoryEntity,
    onDelete: () -> Unit
) {
    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
    val dateStr = dateFormat.format(Date(entry.timestampMs))

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MyRedSecondaryLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.fileName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    fontFamily = Roboto,
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "${if (entry.isSender) "Sent" else "Received"} • ${entry.transferMethod}",
                    fontSize = 13.sp,
                    color = Color.Gray,
                    fontFamily = Roboto
                )
                Text(
                    text = "${bytesToHumanReadableSize(entry.fileSizeBytes.toDouble())} • $dateStr",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    fontFamily = Roboto
                )
                entry.remoteDeviceName?.let {
                    Text(
                        text = "Device: $it",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        fontFamily = Roboto
                    )
                }
            }

            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete entry",
                    tint = MyRed
                )
            }
        }
    }
}