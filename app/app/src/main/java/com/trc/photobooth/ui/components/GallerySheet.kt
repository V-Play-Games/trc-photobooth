package com.trc.photobooth.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.trc.photobooth.data.models.CaptureMetadata
import com.trc.photobooth.theme.BgCard
import com.trc.photobooth.theme.BgElevated
import com.trc.photobooth.theme.BgSurface
import com.trc.photobooth.theme.BgSurfaceElevated
import com.trc.photobooth.theme.BorderMedium
import com.trc.photobooth.theme.BorderSubtle
import com.trc.photobooth.theme.CyberCyan
import com.trc.photobooth.theme.NeonPink
import com.trc.photobooth.theme.TextMain
import com.trc.photobooth.theme.TextMuted

@Composable
fun GallerySheet(
    isOpen: Boolean,
    captures: List<CaptureMetadata>,
    isLoading: Boolean,
    hostAddress: String,
    onSelectCapture: (CaptureMetadata) -> Unit,
    onRefresh: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (!isOpen) return

    var selectedTab by remember { mutableStateOf("ALL") }

    val filteredCaptures = remember(captures, selectedTab) {
        when (selectedTab) {
            "PHOTOS" -> captures.filter { it.type == "photo" }
            "GIFS" -> captures.filter { it.type == "gif" }
            else -> captures
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0x66000000))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Sheet Content Container
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(680.dp)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .background(BgSurface)
                    .border(1.dp, BorderSubtle, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                    .clickable(enabled = false) {} // Prevent click-through
                    .padding(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 24.dp)
            ) {
                // Drag handle
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(BorderMedium)
                        .align(Alignment.CenterHorizontally)
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = CyberCyan,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "STUDIO GALLERY",
                                color = TextMain,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                        Text(
                            text = "${captures.size} captures saved on Booth Pi",
                            color = TextMuted,
                            fontSize = 12.sp
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(onClick = onRefresh) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                tint = TextMuted
                            )
                        }
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextMuted
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Filter Tabs (ALL / PHOTOS / GIFS)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(BgSurfaceElevated)
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("ALL", "PHOTOS", "GIFS").forEach { tab ->
                        val isSelected = selectedTab == tab
                        val count = when (tab) {
                            "PHOTOS" -> captures.count { it.type == "photo" }
                            "GIFS" -> captures.count { it.type == "gif" }
                            else -> captures.size
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0x3300F0FF) else Color.Transparent)
                                .clickable { selectedTab = tab }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "$tab ($count)",
                                color = if (isSelected) CyberCyan else TextMuted,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Grid or Empty / Loading State
                if (isLoading && captures.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = CyberCyan)
                    }
                } else if (filteredCaptures.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoCamera,
                                contentDescription = null,
                                tint = BorderMedium,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "No captures found",
                                color = TextMuted,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Tap the shutter button in the studio to take a photo or record a burst GIF!",
                                color = TextMuted.copy(alpha = 0.7f),
                                fontSize = 12.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(2.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    ) {
                        items(filteredCaptures, key = { it.id }) { capture ->
                            GalleryItemCard(
                                capture = capture,
                                hostAddress = hostAddress,
                                onClick = { onSelectCapture(capture) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GalleryItemCard(
    capture: CaptureMetadata,
    hostAddress: String,
    onClick: () -> Unit,
) {
    val isGif = capture.type == "gif"
    val accentColor = if (isGif) CyberCyan else NeonPink
    val thumbUrl = "http://$hostAddress:8000${capture.thumbnailUrl}"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0x800F172A))
            .border(1.dp, BorderSubtle, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = thumbUrl,
            contentDescription = capture.filename,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Type Badge (PHOTO / GIF)
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xCC000000))
                .border(0.5.dp, accentColor.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = if (isGif) Icons.Default.Movie else Icons.Default.PhotoCamera,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(10.dp)
                )
                Text(
                    text = if (isGif) "GIF" else "PHOTO",
                    color = accentColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // Timestamp / File info overlay at bottom
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(Color(0xB3000000))
                .padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text(
                text = capture.filename,
                color = TextMain,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
