package com.trc.photobooth.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.trc.photobooth.filters.FilterPreset
import com.trc.photobooth.filters.FilterPresets
import com.trc.photobooth.theme.current

@Composable
fun FilterStrip(
    activeFilter: FilterPreset,
    onSelectFilter: (FilterPreset) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = MaterialTheme.current
    val listState = rememberLazyListState()

    // Scroll active filter into view if changed
    LaunchedEffect(activeFilter.id) {
        val index = FilterPresets.ALL.indexOfFirst { it.id == activeFilter.id }
        if (index >= 0) {
            listState.animateScrollToItem(index)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(theme.surface)
            .padding(vertical = 10.dp)
    ) {
        // Label row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = theme.primary,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = "STUDIO PRESETS",
                    color = theme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.2.sp,
                )
            }

            Text(
                text = "${activeFilter.name.uppercase()} • ${activeFilter.tagline}",
                color = activeFilter.accentColor,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Horizontal Carousel
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(FilterPresets.ALL, key = { it.id }) { preset ->
                val isSelected = preset.id == activeFilter.id
                FilterCard(
                    preset = preset,
                    isSelected = isSelected,
                    onClick = { onSelectFilter(preset) }
                )
            }
        }
    }
}

@Composable
private fun FilterCard(
    preset: FilterPreset,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val theme = MaterialTheme.current
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) preset.accentColor else theme.outlineVariant,
        animationSpec = tween(250),
        label = "filterCardBorder"
    )

    val bgColor by animateColorAsState(
        targetValue = if (isSelected) theme.primary.copy(alpha = 0.15f) else theme.surfaceVariant,
        animationSpec = tween(250),
        label = "filterCardBg"
    )

    Box(
        modifier = Modifier
            .width(108.dp)
            .height(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top row: swatch & badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Color swatch
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    preset.accentColor,
                                    preset.accentColor.copy(alpha = 0.5f)
                                )
                            )
                        )
                        .border(1.dp, theme.outlineVariant, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }

                // Badge text pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(preset.accentColor.copy(alpha = 0.18f))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                ) {
                    Text(
                        text = preset.badgeText,
                        color = preset.accentColor,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Bottom name & tagline
            Column {
                Text(
                    text = preset.name,
                    color = if (isSelected) theme.onSurface else theme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = preset.tagline,
                    color = theme.onSurfaceVariant,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
