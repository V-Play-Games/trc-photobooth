package com.trc.photobooth.ui.components

import android.graphics.Bitmap
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trc.photobooth.filters.FilterPreset
import com.trc.photobooth.theme.BgCard
import com.trc.photobooth.theme.BgSurface
import com.trc.photobooth.theme.BgSurfaceElevated
import com.trc.photobooth.theme.BorderMedium
import com.trc.photobooth.theme.BorderSubtle
import com.trc.photobooth.theme.CyberCyan
import com.trc.photobooth.theme.EmeraldGreen
import com.trc.photobooth.theme.NeonPink
import com.trc.photobooth.theme.TextMain
import com.trc.photobooth.theme.TextMuted
import com.trc.photobooth.theme.TextSubtle

/**
 * 2x2 Photo Booth Quadrant Grid.
 *
 * Quadrant indices:
 * [0] Top-Left     | [1] Top-Right
 * -----------------+-----------------
 * [2] Bottom-Left  | [3] Bottom-Right
 *
 * Each quadrant displays:
 * - Captured bitmap if available
 * - Live camera stream with active filter if active
 * - Dark cyber placeholder if awaiting capture
 */
@Composable
fun QuadrantGrid(
    capturedPhotos: List<Bitmap?>,
    activeQuadrant: Int,
    lastFrameBitmap: Bitmap?,
    activeFilter: FilterPreset,
    resolvedFilters: List<FilterPreset>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        // Top Row: Quadrants 0 & 1
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            QuadrantCell(
                index = 0,
                capturedBitmap = capturedPhotos.getOrNull(0),
                isActive = activeQuadrant == 0,
                lastFrameBitmap = lastFrameBitmap,
                activeFilter = activeFilter,
                presetUsed = resolvedFilters.getOrNull(0),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
            )
            QuadrantCell(
                index = 1,
                capturedBitmap = capturedPhotos.getOrNull(1),
                isActive = activeQuadrant == 1,
                lastFrameBitmap = lastFrameBitmap,
                activeFilter = activeFilter,
                presetUsed = resolvedFilters.getOrNull(1),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
            )
        }

        // Bottom Row: Quadrants 2 & 3
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            QuadrantCell(
                index = 2,
                capturedBitmap = capturedPhotos.getOrNull(2),
                isActive = activeQuadrant == 2,
                lastFrameBitmap = lastFrameBitmap,
                activeFilter = activeFilter,
                presetUsed = resolvedFilters.getOrNull(2),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
            )
            QuadrantCell(
                index = 3,
                capturedBitmap = capturedPhotos.getOrNull(3),
                isActive = activeQuadrant == 3,
                lastFrameBitmap = lastFrameBitmap,
                activeFilter = activeFilter,
                presetUsed = resolvedFilters.getOrNull(3),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
            )
        }
    }
}

@Composable
private fun QuadrantCell(
    index: Int,
    capturedBitmap: Bitmap?,
    isActive: Boolean,
    lastFrameBitmap: Bitmap?,
    activeFilter: FilterPreset,
    presetUsed: FilterPreset?,
    modifier: Modifier = Modifier,
) {
    val cornerRadius = 14.dp
    val shape = RoundedCornerShape(cornerRadius)

    // Pulsing neon border animation for the active quadrant
    val infiniteTransition = rememberInfiniteTransition(label = "cellPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(700, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "pulseAlpha",
    )

    val borderModifier = when {
        isActive -> Modifier.border(
            width = 2.5.dp,
            color = NeonPink.copy(alpha = pulseAlpha),
            shape = shape,
        )
        capturedBitmap != null -> Modifier.border(
            width = 1.dp,
            color = EmeraldGreen.copy(alpha = 0.6f),
            shape = shape,
        )
        else -> Modifier.border(
            width = 1.dp,
            color = BorderSubtle,
            shape = shape,
        )
    }

    Box(
        modifier = modifier
            .clip(shape)
            .background(BgCard)
            .then(borderModifier),
        contentAlignment = Alignment.Center,
    ) {
        when {
            // State 1: Captured photo
            capturedBitmap != null -> {
                Image(
                    bitmap = capturedBitmap.asImageBitmap(),
                    contentDescription = "Photo ${index + 1}",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )

                // Top Badge: Captured checkmark + preset name
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(BgSurface.copy(alpha = 0.92f))
                        .border(1.dp, EmeraldGreen.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 3.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = EmeraldGreen,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            text = "#${index + 1} ${presetUsed?.name ?: ""}",
                            color = TextMain,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }

            // State 2: Active live viewfinder
            isActive -> {
                if (lastFrameBitmap != null) {
                    val colorFilter = activeFilter.colorMatrix?.let { ColorFilter.colorMatrix(it) }
                    Image(
                        bitmap = lastFrameBitmap.asImageBitmap(),
                        contentDescription = "Live Quadrant ${index + 1}",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                        colorFilter = colorFilter,
                    )

                    // Optional Vignette
                    if (activeFilter.isVignette) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        colorStops = arrayOf(
                                            0.45f to Color.Transparent,
                                            0.75f to Color(0x66000000),
                                            1.0f to Color(0xCC000000),
                                        ),
                                    ),
                                ),
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(
                            color = NeonPink,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = "Awaiting Camera...",
                            color = TextMuted,
                            fontSize = 11.sp,
                        )
                    }
                }

                // Active live pill badge
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(BgSurface.copy(alpha = 0.95f))
                        .border(1.dp, NeonPink.copy(alpha = pulseAlpha), RoundedCornerShape(6.dp))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(NeonPink),
                        )
                        Text(
                            text = "LIVE • #${index + 1}",
                            color = NeonPink,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }

                // Active filter pill badge on bottom
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(BgSurface.copy(alpha = 0.9f))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(6.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = activeFilter.name,
                        color = activeFilter.accentColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }

            // State 3: Waiting / Empty quadrant
            else -> {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(BgSurfaceElevated)
                            .border(1.dp, BorderSubtle, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = TextSubtle,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Text(
                        text = "Photo ${index + 1}",
                        color = TextSubtle,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}
