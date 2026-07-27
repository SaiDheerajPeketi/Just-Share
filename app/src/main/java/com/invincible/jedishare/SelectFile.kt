package com.invincible.jedishare

import timber.log.Timber

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts.GetMultipleContents
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.invincible.jedishare.ui.theme.JediShareTheme
import com.invincible.jedishare.ui.theme.MyRed
import com.invincible.jedishare.ui.theme.MyRedSecondaryLight
import com.invincible.jedishare.ui.theme.Roboto
import dagger.hilt.android.AndroidEntryPoint

/**
 * File selection Activity.
 *
 * MVVM fixes applied:
 * - All MediaStore queries removed from onCreate and moved to [MediaRepository] / [ImageViewModel].
 * - Selected URIs tracked via Compose state (not a public mutable field on the Activity).
 * - Permissions are now requested through [ActivityResultContracts.RequestMultiplePermissions],
 *   not via the legacy [ActivityCompat.requestPermissions] API.
 * - Utility functions (classifyFileType, bytesToHumanReadableSize, getFileDetailsFromUri)
 *   moved to Utils.kt.
 * - All dead / commented-out code removed.
 */
@AndroidEntryPoint
class SelectFile : ComponentActivity() {

    private val pickFiles = registerForActivityResult(GetMultipleContents()) { uris ->
        selectedUris = selectedUris + uris
    }

    private var selectedUris by mutableStateOf<List<Uri>>(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        Timber.d("SelectFile - onCreate called")
        super.onCreate(savedInstanceState)

        setContent {
            JediShareTheme {
                SelectFileScreen(
                    selectedUris = selectedUris,
                    onPickFiles = { pickFiles.launch("*/*") },
                    onRemoveUri = { uri -> selectedUris = selectedUris.filterNot { it == uri } },
                    onTransfer = { uris ->
                        val transferMethod = intent.getStringExtra("transferMethod")
                        val isFromReceive = intent.getBooleanExtra("source", false)

                        val nextIntent = if (transferMethod == "Wifi-Direct") {
                            Intent(this, WifiDirectDeviceSelectActivity::class.java)
                        } else {
                            Intent(this, DeviceList::class.java)
                        }
                        nextIntent.putExtra("source", isFromReceive)
                        nextIntent.putParcelableArrayListExtra("urilist", ArrayList(uris))
                        startActivity(nextIntent)
                    },
                    onNavigateUp = { finish() }
                )
            }
        }
    }
}

