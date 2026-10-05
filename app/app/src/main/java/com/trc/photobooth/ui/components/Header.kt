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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cameraswitch
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.GridOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.filled.VideocamOff
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.filled.Cameraswitch
import com.trc.photobooth.camera.AndroidLens
import com.trc.photobooth.camera.CameraSource
import com.trc.photobooth.data.models.ConnectionStatus
import com.trc.photobooth.theme.current

/**
 * Compact Dynamic Island Top Bar for the main viewfinder screen.
 * Keeps camera feed clear while providing fast access to camera turn on/off,
 * connection telemetry, mirror flip, framing guides, settings, and booth mode.
 */
@Composable
fun Header(
    connectionStatus: ConnectionStatus,
    fps: Int,
    latencyMs: Int,
    capturesCount: Int,
    showGuides: Boolean,
    isFlipped: Boolean,
    isStreamPaused: Boolean = false,
    cameraSource: CameraSource = CameraSource.RASPI,
    androidLens: AndroidLens = AndroidLens.FRONT,
    onToggleLens: () -> Unit = {},
    onToggleCameraSource: () -> Unit = {},
    onToggleStreamPause: () -> Unit = {},
    isTorchEnabled: Boolean = false,
    onToggleTorch: () -> Unit = {},
    onToggleGuides: () -> Unit,
    onToggleFlip: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenSettings: () -> Unit,
    onNavigateToBooth: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val theme = MaterialTheme.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(theme.surface.copy(alpha = 0.95f))
                .border(width = 1.dp, color = theme.outlineVariant, shape = RoundedCornerShape(22.dp))
                .padding(horizontal = 10.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Brand Title Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.padding(end = 4.dp),
            ) {
                Text(
                    text = "TRC",
                    color = theme.onSurface,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    text = "BOOTH",
                    color = theme.primary,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }

            // Connection & Stream Status Pill
            IslandStatusPill(
                status = connectionStatus,
                fps = fps,
                isStreamPaused = isStreamPaused,
                cameraSource = cameraSource,
                androidLens = androidLens,
            )

            // If using on-device camera, show lens switch button (Front/Back) and Torch toggle
            if (cameraSource == CameraSource.ANDROID) {
                IslandToolButton(
                    icon = Icons.Default.Cameraswitch,
                    isActive = true,
                    activeBg = theme.secondary.copy(alpha = 0.15f),
                    activeBorder = theme.secondary,
                    activeTint = theme.secondary,
                    onClick = onToggleLens,
                    contentDescription = "Switch Camera Lens (${androidLens.name})",
                )
                IslandToolButton(
                    icon = if (isTorchEnabled) Icons.Default.FlashOn else Icons.Default.FlashOff,
                    isActive = isTorchEnabled,
                    activeBg = theme.tertiary.copy(alpha = 0.2f),
                    activeBorder = theme.tertiary,
                    activeTint = theme.tertiary,
                    onClick = onToggleTorch,
                    contentDescription = if (isTorchEnabled) "Turn Torch OFF" else "Turn Torch ON",
                )
            }

            // Camera Turn On / Turn Off Button
            IslandToolButton(
                icon = if (isStreamPaused) Icons.Default.VideocamOff else Icons.Default.Videocam,
                isActive = true,
                activeBg = if (isStreamPaused) theme.tertiary.copy(alpha = 0.15f) else theme.primary.copy(alpha = 0.15f),
                activeBorder = if (isStreamPaused) theme.tertiary else theme.primary,
                activeTint = if (isStreamPaused) theme.tertiary else theme.primary,
                onClick = onToggleStreamPause,
                contentDescription = if (isStreamPaused) "Turn Camera ON" else "Turn Camera OFF",
            )

            // Framing Guides Button
            IslandToolButton(
                icon = Icons.Default.GridOn,
                isActive = showGuides,
                onClick = onToggleGuides,
                contentDescription = "Framing Guides",
            )

            // Selfie Mirror Flip Button
            IslandToolButton(
                icon = Icons.Default.Flip,
                isActive = isFlipped,
                onClick = onToggleFlip,
                contentDescription = "Toggle Mirror",
            )

            // Settings Button
            IslandToolButton(
                icon = Icons.Default.Settings,
                isActive = false,
                onClick = onOpenSettings,
                contentDescription = "Settings",
            )

            // Launch Booth Pill Button
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(theme.primary, theme.secondary)
                        )
                    )
                    .clickable { onNavigateToBooth() }
                    .padding(horizontal = 9.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.CameraAlt,
                        contentDescription = "Booth Mode",
                        tint = Color.White,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = "BOOTH",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.6.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun IslandStatusPill(
    status: ConnectionStatus,
    fps: Int,
    isStreamPaused: Boolean,
    cameraSource: CameraSource = CameraSource.RASPI,
    androidLens: AndroidLens = AndroidLens.FRONT,
) {
    val theme = MaterialTheme.current
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

    val (bgColor, borderColor, text, iconColor) = when {
        cameraSource == CameraSource.ANDROID && isStreamPaused -> Quad(
            theme.tertiary.copy(alpha = 0.15f),
            theme.tertiary.copy(alpha = 0.6f),
            "CAMERA OFF",
            theme.tertiary,
        )
        cameraSource == CameraSource.ANDROID -> Quad(
            theme.secondary.copy(alpha = 0.15f),
            theme.secondary.copy(alpha = 0.6f),
            if (fps > 0) "${if (androidLens == AndroidLens.FRONT) "FRONT" else "BACK"} • ${fps}fps" else "${if (androidLens == AndroidLens.FRONT) "FRONT" else "BACK"} CAM",
            theme.secondary,
        )
        isStreamPaused && status == ConnectionStatus.CONNECTED -> Quad(
            theme.tertiary.copy(alpha = 0.15f),
            theme.tertiary.copy(alpha = 0.6f),
            "CAMERA OFF",
            theme.tertiary,
        )
        status == ConnectionStatus.CONNECTED -> Quad(
            theme.primary.copy(alpha = 0.15f),
            theme.primary.copy(alpha = 0.6f),
            if (fps > 0) "${fps}fps" else "LIVE",
            theme.primary,
        )
        status == ConnectionStatus.CONNECTING -> Quad(
            theme.tertiary.copy(alpha = 0.15f),
            theme.tertiary.copy(alpha = 0.6f),
            "CONNECTING",
            theme.tertiary,
        )
        else -> Quad(
            theme.error.copy(alpha = 0.15f),
            theme.error.copy(alpha = 0.6f),
            "OFFLINE",
            theme.error,
        )
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (status == ConnectionStatus.CONNECTED && !isStreamPaused) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(theme.primary)
            )
        } else if (isStreamPaused) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(theme.tertiary)
            )
        } else if (status == ConnectionStatus.CONNECTING) {
            Icon(
                imageVector = Icons.Default.Wifi,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier
                    .size(10.dp)
                    .alpha(pulseAlpha),
            )
        } else {
            Icon(
                imageVector = Icons.Default.WifiOff,
                contentDescription = null,
                tint = iconColor,
                modifier = Modifier.size(10.dp),
            )
        }

        Text(
            text = text,
            color = iconColor,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun IslandToolButton(
    icon: ImageVector,
    isActive: Boolean,
    onClick: () -> Unit,
    contentDescription: String,
    activeBg: Color = MaterialTheme.current.primary.copy(alpha = 0.15f),
    activeBorder: Color = MaterialTheme.current.primary,
    activeTint: Color = MaterialTheme.current.primary,
) {
    val theme = MaterialTheme.current
    val bg = if (isActive) activeBg else theme.surfaceVariant
    val border = if (isActive) activeBorder else theme.outlineVariant
    val tint = if (isActive) activeTint else theme.onSurfaceVariant

    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
    }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
