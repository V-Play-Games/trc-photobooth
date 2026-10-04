package com.trc.photobooth.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trc.photobooth.data.models.ConnectionStatus
import com.trc.photobooth.data.models.GifRecordingState
import com.trc.photobooth.filters.FilterPreset
import com.trc.photobooth.theme.current

@Composable
fun LivePreview(
    lastFrameBitmap: Bitmap?,
    activeFilter: FilterPreset,
    showGuides: Boolean,
    fps: Int,
    resolution: String,
    connectionStatus: ConnectionStatus,
    gifRecording: GifRecordingState?,
    modifier: Modifier = Modifier,
) {
    val theme = MaterialTheme.current
    val isConnected = connectionStatus == ConnectionStatus.CONNECTED

    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.Center,
    ) {
        val targetRatio = 4f / 3f
        val isHeightConstrained = maxHeight > 0.dp && maxWidth > 0.dp && (maxWidth / maxHeight) > targetRatio

        val boxModifier = if (isHeightConstrained) {
            Modifier
                .fillMaxHeight()
                .aspectRatio(targetRatio, matchHeightConstraintsFirst = true)
        } else {
            Modifier
                .fillMaxWidth()
                .aspectRatio(targetRatio, matchHeightConstraintsFirst = false)
        }

        Box(
            modifier = boxModifier
                .clip(RoundedCornerShape(16.dp))
                .background(Color.Black)
                .border(1.dp, theme.outlineVariant, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
        if (isConnected && lastFrameBitmap != null) {
            // Live Stream Frame with Filter ColorMatrix
            val colorFilter = activeFilter.colorMatrix?.let { ColorFilter.colorMatrix(it) }

            Image(
                bitmap = lastFrameBitmap.asImageBitmap(),
                contentDescription = "Live Viewfinder",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                colorFilter = colorFilter,
            )

            // Vignette Overlay
            if (activeFilter.isVignette) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.radialGradient(
                                colorStops = arrayOf(
                                    0.45f to Color.Transparent,
                                    0.75f to Color(0x66000000),
                                    1.0f to Color(0xCC000000)
                                )
                            )
                        )
                )
            }

            // Polaroid Tint Overlay
            if (activeFilter.isPolaroid) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0x1AFFF0DC))
                )
            }

            // Framing Guides Overlay (Rule of Thirds + Center Crosshair)
            if (showGuides) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height
                    val lineColor = Color(0x55FFFFFF)

                    // Horizontal thirds
                    drawLine(lineColor, Offset(0f, h / 3f), Offset(w, h / 3f), strokeWidth = 1.dp.toPx())
                    drawLine(lineColor, Offset(0f, h * 2f / 3f), Offset(w, h * 2f / 3f), strokeWidth = 1.dp.toPx())

                    // Vertical thirds
                    drawLine(lineColor, Offset(w / 3f, 0f), Offset(w / 3f, h), strokeWidth = 1.dp.toPx())
                    drawLine(lineColor, Offset(w * 2f / 3f, 0f), Offset(w * 2f / 3f, h), strokeWidth = 1.dp.toPx())

                    // Center Crosshair
                    val ch = 12.dp.toPx()
                    val cx = w / 2f
                    val cy = h / 2f
                    drawLine(Color(0x88FFFFFF), Offset(cx - ch, cy), Offset(cx + ch, cy), strokeWidth = 1.dp.toPx())
                    drawLine(Color(0x88FFFFFF), Offset(cx, cy - ch), Offset(cx, cy + ch), strokeWidth = 1.dp.toPx())
                }
            }

            // Telemetry HUD Badges (Bottom Left)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val resText = if (resolution.isNotBlank()) resolution else "${lastFrameBitmap.width}×${lastFrameBitmap.height}"
                HudBadge(text = resText)
                HudBadge(text = "$fps FPS")
                if (activeFilter.id != "none") {
                    HudBadge(
                        text = activeFilter.badgeText,
                        textColor = activeFilter.accentColor,
                        borderColor = activeFilter.accentColor.copy(alpha = 0.5f),
                    )
                }
            }
        } else {
            // Offline / Acquiring Stream Placeholder Card
            val transition = rememberInfiniteTransition(label = "pulse")
            val pulseScale by transition.animateFloat(
                initialValue = 0.95f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "scale",
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(theme.surface)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(theme.primary.copy(alpha = 0.15f))
                        .border(1.dp, theme.primary.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = null,
                        tint = theme.primary,
                        modifier = Modifier.size(36.dp),
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (isConnected) "Acquiring Camera Feed..." else "Connecting to Raspberry Pi",
                    color = theme.onSurface,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isConnected) "Initializing camera sensor and streamer..." else "Ensure phone and Pi are on the same local Wi-Fi",
                    color = theme.onSurfaceVariant,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // GIF Burst Recording Banner
        if (gifRecording != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(theme.surface.copy(alpha = 0.95f))
                    .border(1.dp, theme.primary, RoundedCornerShape(20.dp))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Movie,
                        contentDescription = null,
                        tint = theme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Column {
                        Text(
                            text = "RECORDING GIF BURST",
                            color = theme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 1.sp,
                        )
                        Text(
                            text = "Capturing ${gifRecording.frames} frames @ ${gifRecording.intervalMs}ms",
                            color = theme.onSurfaceVariant,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = theme.primary,
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
        }
    }
}

@Composable
private fun HudBadge(
    text: String,
    textColor: Color = Color.White,
    borderColor: Color = MaterialTheme.current.outlineVariant,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color.Black.copy(alpha = 0.75f))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
