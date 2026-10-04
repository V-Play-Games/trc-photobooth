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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trc.photobooth.data.models.PhotoBoothTemplate
import com.trc.photobooth.theme.BgSurfaceElevated
import com.trc.photobooth.util.BitmapUtils
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.trc.photobooth.data.models.ConnectionStatus
import com.trc.photobooth.filters.FilterPresets
import com.trc.photobooth.theme.AmberGold
import com.trc.photobooth.theme.BgBase
import com.trc.photobooth.theme.BgCard
import com.trc.photobooth.theme.BgSurface
import com.trc.photobooth.theme.BgSurfaceElevated
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
    val selectedTemplate by viewModel.selectedTemplate.collectAsStateWithLifecycle()
    val collageBitmap by viewModel.collageBitmap.collectAsStateWithLifecycle()
    val blankCollageBitmap by viewModel.blankCollageBitmap.collectAsStateWithLifecycle()
    val qrCodeBitmap by viewModel.qrCodeBitmap.collectAsStateWithLifecycle()
    val blankQrCodeBitmap by viewModel.blankQrCodeBitmap.collectAsStateWithLifecycle()
    val cloudinaryUrl by viewModel.cloudinaryUrl.collectAsStateWithLifecycle()
    val blankCloudinaryUrl by viewModel.blankCloudinaryUrl.collectAsStateWithLifecycle()
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
    val isStreamPaused by viewModel.isStreamPaused.collectAsStateWithLifecycle()

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

        // Stream Paused Overlay for Idle State (Thermal management indicator)
        if (isStreamPaused && isIdle && connectionStatus == ConnectionStatus.CONNECTED) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = viewModel::toggleStreamPause),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(BgSurface.copy(alpha = 0.95f))
                        .border(1.dp, AmberGold.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                        .clickable(onClick = viewModel::toggleStreamPause)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.VideocamOff,
                        contentDescription = null,
                        tint = AmberGold,
                        modifier = Modifier.size(20.dp),
                    )
                    Column {
                        Text(
                            text = "CAMERA TURNED OFF",
                            color = AmberGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                        Text(
                            text = "Camera feed is turned off • Tap to turn ON",
                            color = TextMuted,
                            fontSize = 10.sp,
                        )
                    }
                }
            }
        }

        // Layer 3: Floating Top Island Pill: Only visible when NOT capturing
        AnimatedVisibility(
            visible = !isCapturing,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 8.dp),
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(BgSurface.copy(alpha = 0.95f))
                    .border(width = 1.dp, color = BorderSubtle, shape = RoundedCornerShape(22.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Exit / Back button (if idle)
                if (isIdle) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(BgSurfaceElevated)
                            .border(1.dp, BorderSubtle, CircleShape)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = TextMuted,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }

                // Brand Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = "TRC",
                        color = TextMain,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = "BOOTH",
                        color = NeonPink,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                // Status Pill
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            when {
                                isStreamPaused && connectionStatus == ConnectionStatus.CONNECTED -> AmberGold.copy(alpha = 0.15f)
                                connectionStatus == ConnectionStatus.CONNECTED -> EmeraldGreen.copy(alpha = 0.15f)
                                else -> NeonPink.copy(alpha = 0.15f)
                            }
                        )
                        .border(
                            1.dp,
                            when {
                                isStreamPaused && connectionStatus == ConnectionStatus.CONNECTED -> AmberGold.copy(alpha = 0.5f)
                                connectionStatus == ConnectionStatus.CONNECTED -> EmeraldGreen.copy(alpha = 0.5f)
                                else -> NeonPink.copy(alpha = 0.5f)
                            },
                            RoundedCornerShape(12.dp)
                        )
                        .clickable(enabled = connectionStatus != ConnectionStatus.CONNECTED) {
                            viewModel.reconnect()
                        }
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    isStreamPaused && connectionStatus == ConnectionStatus.CONNECTED -> AmberGold
                                    connectionStatus == ConnectionStatus.CONNECTED -> EmeraldGreen
                                    else -> NeonPink
                                }
                            ),
                    )
                    Text(
                        text = when {
                            isStreamPaused && connectionStatus == ConnectionStatus.CONNECTED -> "CAMERA OFF"
                            connectionStatus == ConnectionStatus.CONNECTED -> "READY"
                            else -> "OFFLINE"
                        },
                        color = when {
                            isStreamPaused && connectionStatus == ConnectionStatus.CONNECTED -> AmberGold
                            connectionStatus == ConnectionStatus.CONNECTED -> EmeraldGreen
                            else -> NeonPink
                        },
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                // Camera Turn On / Turn Off Button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isStreamPaused) AmberGold.copy(alpha = 0.15f) else EmeraldGreen.copy(alpha = 0.15f))
                        .border(1.dp, if (isStreamPaused) AmberGold else EmeraldGreen, RoundedCornerShape(8.dp))
                        .clickable { viewModel.toggleStreamPause() }
                        .padding(horizontal = 7.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = if (isStreamPaused) Icons.Default.VideocamOff else Icons.Default.Videocam,
                            contentDescription = if (isStreamPaused) "Turn Camera ON" else "Turn Camera OFF",
                            tint = if (isStreamPaused) AmberGold else EmeraldGreen,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = if (isStreamPaused) "CAM OFF" else "CAM ON",
                            color = if (isStreamPaused) AmberGold else EmeraldGreen,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }

                // Settings Button
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(BgSurfaceElevated)
                        .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                        .clickable { viewModel.openSettings() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Studio Settings",
                        tint = TextMuted,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }

        // Layer 4: Floating Controls
        when {
            isIdle -> {
                // Central 2x2 Timer Grid + Big Center START Button
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 60.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(26.dp))
                            .background(BgSurface.copy(alpha = 0.94f))
                            .border(1.5.dp, BorderMedium, RoundedCornerShape(26.dp))
                            .padding(20.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        // 2x2 Grid of Big Timer Buttons
                        Column(
                            verticalArrangement = Arrangement.spacedBy(26.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // Top Row: 1s (left) and 3s (right)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(36.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BoothTimerGridButton(
                                    seconds = 1,
                                    isSelected = timerSeconds == 1,
                                    onClick = { viewModel.setTimerSeconds(1) }
                                )
                                BoothTimerGridButton(
                                    seconds = 3,
                                    isSelected = timerSeconds == 3,
                                    onClick = { viewModel.setTimerSeconds(3) }
                                )
                            }

                            // Bottom Row: 5s (left) and 10s (right)
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(36.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                BoothTimerGridButton(
                                    seconds = 5,
                                    isSelected = timerSeconds == 5,
                                    onClick = { viewModel.setTimerSeconds(5) }
                                )
                                BoothTimerGridButton(
                                    seconds = 10,
                                    isSelected = timerSeconds == 10,
                                    onClick = { viewModel.setTimerSeconds(10) }
                                )
                            }
                        }

                        // Big START Button in the exact middle
                        val isReadyToStart = connectionStatus == ConnectionStatus.CONNECTED && !isStreamPaused
                        Box(
                            modifier = Modifier
                                .size(108.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isReadyToStart) {
                                        Brush.linearGradient(
                                            listOf(NeonPink, Color(0xFFE11D48), PurpleNeon)
                                        )
                                    } else if (connectionStatus == ConnectionStatus.CONNECTED && isStreamPaused) {
                                        Brush.linearGradient(
                                            listOf(AmberGold, Color(0xFFD97706), AmberGold)
                                        )
                                    } else {
                                        Brush.linearGradient(
                                            listOf(Color(0xFF94A3B8), Color(0xFF64748B))
                                        )
                                    }
                                )
                                .border(
                                    width = 3.dp,
                                    color = if (isReadyToStart) Color.White.copy(alpha = 0.9f) else if (isStreamPaused && connectionStatus == ConnectionStatus.CONNECTED) AmberGold else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable(
                                    enabled = isReadyToStart || (connectionStatus == ConnectionStatus.CONNECTED && isStreamPaused),
                                    onClick = {
                                        if (isStreamPaused) {
                                            viewModel.toggleStreamPause()
                                        } else {
                                            viewModel.startSession()
                                        }
                                    }
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Icon(
                                    imageVector = if (isStreamPaused) Icons.Default.VideocamOff else Icons.Default.CameraAlt,
                                    contentDescription = "Start",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp),
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isStreamPaused) "WAKE" else "START",
                                    color = Color.White,
                                    fontSize = if (isStreamPaused) 15.sp else 18.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.5.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }
                    }
                }

                // Filter Selection Strip docked at bottom
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(BgSurface.copy(alpha = 0.95f))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    BoothFilterStrip(
                        selectedFilter = selectedFilter,
                        onSelectFilter = viewModel::selectFilter,
                        enabled = true,
                    )
                }
            }

            isComplete -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .widthIn(max = 520.dp)
                            .clip(RoundedCornerShape(22.dp))
                            .background(BgSurface.copy(alpha = 0.98f))
                            .border(1.5.dp, BorderMedium, RoundedCornerShape(22.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Title bar with status and quick share
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = EmeraldGreen,
                                    modifier = Modifier.size(17.dp),
                                )
                                Text(
                                    text = "4-SHOT STRIP READY! 🎉",
                                    color = TextMain,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }

                            if (collageBitmap != null) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(BgCard)
                                        .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                                        .clickable {
                                            BitmapUtils.shareBitmap(
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
                                            imageVector = Icons.Default.Share,
                                            contentDescription = "Share",
                                            tint = CyberCyan,
                                            modifier = Modifier.size(12.dp),
                                        )
                                        Text(
                                            text = "SHARE",
                                            color = CyberCyan,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                }
                            }
                        }

                        // Template Selector: Take your pick!
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = CyberCyan,
                                    modifier = Modifier.size(13.dp),
                                )
                                Text(
                                    text = "CHOOSE YOUR FRAME TEMPLATE",
                                    color = TextMuted,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 0.8.sp,
                                )
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                PhotoBoothTemplate.ALL.forEach { tpl ->
                                    val isSelected = selectedTemplate == tpl
                                    val accentColor = Color(tpl.themeColorHex)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(if (isSelected) BgSurfaceElevated else BgCard)
                                            .border(
                                                width = if (isSelected) 2.dp else 1.dp,
                                                color = if (isSelected) CyberCyan else BorderSubtle,
                                                shape = RoundedCornerShape(10.dp)
                                            )
                                            .clickable { viewModel.selectTemplate(tpl) }
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(accentColor)
                                            )
                                            Column {
                                                Text(
                                                    text = tpl.title,
                                                    color = if (isSelected) Color(0xFF0F172A) else TextMain,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Black else FontWeight.Bold,
                                                )
                                                Text(
                                                    text = tpl.subtitle,
                                                    color = if (isSelected) CyberCyan else TextSubtle,
                                                    fontSize = 8.5.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.CheckCircle,
                                                    contentDescription = null,
                                                    tint = CyberCyan,
                                                    modifier = Modifier.size(13.dp),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Live Preview of the Photo Strip
                        if (collageBitmap != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(230.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF030509))
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(12.dp))
                                    .padding(6.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Image(
                                    bitmap = collageBitmap!!.asImageBitmap(),
                                    contentDescription = "Photo Strip Live Preview",
                                    modifier = Modifier.fillMaxHeight(),
                                    contentScale = ContentScale.Fit,
                                )
                            }
                        }

                        // Upload State & Dual QR Codes Section
                        when (val state = uploadState) {
                            is BoothUploadState.Idle -> {
                                // Confirmation area before uploading
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0x1400F0FF))
                                        .border(1.dp, CyberCyan.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                                        .padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    Text(
                                        text = "Preview your strip above. When you're happy with your pick, tap below to upload and generate your QR codes!",
                                        color = TextMuted,
                                        fontSize = 10.sp,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 14.sp,
                                    )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(42.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(CyberCyan, PurpleNeon, NeonPink)
                                                )
                                            )
                                            .clickable { viewModel.uploadCollages() },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CloudUpload,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(16.dp),
                                            )
                                            Text(
                                                text = "CONFIRM & UPLOAD (GET QR CODES)",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Black,
                                                fontFamily = FontFamily.Monospace,
                                                letterSpacing = 0.8.sp,
                                            )
                                        }
                                    }
                                }
                            }

                            is BoothUploadState.Generating, is BoothUploadState.Uploading -> {
                                val msg = if (state is BoothUploadState.Generating) state.message else (state as BoothUploadState.Uploading).message
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0x1A00F0FF))
                                        .border(1.dp, CyberCyan.copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(28.dp),
                                        color = CyberCyan,
                                        strokeWidth = 2.5.dp,
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = msg,
                                            color = CyberCyan,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                        Text(
                                            text = "Uploading themed and blank template strips...",
                                            color = TextMuted,
                                            fontSize = 9.5.sp,
                                        )
                                    }
                                }
                            }

                            is BoothUploadState.Success -> {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CloudDone,
                                            contentDescription = null,
                                            tint = EmeraldGreen,
                                            modifier = Modifier.size(15.dp),
                                        )
                                        Text(
                                            text = "SCAN QR CODES TO DOWNLOAD 📱",
                                            color = EmeraldGreen,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace,
                                            letterSpacing = 0.8.sp,
                                        )
                                    }

                                    // Dual QR Cards
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        // Card 1: Themed Strip QR Code
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(Color(0x1F00E599))
                                                .border(1.dp, EmeraldGreen.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                                                .padding(8.dp),
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(5.dp),
                                            ) {
                                                Text(
                                                    text = "${selectedTemplate.title.uppercase()} STRIP",
                                                    color = EmeraldGreen,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Black,
                                                    fontFamily = FontFamily.Monospace,
                                                    maxLines = 1,
                                                )

                                                if (qrCodeBitmap != null) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(112.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(Color.White)
                                                            .padding(4.dp),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        Image(
                                                            bitmap = qrCodeBitmap!!.asImageBitmap(),
                                                            contentDescription = "Themed Strip QR Code",
                                                            modifier = Modifier.fillMaxSize(),
                                                            contentScale = ContentScale.Fit,
                                                        )
                                                    }
                                                }

                                                Text(
                                                    text = "Themed Frame",
                                                    color = TextMuted,
                                                    fontSize = 8.5.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                )
                                            }
                                        }

                                        // Card 2: Blank Template Strip QR Code
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(14.dp))
                                                .background(Color(0x1A00F0FF))
                                                .border(1.dp, CyberCyan.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                                                .padding(8.dp),
                                        ) {
                                            Column(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalAlignment = Alignment.CenterHorizontally,
                                                verticalArrangement = Arrangement.spacedBy(5.dp),
                                            ) {
                                                Text(
                                                    text = "CLASSIC / BLANK",
                                                    color = CyberCyan,
                                                    fontSize = 9.5.sp,
                                                    fontWeight = FontWeight.Black,
                                                    fontFamily = FontFamily.Monospace,
                                                    maxLines = 1,
                                                )

                                                if (blankQrCodeBitmap != null) {
                                                    Box(
                                                        modifier = Modifier
                                                            .size(112.dp)
                                                            .clip(RoundedCornerShape(8.dp))
                                                            .background(Color.White)
                                                            .padding(4.dp),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        Image(
                                                            bitmap = blankQrCodeBitmap!!.asImageBitmap(),
                                                            contentDescription = "Blank Template Strip QR Code",
                                                            modifier = Modifier.fillMaxSize(),
                                                            contentScale = ContentScale.Fit,
                                                        )
                                                    }
                                                }

                                                Text(
                                                    text = "Clean Template",
                                                    color = TextMuted,
                                                    fontSize = 8.5.sp,
                                                    fontFamily = FontFamily.Monospace,
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            is BoothUploadState.Error -> {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(Color(0x1AFF3366))
                                        .border(1.dp, NeonPink.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                                        .padding(10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Upload failed",
                                            color = NeonPink,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                        Text(
                                            text = state.error,
                                            color = TextSubtle,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace,
                                            maxLines = 2,
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(BgCard)
                                            .border(1.dp, NeonPink, RoundedCornerShape(6.dp))
                                            .clickable { viewModel.retryUpload() }
                                            .padding(horizontal = 8.dp, vertical = 5.dp),
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
                                                modifier = Modifier.size(12.dp),
                                            )
                                            Text(
                                                text = "RETRY",
                                                color = NeonPink,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                fontFamily = FontFamily.Monospace,
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Print Buttons: Print (Pi) + Print (App) [Hidden for now as requested]
                        val showPrintButtons = false
                        if (showPrintButtons && collageBitmap != null) {
                            val isPrinting = printState is PrintState.Printing
                            val isSuccess = printState is PrintState.Success
                            val isError = printState is PrintState.Error

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                // Print via Pi Button (CUPS)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                when {
                                                    isError -> listOf(NeonPink, Color(0xFFE11D48))
                                                    isSuccess -> listOf(Color(0xFF00C853), EmeraldGreen)
                                                    else -> listOf(EmeraldGreen, Color(0xFF00C853))
                                                }
                                            )
                                        )
                                        .clickable(enabled = !isPrinting) {
                                            viewModel.printCollage()
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        if (isPrinting) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(13.dp),
                                                strokeWidth = 1.6.dp,
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
                                                modifier = Modifier.size(14.dp),
                                            )
                                        }
                                        Text(
                                            text = when {
                                                isPrinting -> "PRINTING..."
                                                isSuccess -> "PRINTED (PI)"
                                                isError -> "RETRY (PI)"
                                                else -> "PRINT (PI)"
                                            },
                                            color = Color(0xFF070B14),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                }

                                // Native Android System Print Button (Print via App)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
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
                                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Print,
                                            contentDescription = null,
                                            tint = Color(0xFF070B14),
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Text(
                                            text = "PRINT (APP)",
                                            color = Color(0xFF070B14),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Black,
                                            fontFamily = FontFamily.Monospace,
                                        )
                                    }
                                }
                            }
                        }

                        // Big Start New Session Button
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(42.dp)
                                .clip(RoundedCornerShape(10.dp))
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
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp),
                                )
                                Text(
                                    text = "START NEW SESSION",
                                    color = Color.White,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
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
                        .background(BgSurface.copy(alpha = 0.95f))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(20.dp))
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

@Composable
private fun BoothTimerGridButton(
    seconds: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(76.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                if (isSelected) {
                    Brush.verticalGradient(
                        listOf(CyberCyan, Color(0xFF0099FF))
                    )
                } else {
                    Brush.verticalGradient(
                        listOf(BgCard, BgSurfaceElevated)
                    )
                }
            )
            .border(
                width = if (isSelected) 2.dp else 1.5.dp,
                color = if (isSelected) CyberCyan else BorderSubtle,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "${seconds}s",
                color = if (isSelected) Color.White else TextMain,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = "TIMER",
                color = if (isSelected) Color.White.copy(alpha = 0.85f) else TextMuted,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 0.8.sp,
            )
        }
    }
}

