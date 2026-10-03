package com.trc.photobooth.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trc.photobooth.data.models.ConnectionStatus
import com.trc.photobooth.theme.BorderMedium
import com.trc.photobooth.theme.BorderSubtle
import com.trc.photobooth.theme.CyberCyan
import com.trc.photobooth.theme.EmeraldGreen
import com.trc.photobooth.theme.NeonPink
import com.trc.photobooth.theme.TextMain
import com.trc.photobooth.theme.TextMuted

@Composable
fun Header(
    connectionStatus: ConnectionStatus,
    fps: Int,
    latencyMs: Int,
    capturesCount: Int,
    showGuides: Boolean,
    isFlipped: Boolean,
    onToggleGuides: () -> Unit,
    onToggleFlip: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigateToBooth: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(Color(0xE60B0F17))
            .border(width = 1.dp, color = BorderSubtle)
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left: Brand Lockup & Status Pill
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Camera Brand Icon
//            Box(
//                modifier = Modifier
//                    .size(36.dp)
//                    .clip(RoundedCornerShape(8.dp))
//                    .background(
//                        Brush.linearGradient(
//                            listOf(NeonPink.copy(alpha = 0.35f), CyberCyan.copy(alpha = 0.35f))
//                        )
//                    )
//                    .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp)),
//                contentAlignment = Alignment.Center,
//            ) {
//                Icon(
//                    imageVector = Icons.Default.CameraAlt,
//                    contentDescription = "Photo Booth",
//                    tint = TextMain,
//                    modifier = Modifier.size(20.dp),
//                )
//            }

            // Brand Titles
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "TRC ",
                        color = TextMain,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        letterSpacing = 0.5.sp,
                    )
                    Text(
                        text = "BOOTH",
                        color = NeonPink,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                        letterSpacing = 0.5.sp,
                    )
                }
                Text(
                    text = "PI EDITION",
                    color = CyberCyan,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            }

            // Status Pill
//            StatusPill(
//                status = connectionStatus,
//                fps = fps,
//                latencyMs = latencyMs,
//            )
        }

        // Right: Tool Buttons
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Framing Guides Button
            ToolButton(
                icon = Icons.Default.GridOn,
                isActive = showGuides,
                onClick = onToggleGuides,
                contentDescription = "Framing Guides",
            )

            // Selfie Mirror Flip Button
            ToolButton(
                icon = Icons.Default.Flip,
                isActive = isFlipped,
                onClick = onToggleFlip,
                contentDescription = "Toggle Mirror",
            )

            // Gallery Button with Counter
//            Box {
//                ToolButton(
//                    icon = Icons.Default.PhotoLibrary,
//                    isActive = false,
//                    onClick = onOpenGallery,
//                    contentDescription = "Open Gallery",
//                )
//                if (capturesCount > 0) {
//                    Box(
//                        modifier = Modifier
//                            .align(Alignment.TopEnd)
//                            .size(18.dp)
//                            .clip(CircleShape)
//                            .background(NeonPink),
//                        contentAlignment = Alignment.Center,
//                    ) {
//                        Text(
//                            text = capturesCount.coerceAtMost(99).toString(),
//                            color = Color.White,
//                            fontSize = 9.sp,
//                            fontWeight = FontWeight.Bold,
//                        )
//                    }
//                }
//            }

            // Settings Button
            ToolButton(
                icon = Icons.Default.Settings,
                isActive = false,
                onClick = onOpenSettings,
                contentDescription = "Settings",
            )

            // Launch Booth Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(NeonPink, Color(0xFF8B5CF6))
                        )
                    )
                    .clickable { onNavigateToBooth() }
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Booth Mode",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        text = "BOOTH",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.8.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusPill(
    status: ConnectionStatus,
    fps: Int,
    latencyMs: Int,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    val (bgColor, borderColor, text, iconColor) = when (status) {
        ConnectionStatus.CONNECTED -> Quad(
            Color(0x1A00E599),
            EmeraldGreen.copy(alpha = 0.4f),
            if (fps > 0) "LIVE • ${fps}fps" else "LIVE",
            EmeraldGreen,
        )
        ConnectionStatus.CONNECTING -> Quad(
            Color(0x1AFFB703),
            Color(0x66FFB703),
            "CONNECTING",
            Color(0xFFFFB703),
        )
        ConnectionStatus.DISCONNECTED -> Quad(
            Color(0x1AFF3366),
            NeonPink.copy(alpha = 0.4f),
            "OFFLINE",
            NeonPink,
        )
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(16.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (status == ConnectionStatus.CONNECTED) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(EmeraldGreen)
            )
        } else if (status == ConnectionStatus.CONNECTING) {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier
                    .size(12.dp)
                    .alpha(pulseAlpha),
            )
        } else {
            Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(12.dp),
            )
        }

        Text(
            text = text,
            color = iconColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun ToolButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
) {
    val bg = if (isActive) CyberCyan.copy(alpha = 0.15f) else Color(0x1AFFFFFF)
    val border = if (isActive) CyberCyan else BorderSubtle
    val tint = if (isActive) CyberCyan else TextMuted

    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
