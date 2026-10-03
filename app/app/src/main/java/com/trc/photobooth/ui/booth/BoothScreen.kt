package com.trc.photobooth.ui.booth

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
    val lastFrame by viewModel.lastFrame.collectAsStateWithLifecycle()

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
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Top App Bar / Header: Only visible when NOT capturing
            AnimatedVisibility(
                visible = !isCapturing,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .background(Color(0xE60B0F17))
                        .border(width = 1.dp, color = BorderSubtle)
                        .statusBarsPadding()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        IconButton(
                            onClick = onBack,
                            enabled = !isCapturing,
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back to Admin",
                                tint = TextMain,
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
                }
            }

            // Quadrant Grid (2x2) taking main screen area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                QuadrantGrid(
                    capturedPhotos = capturedPhotos,
                    activeQuadrant = activeQuadrant,
                    lastFrameBitmap = lastFrame,
                    activeFilter = currentFeedFilter,
                    resolvedFilters = resolvedFilters,
                    modifier = Modifier.fillMaxSize(),
                )

                // Countdown Overlay (during sequence)
                if (isCapturing) {
                    BoothCountdownOverlay(
                        photoIndex = activeQuadrant,
                        countdownSeconds = currentCountdown,
                        flashEvent = viewModel.flashEvent,
                        isComplete = isComplete,
                        activeFilterName = currentFeedFilter.name,
                    )
                }
            }

            // Bottom Control Area: Varies by state
            when {
                isIdle -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xF0070B12))
                            .border(1.dp, BorderSubtle)
                            .navigationBarsPadding()
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
                            .fillMaxWidth()
                            .background(Color(0xF0070B12))
                            .border(1.dp, BorderSubtle)
                            .navigationBarsPadding()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        // Success Status Card
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x2000E599))
                                .border(1.dp, EmeraldGreen.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = EmeraldGreen,
                                modifier = Modifier.size(20.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "All 4 Photos Saved Locally! 🎉",
                                    color = EmeraldGreen,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = "Ready for Cloudinary: Pictures/TRCPhotoBooth/sessions/${sessionTimestamp ?: ""}",
                                    color = TextSubtle,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }
                        }

                        // Action Buttons: New Session & Return to Admin
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            // Back to Admin
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(BgCard)
                                    .border(1.dp, BorderMedium, RoundedCornerShape(10.dp))
                                    .clickable(onClick = onBack),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "BACK TO ADMIN",
                                    color = TextMain,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                )
                            }

                            // New Session Button
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(46.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(NeonPink, PurpleNeon)
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
                                        text = "NEW SESSION",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
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
                            .fillMaxWidth()
                            .background(Color(0xE6070B12))
                            .navigationBarsPadding()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "CAPTURING SEQUENCE IN PROGRESS • DO NOT EXIT",
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
    }
}
