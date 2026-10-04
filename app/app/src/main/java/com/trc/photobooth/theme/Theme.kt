package com.trc.photobooth.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StudioLightColorScheme = lightColorScheme(
    primary = NeonPink,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE4EC),
    onPrimaryContainer = NeonPink,
    secondary = CyberCyan,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = CyberCyan,
    tertiary = AmberGold,
    onTertiary = Color.White,
    background = BgBase,
    onBackground = TextMain,
    surface = BgSurface,
    onSurface = TextMain,
    surfaceVariant = BgCard,
    onSurfaceVariant = TextMuted,
    outline = BorderSubtle,
    outlineVariant = BorderMedium,
)

@Composable
fun TRCPhotoBoothTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = StudioLightColorScheme,
        typography = Typography,
        content = content,
    )
}
