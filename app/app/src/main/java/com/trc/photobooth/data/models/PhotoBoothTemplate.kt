package com.trc.photobooth.data.models

import androidx.annotation.DrawableRes
import com.trc.photobooth.R

/**
 * Overlay frame templates for photobooth strips.
 * Each template overlays decorative stickers, borders, and character art over the 4 photo frames.
 */
enum class PhotoBoothTemplate(
    val id: String,
    val title: String,
    val subtitle: String,
    @get:DrawableRes val drawableResId: Int,
    val themeColorHex: Long,
) {
    BLANK(
        id = "blank",
        title = "Classic",
        subtitle = "Clean Frames",
        drawableResId = R.drawable.template_blank,
        themeColorHex = 0xFFFFFFFF,
    ),
    HARRY_POTTER(
        id = "harry_potter",
        title = "Hogwarts",
        subtitle = "Gryffindor Magic",
        drawableResId = R.drawable.template_harry_potter,
        themeColorHex = 0xFFFFD700,
    ),
    RETRO(
        id = "retro",
        title = "Vintage",
        subtitle = "Oldies but Goodies",
        drawableResId = R.drawable.template_retro,
        themeColorHex = 0xFFD4A373,
    ),
    SPIDERMAN(
        id = "spiderman",
        title = "Spidey",
        subtitle = "Web Slinger",
        drawableResId = R.drawable.template_spiderman,
        themeColorHex = 0xFFE63946,
    ),
    POKEMON(
        id = "pokemon",
        title = "Pokémon",
        subtitle = "Gotta Catch 'Em All",
        drawableResId = R.drawable.template_pokemon,
        themeColorHex = 0xFFFFCC00,
    );

    companion object {
        val ALL: List<PhotoBoothTemplate> = entries.toList()
        val DEFAULT: PhotoBoothTemplate = HARRY_POTTER

        fun fromId(id: String?): PhotoBoothTemplate {
            return entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: DEFAULT
        }
    }
}
