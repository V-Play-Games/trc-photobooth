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
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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

/**
 * Filter selector strip for the photo booth screen.
 * Displays all available presets including the special RANDOM filter.
 * Locked/disabled once a capture sequence is underway.
 */
@Composable
fun BoothFilterStrip(
    selectedFilter: FilterPreset,
    onSelectFilter: (FilterPreset) -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val theme = MaterialTheme.current
    val listState = rememberLazyListState()

    // Scroll selected filter into view
    LaunchedEffect(selectedFilter.id) {
        val index = FilterPresets.ALL.indexOfFirst { it.id == selectedFilter.id }
        if (index >= 0) {
            listState.animateScrollToItem(index)
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.5f)
            .padding(vertical = 4.dp),
    ) {
        // Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = theme.primary,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = "SELECT FILTER STYLE",
                    color = theme.onSurfaceVariant,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.1.sp,
                )
            }

            if (selectedFilter.id == FilterPresets.RANDOM.id) {
                Text(
                    text = "🎲 New Filter Each Shot",
                    color = theme.tertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Text(
                    text = selectedFilter.name,
                    color = selectedFilter.accentColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(FilterPresets.ALL, key = { it.id }) { preset ->
                BoothFilterCard(
                    preset = preset,
                    isSelected = preset.id == selectedFilter.id,
                    enabled = enabled,
                    onClick = { if (enabled) onSelectFilter(preset) },
                )
            }
        }
    }
}

@Composable
private fun BoothFilterCard(
    preset: FilterPreset,
    isSelected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val theme = MaterialTheme.current
    val isRandom = preset.id == FilterPresets.RANDOM.id

    val borderColor by animateColorAsState(
        targetValue = when {
            isSelected && isRandom -> theme.tertiary
            isSelected -> preset.accentColor
            else -> theme.outlineVariant
        },
        animationSpec = tween(200),
        label = "borderColor",
    )

    val backgroundBrush = when {
        isSelected && isRandom -> Brush.linearGradient(
            listOf(theme.tertiary.copy(alpha = 0.35f), theme.primary.copy(alpha = 0.25f))
        )
        isSelected -> Brush.linearGradient(
            listOf(preset.accentColor.copy(alpha = 0.28f), theme.surfaceVariant)
        )
        else -> Brush.linearGradient(
            listOf(theme.surfaceVariant, theme.surface)
        )
    }

    Box(
        modifier = Modifier
            .width(108.dp)
            .height(68.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundBrush)
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(8.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Color Dot or Dice Icon
                if (isRandom) {
                    Icon(
                        imageVector = Icons.Default.Casino,
                        contentDescription = "Random",
                        tint = theme.tertiary,
                        modifier = Modifier.size(16.dp),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(preset.accentColor),
                    )
                }

                if (isSelected) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (isRandom) theme.tertiary else preset.accentColor),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = "Selected",
                            tint = Color.White,
                            modifier = Modifier.size(11.dp),
                        )
                    }
                }
            }

            Column {
                Text(
                    text = preset.name,
                    color = if (isSelected) theme.onSurface else theme.onSurfaceVariant,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = preset.tagline,
                    color = theme.onSurfaceVariant,
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
