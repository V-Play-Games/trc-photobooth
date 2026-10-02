package com.trc.photobooth.filters

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorMatrix

data class FilterPreset(
    val id: String,
    val name: String,
    val tagline: String,
    val badgeText: String,
    val accentColor: Color,
    val description: String,
    val colorMatrix: ColorMatrix? = null,
    val isVignette: Boolean = false,
    val isGrain: Boolean = false,
    val isPolaroid: Boolean = false,
)

object FilterPresets {
    // 1. None / Raw
    val NONE = FilterPreset(
        id = "none",
        name = "Normal",
        tagline = "Raw & Natural",
        badgeText = "RAW",
        accentColor = Color(0xFF94A3B8),
        description = "Direct camera feed with natural colors and exposure",
    )

    // 2. Monochrome / B&W
    private val bwMatrix = ColorMatrix().apply {
        setToSaturation(0f)
        // High-contrast deep black & white
        val m = floatArrayOf(
            1.3f, 0f, 0f, 0f, -25f,
            0f, 1.3f, 0f, 0f, -25f,
            0f, 0f, 1.3f, 0f, -25f,
            0f, 0f, 0f, 1f, 0f
        )
        // Combine saturation 0 with contrast
        timesAssign(ColorMatrix(m))
    }
    val BW = FilterPreset(
        id = "bw",
        name = "Monochrome",
        tagline = "Timeless B&W",
        badgeText = "B&W",
        accentColor = Color(0xFFE2E8F0),
        description = "High-definition deep black & white with punchy contrast",
        colorMatrix = bwMatrix,
    )

    // 3. Sepia
    private val sepiaMatrix = ColorMatrix(
        floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )
    )
    val SEPIA = FilterPreset(
        id = "sepia",
        name = "Sepia",
        tagline = "Early 1900s",
        badgeText = "SEPIA",
        accentColor = Color(0xFFD97706),
        description = "Warm antique sepia tone reminiscent of early photography",
        colorMatrix = sepiaMatrix,
    )

    // 4. Vintage 70s Film Roll
    private val vintageMatrix = ColorMatrix(
        floatArrayOf(
            1.2f, 0.1f, 0.05f, 0f, 10f,
            0.05f, 1.15f, 0.05f, 0f, 5f,
            0.05f, 0.05f, 0.85f, 0f, -10f,
            0f, 0f, 0f, 1f, 0f
        )
    )
    val VINTAGE = FilterPreset(
        id = "vintage",
        name = "Vintage",
        tagline = "70s Film Roll",
        badgeText = "70s FILM",
        accentColor = Color(0xFFF59E0B),
        description = "Warm, sun-faded color saturation of classic 35mm film",
        colorMatrix = vintageMatrix,
    )

    // 5. Cool Tone
    private val coolMatrix = ColorMatrix(
        floatArrayOf(
            0.85f, 0.05f, 0.1f, 0f, -10f,
            0.05f, 1.1f, 0.15f, 0f, 10f,
            0.1f, 0.15f, 1.35f, 0f, 25f,
            0f, 0f, 0f, 1f, 0f
        )
    )
    val COOL = FilterPreset(
        id = "cool",
        name = "Cool Tone",
        tagline = "Arctic Cyan",
        badgeText = "COOL",
        accentColor = Color(0xFF06B6D4),
        description = "Crisp cool blue & cyan palette with vivid clarity",
        colorMatrix = coolMatrix,
    )

    // 6. Warm Golden Hour
    private val warmMatrix = ColorMatrix(
        floatArrayOf(
            1.25f, 0.1f, 0.05f, 0f, 20f,
            0.05f, 1.15f, 0.05f, 0f, 10f,
            0.02f, 0.05f, 0.85f, 0f, -15f,
            0f, 0f, 0f, 1f, 0f
        )
    )
    val WARM = FilterPreset(
        id = "warm",
        name = "Golden Hour",
        tagline = "Sunset Glow",
        badgeText = "GOLDEN",
        accentColor = Color(0xFFF97316),
        description = "Warm, golden illumination flattering for portraits",
        colorMatrix = warmMatrix,
    )

    // 7. Dramatic Noir
    private val highContrastMatrix = ColorMatrix(
        floatArrayOf(
            1.45f, 0f, 0f, 0f, -30f,
            0f, 1.45f, 0f, 0f, -30f,
            0f, 0f, 1.45f, 0f, -30f,
            0f, 0f, 0f, 1f, 0f
        )
    )
    val HIGH_CONTRAST = FilterPreset(
        id = "high_contrast",
        name = "Dramatic",
        tagline = "Noir Contrast",
        badgeText = "NOIR",
        accentColor = Color(0xFFEC4899),
        description = "Deep shadows, punchy highlights, and cinematic presence",
        colorMatrix = highContrastMatrix,
    )

    // 8. Vignette
    val VIGNETTE = FilterPreset(
        id = "vignette",
        name = "Vignette",
        tagline = "Edge Shadowing",
        badgeText = "VIGNETTE",
        accentColor = Color(0xFF8B5CF6),
        description = "Soft edge darkening that draws attention straight to the center",
        isVignette = true,
    )

    // 9. Film Grain
    val FILM_GRAIN = FilterPreset(
        id = "film_grain",
        name = "Film Grain",
        tagline = "Textured ISO",
        badgeText = "GRAIN",
        accentColor = Color(0xFF10B981),
        description = "Analog film noise texture for an authentic retro feel",
        isGrain = true,
    )

    // 10. Polaroid
    private val polaroidMatrix = ColorMatrix(
        floatArrayOf(
            1.08f, 0.05f, 0.05f, 0f, 10f,
            0.05f, 1.08f, 0.05f, 0f, 10f,
            0.05f, 0.05f, 0.95f, 0f, -5f,
            0f, 0f, 0f, 1f, 0f
        )
    )
    val POLAROID = FilterPreset(
        id = "polaroid",
        name = "Polaroid",
        tagline = "Instant Print",
        badgeText = "POLAROID",
        accentColor = Color(0xFF38BDF8),
        description = "Classic instant camera color grading with vintage border",
        colorMatrix = polaroidMatrix,
        isPolaroid = true,
    )

    val ALL: List<FilterPreset> = listOf(
        NONE,
        BW,
        SEPIA,
        VINTAGE,
        COOL,
        WARM,
        HIGH_CONTRAST,
        VIGNETTE,
        FILM_GRAIN,
        POLAROID,
    )

    fun getById(id: String): FilterPreset {
        return ALL.firstOrNull { it.id == id } ?: NONE
    }
}
