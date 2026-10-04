package com.trc.photobooth.ui.booth

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trc.photobooth.util.BitmapUtils
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trc.photobooth.data.models.ConnectionStatus
import com.trc.photobooth.filters.FilterPresets
import com.trc.photobooth.theme.AmberGold
import com.trc.photobooth.theme.BgBase
import com.trc.photobooth.theme.BgCard
import com.trc.photobooth.theme.BorderMedium
import com.trc.photobooth.theme.BorderSubtle
import com.trc.photobooth.theme.CyberCyan
import com.trc.photobooth.theme.EmeraldGreen
import com.trc.photobooth.theme.NeonPink
import com.trc.photobooth.theme.NeonPinkHover
import com.trc.photobooth.theme.PurpleNeon
import com.trc.photobooth.theme.TextMain
import com.trc.photobooth.theme.TextMuted
import com.trc.photobooth.theme.TextSubtle
import com.trc.photobooth.ui.components.BoothCountdownOverlay
import com.trc.photobooth.ui.components.BoothFilterStrip
import com.trc.photobooth.ui.components.QuadrantGrid
import com.trc.photobooth.ui.components.SettingsDialog
import kotlinx.coroutines.flow.collectLatest

/**
 * Dedicated 4-Quadrant Photo Booth UI.
 *
 * Sequence:
 * 1. IDLE: Live feed appears in Quadrant 1 (top-left). User selects filter style (including RANDOM) and timer.
 * 2. CAPTURING: Once START is tapped, pictures are clicked automatically without intervention.
 *    Quadrant 1 -> Quadrant 2 -> Quadrant 3 -> Quadrant 4.
 *    UNSTOPPABLE: Cannot be reversed or stopped once initiated.
 * 3. COMPLETE: All 4 images saved to Pictures/TRCPhotoBooth/sessions/<timestamp>/
 *    Ready for Cloudinary upload.
 */
