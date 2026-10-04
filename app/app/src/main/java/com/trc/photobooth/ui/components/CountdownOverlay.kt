package com.trc.photobooth.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trc.photobooth.data.models.CountdownState
import com.trc.photobooth.theme.current
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest

@Composable
fun CountdownOverlay(
    countdown: CountdownState?,
    flashEvent: SharedFlow<Unit>,
    modifier: Modifier = Modifier,
) {
    val theme = MaterialTheme.current

    // Strobe flash animatable alpha
    val flashAlpha = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        flashEvent.collectLatest {
            flashAlpha.snapTo(1f)
            flashAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
            )
        }
    }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        // Countdown overlay
        if (countdown != null) {
            val seconds = countdown.secondsLeft
            val isZero = seconds <= 0

            // Semi-transparent backdrop
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(theme.scrim.copy(alpha = 0.6f))
            )

            if (!isZero) {
                // Giant animated countdown number
                val pulseAnim = rememberInfiniteTransition(label = "pulse")
                val scale by pulseAnim.animateFloat(
                    initialValue = 0.92f,
                    targetValue = 1.08f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(400, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "countdownScale"
                )

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .scale(scale)
                            .background(
                                Brush.radialGradient(
                                    listOf(theme.primary.copy(alpha = 0.35f), Color.Transparent)
                                ),
                                CircleShape
                            )
                            .border(
                                width = 3.dp,
                                brush = Brush.sweepGradient(
                                    listOf(theme.primary, theme.secondary, theme.primary)
                                ),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "$seconds",
                            color = Color.White,
                            fontSize = 76.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace,
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Text(
                        text = "GET READY!",
                        color = theme.secondary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp,
                    )
                }
            } else {
                // Smile banner
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "✨ SMILE! 📸 ✨",
                        color = Color.White,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.5.sp,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "HOLD STILL FOR THE SHUTTER",
                        color = theme.secondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.2.sp,
                    )
                }
            }
        }

        // Studio strobe white flash
        if (flashAlpha.value > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(flashAlpha.value)
                    .background(Color.White)
            )
        }
    }
}
