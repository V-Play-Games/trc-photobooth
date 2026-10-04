package com.trc.photobooth.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.trc.photobooth.data.models.CaptureMetadata
import com.trc.photobooth.data.models.CaptureType
import com.trc.photobooth.data.models.ConnectionStatus
import com.trc.photobooth.theme.current

@Composable
fun CaptureControls(
    captureMode: CaptureType,
    countdownSec: Int,
    isCountingDown: Boolean,
    isRecordingGif: Boolean,
    connectionStatus: ConnectionStatus,
    recentCapture: CaptureMetadata?,
    capturesCount: Int,
    hostAddress: String,
    gifFrames: Int,
    gifIntervalMs: Int,
    onSelectMode: (CaptureType) -> Unit,
    onSelectCountdown: (Int) -> Unit,
    onChangeGifFrames: (Int) -> Unit,
    onChangeGifInterval: (Int) -> Unit,
    onShutterClick: () -> Unit,
    onOpenGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = MaterialTheme.current
    val isConnected = connectionStatus == ConnectionStatus.CONNECTED
    val isBusy = isCountingDown || isRecordingGif

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.surface.copy(alpha = 0.95f))
            .border(width = 1.dp, color = theme.outlineVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Mode & Timer Top Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Mode Selector Pill (Photo vs GIF)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(theme.surfaceVariant)
                    .border(1.dp, theme.outlineVariant, RoundedCornerShape(20.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                ModePill(
                    label = "PHOTO",
                    isSelected = captureMode == CaptureType.PHOTO,
                    accentColor = theme.primary,
                    onClick = { onSelectMode(CaptureType.PHOTO) }
                )
                ModePill(
                    label = "GIF BURST",
                    isSelected = captureMode == CaptureType.GIF,
                    accentColor = theme.secondary,
                    onClick = { onSelectMode(CaptureType.GIF) }
                )
            }

            // Timer Selector (0s, 3s, 5s, 10s)
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(theme.surfaceVariant)
                    .border(1.dp, theme.outlineVariant, RoundedCornerShape(20.dp))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                listOf(0 to "OFF", 3 to "3s", 5 to "5s", 10 to "10s").forEach { (sec, label) ->
                    val isSelected = countdownSec == sec
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (isSelected) theme.primary.copy(alpha = 0.2f) else Color.Transparent)
                            .clickable { onSelectCountdown(sec) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = label,
                            color = if (isSelected) theme.primary else theme.onSurfaceVariant,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // Optional GIF burst configuration row (Frames & Speed)
        AnimatedVisibility(
            visible = captureMode == CaptureType.GIF,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(theme.surfaceVariant)
                    .border(1.dp, theme.outlineVariant, RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "BURST FRAMES",
                        color = theme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(6, 10, 15, 20).forEach { f ->
                            val sel = gifFrames == f
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (sel) theme.primary.copy(alpha = 0.25f) else Color.Transparent)
                                    .border(0.5.dp, if (sel) theme.primary else theme.outlineVariant, RoundedCornerShape(6.dp))
                                    .clickable { onChangeGifFrames(f) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${f}f",
                                    color = if (sel) theme.primary else theme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "SPEED (INTERVAL)",
                        color = theme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(100 to "Fast", 150 to "Norm", 250 to "Slow").forEach { (ms, name) ->
                            val sel = gifIntervalMs == ms
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (sel) theme.primary.copy(alpha = 0.25f) else Color.Transparent)
                                    .border(0.5.dp, if (sel) theme.primary else theme.outlineVariant, RoundedCornerShape(6.dp))
                                    .clickable { onChangeGifInterval(ms) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = name,
                                    color = if (sel) theme.primary else theme.onSurfaceVariant,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Main Bottom Controls Row: Gallery Thumb | Shutter Button | Mode/Timer helper
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Recent capture thumbnail / Gallery trigger
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(theme.surfaceVariant)
                    .border(1.dp, theme.outlineVariant, RoundedCornerShape(14.dp))
                    .clickable { onOpenGallery() },
                contentAlignment = Alignment.Center
            ) {
                if (recentCapture != null) {
                    val imageUrl = "http://$hostAddress:8000${recentCapture.thumbnailUrl}"
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = "Recent capture",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize()
                    )
                    // Photo count badge
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(3.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(theme.primary)
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "$capturesCount",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = "Gallery",
                            tint = theme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = "$capturesCount",
                            color = theme.onSurfaceVariant,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Center: Glowing Shutter Button
            ShutterButton(
                captureMode = captureMode,
                countdownSec = countdownSec,
                isBusy = isBusy,
                isEnabled = isConnected,
                onClick = onShutterClick,
            )

            // Right: Spacer / Quick Info
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(theme.surfaceVariant)
                    .border(1.dp, theme.outlineVariant, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = if (captureMode == CaptureType.PHOTO) Icons.Default.CameraAlt else Icons.Default.Movie,
                        contentDescription = null,
                        tint = if (captureMode == CaptureType.PHOTO) theme.primary else theme.secondary,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = if (countdownSec > 0) "${countdownSec}s" else "NOW",
                        color = theme.onSurfaceVariant,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
private fun ModePill(
    label: String,
    isSelected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    val theme = MaterialTheme.current
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) accentColor.copy(alpha = 0.22f) else Color.Transparent,
        label = "modePillBg"
    )
    val textColor by animateColorAsState(
        targetValue = if (isSelected) accentColor else theme.onSurfaceVariant,
        label = "modePillText"
    )

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 11.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun ShutterButton(
    captureMode: CaptureType,
    countdownSec: Int,
    isBusy: Boolean,
    isEnabled: Boolean,
    onClick: () -> Unit,
) {
    val theme = MaterialTheme.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val primaryColor = if (captureMode == CaptureType.PHOTO) theme.primary else theme.secondary
    val secondaryColor = if (captureMode == CaptureType.PHOTO) theme.secondary else theme.tertiary

    // Pulse animation while busy
    val infiniteTransition = rememberInfiniteTransition(label = "shutterPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(600),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val scaleModifier = Modifier.scale(
        when {
            isPressed -> 0.92f
            isBusy -> pulseScale
            else -> 1.0f
        }
    )

    Box(
        modifier = scaleModifier
            .size(76.dp)
            .clip(CircleShape)
            .background(
                brush = Brush.radialGradient(
                    listOf(primaryColor.copy(alpha = 0.35f), Color.Transparent)
                )
            )
            .border(
                width = 3.dp,
                brush = Brush.linearGradient(listOf(primaryColor, secondaryColor)),
                shape = CircleShape
            )
            .padding(5.dp)
            .clip(CircleShape)
            .background(
                brush = if (isEnabled && !isBusy) {
                    Brush.linearGradient(listOf(primaryColor, secondaryColor))
                } else {
                    Brush.linearGradient(listOf(theme.surfaceVariant, theme.outlineVariant))
                }
            )
            .clickable(
                enabled = isEnabled && !isBusy,
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        if (isBusy) {
            Text(
                text = "...",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Black
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = if (captureMode == CaptureType.PHOTO) Icons.Default.CameraAlt else Icons.Default.Movie,
                    contentDescription = "Shutter",
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
        }
    }
}
