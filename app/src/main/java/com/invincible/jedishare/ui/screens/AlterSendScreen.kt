package com.invincible.jedishare.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Shield
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.invincible.jedishare.domain.altersend.AlterSendConnectionPhase
import com.invincible.jedishare.domain.altersend.AlterSendFileOffer
import com.invincible.jedishare.domain.altersend.AlterSendRole
import com.invincible.jedishare.presentation.AlterSendViewModel
import com.invincible.jedishare.ui.components.BackBar
import com.invincible.jedishare.ui.components.EncryptedBadge
import com.invincible.jedishare.ui.components.MimeTypeIcon
import com.invincible.jedishare.ui.components.PillButton
import com.invincible.jedishare.ui.components.PillButtonSize
import com.invincible.jedishare.ui.components.PillButtonVariant
import com.invincible.jedishare.ui.theme.JediShareTheme
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScanner
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.delay

@Composable
fun AlterSendScreen(
    onBack: () -> Unit,
    onNavigateHome: () -> Unit,
    onSelectFiles: () -> Unit,
    viewModel: AlterSendViewModel = hiltViewModel()
) {
    val colors = JediShareTheme.colors
    val state by viewModel.state.collectAsStateWithLifecycle()
    var joinCode by remember { mutableStateOf("") }
    var scanError by remember { mutableStateOf<String?>(null) }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val scannerOptions = remember {
        GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
            .build()
    }
    val qrScanner = remember(context, scannerOptions) {
        GmsBarcodeScanning.getClient(context, scannerOptions)
    }
    val startQrScan = {
        scanError = null
        qrScanner.startQrScan(
            onCode = { code ->
                joinCode = code
                viewModel.join(code)
            },
            onError = { message -> scanError = message }
        )
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            startQrScan()
        } else {
            scanError = "Camera permission is required to scan a Remote Transfer QR code."
        }
    }
    val leaveScreen = {
        viewModel.reset()
        onBack()
    }

    BackHandler(onBack = leaveScreen)

    LaunchedEffect(state.phase) {
        if (state.phase == AlterSendConnectionPhase.Complete) {
            delay(1500)
            viewModel.reset()
            onNavigateHome()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.surface)
    ) {
        BackBar(title = "Remote Transfer", onBack = leaveScreen)

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            AlterSendHeader()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.cardBg)
                    .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                    .padding(16.dp)
            ) {
                Text("SEND", style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = colors.mutedFg)
                Spacer(modifier = Modifier.height(12.dp))
                PillButton(
                    label = "Choose Files",
                    onClick = onSelectFiles,
                    size = PillButtonSize.LG,
                    modifier = Modifier.fillMaxWidth()
                )

                state.topicHex?.let { topic ->
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Connection Code", style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.SemiBold), color = colors.black)
                    Spacer(modifier = Modifier.height(8.dp))
                    SelectionContainer {
                        Text(
                            topic,
                            style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.Bold),
                            color = colors.red
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    PillButton(
                        label = "Copy Code",
                        onClick = { clipboard.setText(AnnotatedString(topic)) },
                        variant = PillButtonVariant.OUTLINE,
                        size = PillButtonSize.MD,
                        modifier = Modifier.fillMaxWidth(),
                        icon = {
                            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                    if (topic.startsWith("JSAS")) {
                        Spacer(modifier = Modifier.height(16.dp))
                        AlterSendQrCode(content = topic, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }

                if (state.role != AlterSendRole.Receiver) state.offers.forEach { offer ->
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        MimeTypeIcon(mimeType = offer.mimeType, modifier = Modifier.size(40.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                offer.name,
                                style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.SemiBold),
                                color = colors.black,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(formatSize(offer.sizeBytes), style = MaterialTheme.typography.caption, color = colors.mutedFg)
                        }
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .background(colors.cardBg)
                    .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                    .padding(16.dp)
            ) {
                Text("RECEIVE", style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp), color = colors.mutedFg)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = joinCode,
                    onValueChange = { joinCode = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Connection code") }
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    PillButton(
                        label = "Scan QR",
                        onClick = {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                startQrScan()
                            } else {
                                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        variant = PillButtonVariant.OUTLINE,
                        size = PillButtonSize.MD,
                        modifier = Modifier.weight(1f),
                        icon = {
                            Icon(Icons.Default.QrCode, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                    )
                    PillButton(
                        label = "Join",
                        onClick = {
                            viewModel.join(joinCode.filterNot { it.isWhitespace() })
                        },
                        size = PillButtonSize.MD,
                        modifier = Modifier.weight(1f)
                    )
                }
                scanError?.let { message ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(message, color = colors.red, style = MaterialTheme.typography.caption)
                }
            }

            if (state.phase == AlterSendConnectionPhase.IncomingOffer && state.role == AlterSendRole.Receiver) {
                IncomingOfferCard(
                    offers = state.offers,
                    onAccept = viewModel::acceptIncomingTransfer,
                    onReject = viewModel::rejectIncomingTransfer
                )
            }

            state.progress?.let { progress ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(colors.cardBg)
                        .border(1.dp, colors.border, RoundedCornerShape(24.dp))
                        .padding(16.dp)
                ) {
                    Text(
                        progress.fileName,
                        style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    androidx.compose.material.LinearProgressIndicator(
                        progress = progress.percent / 100f,
                        color = colors.red,
                        backgroundColor = colors.border,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "${progress.percent.toInt()}% · ${formatSize(progress.bytesTransferred)} of ${formatSize(progress.totalBytes)}",
                        style = MaterialTheme.typography.caption,
                        color = colors.mutedFg
                    )
                }
            }

            Text(
                text = when (state.phase) {
                    AlterSendConnectionPhase.Idle -> "Ready"
                    AlterSendConnectionPhase.Hosting -> "Waiting for receiver..."
                    AlterSendConnectionPhase.Joining -> "Joining..."
                    AlterSendConnectionPhase.Connecting -> "Connecting..."
                    AlterSendConnectionPhase.Connected -> "Connected"
                    AlterSendConnectionPhase.IncomingOffer -> "Receiving offer..."
                    AlterSendConnectionPhase.Transferring -> "Transferring..."
                    AlterSendConnectionPhase.Complete -> "Transfer complete"
                    AlterSendConnectionPhase.Cancelled -> "Cancelled"
                    AlterSendConnectionPhase.Failed -> "Transfer failed"
                },
                style = MaterialTheme.typography.body2,
                color = colors.mutedFg,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            if (state.phase == AlterSendConnectionPhase.Failed && state.errorMessage != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.red.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                        .padding(16.dp)
                ) {
                    Text(state.errorMessage ?: "", color = colors.red, style = MaterialTheme.typography.body2)
                }
            }
        }
    }
}

@Composable
private fun AlterSendQrCode(content: String, modifier: Modifier = Modifier) {
    val matrix = remember(content) {
        runCatching {
            QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 256, 256)
        }.getOrNull()
    }
    Box(
        modifier = modifier
            .size(176.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Color.White)
            .padding(12.dp),
        contentAlignment = Alignment.Center
    ) {
        if (matrix != null) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val cellSize = minOf(size.width / matrix.width, size.height / matrix.height)
                val left = (size.width - cellSize * matrix.width) / 2f
                val top = (size.height - cellSize * matrix.height) / 2f
                for (x in 0 until matrix.width) {
                    for (y in 0 until matrix.height) {
                        if (matrix[x, y]) {
                            drawRect(
                                color = Color.Black,
                                topLeft = Offset(left + x * cellSize, top + y * cellSize),
                                size = Size(cellSize, cellSize)
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun Task<com.google.mlkit.vision.barcode.common.Barcode>.startCallbacks(
    onCode: (String) -> Unit,
    onError: (String) -> Unit
) {
    addOnSuccessListener { barcode ->
        val code = barcode.rawValue?.filterNot { it.isWhitespace() }.orEmpty()
        if (code.isBlank()) {
            onError("QR code did not contain a Remote Transfer code.")
        } else {
            onCode(code)
        }
    }.addOnFailureListener { error ->
        val message = when ((error as? com.google.android.gms.common.api.ApiException)?.statusCode) {
            CommonStatusCodes.CANCELED -> "QR scan cancelled."
            else -> error.localizedMessage ?: "Could not scan QR code."
        }
        onError(message)
    }
}

private fun GmsBarcodeScanner.startQrScan(
    onCode: (String) -> Unit,
    onError: (String) -> Unit
) {
    startScan().startCallbacks(onCode, onError)
}

@Composable
private fun IncomingOfferCard(
    offers: List<AlterSendFileOffer>,
    onAccept: () -> Unit,
    onReject: () -> Unit
) {
    val colors = JediShareTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.cardBg)
            .border(1.dp, colors.border, RoundedCornerShape(24.dp))
            .padding(16.dp)
    ) {
        Text(
            "INCOMING TRANSFER",
            style = MaterialTheme.typography.caption.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = colors.mutedFg
        )
        Spacer(modifier = Modifier.height(12.dp))
        offers.forEach { offer ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MimeTypeIcon(mimeType = offer.mimeType, modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        offer.name,
                        style = MaterialTheme.typography.body2.copy(fontWeight = FontWeight.SemiBold),
                        color = colors.black,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(formatSize(offer.sizeBytes), style = MaterialTheme.typography.caption, color = colors.mutedFg)
                }
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
            PillButton(
                label = "Cancel",
                onClick = onReject,
                variant = PillButtonVariant.OUTLINE,
                size = PillButtonSize.MD,
                modifier = Modifier.weight(1f)
            )
            PillButton(
                label = "Accept",
                onClick = onAccept,
                size = PillButtonSize.MD,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun AlterSendHeader() {
    val colors = JediShareTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(colors.cardBg)
            .border(1.dp, colors.border, RoundedCornerShape(24.dp))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .background(colors.red.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Default.Shield, contentDescription = null, tint = colors.red, modifier = Modifier.size(32.dp))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            "Remote Transfer",
            style = MaterialTheme.typography.h2.copy(fontWeight = FontWeight.Bold, fontSize = 24.sp),
            color = colors.black
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            "Peer-to-peer encrypted transfer over the internet or local network.",
            style = MaterialTheme.typography.body2,
            color = colors.mutedFg,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        EncryptedBadge()
    }
}
