package com.trc.photobooth.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StudioDarkColorScheme = darkColorScheme(
    primary = NeonPink,
    onPrimary = Color.White,
    primaryContainer = NeonPinkHover,
    onPrimaryContainer = Color.White,
    secondary = CyberCyan,
    onSecondary = Color.Black,
    secondaryContainer = BgSurfaceElevated,
    onSecondaryContainer = CyberCyan,
    tertiary = AmberGold,
    onTertiary = Color.Black,
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
        colorScheme = StudioDarkColorScheme,
        typography = Typography,
        content = content,
    )
}
