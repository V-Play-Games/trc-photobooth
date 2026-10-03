package com.trc.photobooth.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trc.photobooth.theme.BorderMedium
import com.trc.photobooth.theme.BorderSubtle
import com.trc.photobooth.theme.CyberCyan
import com.trc.photobooth.theme.EmeraldGreen
import com.trc.photobooth.theme.NeonPink
import com.trc.photobooth.theme.TextMain
import com.trc.photobooth.theme.TextMuted
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest

/**
 * Full-screen overlay providing:
 * - Giant photo countdown numbers
 * - Photo sequence tracker ("Photo 2 of 4")
 * - Camera flash animation
 * - Session completion celebration
 */
@Composable
fun BoothCountdownOverlay(
    photoIndex: Int,
    countdownSeconds: Int?,
    flashEvent: SharedFlow<Unit>,
    isComplete: Boolean,
    activeFilterName: String?,
    modifier: Modifier = Modifier,
) {
    val flashAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        flashEvent.collectLatest {
            flashAlpha.snapTo(1f)
            flashAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
            )
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // Countdown overlay
        if (countdownSeconds != null) {
            val pulseAnim = rememberInfiniteTransition(label = "countdownPulse")
            val scale by pulseAnim.animateFloat(
                initialValue = 0.95f,
                targetValue = 1.05f,
                animationSpec = infiniteRepeatable(
                    animation = tween(450, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "countdownScale",
            )

            // Semi-transparent darkened backdrop
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0x99000000)),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                // Photo sequence badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xCC0E121A))
                        .border(1.5.dp, CyberCyan.copy(alpha = 0.7f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            tint = CyberCyan,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "PHOTO ${photoIndex + 1} OF 4",
                            color = TextMain,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            letterSpacing = 1.5.sp,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(28.dp))

                // Giant Countdown Number
                Box(
                    modifier = Modifier
                        .scale(scale)
                        .size(140.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    Color(0x40FF3366),
                                    Color(0x2000F0FF),
                                    Color(0x00000000),
                                )
                            )
                        )
                        .border(2.dp, NeonPink.copy(alpha = 0.8f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "$countdownSeconds",
                        color = Color.White,
                        fontSize = 80.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Pose advice prompt
                val promptText = when {
                    countdownSeconds > 2 -> "GET READY!"
                    countdownSeconds == 2 -> "STRIKE A POSE! 📸"
                    countdownSeconds == 1 -> "SMILE! ✨"
                    else -> "HOLD STILL!"
                }

                Text(
                    text = promptText,
                    color = NeonPink,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 2.sp,
                )

                if (activeFilterName != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Filter: $activeFilterName",
                        color = CyberCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        // Strobe Flash Effect
        if (flashAlpha.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.White.copy(alpha = flashAlpha.value)),
            )
        }
    }
}
