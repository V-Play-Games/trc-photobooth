package com.trc.photobooth.ui.components

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.trc.photobooth.data.PhotoBoothRepository
import com.trc.photobooth.data.models.CaptureMetadata
import com.trc.photobooth.filters.FilterPreset
import com.trc.photobooth.filters.FilterPresets
import com.trc.photobooth.theme.BgElevated
import com.trc.photobooth.theme.BorderMedium
import com.trc.photobooth.theme.BorderSubtle
import com.trc.photobooth.theme.CyberCyan
import com.trc.photobooth.theme.EmeraldGreen
import com.trc.photobooth.theme.NeonPink
import com.trc.photobooth.theme.TextMain
import com.trc.photobooth.theme.TextMuted
import com.trc.photobooth.util.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun LightboxDialog(
    capture: CaptureMetadata?,
    hostAddress: String,
    onDelete: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (capture == null) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isGif = capture.type == "gif"
    val fullUrl = "http://$hostAddress:8000${capture.url}"

    var selectedFilter by remember { mutableStateOf(FilterPresets.NONE) }
    var loadedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var processedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isDownloading by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    // Load original bitmap for photos so we can bake filters client-side
    LaunchedEffect(capture.id) {
        selectedFilter = FilterPresets.NONE
        loadedBitmap = null
        processedBitmap = null

        if (!isGif) {
            isDownloading = true
            val loader = ImageLoader.Builder(context).build()
            val request = ImageRequest.Builder(context)
                .data(fullUrl)
                .allowHardware(false) // Software bitmap so we can draw on Canvas
                .build()

            val result = withContext(Dispatchers.IO) { loader.execute(request) }
            if (result is SuccessResult) {
                val bmp = (result.drawable as? BitmapDrawable)?.bitmap
                loadedBitmap = bmp
                processedBitmap = bmp
            }
            isDownloading = false
        }
    }

    // Re-bake filter when preset changes
    LaunchedEffect(selectedFilter, loadedBitmap) {
        val src = loadedBitmap ?: return@LaunchedEffect
        processedBitmap = withContext(Dispatchers.Default) {
            BitmapUtils.bakeFilter(src, selectedFilter)
        }
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xF007090E))
                .padding(16.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                // Top Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = capture.filename,
                            color = TextMain,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = if (isGif) "Animated Burst GIF" else "High-Res Studio Capture",
                            color = if (isGif) CyberCyan else NeonPink,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(onClick = { showDeleteConfirm = true }) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = Color(0xFFEF4444)
                            )
                        }
                        IconButton(onClick = onClose) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Close",
                                tint = TextMain
                            )
                        }
                    }
                }

                // Middle: Image / GIF Viewport
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF000000))
                        .border(1.dp, BorderSubtle, RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (isGif) {
                        // GIF display using Coil GifDecoder
                        val gifImageLoader = remember {
                            ImageLoader.Builder(context)
                                .components {
                                    if (android.os.Build.VERSION.SDK_INT >= 28) {
                                        add(ImageDecoderDecoder.Factory())
                                    } else {
                                        add(GifDecoder.Factory())
                                    }
                                }
                                .build()
                        }
                        AsyncImage(
                            model = fullUrl,
                            imageLoader = gifImageLoader,
                            contentDescription = "Animated GIF",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        // Filtered or Original Photo
                        if (processedBitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = processedBitmap!!.asImageBitmap(),
                                contentDescription = "Photo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else if (isDownloading) {
                            CircularProgressIndicator(color = CyberCyan)
                        } else {
                            AsyncImage(
                                model = fullUrl,
                                contentDescription = "Photo",
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }

                // If Photo, show Filter strip to apply retro styles!
                if (!isGif) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        Text(
                            text = "APPLY FILTER BEFORE SAVING",
                            color = TextMuted,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 6.dp)
                        )
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(horizontal = 2.dp)
                        ) {
                            items(FilterPresets.ALL, key = { it.id }) { preset ->
                                val isSelected = preset.id == selectedFilter.id
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) preset.accentColor.copy(alpha = 0.2f) else Color(0x661E293B))
                                        .border(
                                            width = if (isSelected) 1.5.dp else 1.dp,
                                            color = if (isSelected) preset.accentColor else BorderSubtle,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable { selectedFilter = preset }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = preset.name,
                                        color = if (isSelected) preset.accentColor else TextMuted,
                                        fontSize = 11.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }

                // Print Button for still photos
                if (!isGif) {
                    var isPrinting by remember { mutableStateOf(false) }
                    Button(
                        onClick = {
                            if (isPrinting) return@Button
                            scope.launch {
                                isPrinting = true
                                Toast.makeText(context, "Sending photo to TRC_Printer...", Toast.LENGTH_SHORT).show()
                                val bmp = processedBitmap ?: loadedBitmap
                                val repo = PhotoBoothRepository.getInstance(context)
                                val result = if (bmp != null) {
                                    repo.printBitmap(bmp, "photo_${capture.id}.jpg")
                                } else {
                                    repo.printCapture(capture.id)
                                }
                                isPrinting = false
                                result.fold(
                                    onSuccess = { res ->
                                        val jobInfo = if (res.jobId != null) " (Job: ${res.jobId})" else ""
                                        Toast.makeText(context, "🖨️ Sent to TRC_Printer!$jobInfo", Toast.LENGTH_SHORT).show()
                                    },
                                    onFailure = { err ->
                                        Toast.makeText(context, "❌ Pi Print Error: ${err.message}", Toast.LENGTH_LONG).show()
                                    }
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = EmeraldGreen,
                            contentColor = Color(0xFF070B14)
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .padding(bottom = 6.dp)
                    ) {
                        if (isPrinting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = Color(0xFF070B14),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "SENDING TO PI PRINTER...",
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "PRINT TO TRC_PRINTER (PI)",
                                fontWeight = FontWeight.Black,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
                }

                // Bottom Export Buttons (Save to Gallery & Share Sheet)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Save to Android Gallery
                    Button(
                        onClick = {
                            scope.launch {
                                val bmp = processedBitmap ?: loadedBitmap
                                if (bmp != null) {
                                    val uri = BitmapUtils.saveToGallery(
                                        context = context,
                                        bitmap = bmp,
                                        title = "TRC_${capture.id}_${selectedFilter.id}"
                                    )
                                    if (uri != null) {
                                        Toast.makeText(context, "Saved to Photos gallery!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Failed to save photo", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Image still loading...", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberCyan,
                            contentColor = Color.Black
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Save to Photos",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    // Native Share Sheet
                    Button(
                        onClick = {
                            val bmp = processedBitmap ?: loadedBitmap
                            if (bmp != null) {
                                BitmapUtils.shareBitmap(
                                    context = context,
                                    bitmap = bmp,
                                    title = "Share TRC Booth Photo"
                                )
                            } else {
                                Toast.makeText(context, "Image not ready", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = NeonPink,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Share,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Share",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        }
    }

    // Delete Confirmation Dialog
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = Color(0xFF0F172A),
            title = {
                Text(
                    text = "Delete Capture?",
                    color = TextMain,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "This will permanently remove '${capture.filename}' from the booth storage.",
                    color = TextMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDelete(capture.id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFEF4444),
                        contentColor = Color.White
                    )
                ) {
                    Text("Delete", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TextMuted)
                }
            }
        )
    }
}
