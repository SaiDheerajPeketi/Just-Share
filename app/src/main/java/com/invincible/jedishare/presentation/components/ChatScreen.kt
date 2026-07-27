package com.invincible.jedishare.presentation.components

import timber.log.Timber

import android.content.ContentResolver
import android.net.Uri
import android.util.Log
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Send
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.invincible.jedishare.AnimatedPreloader
import com.invincible.jedishare.R
import com.invincible.jedishare.bytesToHumanReadableSize
import com.invincible.jedishare.classifyFileType
import com.invincible.jedishare.getFileDetailsFromUri
import com.invincible.jedishare.presentation.BluetoothUiState
import com.invincible.jedishare.presentation.BluetoothViewModel
import com.invincible.jedishare.presentation.TransferProgressState
import com.invincible.jedishare.ui.theme.MyRed
import com.invincible.jedishare.ui.theme.MyRedSecondary
import com.invincible.jedishare.ui.theme.MyRedSecondaryLight
import com.invincible.jedishare.ui.theme.MyRedSecondaryLight2
import com.invincible.jedishare.ui.theme.Roboto
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * Shows the active transfer progress for a list of files.
 *
 * Fix: now uses [TransferProgressState] from ViewModel instead of
 * the dual _state / _statee StateFlow anti-pattern.
 * The contentResolver is now passed from Activity, but only used for display metadata,
 * not for file I/O (which is handled by FileTransferRepository).
 */
@Composable
fun ChatScreen(
    state: BluetoothUiState? = null,
    onDisconnect: () -> Unit,
    onSendMessage: (String) -> Unit,
    uriList: List<Uri>? = null,
    viewModel: BluetoothViewModel? = null,
    contentResolver: ContentResolver? = null,
    isFromWifi: Boolean = false
) {
    val displayList = uriList ?: emptyList()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.background),
    ) {
        Column(verticalArrangement = Arrangement.Top) {
            Spacer(modifier = Modifier.size(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "Transfer Process",
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = Roboto,
                    fontSize = 24.sp
                )
            }

            Divider(
                color = Color.LightGray,
                thickness = 2.dp,
                modifier = Modifier.padding(top = 16.dp, start = 16.dp)
            )

            AnimatedPreloader(modifier = Modifier.size(400.dp), R.raw.file_transfer_animation)

            if (displayList.isNotEmpty() && contentResolver != null && viewModel != null) {
                DisplayFileswithProgressBar(
                    list = displayList,
                    contentResolver = contentResolver,
                    viewModel = viewModel
                )
            }
        }
    }

    // Disconnect / Send action row
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDisconnect) {
                Icon(imageVector = Icons.Default.Close, contentDescription = "Disconnect")
            }
        }
    // Auto-start transfer for Bluetooth when files are selected
    LaunchedEffect(uriList) {
        if (!isFromWifi && !uriList.isNullOrEmpty() && viewModel != null) {
            viewModel.setUriList(uriList)
            uriList.firstOrNull()?.let { onSendMessage(it.toString()) }
        }
    }
    }
}