@Composable
private fun SelectFileScreen(
    selectedUris: List<Uri>,
    onPickFiles: () -> Unit,
    onRemoveUri: (Uri) -> Unit,
    onTransfer: (List<Uri>) -> Unit,
    onNavigateUp: () -> Unit
) {
    val contentResolver = androidx.compose.ui.platform.LocalContext.current.contentResolver

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.size(16.dp))

            // ── Top Bar ────────────────────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateUp) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowLeft,
                        contentDescription = "Back",
                        tint = MyRed,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Text(
                    text = "Select Files",
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.h3,
                )
                // Spacer to balance the row
                Spacer(modifier = Modifier.size(48.dp))
            }

            Divider(
                color = Color.LightGray,
                thickness = 2.dp,
                modifier = Modifier.padding(top = 8.dp, start = 16.dp, end = 16.dp)
            )

            // ── File list or empty picker ──────────────────────────────────────
            if (selectedUris.isEmpty()) {
                FilePickerPlaceholder(onPickFiles = onPickFiles)
            } else {
                SelectedFilesList(
                    uris = selectedUris,
                    contentResolver = contentResolver,
                    onPickMore = onPickFiles,
                    onRemove = onRemoveUri
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            // ── Transfer Button ────────────────────────────────────────────────
            Button(
                enabled = selectedUris.isNotEmpty(),
                onClick = { onTransfer(selectedUris) },
                modifier = Modifier
                    .padding(bottom = 20.dp, start = 20.dp, end = 20.dp)
                    .fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (selectedUris.isNotEmpty()) MyRed else Color.LightGray,
                    disabledBackgroundColor = Color.LightGray
                ),
                shape = RoundedCornerShape(50)
            ) {
                Text(
                    text = "Transfer",
                    style = MaterialTheme.typography.h5,
                    color = if (selectedUris.isNotEmpty()) Color.White else Color.Gray,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun FilePickerPlaceholder(onPickFiles: () -> Unit) {
    Timber.d("SelectFile - FilePickerPlaceholder called")
    Box(
        modifier = Modifier
            .padding(20.dp)
            .height(300.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MyRedSecondaryLight)
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onPickFiles
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                modifier = Modifier.size(60.dp),
                imageVector = Icons.Default.AddCircle,
                contentDescription = null,
                tint = MyRed,
            )
            Text(
                text = "Upload Files",
                style = MaterialTheme.typography.h5,
                color = Color.Gray,
            )
        }
    }
}

@Composable
private fun SelectedFilesList(
    uris: List<Uri>,
    contentResolver: android.content.ContentResolver,
    onPickMore: () -> Unit,
    onRemove: (Uri) -> Unit
) {
    Box(
        modifier = Modifier
            .padding(20.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MyRedSecondaryLight)
            .fillMaxWidth()
            .heightIn(max = 660.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.size(12.dp))

            LazyColumn(
                modifier = Modifier
                    .heightIn(max = 570.dp)
                    .animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        )
                    )
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.Top,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // "Add more" button at top of list
                item {
                    IconButton(onClick = onPickMore) {
                        Icon(
                            modifier = Modifier.size(60.dp),
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = "Add more files",
                            tint = MyRed,
                        )
                    }
                }

                items(uris, key = { it.toString() }) { uri ->
                    FileListItem(
                        uri = uri,
                        contentResolver = contentResolver,
                        onRemove = { onRemove(uri) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FileListItem(
    uri: Uri,
    contentResolver: android.content.ContentResolver,
    onRemove: () -> Unit
) {
    Timber.d("SelectFile - FileListItem called")
    val fileInfo = remember(uri) { getFileDetailsFromUri(uri, contentResolver) }
    val fileType = fileInfo.format?.let { classifyFileType(it) }

    val (icon, iconTint, iconBackground) = when (fileType) {
        "Photo" -> Triple(painterResource(R.drawable.photo_icon), Color(0xFF33A850), Color(0x2233A850))
        "Video" -> Triple(painterResource(R.drawable.video_icon), Color(0xFFC54EE6), Color(0x22C54EE6))
        "Music" -> Triple(painterResource(R.drawable.music_icon), Color(0xFFFF0000), Color(0x22FF0000))
        else    -> Triple(painterResource(R.drawable.document_icon), Color(0xFF4187E6), Color(0x224187E6))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 8.dp, start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // File type icon
        Box(
            modifier = Modifier
                .background(iconBackground, shape = CircleShape)
                .size(50.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(30.dp)
            )
        }

        Spacer(modifier = Modifier.size(10.dp))

        // File name + size
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.Start,
        ) {
            var expanded by remember { mutableStateOf(false) }
            Text(
                text = fileInfo.fileName ?: "Unknown file",
                fontSize = 15.sp,
                fontFamily = Roboto,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .animateContentSize()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { expanded = !expanded },
                maxLines = if (expanded) 10 else 2
            )
            Text(
                text = fileInfo.size?.toDoubleOrNull()?.let { bytesToHumanReadableSize(it) }
                    ?: "Unknown size",
                fontSize = 13.sp,
                fontFamily = Roboto,
                color = Color.Gray
            )
        }

        // Remove button
        IconButton(onClick = onRemove) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Remove file",
                tint = Color.Gray
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LOTTIE PRELOADER (shared composable, used across many Activities)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Displays a Lottie animation from the raw resources.
 *
 * @param drawable The raw resource ID (e.g. R.raw.connecting_animation)
 * @param iterations Number of iterations; defaults to [LottieConstants.IterateForever]
 */
@Composable
fun AnimatedPreloader(
    modifier: Modifier = Modifier,
    drawable: Int,
    iterations: Int = LottieConstants.IterateForever
) {
    val composition by rememberLottieComposition(LottieCompositionSpec.RawRes(drawable))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = iterations,
        isPlaying = true
    )
    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier,
    )
}
