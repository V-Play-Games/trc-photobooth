package com.trc.photobooth.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color

val NeonRedDarkColorScheme = darkColorScheme(
    primary = Color(0xFFFF2247),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF4D0012),
    onPrimaryContainer = Color(0xFFFFD9DF),
    inversePrimary = Color(0xFFA8112D),
    secondary = Color(0xFFFF4D79),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF450D19),
    onSecondaryContainer = Color(0xFFFFD3DB),
    tertiary = Color(0xFFFF7A59),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF481A0E),
    onTertiaryContainer = Color(0xFFFFDBD1),
    background = Color(0xFF0D090A),
    onBackground = Color(0xFFF7ECEE),
    surface = Color(0xFF160F11),
    onSurface = Color(0xFFF7ECEE),
    surfaceVariant = Color(0xFF221619),
    onSurfaceVariant = Color(0xFFC7B1B6),
    outline = Color(0xFF3B2328),
    outlineVariant = Color(0xFF5A363E),
    error = Color(0xFFFF4958),
    onError = Color.White,
    errorContainer = Color(0xFF68000F),
    onErrorContainer = Color(0xFFFFDAD6),
    scrim = Color.Black,
)

val NeonRedLightColorScheme = lightColorScheme(
    primary = Color(0xFFDC143C),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE8ED),
    onPrimaryContainer = Color(0xFF90001D),
    inversePrimary = Color(0xFFFF829A),
    secondary = Color(0xFFBE123C),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE8EC),
    onSecondaryContainer = Color(0xFF700B21),
    tertiary = Color(0xFFB83814),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFEDE6),
    onTertiaryContainer = Color(0xFF6E1800),
    background = Color(0xFFFDF8F9),
    onBackground = Color(0xFF1C1114),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF1C1114),
    surfaceVariant = Color(0xFFF7EFF1),
    onSurfaceVariant = Color(0xFF5E4F53),
    outline = Color(0xFFE8D7DC),
    outlineVariant = Color(0xFFCFBCC2),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    scrim = Color.Black,
)

/**
 * Convenience accessor matching `MaterialTheme.current.<variable>`
 */
val MaterialTheme.current: ColorScheme
    @Composable
    @ReadOnlyComposable
    get() = MaterialTheme.colorScheme

@Composable
fun TRCPhotoBoothTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) NeonRedDarkColorScheme else NeonRedLightColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