@Composable
fun DisplayFileswithProgressBar(
    list: List<Uri>,
    contentResolver: ContentResolver,
    viewModel: BluetoothViewModel
) {
    Timber.d("Global - DisplayFileswithProgressBar called")
    Box(
        modifier = Modifier
            .padding(bottom = 20.dp, start = 20.dp, end = 20.dp)
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
                items(list) { item ->
                    val currFileCount by viewModel.currFileCount.collectAsStateWithLifecycle()
                    val index = list.indexOf(item)

                    Column(
                        modifier = Modifier
                            .padding(top = 8.dp, bottom = 8.dp, end = 8.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier
                                .wrapContentSize()
                                .fillMaxWidth()
                                .padding(top = 4.dp, bottom = 4.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Spacer(modifier = Modifier.size(10.dp))

                            val fileInfo = remember(item) {
                                getFileDetailsFromUri(item, contentResolver)
                            }
                            val fileType = fileInfo.format?.let { classifyFileType(it) }

                            val (icon, iconTint, iconBackground) = when (fileType) {
                                "Photo" -> Triple(
                                    painterResource(R.drawable.photo_icon),
                                    Color(0xFF33A850),
                                    Color(0x2233A850)
                                )
                                "Video" -> Triple(
                                    painterResource(R.drawable.video_icon),
                                    Color(0xFFC54EE6),
                                    Color(0x22C54EE6)
                                )
                                "Music" -> Triple(
                                    painterResource(R.drawable.music_icon),
                                    Color(0xFFFF0000),
                                    Color(0x22FF0000)
                                )
                                else -> Triple(
                                    painterResource(R.drawable.document_icon),
                                    Color(0xFF4187E6),
                                    Color(0x224187E6)
                                )
                            }

                            Row {
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

                                Column(
                                    modifier = Modifier.width(180.dp),
                                    horizontalAlignment = Alignment.Start,
                                    verticalArrangement = Arrangement.SpaceBetween
                                ) {
                                    var expanded by remember { mutableStateOf(false) }
                                    val interactionSource = remember { MutableInteractionSource() }

                                    fileInfo.fileName?.let { name ->
                                        Text(
                                            text = name,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .animateContentSize()
                                                .clickable(
                                                    indication = null,
                                                    interactionSource = interactionSource
                                                ) { expanded = !expanded },
                                            maxLines = if (expanded) 10 else 2
                                        )
                                        Text(
                                            text = fileInfo.size?.toDoubleOrNull()?.let {
                                                bytesToHumanReadableSize(it)
                                            } ?: "Unknown size",
                                            fontSize = 15.sp,
                                        )
                                    }
                                }

                                // Per-file status indicator
                                when {
                                    currFileCount > index -> {
                                        Spacer(modifier = Modifier.width(50.dp))
                                        AnimatedPreloader(
                                            modifier = Modifier.size(50.dp),
                                            R.raw.done_tick_animation,
                                            1
                                        )
                                    }
                                    currFileCount == index -> {
                                        val progress by viewModel.transferProgress.collectAsStateWithLifecycle()
                                        val iterCount by viewModel.getIterationCountFlow().collectAsState(0L)
                                        val fileSize by viewModel.fileInfoState.collectAsStateWithLifecycle()
                                        val receiverPct = if (fileSize > 0) (iterCount * 8192 * 100 / fileSize).toInt() else 0
                                        val sentPct = progress.sentPercent

                                        Spacer(modifier = Modifier.width(40.dp))
                                        Text(
                                            text = "${maxOf(receiverPct, sentPct).coerceIn(0, 100)}%",
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color.Black,
                                            fontFamily = Roboto,
                                            fontSize = 15.sp,
                                        )
                                    }
                                }
                            }

                        }

                        if (currFileCount == index) {
                            CustomProgressIndicator(viewModel = viewModel)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CustomProgressIndicator(
    width: Dp = 270.dp,
    height: Dp = 20.dp,
    checkedTrackColor: Color = Color.Black,
    uncheckedTrackColor: Color = MyRedSecondary,
    gapBetweenThumbAndTrackEdge: Dp = 5.dp,
    borderWidth: Dp = 3.dp,
    cornerSize: Int = 50,
    viewModel: BluetoothViewModel
) {
    val interactionSource = remember { MutableInteractionSource() }
    val progress by viewModel.transferProgress.collectAsStateWithLifecycle()
    val iterCount by viewModel.getIterationCountFlow().collectAsState(0L)
    val fileSize by viewModel.fileInfoState.collectAsStateWithLifecycle()
    val receiverPct = if (fileSize > 0) (iterCount * 8192 * 100 / fileSize).toInt() else 0
    val progressValue = maxOf(receiverPct, progress.sentPercent).coerceIn(0, 100)
    val indColor = if (progressValue >= 100) MyRed else Color.Black

    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .border(width = borderWidth, color = indColor, shape = RoundedCornerShape(percent = cornerSize)),
        contentAlignment = Alignment.Center
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .padding(start = gapBetweenThumbAndTrackEdge, end = gapBetweenThumbAndTrackEdge)
                    .background(MyRedSecondaryLight2)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                LinearProgressIndicator(
                    progress = progressValue / 100f,
                    modifier = Modifier
                        .height(15.dp)
                        .fillMaxWidth()
                        .padding(top = 2.dp, bottom = 2.dp)
                        .clip(RoundedCornerShape(percent = 50)),
                    color = indColor,
                    backgroundColor = MyRedSecondaryLight2,
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(5.dp))
}

@Composable
private fun animateAlignmentAsState(targetBiasValue: Float): State<BiasAlignment> {
    Timber.d("Global - animateAlignmentAsState called")
    val bias by animateFloatAsState(targetBiasValue)
    return derivedStateOf { BiasAlignment(horizontalBias = bias, verticalBias = 0f) }
}