@Composable
fun BoothScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: BoothScreenViewModel = viewModel(),
) {
    val context = LocalContext.current

    val boothState by viewModel.boothState.collectAsStateWithLifecycle()
    val activeQuadrant by viewModel.activeQuadrant.collectAsStateWithLifecycle()
    val capturedPhotos by viewModel.capturedPhotos.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val currentFeedFilter by viewModel.currentFeedFilter.collectAsStateWithLifecycle()
    val resolvedFilters by viewModel.resolvedFilters.collectAsStateWithLifecycle()
    val timerSeconds by viewModel.timerSeconds.collectAsStateWithLifecycle()
    val currentCountdown by viewModel.currentCountdown.collectAsStateWithLifecycle()
    val savedSessionFiles by viewModel.savedSessionFiles.collectAsStateWithLifecycle()
    val sessionTimestamp by viewModel.sessionTimestamp.collectAsStateWithLifecycle()

    val uploadState by viewModel.uploadState.collectAsStateWithLifecycle()
    val collageBitmap by viewModel.collageBitmap.collectAsStateWithLifecycle()
    val qrCodeBitmap by viewModel.qrCodeBitmap.collectAsStateWithLifecycle()
    val cloudinaryUrl by viewModel.cloudinaryUrl.collectAsStateWithLifecycle()
    val printState by viewModel.printState.collectAsStateWithLifecycle()

    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val lastFrame by viewModel.lastFrame.collectAsStateWithLifecycle()
    val isSettingsOpen by viewModel.isSettingsOpen.collectAsStateWithLifecycle()
    val cameraConfig by viewModel.cameraConfig.collectAsStateWithLifecycle()
    val systemStats by viewModel.systemStats.collectAsStateWithLifecycle()
    val hostAddress by viewModel.hostAddress.collectAsStateWithLifecycle()
    val printerName by viewModel.printerName.collectAsStateWithLifecycle()
    val printerColorMode by viewModel.printerColorMode.collectAsStateWithLifecycle()
    val printerCopies by viewModel.printerCopies.collectAsStateWithLifecycle()

    val isCapturing = boothState == BoothState.CAPTURING
    val isComplete = boothState == BoothState.COMPLETE
    val isIdle = boothState == BoothState.IDLE

    // Trap Android system back button when sequence is running: cannot stop or reverse
    BackHandler(enabled = isCapturing) {
        // Explicitly no-op: unstoppable capture sequence
    }

    // Toast listener
    LaunchedEffect(Unit) {
        viewModel.toastMessage.collectLatest { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(BgBase),
    ) {
        // Layer 1: Full-Screen Camera Quadrant Grid (takes the full screen)
        QuadrantGrid(
            capturedPhotos = capturedPhotos,
            activeQuadrant = activeQuadrant,
            lastFrameBitmap = lastFrame,
            activeFilter = currentFeedFilter,
            resolvedFilters = resolvedFilters,
            modifier = Modifier.fillMaxSize(),
        )

        // Layer 2: Countdown & Strobe Flash Overlay (during sequence)
        if (isCapturing) {
            BoothCountdownOverlay(
                photoIndex = activeQuadrant,
                countdownSeconds = currentCountdown,
                flashEvent = viewModel.flashEvent,
                isComplete = isComplete,
                activeFilterName = currentFeedFilter.name,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // Layer 3: Floating Top App Bar / Header: Only visible when NOT capturing
        AnimatedVisibility(
            visible = !isCapturing,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            Row(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xD90B0F17))
                    .border(width = 1.dp, color = BorderSubtle, shape = RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(Color(0x33FF3366))
                            .border(1.dp, NeonPink.copy(alpha = 0.6f), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = NeonPink,
                            modifier = Modifier.size(18.dp),
                        )
                    }

                    Column {
                        Text(
                            text = "TRC PHOTO BOOTH",
                            color = TextMain,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 1.4.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            text = if (isComplete) "SESSION COMPLETE" else "4-SHOT BOOTH EXPERIENCE",
                            color = if (isComplete) EmeraldGreen else CyberCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }

                // Right Actions: Status Pill & Studio Settings
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // Status Pill
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (connectionStatus == ConnectionStatus.CONNECTED) Color(0x1A00E599)
                                else Color(0x1AFF3366)
                            )
                            .border(
                                1.dp,
                                if (connectionStatus == ConnectionStatus.CONNECTED) EmeraldGreen.copy(alpha = 0.5f)
                                else NeonPink.copy(alpha = 0.5f),
                                RoundedCornerShape(12.dp)
                            )
                            .clickable(enabled = connectionStatus != ConnectionStatus.CONNECTED) {
                                viewModel.reconnect()
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(
                                    if (connectionStatus == ConnectionStatus.CONNECTED) EmeraldGreen
                                    else NeonPink
                                ),
                        )
                        Text(
                            text = if (connectionStatus == ConnectionStatus.CONNECTED) "READY" else "OFFLINE",
                            color = if (connectionStatus == ConnectionStatus.CONNECTED) EmeraldGreen else NeonPink,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }

                    // Settings Button
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0x2600F0FF))
                            .border(1.dp, CyberCyan.copy(alpha = 0.5f), CircleShape)
                            .clickable { viewModel.openSettings() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Studio & Printer Settings",
                            tint = CyberCyan,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }

        // Layer 4: Floating Bottom Controls
        when {
            isIdle -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xD9070B12))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    // Filter Selection Strip (Includes RANDOM)
                    BoothFilterStrip(
                        selectedFilter = selectedFilter,
                        onSelectFilter = viewModel::selectFilter,
                        enabled = true,
                    )

                    // Timer Options & Start Button Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Timer Selector Pills
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(BgCard)
                                .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = TextMuted,
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(14.dp),
                            )

                            listOf(3, 5, 10).forEach { seconds ->
                                val isSelected = timerSeconds == seconds
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (isSelected) CyberCyan.copy(alpha = 0.25f)
                                            else Color.Transparent
                                        )
                                        .border(
                                            width = if (isSelected) 1.dp else 0.dp,
                                            color = if (isSelected) CyberCyan else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp),
                                        )
                                        .clickable { viewModel.setTimerSeconds(seconds) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "${seconds}s",
                                        color = if (isSelected) CyberCyan else TextMuted,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                }
                            }
                        }

                        // Big Start Button
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(NeonPink, Color(0xFFE11D48), PurpleNeon)
                                    )
                                )
                                .clickable(
                                    enabled = connectionStatus == ConnectionStatus.CONNECTED,
                                    onClick = viewModel::startSession,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp),
                                )
                                Text(
                                    text = "START BOOTH",
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.2.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }
            }

            isComplete -> {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(Color(0xF0070B14))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(22.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Header Bar: Status + Local Share
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldGreen,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "4-SHOT PHOTO STRIP READY! 🎉",
                                color = TextMain,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                fontFamily = FontFamily.Monospace,
                            )
                        }

                        if (collageBitmap != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                // Print via App Button (System Print Dialog)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(BgCard)
                                        .border(1.dp, CyberCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .clickable {
                                            BitmapUtils.printBitmap(
                                                context,
                                                collageBitmap!!,
                                                "TRC Photo Booth Photo Strip"
                                            )
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Print,
                                            contentDescription = "Print via App",
                                            tint = CyberCyan,
                                            modifier = Modifier.size(13.dp),
                                        )
                                        Text(
                                            text = "PRINT (APP)",
                                            color = CyberCyan,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                }

                                // Print via Pi Button (CUPS)
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(BgCard)
                                        .border(1.dp, EmeraldGreen.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                        .clickable {
                                            viewModel.printCollage()
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        if (printState is PrintState.Printing) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(11.dp),
                                                strokeWidth = 1.5.dp,
                                                color = EmeraldGreen,
                                            )
                                        } else {
                                            Icon(
                                                imageVector = Icons.Default.Print,
                                                contentDescription = "Print via Pi",
                                                tint = EmeraldGreen,
                                                modifier = Modifier.size(13.dp),
                                            )
                                        }
                                        Text(
                                            text = when (printState) {
                                                is PrintState.Printing -> "PRINTING..."
                                                is PrintState.Success -> "PRINTED"
                                                is PrintState.Error -> "RETRY"
                                                PrintState.Idle -> "PRINT (PI)"
                                            },
                                            color = EmeraldGreen,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(BgCard)
                                        .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                                        .clickable {
                                            BitmapUtils.shareBitmap(
                                                context,
                                                collageBitmap!!,
                                                "TRC Photo Booth Collage"
                                            )
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share",
                                            tint = CyberCyan,
                                            modifier = Modifier.size(13.dp),
                                        )
                                        Text(
                                            text = "SHARE",
                                            color = CyberCyan,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Main Content: Uploading Progress OR Cloudinary QR Code OR Error/Retry
                    when (val state = uploadState) {
                        is BoothUploadState.Generating, is BoothUploadState.Uploading -> {
                            val msg = if (state is BoothUploadState.Generating) state.message else (state as BoothUploadState.Uploading).message
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0x1A00F0FF))
                                    .border(1.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(32.dp),
                                    color = CyberCyan,
                                    strokeWidth = 3.dp,
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = msg,
                                        color = CyberCyan,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        fontFamily = FontFamily.Monospace,
                                    )
                                    Text(
                                        text = "Uploading 1×4 photo strip to Cloudinary...",
                                        color = TextMuted,
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                        }

                        is BoothUploadState.Success -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0x1F00E599))
                                    .border(1.dp, EmeraldGreen.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                // White rounded box for QR Code
                                if (qrCodeBitmap != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(118.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color.White)
                                            .padding(6.dp),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Image(
                                            bitmap = qrCodeBitmap!!.asImageBitmap(),
                                            contentDescription = "QR Code to download photo strip",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit,
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudDone,
                                            contentDescription = null,
                                            tint = EmeraldGreen,
                                            modifier = Modifier.size(15.dp),
                                        )
                                        Text(
                                            text = "SCAN FOR PHOTOS",
                                            color = EmeraldGreen,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 1.sp,
                                        )
                                    }

                                    Text(
                                        text = "Scan QR code with your phone to view and download your 1×4 photo strip.",
                                        color = TextMain,
                                        fontSize = 11.sp,
                                        lineHeight = 15.sp,
                                    )

                                    if (cloudinaryUrl != null) {
                                        Text(
                                            text = cloudinaryUrl!!,
                                            color = CyberCyan,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 1,
                                        )
                                    }
                                }

                                // 1x4 Photo Strip Thumbnail
                                if (collageBitmap != null) {
                                    Box(
                                        modifier = Modifier
                                            .size(width = 44.dp, height = 124.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                                            .clickable {
                                                viewModel.printCollage()
                                            },
                                    ) {
                                        Image(
                                            bitmap = collageBitmap!!.asImageBitmap(),
                                            contentDescription = "1x4 Photo Strip Preview",
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Fit,
                                        )
                                    }
                                }
                            }
                        }

                        is BoothUploadState.Error -> {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0x1AFF3366))
                                    .border(1.dp, NeonPink.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Saved locally to Gallery! 🎉",
                                        color = EmeraldGreen,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                    )
                                    Text(
                                        text = state.error,
                                        color = TextSubtle,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        maxLines = 2,
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(BgCard)
                                        .border(1.dp, NeonPink, RoundedCornerShape(8.dp))
                                        .clickable { viewModel.retryUpload() }
                                        .padding(horizontal = 10.dp, vertical = 6.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Refresh,
                                            contentDescription = "Retry",
                                            tint = NeonPink,
                                            modifier = Modifier.size(13.dp),
                                        )
                                        Text(
                                            text = "RETRY",
                                            color = NeonPink,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                }
                            }
                        }

                        is BoothUploadState.Idle -> {
                            // No-op
                        }
                    }

                    // Print from Printer Button (displayed as soon as collage is ready)
                    if (collageBitmap != null) {
                        val isPrinting = printState is PrintState.Printing
                        val isSuccess = printState is PrintState.Success
                        val isError = printState is PrintState.Error

                        val gradientColors = when {
                            isError -> listOf(NeonPink, Color(0xFFE11D48))
                            isSuccess -> listOf(Color(0xFF00C853), EmeraldGreen)
                            else -> listOf(EmeraldGreen, Color(0xFF00C853))
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Brush.horizontalGradient(gradientColors))
                                .clickable(enabled = !isPrinting) {
                                    viewModel.printCollage()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (isPrinting) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF070B14),
                                    )
                                } else {
                                    Icon(
                                        imageVector = when {
                                            isSuccess -> Icons.Default.CheckCircle
                                            isError -> Icons.Default.Refresh
                                            else -> Icons.Default.Print
                                        },
                                        contentDescription = null,
                                        tint = Color(0xFF070B14),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                                Text(
                                    text = when {
                                        isPrinting -> "SENDING TO TRC_PRINTER..."
                                        isSuccess -> "PRINTED TO TRC_PRINTER (TAP TO PRINT AGAIN)"
                                        isError -> "RETRY PRINT VIA PI PRINTER"
                                        else -> "PRINT FROM PRINTER (PI)"
                                    },
                                    color = Color(0xFF070B14),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.8.sp,
                                )
                            }
                        }

                        // Native Android System Print Button (Print via App)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(CyberCyan, Color(0xFF0099FF))
                                    )
                                )
                                .clickable {
                                    BitmapUtils.printBitmap(
                                        context,
                                        collageBitmap!!,
                                        "TRC Photo Booth Photo Strip"
                                    )
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Print,
                                    contentDescription = null,
                                    tint = Color(0xFF070B14),
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = "PRINT VIA APP (SYSTEM DIALOG)",
                                    color = Color(0xFF070B14),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.8.sp,
                                )
                            }
                        }
                    }

                    // Big Full-Width Start New Session Button (NO Return to Admin button)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                Brush.horizontalGradient(
                                    listOf(NeonPink, Color(0xFFE11D48), PurpleNeon)
                                )
                            )
                            .clickable(onClick = viewModel::resetSession),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.Replay,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp),
                            )
                            Text(
                                text = "START NEW SESSION",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }
            }

            isCapturing -> {
                // During capture: sequence in progress label
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(bottom = 16.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xD9070B12))
                        .border(1.dp, NeonPink.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(NeonPink),
                        )
                        Text(
                            text = "PHOTO ${activeQuadrant + 1} OF 4 • SEQUENCE IN PROGRESS",
                            color = NeonPink,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.2.sp,
                        )
                    }
                }
            }
        }

        // Layer 5: Studio & Printer Settings Dialog
        SettingsDialog(
            isOpen = isSettingsOpen,
            currentHost = hostAddress,
            connectionStatus = connectionStatus,
            cameraConfig = cameraConfig,
            systemStats = systemStats,
            onSaveHost = viewModel::setHost,
            onStartNsdSearch = viewModel::startNsdSearch,
            onToggleFlip = viewModel::toggleFlip,
            onToggleSwapRb = viewModel::toggleSwapRb,
            onSetFps = viewModel::setFps,
            onSetQuality = viewModel::setQuality,
            onSetResolution = viewModel::setResolution,
            onRefreshStats = viewModel::refreshStats,
            onSetCameraDevice = viewModel::setCameraDevice,
            onRefreshDevices = viewModel::refreshDevices,
            currentPrinterName = printerName,
            currentColorMode = printerColorMode,
            currentCopies = printerCopies,
            onSavePrinterSettings = viewModel::updatePrinterSettings,
            onClose = viewModel::closeSettings,
        )
    }
}
